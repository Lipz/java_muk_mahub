package com.dev.monitor.controller;

import com.dev.monitor.dto.server.ServerLogCreateRequest;
import com.dev.monitor.dto.server.ServerLogResponse;
import com.dev.monitor.services.ServerLogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/server-logs")
public class ServerLogController {

    private final ServerLogService serverLogService;

    public ServerLogController(ServerLogService serverLogService) {
        this.serverLogService = serverLogService;
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
}
