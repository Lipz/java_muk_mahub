package com.dev.monitor.services;

import com.dev.monitor.dto.server.ServerStoragePayload;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ingests storage- messages into the server_storage and
 * server_storage_summary hypertables.
 *
 * Same architecture as {@link ServerResourceWriterService}, with two
 * differences:
 *
 *  - Fan-out. One message carries a host rollup plus an array of mounts, so
 *    it produces N mount rows plus one summary row, written as two batches.
 *  - No presence touch. Presence is driven by resource-info at a 3-5s
 *    cadence with a 15s TTL; an hourly storage message would set a key that
 *    expires 15 seconds later, making a server blink "online" once an hour.
 *
 * At roughly 24 messages per server per day, the flush timer is not doing
 * throughput work -- it just bounds latency. The batching still matters
 * across the mounts of a single scrape.
 */
@Service
public class ServerStorageWriterService {

    private static final Logger log = LoggerFactory.getLogger(ServerStorageWriterService.class);

    private static final long UNKNOWN_SERVER_TTL_MS = 60_000;
    private static final int FLUSH_THRESHOLD = 500;
    private static final long FLUSH_INTERVAL_MS = 1_000;
    private static final int QUEUE_CAPACITY = 20_000;

    private static final String INSERT_MOUNT_SQL = """
            INSERT INTO server_storage (
                server_id, mount_point, record_timestamp,
                device, fstype,
                total_bytes, used_bytes, free_bytes, reserved_bytes, used_pct,
                error, extra
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?::jsonb)
            ON CONFLICT (server_id, mount_point, record_timestamp) DO NOTHING
            """;

    private static final int[] MOUNT_TYPES = {
            Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE,
            Types.VARCHAR, Types.VARCHAR,
            Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.REAL,
            Types.VARCHAR, Types.VARCHAR
    };

    private static final String INSERT_SUMMARY_SQL = """
            INSERT INTO server_storage_summary (
                server_id, record_timestamp,
                total_bytes, used_bytes, free_bytes, reserved_bytes, used_pct, partial
            ) VALUES (?,?,?,?,?,?,?,?)
            ON CONFLICT (server_id, record_timestamp) DO NOTHING
            """;

    private static final int[] SUMMARY_TYPES = {
            Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE,
            Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.REAL, Types.BOOLEAN
    };

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ServerRepository serverRepository;

