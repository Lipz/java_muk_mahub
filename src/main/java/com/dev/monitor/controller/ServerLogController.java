package com.dev.monitor.controller;

import com.dev.monitor.dto.server.ServerLogCreateRequest;
import com.dev.monitor.dto.server.ServerLogResponse;
import com.dev.monitor.entity.server.ServerLog;
import com.dev.monitor.services.LogFileWriterService;
import com.dev.monitor.services.LogTailService;
import com.dev.monitor.services.ServerLogService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/server-logs")
public class ServerLogController {

    private final ServerLogService serverLogService;
    private final LogTailService logTailService;
    private final LogFileWriterService logFileWriterService;

    public ServerLogController(ServerLogService serverLogService, LogTailService logTailService,
                               LogFileWriterService logFileWriterService) {
        this.serverLogService = serverLogService;
        this.logTailService = logTailService;
        this.logFileWriterService = logFileWriterService;
    }

    @PostMapping
    public ResponseEntity<ServerLogResponse> createServerLog(@Valid @RequestBody ServerLogCreateRequest request) {
        ServerLogResponse response = serverLogService.createServerLog(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ServerLogResponse>> getLogs(
            @RequestParam(required = false) String server,
            @RequestParam(required = false) String serverId,
            @RequestParam(required = false) String serverName) {
        String identifier = (server != null && !server.isBlank()) ? server
                : (serverId != null && !serverId.isBlank() ? serverId : serverName);
        List<ServerLogResponse> logs = serverLogService.getLogs(identifier);
        return ResponseEntity.ok(logs);
    }

    /**
     * Live tail of one log channel as Server-Sent Events: a {@code backfill} event with the end
     * of today's file, then a {@code line} event per new log line, and a final {@code evicted}
     * event when the user opened too many other tails.
     */
    @GetMapping(path = "/{logUuid}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamLog(@PathVariable String logUuid, Principal principal) {
        // Not an exception: the JSON error body cannot be written to an event stream
        return serverLogService.findChannel(logUuid)
                .map(sl -> ResponseEntity.ok(logTailService.open(principal.getName(), sl)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Today's file of one log channel as a download named {@code {channel}_{yyyy-MM-dd}.log}.
     * It stops at the length the file had when the request came in, so {@code Content-Length}
     * holds while lines keep being appended. 404 when nothing was written today.
     */
    @GetMapping("/{logUuid}/download")
    public ResponseEntity<StreamingResponseBody> downloadToday(@PathVariable String logUuid) throws IOException {
        Optional<ServerLog> serverLog = serverLogService.findChannel(logUuid);
        if (serverLog.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Optional<LogFileWriterService.FileSnapshot> snapshot =
                logFileWriterService.snapshotToday(serverLog.get().getSavePath());
        if (snapshot.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        LogFileWriterService.FileSnapshot file = snapshot.get();
        String filename = serverLog.get().getChannel() + "_" + file.date().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".log";
        StreamingResponseBody body = out -> {
            try (InputStream in = Files.newInputStream(file.path())) {
                byte[] buf = new byte[8192];
                long left = file.length();
                while (left > 0) {
                    int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                    if (n < 0) {
                        break;
                    }
                    out.write(buf, 0, n);
                    left -= n;
                }
            }
        };

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .contentLength(file.length())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
