package com.dev.monitor.controller;

import com.dev.monitor.dto.system.SystemCreateRequest;
import com.dev.monitor.dto.system.SystemResponse;
import com.dev.monitor.services.SystemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @PostMapping
    public ResponseEntity<SystemResponse> createSystem(@Valid @RequestBody SystemCreateRequest request) {
        SystemResponse response = systemService.createSystem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SystemResponse>> getAllSystems() {
        List<SystemResponse> systems = systemService.getAllSystems();
        return ResponseEntity.ok(systems);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<SystemResponse> getSystemById(@PathVariable String uuid) {
        SystemResponse system = systemService.getSystemById(uuid);
        return ResponseEntity.ok(system);
    }
}
