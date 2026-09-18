package com.dev.monitor.controller;

import com.dev.monitor.dto.server.ServerLogCreateRequest;
import com.dev.monitor.dto.server.ServerLogResponse;
import com.dev.monitor.services.ServerLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ServerLogControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ServerLogService serverLogService;

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
}
