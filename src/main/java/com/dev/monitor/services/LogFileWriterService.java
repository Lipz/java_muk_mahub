package com.dev.monitor.services;

import com.dev.monitor.dto.server.ServerLogPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LogFileWriterService {

    private static final Logger log = LoggerFactory.getLogger(LogFileWriterService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${log.storage.base-path:./logs}")
    private String basePath;

    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Object> fileLocks = new ConcurrentHashMap<>();

    public LogFileWriterService() {
        this.objectMapper = new ObjectMapper();
    }

    public LogFileWriterService(ObjectMapper objectMapper) {
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

    /**
     * Asynchronously process and append the incoming log message to /logs/{server}/{channel}/{yyyy-MM-dd.log}
     */
    public CompletableFuture<Void> writeLogAsync(String channel, String rawBody) {
        return CompletableFuture.runAsync(() -> writeLog(channel, rawBody), executor);
    }

    private void writeLog(String channel, String rawBody) {
        String serverName;
        String logLine;

        try {
            ServerLogPayload payload = objectMapper.readValue(rawBody, ServerLogPayload.class);
            serverName = (payload.serverName() != null && !payload.serverName().isBlank())
                    ? payload.serverName()
                    : (payload.serverIp() != null && !payload.serverIp().isBlank() ? payload.serverIp() : channel);
            logLine = payload.message() != null ? payload.message() : rawBody;
        } catch (Exception e) {
            // Fallback for non-JSON or raw text messages (e.g. CLI testing)
            log.debug("Non-JSON message on channel [{}], using raw content: {}", channel, e.getMessage());
            serverName = channel;
            logLine = rawBody;
        }

        // Sanitize server name and channel to prevent path traversal
        String safeServerName = sanitizeFileName(serverName, "unknown_server");
        String safeChannel = sanitizeFileName(channel, "unknown_channel");

        // Date direct from server in yyyy-MM-dd format
        String fileName = LocalDate.now().format(DATE_FORMATTER) + ".log";

        Path channelDir = Paths.get(basePath, safeServerName, safeChannel);
        Path logFilePath = channelDir.resolve(fileName);

        // Lock per file to prevent concurrent thread write collisions
        Object lock = fileLocks.computeIfAbsent(logFilePath.toString(), k -> new Object());
        synchronized (lock) {
            try {
                if (!Files.exists(channelDir)) {
                    Files.createDirectories(channelDir);
                }

                Files.writeString(
                        logFilePath,
                        logLine + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException e) {
                log.error("Failed to write log to [{}]: {}", logFilePath, e.getMessage(), e);
            }
        }
    }

    private String sanitizeFileName(String name) {
        return sanitizeFileName(name, "unknown_server");
    }

    private String sanitizeFileName(String name, String defaultName) {
        if (name == null || name.isBlank()) {
            return defaultName;
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
