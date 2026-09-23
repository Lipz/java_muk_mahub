package com.dev.monitor.services;

import com.dev.monitor.dto.server.ServerResourcePayload;
import com.dev.monitor.repository.server.ServerRepository;
import jakarta.annotation.PreDestroy;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Ingests resource-info messages into the server_resources hypertable.
 *
 * Why this bypasses JPA: at a 3-5s scrape interval across a fleet, one
 * persist() per message in its own transaction is mostly transaction
 * overhead. Rows are buffered and flushed as a JDBC batch instead.
 *
 * ON CONFLICT DO NOTHING makes ingest idempotent. That is a direct dividend
 * of the natural primary key (server_id, record_timestamp): a redelivered
 * Redis message collides with the row it already wrote and is skipped, so
 * at-least-once delivery cannot produce duplicates.
 *
 * Unknown servers are cached with a TTL rather than retried per message,
 * mirroring LogFileWriterService -- the FK on server_id would otherwise
 * reject every row from an unregistered agent, once per message.
 */
@Service
public class ServerResourceWriterService {

    private static final Logger log = LoggerFactory.getLogger(ServerResourceWriterService.class);

    private static final long UNKNOWN_SERVER_TTL_MS = 60_000;
    /** Flush when this many rows are pending, without waiting for the timer. */
    private static final int FLUSH_THRESHOLD = 500;
    private static final long FLUSH_INTERVAL_MS = 1_000;
    /** Bounded so a database outage sheds load instead of exhausting the heap. */
    private static final int QUEUE_CAPACITY = 50_000;

    private static final String INSERT_SQL = """
            INSERT INTO server_resources (
                server_id, record_timestamp, uptime_seconds,
                cpu_count, cpu_usage_pct, load_avg_1m, load_avg_5m, load_avg_15m,
                mem_total_bytes, mem_used_bytes, mem_available_bytes, mem_used_pct, mem_estimated,
                swap_total_bytes, swap_used_bytes, swap_used_pct,
                net_rx_bps, net_tx_bps
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (server_id, record_timestamp) DO NOTHING
            """;

    /** Explicit SQL types: PostgreSQL cannot infer the type of an untyped NULL. */
    private static final int[] ARG_TYPES = {
            Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE, Types.BIGINT,
            Types.SMALLINT, Types.REAL, Types.REAL, Types.REAL, Types.REAL,
            Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.REAL, Types.BOOLEAN,
            Types.BIGINT, Types.BIGINT, Types.REAL,
            Types.BIGINT, Types.BIGINT
    };

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ServerRepository serverRepository;
    private final ServerPresenceService presenceService;

