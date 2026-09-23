package com.dev.monitor.services;

import com.dev.monitor.dto.server.ServerLogPayload;
import com.dev.monitor.entity.server.ServerLog;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
// import com.fasterxml.jackson.databind.ObjectMapper;


@Service
public class LogFileWriterService {

    private static final Logger log = LoggerFactory.getLogger(LogFileWriterService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long UNKNOWN_SERVER_TTL_MS = 60_000;
    // Upper bound on what a backfill reads, however long the lines are
    private static final int MAX_TAIL_BYTES = 1024 * 1024;
    private static final int TAIL_CHUNK_BYTES = 8192;

    private final ObjectMapper objectMapper;
    private final ServerLogService serverLogService;
    private final LogStreamBroadcaster broadcaster;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Object> fileLocks = new ConcurrentHashMap<>();

    // channel -> last queued task. Each message chains onto the previous one of its
    // channel, so lines are written in publish order while channels run in parallel.
    private final Map<String, CompletableFuture<Void>> lanes = new ConcurrentHashMap<>();

    // "serverId|channel" -> savePath, so the DB is only hit once per channel
    private final Map<String, String> savePathCache = new ConcurrentHashMap<>();
    // serverId -> expiry (epoch ms), so an unregistered server does not hit the DB on every line
    private final Map<String, Long> unknownServers = new ConcurrentHashMap<>();

    public LogFileWriterService(ObjectMapper objectMapper, ServerLogService serverLogService,
                                LogStreamBroadcaster broadcaster) {
        this.objectMapper = objectMapper;
        this.serverLogService = serverLogService;
        this.broadcaster = broadcaster;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmCache() {
        serverLogService.getAllChannels().forEach(sl ->
                savePathCache.put(cacheKey(sl.getServer().getUuid(), sl.getChannel()), sl.getSavePath()));
        log.info("Loaded [{}] log channels into cache", savePathCache.size());
    }

    /**
     * Asynchronously resolve the channel of the payload's server, then append the message
     * to {savePath}/{yyyy-MM-dd}.log. Messages of the same channel are written in call order.
     */
    public CompletableFuture<Void> writeLogAsync(String channel, String rawBody) {
        return runOnLane(channel, () -> ingest(channel, rawBody));
    }

    /**
     * Run {@code task} on the lane of {@code channel}: after every line queued before it and
     * before every line queued after it. Nothing is written to the channel's files meanwhile.
     */
    public CompletableFuture<Void> runOnLane(String channel, Runnable task) {
        CompletableFuture<Void> next = lanes.compute(channel, (k, tail) ->
                (tail == null ? CompletableFuture.<Void>completedFuture(null) : tail)
                        // handle, not thenRun: a failed task must not stall the rest of the lane
                        .handleAsync((ignored, previousFailure) -> {
                            task.run();
                            return null;
                        }, executor));
        // Drop the lane once idle so the map only holds channels with work in flight
        next.whenComplete((ignored, failure) -> lanes.remove(channel, next));
        return next;
    }

    private void ingest(String channel, String rawBody) {
        ServerLogPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, ServerLogPayload.class);
        } catch (Exception e) {
            log.warn("Invalid log payload on channel [{}], dropped: {}", channel, e.getMessage());
            return;
        }

        String serverId = payload.serverId();
        if (serverId == null || serverId.isBlank()) {
            log.warn("Missing serverId on channel [{}], dropped", channel);
            return;
        }

        String key = cacheKey(serverId, channel);
        String savePath = savePathCache.get(key);
        if (savePath == null) {
            Long unknownUntil = unknownServers.get(serverId);
            if (unknownUntil != null && unknownUntil > System.currentTimeMillis()) {
                return;
            }

            savePath = resolveSavePath(serverId, channel, payload.path());
            if (savePath == null) {
                log.warn("Unknown serverId [{}] on channel [{}], dropping messages for {}s",
                        serverId, channel, UNKNOWN_SERVER_TTL_MS / 1000);
                unknownServers.put(serverId, System.currentTimeMillis() + UNKNOWN_SERVER_TTL_MS);
                return;
            }
            unknownServers.remove(serverId);
            savePathCache.put(key, savePath);
        }

        String line = payload.message() != null ? payload.message() : rawBody;
        append(Paths.get(savePath), line);
        // Still on the channel's lane, so live viewers get lines in the same order as the file
        broadcaster.publish(LogStreamBroadcaster.streamKey(serverId, channel), payload.timestamp(), line);
    }

