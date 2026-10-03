package com.dev.monitor.controller;

import com.dev.monitor.dto.server.ResourcePoint;
import com.dev.monitor.dto.server.ServerCreateRequest;
import com.dev.monitor.dto.server.ServerGroupResponse;
import com.dev.monitor.dto.server.ServerResponse;
import com.dev.monitor.services.ResourceStreamService;
import com.dev.monitor.services.ServerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/servers")
public class ServerController {

    private final ServerService serverService;
    private final ResourceStreamService resourceStreamService;

    public ServerController(ServerService serverService, ResourceStreamService resourceStreamService) {
        this.serverService = serverService;
        this.resourceStreamService = resourceStreamService;
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

    /** Averaged CPU / memory / network history for the node page sparklines. */
    @GetMapping("/{uuid}/resources/history")
    public ResponseEntity<List<ResourcePoint>> getResourceHistory(
            @PathVariable String uuid,
            @RequestParam(defaultValue = "60") int minutes) {
        return ResponseEntity.ok(serverService.getResourceHistory(uuid, minutes));
    }

    /**
     * Live resource scrapes of one server as Server-Sent Events: a {@code backfill} event with
     * the last few minutes, oldest first, then a {@code sample} event per scrape, and a final
     * {@code evicted} event when the user opened too many other streams.
     */
    @GetMapping(path = "/{uuid}/resources/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamResources(@PathVariable String uuid, Principal principal) {
        // Not an exception: the JSON error body cannot be written to an event stream
        return resourceStreamService.open(principal.getName(), uuid)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
