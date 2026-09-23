package com.dev.monitor.services;

import com.dev.monitor.controller.ServerLogController;
import com.dev.monitor.entity.server.Server;
import com.dev.monitor.entity.server.ServerLog;
import com.dev.monitor.entity.server.ServerType;
import com.dev.monitor.entity.system.SystemEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Drives the real writer, lane, broadcaster and endpoint together, reading the raw SSE output.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogStreamBroadcasterTest {

    private static final String KEY = "srv-01|log-app";
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Mock
    private ServerLogService serverLogService;

    private LogStreamBroadcaster broadcaster;
    private LogFileWriterService writer;
    private MockMvc mockMvc;
    private Path saveDir;

    @BeforeEach
    void setUp() {
        broadcaster = new LogStreamBroadcaster(2, 15);
        writer = new LogFileWriterService(JSON, serverLogService, broadcaster);
        LogTailService tails = new LogTailService(writer, broadcaster, 200);
        mockMvc = MockMvcBuilders.standaloneSetup(new ServerLogController(serverLogService, tails, writer)).build();

        SystemEntity system = new SystemEntity("ecustoms");
        Server server = new Server(system, "app-server-01", "10.0.0.5", ServerType.APP);
        server.setUuid("srv-01");
        saveDir = tempDir.resolve("app");
        ServerLog serverLog = new ServerLog(server, "log-app", "/var/log/app.log", saveDir.toString());
        when(serverLogService.findChannel("log-01")).thenReturn(Optional.of(serverLog));
        when(serverLogService.resolveOrCreateChannel(eq("srv-01"), eq("log-app"), any()))
                .thenReturn(Optional.of(serverLog));
    }

    @AfterEach
    void tearDown() {
        broadcaster.shutdown();
        writer.shutdown();
    }

    @Test
    void shouldSendTodaysTailThenLiveLines() throws Exception {
        Files.createDirectories(saveDir);
        Files.write(todayFile(), IntStream.range(0, 250).mapToObj(i -> "old " + i).toList());
        MockHttpServletResponse response = open("alice");

        await(() -> events(response).size() == 1);
        write("fresh").get();

        await(() -> events(response).size() == 2);
        List<Event> events = events(response);
        assertEquals("backfill", events.get(0).name);
        assertEquals(IntStream.range(50, 250).mapToObj(i -> "old " + i).toList(),
                lines(events.get(0).data.get("lines")));
        long backfillSeq = events.get(0).data.get("seq").asLong();

        assertEquals("line", events.get(1).name);
        assertEquals("fresh", events.get(1).data.get("line").asString());
        assertEquals(backfillSeq + 1, events.get(1).data.get("seq").asLong());
    }

    @Test
    void shouldSendEmptyBackfillWhenNothingWrittenToday() throws Exception {
        MockHttpServletResponse response = open("alice");

        await(() -> events(response).size() == 1);
        Event backfill = events(response).get(0);
        assertEquals("backfill", backfill.name);
        assertEquals(List.of(), lines(backfill.data.get("lines")));
    }

    @Test
    void shouldHaveNoGapOrDuplicateWhenOpeningDuringBurst() throws Exception {
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            writes.add(write("burst " + i));
        }
        MockHttpServletResponse response = open("alice");
        for (int i = 150; i < 300; i++) {
            writes.add(write("burst " + i));
        }
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).get();

        // Backfill plus live lines must be exactly the end of the file, in order
        await(() -> seen(events(response)).contains("burst 299"));
        List<String> file = Files.readAllLines(todayFile());
        List<String> seen = seen(events(response));
        assertEquals(file.subList(file.size() - seen.size(), file.size()), seen);
        assertTrue(seen.size() >= 150, "every line after the open is live: " + seen.size());
    }

    @Test
    void shouldKeepMultilineMessageInOneEvent() throws Exception {
        MockHttpServletResponse response = open("alice");
        await(() -> events(response).size() == 1);

        broadcaster.publish(KEY, null, "Exception\n\tat Foo.bar");

        await(() -> events(response).size() == 2);
        assertEquals("Exception\n\tat Foo.bar", events(response).get(1).data.get("line").asString());
    }

    @Test
    void shouldEvictOldestStreamWhenUserExceedsCap() throws Exception {
        MockHttpServletResponse oldest = open("alice");
        open("alice");
        open("bob");
        assertEquals(2, broadcaster.streamCount("alice"));

        MockHttpServletResponse newest = open("alice");

        await(() -> body(oldest).contains("event:evicted"));
        assertEquals(2, broadcaster.streamCount("alice"));
        assertEquals(1, broadcaster.streamCount("bob"));

        await(() -> events(newest).size() == 1);
        write("after evict").get();
        await(() -> body(newest).contains("after evict"));
        assertFalse(body(oldest).contains("after evict"));
    }

    @Test
    void shouldForgetKeyOnceLastSubscriberLeaves() throws Exception {
        MockHttpServletResponse response = open("alice");
        await(() -> events(response).size() == 1);
        assertTrue(broadcaster.hasSubscribers(KEY));

        broadcaster.shutdown();

        assertFalse(broadcaster.hasSubscribers(KEY));
        assertEquals(0, broadcaster.streamCount("alice"));
    }

    private MockHttpServletResponse open(String user) throws Exception {
        return mockMvc.perform(get("/api/v1/server-logs/log-01/stream").principal(() -> user))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();
    }

    private CompletableFuture<Void> write(String message) {
        return writer.writeLogAsync("log-app",
                "{\"serverId\":\"srv-01\",\"path\":\"/var/log/app.log\",\"message\":\"" + message + "\"}");
    }

    private Path todayFile() {
        return saveDir.resolve(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".log");
    }

    private record Event(String name, JsonNode data) {}

    /** Named events only, skipping comments (connected, ping). */
    private static List<Event> events(MockHttpServletResponse response) {
        List<Event> events = new ArrayList<>();
        for (String block : body(response).split("\n\n")) {
            String name = null;
            String data = null;
            for (String field : block.split("\n")) {
                if (field.startsWith("event:")) {
                    name = field.substring(6);
                } else if (field.startsWith("data:")) {
                    data = field.substring(5);
                }
            }
            if (name != null && data != null && data.startsWith("{")) {
                events.add(new Event(name, JSON.readTree(data)));
            }
        }
        return events;
    }

    /** Backfill lines followed by live lines, as a viewer would display them. */
    private static List<String> seen(List<Event> events) {
        List<String> seen = new ArrayList<>();
        for (Event e : events) {
            if (e.name.equals("backfill")) {
                seen.addAll(lines(e.data.get("lines")));
            } else if (e.name.equals("line")) {
                seen.add(e.data.get("line").asString());
            }
        }
        return seen;
    }

    private static List<String> lines(JsonNode array) {
        List<String> lines = new ArrayList<>();
        array.forEach(n -> lines.add(n.asString()));
        return lines;
    }

    private static String body(MockHttpServletResponse response) {
        try {
            return response.getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                fail("Condition not met within 5s");
            }
            Thread.sleep(10);
        }
    }
}