    private String resolveSavePath(String serverId, String channel, String pubPath) {
        try {
            return serverLogService.resolveOrCreateChannel(serverId, channel, pubPath)
                    .map(ServerLog::getSavePath)
                    .orElse(null);
        } catch (DataIntegrityViolationException race) {
            // Another thread created the same (server_id, channel) first, read it back
            return serverLogService.resolveOrCreateChannel(serverId, channel, pubPath)
                    .map(ServerLog::getSavePath)
                    .orElse(null);
        }
    }

    /** Today's file as it was at one instant: {@code length} ends on a line break. */
    public record FileSnapshot(Path path, LocalDate date, long length) {}

    /**
     * Today's file under {@code savePath} and its current length, or empty when nothing was
     * written today. Measured under the append lock, so reading {@code length} bytes never
     * ends on half a line, however many lines are appended meanwhile.
     */
    public Optional<FileSnapshot> snapshotToday(String savePath) throws IOException {
        LocalDate today = LocalDate.now();
        Path logFilePath = Paths.get(savePath).resolve(today.format(DATE_FORMATTER) + ".log");
        Object lock = fileLocks.computeIfAbsent(logFilePath.toString(), k -> new Object());
        synchronized (lock) {
            if (!Files.exists(logFilePath)) {
                return Optional.empty();
            }
            long length = Files.size(logFilePath);
            return length == 0 ? Optional.empty() : Optional.of(new FileSnapshot(logFilePath, today, length));
        }
    }

    /**
     * Last {@code maxLines} lines of today's file under {@code savePath}, oldest first; empty
     * when nothing was written today. Call it on the channel's lane to get a consistent cut.
     */
    public List<String> readTodayTail(String savePath, int maxLines) throws IOException {
        Path logFilePath = todayFile(Paths.get(savePath));
        Object lock = fileLocks.computeIfAbsent(logFilePath.toString(), k -> new Object());
        synchronized (lock) {
            return tailLines(logFilePath, maxLines);
        }
    }

    /**
     * Scan backwards from the end in chunks, so a large file costs no more than its tail.
     */
    static List<String> tailLines(Path file, int maxLines) throws IOException {
        if (maxLines <= 0 || !Files.exists(file)) {
            return List.of();
        }
        try (SeekableByteChannel ch = Files.newByteChannel(file, StandardOpenOption.READ)) {
            long size = ch.size();
            long start = size;
            boolean truncated = false;
            int newlines = 0;
            ByteBuffer chunk = ByteBuffer.allocate(TAIL_CHUNK_BYTES);

            scan:
            while (start > 0) {
                if (size - start >= MAX_TAIL_BYTES) {
                    truncated = true;
                    break;
                }
                int len = (int) Math.min(TAIL_CHUNK_BYTES, start);
                start -= len;
                readFully(ch, chunk, start, len);
                for (int i = len - 1; i >= 0; i--) {
                    // Every line ends with a newline, so the one before the first wanted
                    // line is newline number maxLines + 1
                    if (chunk.get(i) == '\n' && ++newlines > maxLines) {
                        start += i + 1;
                        break scan;
                    }
                }
            }

            byte[] bytes = new byte[(int) (size - start)];
            readFully(ch, ByteBuffer.wrap(bytes), start, bytes.length);
            String[] parts = new String(bytes, StandardCharsets.UTF_8).split("\n");

            List<String> lines = new ArrayList<>(parts.length);
            // A byte-capped cut lands mid-line: drop that partial first line
            for (int i = truncated ? 1 : 0; i < parts.length; i++) {
                String line = parts[i];
                lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
            }
            return lines.size() > maxLines ? lines.subList(lines.size() - maxLines, lines.size()) : lines;
        }
    }

    private static void readFully(SeekableByteChannel ch, ByteBuffer buf, long position, int len)
            throws IOException {
        buf.clear().limit(len);
        ch.position(position);
        while (buf.hasRemaining() && ch.read(buf) >= 0) {
            // keep reading
        }
    }

    private static Path todayFile(Path channelDir) {
        return channelDir.resolve(LocalDate.now().format(DATE_FORMATTER) + ".log");
    }

    private void append(Path channelDir, String line) {
        Path logFilePath = todayFile(channelDir);

        // Lock per file to prevent concurrent thread write collisions
        Object lock = fileLocks.computeIfAbsent(logFilePath.toString(), k -> new Object());
        synchronized (lock) {
            try {
                Files.createDirectories(channelDir);
                Files.writeString(
                        logFilePath,
                        line + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException e) {
                log.error("Failed to write log to [{}]: {}", logFilePath, e.getMessage(), e);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private static String cacheKey(String serverId, String channel) {
        return serverId + "|" + channel;
    }
}
