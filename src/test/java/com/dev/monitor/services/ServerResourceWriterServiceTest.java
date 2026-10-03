package com.dev.monitor.services;

import com.dev.monitor.repository.server.ServerRepository;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The ingest half of the live tiles: a resource-info message, as the agent publishes it,
 * must reach an open stream converted exactly as the row is stored.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServerResourceWriterServiceTest {

    private static final String SERVER = "be91-srv";
    private static final String PAYLOAD = """
            {"serverId":"be91-srv","timestamp":"2026-09-25T04:55:30Z","uptimeSeconds":6306750,
             "cpu":{"count":16,"usedPercent":72.7,"load1":11.52,"load5":9.68,"load15":8.29},
             "memory":{"totalBytes":33719185408,"usedBytes":11708960768,
                       "availableBytes":22010224640,"usedPercent":34.72,"estimated":false},
             "swap":{"totalBytes":20971515904,"usedBytes":0,"usedPercent":0},
             "network":{"rxBytesPerSec":103669.046,"txBytesPerSec":239812.334}}
            """;

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ServerRepository serverRepository;
    @Mock
    private ServerPresenceService presenceService;

    private ResourceStreamBroadcaster broadcaster;
    private ServerResourceWriterService writer;

    @BeforeEach
    void setUp() {
        broadcaster = spy(new ResourceStreamBroadcaster(3, 15));
        writer = new ServerResourceWriterService(new ObjectMapper(), jdbcTemplate, serverRepository,
                presenceService, broadcaster, ZoneId.of("Asia/Phnom_Penh"));
        when(serverRepository.existsById(SERVER)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        broadcaster.shutdown();
        writer.shutdown();
    }

    @Test
    void shouldPublishEachScrapeToLiveStreamsAsStored() throws Exception {
        writer.writeResourceAsync("resource-app", PAYLOAD).get();

        verify(presenceService).touch(SERVER);
        ArgumentCaptor<ResourceStreamBroadcaster.Sample> published =
                ArgumentCaptor.forClass(ResourceStreamBroadcaster.Sample.class);
        verify(broadcaster).publish(eq(SERVER), published.capture());
        ResourceStreamBroadcaster.Sample sample = published.getValue();
        assertEquals(Instant.parse("2026-09-25T04:55:30Z"), sample.at());
        assertEquals(72.7f, sample.resources().cpuPct());
        assertEquals(34.72f, sample.resources().memUsedPct());
        // Rounded to whole bytes/sec, as the row stores it
        assertEquals(103669L, sample.resources().netRxBps());
        assertEquals(239812L, sample.resources().netTxBps());
        assertEquals((short) 16, sample.resources().cpuCount());
        assertEquals(1, writer.pendingCount());
    }
}
