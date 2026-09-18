package com.dev.monitor.controller;

import com.dev.monitor.dto.server.ServerCreateRequest;
import com.dev.monitor.dto.server.ServerGroupResponse;
import com.dev.monitor.dto.server.ServerResponse;
import com.dev.monitor.services.ServerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/servers")
public class ServerController {

    private final ServerService serverService;

    public ServerController(ServerService serverService) {
        this.serverService = serverService;
    }

    @PostMapping
    public ResponseEntity<ServerResponse> createServer(@Valid @RequestBody ServerCreateRequest request) {
        ServerResponse response = serverService.createServer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ServerGroupResponse>> getAllServers(
            @RequestParam(required = false) String systemId) {
        List<ServerGroupResponse> servers = serverService.getAllServers(systemId);
        return ResponseEntity.ok(servers);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<ServerResponse> getServerById(@PathVariable String uuid) {
        ServerResponse server = serverService.getServerById(uuid);
        return ResponseEntity.ok(server);
    }
}