    private final ExecutorService parseExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService flusher =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "storage-flusher");
                t.setDaemon(true);
                return t;
            });

    private final BlockingQueue<Object[]> pendingMounts = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final BlockingQueue<Object[]> pendingSummaries = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    private final Set<String> knownServers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> unknownServers = new ConcurrentHashMap<>();

    private final AtomicLong mountRows = new AtomicLong();
    private final AtomicLong summaryRows = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    public ServerStorageWriterService(ObjectMapper objectMapper,
                                      JdbcTemplate jdbcTemplate,
                                      ServerRepository serverRepository) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.serverRepository = serverRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        serverRepository.findAll().forEach(s -> knownServers.add(s.getUuid()));
        flusher.scheduleWithFixedDelay(
                this::flushQuietly, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public CompletableFuture<Void> writeStorageAsync(String channel, String rawBody) {
        return CompletableFuture.runAsync(() -> ingest(channel, rawBody), parseExecutor);
    }

    private void ingest(String channel, String rawBody) {
        ServerStoragePayload payload;
        try {
            payload = objectMapper.readValue(rawBody, ServerStoragePayload.class);
        } catch (Exception e) {
            log.warn("Invalid storage payload on channel [{}], dropped: {}", channel, e.getMessage());
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

        Instant recordedAt = payload.timestamp() != null ? payload.timestamp() : Instant.now();
        OffsetDateTime ts = OffsetDateTime.ofInstant(recordedAt, ZoneOffset.UTC);

        if (payload.server() != null) {
            enqueue(pendingSummaries, summaryRow(serverId, ts, payload.server()));
        }

        if (payload.mounts() != null) {
            // The raw mount nodes, kept alongside the parsed ones. Re-serializing
            // the DTO would be pointless here: it is declared ignoreUnknown, so
            // by that point any field we do not map has already been discarded --
            // exactly the fields `extra` exists to preserve.
            List<String> rawMounts = rawMountNodes(rawBody, payload.mounts().size());

            for (int i = 0; i < payload.mounts().size(); i++) {
                ServerStoragePayload.Mount mount = payload.mounts().get(i);
                if (mount == null || mount.path() == null || mount.path().isBlank()) {
                    // path is part of the key; without it the row cannot exist.
                    log.warn("Mount without a path on channel [{}] for server [{}], skipped",
                            channel, serverId);
                    continue;
                }
                enqueue(pendingMounts, mountRow(serverId, ts, mount, rawMounts.get(i)));
            }
        }

        if (pendingMounts.size() >= FLUSH_THRESHOLD || pendingSummaries.size() >= FLUSH_THRESHOLD) {
            flushQuietly();
        }
    }

    private void enqueue(BlockingQueue<Object[]> queue, Object[] row) {
        if (!queue.offer(row)) {
            long total = dropped.incrementAndGet();
            if (total % 100 == 1) {
                log.error("Storage ingest queue full ({} rows); dropped {} total. "
                        + "Is the database keeping up?", QUEUE_CAPACITY, total);
            }
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

    /**
     * Pulls the untouched mounts[] elements out of the original message, so
     * `extra` preserves fields the DTO does not declare. Returns a list of
     * the requested size, padded with nulls if the tree cannot be read.
     */
    private List<String> rawMountNodes(String rawBody, int expected) {
        List<String> raw = new ArrayList<>(expected);
        try {
            JsonNode mounts = objectMapper.readTree(rawBody).path("mounts");
            for (int i = 0; i < expected; i++) {
                JsonNode node = mounts.path(i);
                raw.add(node.isObject() ? node.toString() : null);
            }
        } catch (Exception e) {
            // extra is a convenience, never a reason to drop the row
            log.debug("Could not re-read raw mounts for extra: {}", e.getMessage());
            while (raw.size() < expected) {
                raw.add(null);
            }
        }
        return raw;
    }

    private static Object[] mountRow(String serverId, OffsetDateTime ts,
                                     ServerStoragePayload.Mount m, String rawMount) {
        return new Object[]{
                serverId,
                m.path(),
                ts,
                m.device(),
                m.fsType(),
                m.totalBytes(),
                m.usedBytes(),
                m.freeBytes(),
                m.reservedBytes(),
                toFloat(m.usedPercent()),
                m.error(),
                // The mount object exactly as the agent sent it. At an hourly
                // cadence this costs nothing and is the only place an
                // unmapped field survives.
                rawMount
        };
    }

    private static Object[] summaryRow(String serverId, OffsetDateTime ts,
                                       ServerStoragePayload.Summary s) {
        return new Object[]{
                serverId,
                ts,
                s.totalBytes(),
                s.usedBytes(),
                s.freeBytes(),
                s.reservedBytes(),
                toFloat(s.usedPercent()),
                s.partial()
        };
    }

    private static Float toFloat(Double v) {
        return v == null ? null : v.floatValue();
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (Exception e) {
            log.error("Storage batch flush failed: {}", e.getMessage(), e);
        }
    }

    void flush() {
        // Summaries first: a mount row is only meaningful alongside the
        // rollup for the same scrape.
        drainAndWrite(pendingSummaries, INSERT_SUMMARY_SQL, SUMMARY_TYPES, summaryRows);
        drainAndWrite(pendingMounts, INSERT_MOUNT_SQL, MOUNT_TYPES, mountRows);
    }

    private void drainAndWrite(BlockingQueue<Object[]> queue, String sql, int[] types,
                               AtomicLong counter) {
        List<Object[]> batch = new ArrayList<>(FLUSH_THRESHOLD);
        queue.drainTo(batch, FLUSH_THRESHOLD);
        if (batch.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(sql, batch, types);
        counter.addAndGet(batch.size());
    }

    @PreDestroy
    void shutdown() {
        flusher.shutdown();
        parseExecutor.shutdown();
        flushQuietly();
        log.info("Storage ingest stopped after {} mount rows, {} summary rows, {} dropped",
                mountRows.get(), summaryRows.get(), dropped.get());
    }

    public long mountRowCount() {
        return mountRows.get();
    }

    public long summaryRowCount() {
        return summaryRows.get();
    }
}
