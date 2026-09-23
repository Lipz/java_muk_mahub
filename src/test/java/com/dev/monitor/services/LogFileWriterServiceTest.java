package com.dev.monitor.services;

import com.dev.monitor.entity.server.Server;
import com.dev.monitor.entity.server.ServerLog;
import com.dev.monitor.entity.server.ServerType;
import com.dev.monitor.entity.system.SystemEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;
// import com.fasterxml.jackson.databind.ObjectMapper;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogFileWriterServiceTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @TempDir
    Path tempDir;

    @Mock
    private ServerLogService serverLogService;

    @Mock
    private LogStreamBroadcaster broadcaster;

    private LogFileWriterService service;
    private Server server;

    @BeforeEach
    void setUp() {
        service = new LogFileWriterService(new ObjectMapper(), serverLogService, broadcaster);

        SystemEntity system = new SystemEntity("ecustoms");
        system.setUuid("sys-01");
        server = new Server(system, "app-server-01", "10.0.0.5", ServerType.DATABASE);
        server.setUuid("srv-01");
    }

    private ServerLog channel(String channel, Path savePath) {
        return new ServerLog(server, channel, "/var/log/app/app.log", savePath.toString());
    }

    private Path todayFile(Path dir) {
        return dir.resolve(LocalDate.now().format(DATE_FORMATTER) + ".log");
    }

    private static String payload(String serverId, String message) {
        return """
            {"serverId": "%s", "path": "/var/log/app/app.log", "message": "%s"}
            """.formatted(serverId, message);
    }

    @Test
    void shouldWriteMessageToSavePathOfResolvedChannel() throws Exception {
        Path saveDir = tempDir.resolve("app-server-01").resolve("log-app");
        when(serverLogService.resolveOrCreateChannel("srv-01", "log-app", "/var/log/app/app.log"))
                .thenReturn(Optional.of(channel("log-app", saveDir)));

        service.writeLogAsync("log-app", payload("srv-01", "User 101 logged in successfully")).get();

        Path file = todayFile(saveDir);
        assertTrue(Files.exists(file), "Log file should exist");
        assertEquals(List.of("User 101 logged in successfully"), Files.readAllLines(file));
        verify(broadcaster).publish("srv-01|log-app", null, "User 101 logged in successfully");
    }

    @Test
    void shouldAppendAndResolveChannelOnlyOnce() throws Exception {
        Path saveDir = tempDir.resolve("web");
        when(serverLogService.resolveOrCreateChannel(anyString(), anyString(), any()))
                .thenReturn(Optional.of(channel("log-web", saveDir)));

        service.writeLogAsync("log-web", payload("srv-01", "Line 1: Started")).get();
        service.writeLogAsync("log-web", payload("srv-01", "Line 2: Stopped")).get();

        assertEquals(List.of("Line 1: Started", "Line 2: Stopped"), Files.readAllLines(todayFile(saveDir)));
        verify(serverLogService, times(1)).resolveOrCreateChannel(anyString(), anyString(), any());
    }

    @Test
    void shouldUseWarmedCacheWithoutHittingDb() throws Exception {
        Path saveDir = tempDir.resolve("warm");
        when(serverLogService.getAllChannels()).thenReturn(List.of(channel("log-app", saveDir)));

        service.warmCache();
        service.writeLogAsync("log-app", payload("srv-01", "from cache")).get();

        assertEquals(List.of("from cache"), Files.readAllLines(todayFile(saveDir)));
        verify(serverLogService, never()).resolveOrCreateChannel(anyString(), anyString(), any());
    }

    @Test
    void shouldDropNonJsonMessage() throws Exception {
        service.writeLogAsync("log-app", "Raw plain text log line").get();

        verify(serverLogService, never()).resolveOrCreateChannel(anyString(), anyString(), any());
        assertDirEmpty();
        verify(broadcaster, never()).publish(anyString(), any(), anyString());
    }

    @Test
    void shouldDropMessageWithoutServerId() throws Exception {
        service.writeLogAsync("log-app", """
            {"serverName": "app-server-01", "message": "no id"}
            """).get();

        verify(serverLogService, never()).resolveOrCreateChannel(anyString(), anyString(), any());
        assertDirEmpty();
    }

    @Test
    void shouldDropUnknownServerAndNotRequeryWithinTtl() throws Exception {
        when(serverLogService.resolveOrCreateChannel(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        service.writeLogAsync("log-app", payload("ghost", "first")).get();
        service.writeLogAsync("log-app", payload("ghost", "second")).get();

        verify(serverLogService, times(1)).resolveOrCreateChannel(anyString(), anyString(), any());
        assertDirEmpty();
    }

    @Test
    void shouldRetryResolveOnConcurrentCreate() throws Exception {
        Path saveDir = tempDir.resolve("race");
        when(serverLogService.resolveOrCreateChannel(anyString(), anyString(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"))
                .thenReturn(Optional.of(channel("log-app", saveDir)));

        service.writeLogAsync("log-app", payload("srv-01", "after race")).get();

        assertEquals(List.of("after race"), Files.readAllLines(todayFile(saveDir)));
        verify(serverLogService, times(2)).resolveOrCreateChannel(anyString(), anyString(), any());
    }

    @Test
    void shouldKeepPublishOrderWithinChannelUnderBurst() throws Exception {
        Path appDir = tempDir.resolve("app");
        Path webDir = tempDir.resolve("web");
        when(serverLogService.resolveOrCreateChannel("srv-01", "log-app", "/var/log/app/app.log"))
                .thenReturn(Optional.of(channel("log-app", appDir)));
        when(serverLogService.resolveOrCreateChannel("srv-01", "log-web", "/var/log/app/app.log"))
                .thenReturn(Optional.of(channel("log-web", webDir)));

        // Fire without waiting, interleaving two channels, like the Redis dispatch thread does
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            writes.add(service.writeLogAsync("log-app", payload("srv-01", "app " + i)));
            writes.add(service.writeLogAsync("log-web", payload("srv-01", "web " + i)));
        }
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).get();

        assertEquals(IntStream.range(0, 500).mapToObj(i -> "app " + i).toList(),
                Files.readAllLines(todayFile(appDir)));
        assertEquals(IntStream.range(0, 500).mapToObj(i -> "web " + i).toList(),
                Files.readAllLines(todayFile(webDir)));
    }

    @Test
    void shouldKeepLaneRunningAfterFailedLine() throws Exception {
        Path saveDir = tempDir.resolve("recover");
        when(serverLogService.resolveOrCreateChannel(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("db down"))
                .thenReturn(Optional.of(channel("log-app", saveDir)));

        CompletableFuture<Void> failed = service.writeLogAsync("log-app", payload("srv-01", "lost"));
        service.writeLogAsync("log-app", payload("srv-01", "kept")).get();

        assertTrue(failed.isCompletedExceptionally());
        assertEquals(List.of("kept"), Files.readAllLines(todayFile(saveDir)));
    }

    @Test
    void shouldSnapshotNothingWhenNoFileToday() throws Exception {
        assertTrue(service.snapshotToday(tempDir.resolve("none").toString()).isEmpty());
    }

    @Test
    void shouldSnapshotTodaysFileLengthAndDate() throws Exception {
        Path saveDir = tempDir.resolve("snap");
        Files.createDirectories(saveDir);
        Files.writeString(todayFile(saveDir), "a\nbc\n");

        LogFileWriterService.FileSnapshot snap = service.snapshotToday(saveDir.toString()).orElseThrow();

        assertEquals(todayFile(saveDir), snap.path());
        assertEquals(LocalDate.now(), snap.date());
        assertEquals(5, snap.length());
    }

    @Test
    void shouldReadNothingWhenNoFileToday() throws Exception {
        assertEquals(List.of(), service.readTodayTail(tempDir.resolve("none").toString(), 200));
    }

    @Test
    void shouldReadWholeFileWhenShorterThanTail() throws Exception {
        Path saveDir = tempDir.resolve("short");
        Files.createDirectories(saveDir);
        Files.writeString(todayFile(saveDir), "a\nb\n");

        assertEquals(List.of("a", "b"), service.readTodayTail(saveDir.toString(), 200));
    }

    @Test
    void shouldReadOnlyLastLinesAcrossChunks() throws Exception {
        Path file = tempDir.resolve("long.log");
        // ~150 bytes a line, so 200 lines span several 8 KiB chunks
        List<String> written = IntStream.range(0, 1_000)
                .mapToObj(i -> "line " + i + " " + "x".repeat(140)).toList();
        Files.write(file, written);

        assertEquals(written.subList(800, 1_000), LogFileWriterService.tailLines(file, 200));
        assertEquals(List.of(written.get(999)), LogFileWriterService.tailLines(file, 1));
    }

    @Test
    void shouldStripCarriageReturnsAndKeepUtf8() throws Exception {
        Path file = tempDir.resolve("crlf.log");
        Files.writeString(file, "first\r\nsecond é ✓\r\n");

        assertEquals(List.of("first", "second é ✓"), LogFileWriterService.tailLines(file, 10));
    }

    @Test
    void shouldCapTailBytesAndDropPartialLine() throws Exception {
        Path file = tempDir.resolve("huge.log");
        // 5 lines of 400 KiB: 200 of them cannot fit in the 1 MiB cap
        List<String> written = IntStream.range(0, 5)
                .mapToObj(i -> i + "y".repeat(400 * 1024)).toList();
        Files.write(file, written);

        List<String> tail = LogFileWriterService.tailLines(file, 200);

        assertEquals(written.subList(3, 5), tail);
    }

    @Test
    void shouldRunLaneTaskAfterLinesQueuedBeforeIt() throws Exception {
        Path saveDir = tempDir.resolve("lane");
        when(serverLogService.resolveOrCreateChannel(anyString(), anyString(), any()))
                .thenReturn(Optional.of(channel("log-app", saveDir)));

        for (int i = 0; i < 100; i++) {
            service.writeLogAsync("log-app", payload("srv-01", "line " + i));
        }
        List<List<String>> seen = new ArrayList<>();
        service.runOnLane("log-app", () -> {
            try {
                seen.add(service.readTodayTail(saveDir.toString(), 1_000));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).get();

        assertEquals(100, seen.get(0).size());
        assertEquals("line 99", seen.get(0).get(99));
    }

    private void assertDirEmpty() throws Exception {
        try (var entries = Files.list(tempDir)) {
            assertFalse(entries.findAny().isPresent(), "No log file should be written");
        }
    }
}
