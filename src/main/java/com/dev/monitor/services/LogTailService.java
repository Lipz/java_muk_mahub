package com.dev.monitor.services;

import com.dev.monitor.entity.server.ServerLog;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Opens a live tail: the end of today's file first, then every new line, with no gap and
 * no duplicate between the two.
 */
@Service
public class LogTailService {

    private static final Logger log = LoggerFactory.getLogger(LogTailService.class);

    private final LogFileWriterService writer;
    private final LogStreamBroadcaster broadcaster;
    private final int backfillLines;

    public LogTailService(LogFileWriterService writer, LogStreamBroadcaster broadcaster,
                          @Value("${log.stream.backfill-lines:200}") int backfillLines) {
        this.writer = writer;
        this.broadcaster = broadcaster;
        this.backfillLines = backfillLines;
    }

    public SseEmitter open(String username, ServerLog serverLog) {
        LogStreamBroadcaster.Tail tail = broadcaster.open(username,
                LogStreamBroadcaster.streamKey(serverLog.getServer().getUuid(), serverLog.getChannel()));

        // On the channel's lane: lines queued before this are in the file read, lines queued
        // after it reach the tail live
        writer.runOnLane(serverLog.getChannel(), () -> {
            List<String> lines = List.of();
            try {
                lines = writer.readTodayTail(serverLog.getSavePath(), backfillLines);
            } catch (Exception e) {
                // Still go live: a tail without history beats one that never starts
                log.warn("Backfill of [{}] failed: {}", serverLog.getSavePath(), e.getMessage());
            }
            tail.attach(lines);
        });
        return tail.emitter();
    }
}
