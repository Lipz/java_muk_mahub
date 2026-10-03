package com.dev.monitor.services;

import com.dev.monitor.controller.ServerController;
import com.dev.monitor.dto.server.ServerResponse;
import com.dev.monitor.entity.server.ServerResource;
import com.dev.monitor.repository.server.ServerRepository;
import com.dev.monitor.repository.server.ServerResourceRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the real broadcaster, stream service and endpoint together, reading the raw SSE output.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResourceStreamBroadcasterTest {

    private static final String SERVER = "srv-01";
    private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private ServerService serverService;
    @Mock
    private ServerRepository serverRepository;
    @Mock
    private ServerResourceRepository resourceRepository;

    private ResourceStreamBroadcaster broadcaster;
    private MockMvc mockMvc;
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        broadcaster = new ResourceStreamBroadcaster(2, 15);
        ResourceStreamService streams =
                new ResourceStreamService(serverRepository, resourceRepository, broadcaster, ZONE, 5);
        mockMvc = MockMvcBuilders.standaloneSetup(new ServerController(serverService, streams)).build();
        when(serverRepository.existsById(SERVER)).thenReturn(true);
        dbHolds();
    }

    @AfterEach
    void tearDown() {
        broadcaster.shutdown();
    }

    @Test
    void shouldSendHistoryThenLiveSamples() throws Exception {
        dbHolds(row(-20, 10f), row(-10, 20f));
        MockHttpServletResponse response = open("alice");

        await(() -> events(response).size() == 1);
        publish(0, 30f);

        await(() -> events(response).size() == 2);
        List<Event> events = events(response);
        assertEquals("backfill", events.get(0).name);
        assertEquals(List.of(10.0, 20.0), cpu(events.get(0).data));
        assertEquals("sample", events.get(1).name);
        assertEquals(30.0, events.get(1).data.get("cpuPct").asDouble());
    }

    @Test
    void shouldBackfillScrapesNotYetFlushedToTheDatabase() throws Exception {
        // -10 is in both the database and the in-memory window; -5 only in memory, as if
        // the writer had not flushed its batch yet
        dbHolds(row(-20, 10f), row(-10, 20f));
        publish(-10, 20f);
        publish(-5, 25f);

        MockHttpServletResponse response = open("alice");

        await(() -> events(response).size() == 1);
        assertEquals(List.of(10.0, 20.0, 25.0), cpu(events(response).get(0).data));
    }

    @Test
    void shouldStillGoLiveWhenBackfillQueryFails() throws Exception {
        when(resourceRepository.findLatestSpan(eq(SERVER), anyLong())).thenThrow(new IllegalStateException("database down"));
        MockHttpServletResponse response = open("alice");

        await(() -> events(response).size() == 1);
        assertEquals(List.of(), cpu(events(response).get(0).data));
        publish(0, 40f);
        await(() -> events(response).size() == 2);
    }

    @Test
    void shouldEvictOldestStreamWhenUserExceedsCap() throws Exception {
        MockHttpServletResponse oldest = open("alice");
        open("alice");
        open("bob");

        MockHttpServletResponse newest = open("alice");

        await(() -> body(oldest).contains("event:evicted"));
        assertEquals(2, broadcaster.streamCount("alice"));
        assertEquals(1, broadcaster.streamCount("bob"));

        await(() -> events(newest).size() == 1);
        publish(0, 77f);
        await(() -> events(newest).size() == 2);
        assertFalse(body(oldest).contains("77.0"));
    }

    @Test
    void shouldReportAgentClockSkewOnlyWhileReporting() throws Exception {
        // The agent stamped this scrape 100s before the hub received it
        publish(-100, 20f);
        MockHttpServletResponse reporting = open("alice");
        await(() -> events(reporting).size() == 1);
        long skew = events(reporting).get(0).data.get("skewMs").asLong();
        assertTrue(skew >= 100_000 && skew < 105_000, "skew " + skew);

        ResourceStreamBroadcaster quiet = new ResourceStreamBroadcaster(2, 15);
        ResourceStreamService streams =
                new ResourceStreamService(serverRepository, resourceRepository, quiet, ZONE, 1);
        MockMvc never = MockMvcBuilders.standaloneSetup(new ServerController(serverService, streams)).build();
        MockHttpServletResponse silent = never.perform(get("/api/v1/servers/" + SERVER + "/resources/stream")
                        .principal(() -> "bob"))
                .andExpect(request().asyncStarted()).andReturn().getResponse();
        await(() -> events(silent).size() == 1);
        assertTrue(events(silent).get(0).data.get("skewMs").isNull());
        quiet.shutdown();
    }

    @Test
    void shouldAnswerNotFoundForUnknownServer() throws Exception {
        mockMvc.perform(get("/api/v1/servers/nope/resources/stream").principal(() -> "alice"))
                .andExpect(status().isNotFound());
        assertEquals(0, broadcaster.streamCount("alice"));
    }

    private MockHttpServletResponse open(String user) throws Exception {
        return mockMvc.perform(get("/api/v1/servers/" + SERVER + "/resources/stream").principal(() -> user))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();
    }

    private void dbHolds(ServerResource... rows) {
        when(resourceRepository.findLatestSpan(eq(SERVER), anyLong())).thenReturn(List.of(rows));
    }

    private ServerResource row(int offsetSeconds, float cpuPct) {
        ServerResource r = new ServerResource(SERVER, now.plusSeconds(offsetSeconds));
        r.setCpuUsagePct(cpuPct);
        return r;
    }

    private void publish(int offsetSeconds, float cpuPct) {
        ServerResource r = row(offsetSeconds, cpuPct);
        broadcaster.publish(SERVER, new ResourceStreamBroadcaster.Sample(
                r.getRecordTimestamp(), ServerResponse.Resources.of(r, ZONE)));
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
            if (name != null && data != null && (data.startsWith("{") || data.startsWith("["))) {
                events.add(new Event(name, JSON.readTree(data)));
            }
        }
        return events;
    }

    private static List<Double> cpu(JsonNode backfill) {
        List<Double> values = new ArrayList<>();
        backfill.get("samples").forEach(n -> values.add(n.get("cpuPct").asDouble()));
        return values;
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
