package com.dev.monitor.controller;

import com.dev.monitor.dto.server.ServerLogCreateRequest;
import com.dev.monitor.dto.server.ServerLogResponse;
import com.dev.monitor.services.LogFileWriterService;
import com.dev.monitor.services.LogTailService;
import com.dev.monitor.services.ServerLogService;
import com.dev.monitor.entity.server.Server;
import com.dev.monitor.entity.server.ServerLog;
import com.dev.monitor.entity.server.ServerType;
import com.dev.monitor.entity.system.SystemEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
// import com.fasterxml.jackson.databind.ObjectMapper;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ServerLogControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ServerLogService serverLogService;

    @Mock
    private LogTailService logTailService;

    @Mock
    private LogFileWriterService logFileWriterService;

    @InjectMocks
    private ServerLogController serverLogController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(serverLogController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldCreateServerLog() throws Exception {
        ServerLogCreateRequest request = new ServerLogCreateRequest("ecustoms-Oracle-Dev", "log-listener", "/var/log/listener.log");
        ServerLogResponse response = new ServerLogResponse("log-01", "srv-01", "ecustoms-Oracle-Dev", "log-listener", "/var/log/listener.log", "logs/ecustoms-Oracle-Dev/log-listener");

        when(serverLogService.createServerLog(any(ServerLogCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/server-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").value("log-01"))
                .andExpect(jsonPath("$.serverId").value("srv-01"))
                .andExpect(jsonPath("$.serverName").value("ecustoms-Oracle-Dev"))
                .andExpect(jsonPath("$.channel").value("log-listener"))
                .andExpect(jsonPath("$.savePath").value("logs/ecustoms-Oracle-Dev/log-listener"));

        verify(serverLogService).createServerLog(any(ServerLogCreateRequest.class));
    }

    @Test
    void shouldGetLogsByServerQueryParam() throws Exception {
        ServerLogResponse response = new ServerLogResponse("log-01", "srv-01", "ecustoms-Oracle-Dev", "log-listener", "/var/log/listener.log", "logs/ecustoms-Oracle-Dev/log-listener");

        when(serverLogService.getLogs("ecustoms-Oracle-Dev")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/server-logs")
                        .param("server", "ecustoms-Oracle-Dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uuid").value("log-01"))
                .andExpect(jsonPath("$[0].serverName").value("ecustoms-Oracle-Dev"));

        verify(serverLogService).getLogs("ecustoms-Oracle-Dev");
    }

    @Test
    void shouldGetLogsByServerIdQueryParam() throws Exception {
        ServerLogResponse response = new ServerLogResponse("log-01", "srv-01", "ecustoms-Oracle-Dev", "log-listener", "/var/log/listener.log", "logs/ecustoms-Oracle-Dev/log-listener");

        when(serverLogService.getLogs("srv-01")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/server-logs")
                        .param("serverId", "srv-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serverId").value("srv-01"));

        verify(serverLogService).getLogs("srv-01");
    }

    @Test
    void shouldReturnNotFoundWhenStreamingUnknownLog() throws Exception {
        when(serverLogService.findChannel("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/server-logs/missing/stream").principal(() -> "alice"))
                .andExpect(status().isNotFound());

        verify(logTailService, never()).open(anyString(), any());
    }

    @Test
    void shouldOpenTailOfResolvedChannelForCaller() throws Exception {
        SystemEntity system = new SystemEntity("ecustoms");
        Server server = new Server(system, "app-server-01", "10.0.0.5", ServerType.APP);
        server.setUuid("srv-01");
        ServerLog serverLog = new ServerLog(server, "log-app", "/var/log/app.log", "logs/app");
        when(serverLogService.findChannel("log-01")).thenReturn(Optional.of(serverLog));
        when(logTailService.open("alice", serverLog)).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/v1/server-logs/log-01/stream").principal(() -> "alice"))
                .andExpect(request().asyncStarted());

        verify(logTailService).open("alice", serverLog);
    }

    private ServerLog appLog() {
        SystemEntity system = new SystemEntity("ecustoms");
        Server server = new Server(system, "app-server-01", "10.0.0.5", ServerType.APP);
        server.setUuid("srv-01");
        return new ServerLog(server, "log-app", "/var/log/app.log", "logs/app");
    }

    @Test
    void shouldReturnNotFoundWhenDownloadingUnknownLog() throws Exception {
        when(serverLogService.findChannel("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/server-logs/missing/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenNothingWrittenToday() throws Exception {
        when(serverLogService.findChannel("log-01")).thenReturn(Optional.of(appLog()));
        when(logFileWriterService.snapshotToday("logs/app")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/server-logs/log-01/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDownloadTodaysFileUpToSnapshotLength(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("today.log");
        // The snapshot covers the first two lines; the third was appended after it
        Files.writeString(file, "first\nsecond\nappended later\n");
        LocalDate date = LocalDate.of(2026, 9, 23);
        when(serverLogService.findChannel("log-01")).thenReturn(Optional.of(appLog()));
        when(logFileWriterService.snapshotToday("logs/app"))
                .thenReturn(Optional.of(new LogFileWriterService.FileSnapshot(file, date, 13)));

        var started = mockMvc.perform(get("/api/v1/server-logs/log-01/download"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andExpect(header().longValue("Content-Length", 13))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("log-app_2026-09-23.log")))
                .andExpect(content().string("first\nsecond\n"));
    }
}
