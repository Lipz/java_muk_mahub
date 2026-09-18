package com.dev.monitor.services;

import com.dev.monitor.dto.server.ServerCreateRequest;
import com.dev.monitor.dto.server.ServerGroupResponse;
import com.dev.monitor.dto.server.ServerResponse;
import com.dev.monitor.entity.server.Server;
import com.dev.monitor.entity.system.SystemEntity;
import com.dev.monitor.exception.server.ServerAlreadyExistsException;
import com.dev.monitor.exception.server.ServerNotFoundException;
import com.dev.monitor.exception.system.SystemNotFoundException;
import com.dev.monitor.repository.server.ServerRepository;
import com.dev.monitor.repository.system.SystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ServerService {

    private final ServerRepository serverRepository;
    private final SystemRepository systemRepository;

    public ServerService(ServerRepository serverRepository, SystemRepository systemRepository) {
        this.serverRepository = serverRepository;
        this.systemRepository = systemRepository;
    }

    @Transactional
    public ServerResponse createServer(ServerCreateRequest request) {
        SystemEntity system = systemRepository.findById(request.systemId())
                .orElseThrow(() -> new SystemNotFoundException(request.systemId()));

        if (serverRepository.existsBySystemUuidAndName(system.getUuid(), request.name())) {
            throw new ServerAlreadyExistsException(request.name());
        }

        Server server = new Server(system, request.name(), request.ip(), request.serverType());
        Server saved = serverRepository.save(server);
        return ServerResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ServerGroupResponse> getAllServers(String systemId) {
        if (systemId != null && !systemId.isBlank()) {
            SystemEntity system = systemRepository.findById(systemId)
                    .orElseThrow(() -> new SystemNotFoundException(systemId));
            List<Server> servers = serverRepository.findBySystemUuid(systemId);
            return List.of(new ServerGroupResponse(
                    system.getUuid(),
                    system.getName(),
                    servers.stream().map(ServerGroupResponse.ServerItem::fromEntity).toList()
            ));
        }

        List<Server> servers = serverRepository.findAllWithSystem();
        Map<String, String> systemNames = new LinkedHashMap<>();
        Map<String, List<ServerGroupResponse.ServerItem>> groupedServers = new LinkedHashMap<>();

        for (Server server : servers) {
            SystemEntity system = server.getSystem();
            if (system != null) {
                systemNames.putIfAbsent(system.getUuid(), system.getName());
                groupedServers.computeIfAbsent(system.getUuid(), k -> new ArrayList<>())
                        .add(ServerGroupResponse.ServerItem.fromEntity(server));
            }
        }

        return groupedServers.entrySet().stream()
                .map(entry -> new ServerGroupResponse(
                        entry.getKey(),
                        systemNames.get(entry.getKey()),
                        entry.getValue()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ServerResponse getServerById(String uuid) {
        Server server = serverRepository.findById(uuid)
                .orElseThrow(() -> new ServerNotFoundException(uuid));
        return ServerResponse.fromEntity(server);
    }
}