    private final ExecutorService parseExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService flusher =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "resource-flusher");
                t.setDaemon(true);
                return t;
            });

    private final BlockingQueue<Object[]> pending = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    private final Set<String> knownServers = ConcurrentHashMap.newKeySet();
    /** serverId -> expiry (epoch ms), so an unregistered agent does not hit the DB per message. */
    private final Map<String, Long> unknownServers = new ConcurrentHashMap<>();

    private final AtomicLong written = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    public ServerResourceWriterService(ObjectMapper objectMapper,
                                       JdbcTemplate jdbcTemplate,
                                       ServerRepository serverRepository,
                                       ServerPresenceService presenceService) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.serverRepository = serverRepository;
        this.presenceService = presenceService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        serverRepository.findAll().forEach(s -> knownServers.add(s.getUuid()));
        log.info("Loaded [{}] registered servers into resource ingest cache", knownServers.size());
        flusher.scheduleWithFixedDelay(
                this::flushQuietly, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /** Parse off the Redis listener thread, then enqueue for the next batch. */
    public CompletableFuture<Void> writeResourceAsync(String channel, String rawBody) {
        return CompletableFuture.runAsync(() -> ingest(channel, rawBody), parseExecutor);
    }

    private void ingest(String channel, String rawBody) {
        ServerResourcePayload payload;
        try {
            payload = objectMapper.readValue(rawBody, ServerResourcePayload.class);
        } catch (Exception e) {
            log.warn("Invalid resource payload on channel [{}], dropped: {}", channel, e.getMessage());
            return;
        }

        String serverId = payload.serverId();
        if (serverId == null || serverId.isBlank()) {
            log.warn("Missing serverId on channel [{}], dropped", channel);
            return;
        }

        if (!isKnownServer(serverId, channel)) {
            return;
        }

        // Mark the server alive on receipt, before buffering. Presence answers
        // "can we still hear it", which is true regardless of whether the row
        // survives the batch insert.
        presenceService.touch(serverId);

        // The agent's own timestamp is when the sample was taken, which is the
        // honest value for a metric row. Fall back to receive time only if absent.
        Instant recordedAt = payload.timestamp() != null ? payload.timestamp() : Instant.now();

        if (!pending.offer(toRow(serverId, recordedAt, payload))) {
            long total = dropped.incrementAndGet();
            if (total % 100 == 1) {
                log.error("Resource ingest queue full ({} rows); dropped {} total. "
                        + "Is the database keeping up?", QUEUE_CAPACITY, total);
            }
            return;
        }

        if (pending.size() >= FLUSH_THRESHOLD) {
            flushQuietly();
        }
    }

    private boolean isKnownServer(String serverId, String channel) {
        if (knownServers.contains(serverId)) {
            return true;
        }

        Long unknownUntil = unknownServers.get(serverId);
        if (unknownUntil != null && unknownUntil > System.currentTimeMillis()) {
            return false;
        }

        if (serverRepository.existsById(serverId)) {
            knownServers.add(serverId);
            unknownServers.remove(serverId);
            return true;
        }

        log.warn("Unknown serverId [{}] on channel [{}], dropping messages for {}s",
                serverId, channel, UNKNOWN_SERVER_TTL_MS / 1000);
        unknownServers.put(serverId, System.currentTimeMillis() + UNKNOWN_SERVER_TTL_MS);
        return false;
    }

    private static Object[] toRow(String serverId, Instant recordedAt, ServerResourcePayload p) {
        ServerResourcePayload.Cpu cpu = p.cpu();
        ServerResourcePayload.Memory mem = p.memory();
        ServerResourcePayload.Swap swap = p.swap();
        ServerResourcePayload.Network net = p.network();

        return new Object[]{
                serverId,
                OffsetDateTime.ofInstant(recordedAt, ZoneOffset.UTC),
                p.uptimeSeconds(),

                cpu == null ? null : toShort(cpu.count()),
                cpu == null ? null : toFloat(cpu.usedPercent()),
                cpu == null ? null : toFloat(cpu.load1()),
                cpu == null ? null : toFloat(cpu.load5()),
                cpu == null ? null : toFloat(cpu.load15()),

                mem == null ? null : mem.totalBytes(),
                mem == null ? null : mem.usedBytes(),
                mem == null ? null : mem.availableBytes(),
                mem == null ? null : toFloat(mem.usedPercent()),
                mem == null ? null : mem.estimated(),

                swap == null ? null : swap.totalBytes(),
                swap == null ? null : swap.usedBytes(),
                swap == null ? null : toFloat(swap.usedPercent()),

                // Rates arrive as fractional doubles; sub-byte/sec precision is
                // meaningless and integers aggregate without float drift.
                net == null ? null : toRoundedLong(net.rxBytesPerSec()),
                net == null ? null : toRoundedLong(net.txBytesPerSec())
        };
    }

    private static Float toFloat(Double v) {
        return v == null ? null : v.floatValue();
    }

    private static Short toShort(Integer v) {
        return v == null ? null : v.shortValue();
    }

    private static Long toRoundedLong(Double v) {
        return v == null ? null : Math.round(v);
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (Exception e) {
            log.error("Resource batch flush failed: {}", e.getMessage(), e);
        }
    }

    /** Drains whatever is buffered and writes it as one JDBC batch. */
    void flush() {
        List<Object[]> batch = new ArrayList<>(FLUSH_THRESHOLD);
        pending.drainTo(batch, FLUSH_THRESHOLD);
        if (batch.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(INSERT_SQL, batch, ARG_TYPES);
        long total = written.addAndGet(batch.size());
        log.debug("Flushed [{}] resource rows ({} total)", batch.size(), total);
    }

    @PreDestroy
    void shutdown() {
        flusher.shutdown();
        parseExecutor.shutdown();
        flushQuietly();
        log.info("Resource ingest stopped after {} rows written, {} dropped",
                written.get(), dropped.get());
    }

    /** Test/diagnostic accessors. */
    public long writtenCount() {
        return written.get();
    }

    public int pendingCount() {
        return pending.size();
    }
}
