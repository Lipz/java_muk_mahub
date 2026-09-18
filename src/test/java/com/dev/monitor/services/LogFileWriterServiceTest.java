package com.dev.monitor.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileWriterServiceTest {

    @TempDir
    Path tempDir;

    private LogFileWriterService service;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @BeforeEach
    void setUp() {
        service = new LogFileWriterService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "basePath", tempDir.toString());
    }

    @Test
    void shouldWriteRawMessageToDatedFile() throws Exception {
        String json = """
            {
              "systemId": "sys-001",
              "systemName": "GDCE-Core",
              "serverName": "app-server-01",
              "serverIp": "10.0.0.5",
              "path": "/var/log/app/app.log",
              "channel": "log-app",
              "timestamp": "2026-05-28T10:00:00Z",
              "message": "User 101 logged in successfully"
            }
            """;

        service.writeLogAsync("log-app", json).get();

        String expectedDate = LocalDate.now().format(DATE_FORMATTER);
        Path expectedFile = tempDir.resolve("app-server-01").resolve("log-app").resolve(expectedDate + ".log");

        assertTrue(Files.exists(expectedFile), "Log file should exist");
        List<String> lines = Files.readAllLines(expectedFile);
        assertEquals(1, lines.size());
        assertEquals("User 101 logged in successfully", lines.get(0));
    }

    @Test
    void shouldAppendMultipleLines() throws Exception {
        String json1 = """
            {
              "serverName": "web-server-01",
              "message": "Line 1: Started"
            }
            """;

        String json2 = """
            {
              "serverName": "web-server-01",
              "message": "Line 2: Stopped"
            }
            """;

        service.writeLogAsync("log-web", json1).get();
        service.writeLogAsync("log-web", json2).get();

        String expectedDate = LocalDate.now().format(DATE_FORMATTER);
        Path expectedFile = tempDir.resolve("web-server-01").resolve("log-web").resolve(expectedDate + ".log");

        List<String> lines = Files.readAllLines(expectedFile);
        assertEquals(2, lines.size());
        assertEquals("Line 1: Started", lines.get(0));
        assertEquals("Line 2: Stopped", lines.get(1));
    }

    @Test
    void shouldFallbackForRawNonJsonMessage() throws Exception {
        String rawMessage = "Raw plain text log line";

        service.writeLogAsync("log-CoreGDCE-java", rawMessage).get();

        String expectedDate = LocalDate.now().format(DATE_FORMATTER);
        Path expectedFile = tempDir.resolve("log-CoreGDCE-java").resolve("log-CoreGDCE-java").resolve(expectedDate + ".log");

        assertTrue(Files.exists(expectedFile));
        List<String> lines = Files.readAllLines(expectedFile);
        assertEquals(1, lines.size());
        assertEquals("Raw plain text log line", lines.get(0));
    }


    @Test
    void shouldSanitizeChannelName() throws Exception {
        String json = """
            {
              "serverName": "app-server-01",
              "message": "Sanitized channel test"
            }
            """;

        service.writeLogAsync("log-app:sub/test", json).get();

        String expectedDate = LocalDate.now().format(DATE_FORMATTER);
        Path expectedFile = tempDir.resolve("app-server-01").resolve("log-app_sub_test").resolve(expectedDate + ".log");

        assertTrue(Files.exists(expectedFile), "Sanitized channel log file should exist");
        List<String> lines = Files.readAllLines(expectedFile);
        assertEquals(1, lines.size());
        assertEquals("Sanitized channel test", lines.get(0));
    }
}
