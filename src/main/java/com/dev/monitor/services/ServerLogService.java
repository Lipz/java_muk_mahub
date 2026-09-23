package com.dev.monitor.services;

import com.dev.monitor.dto.server.ServerLogCreateRequest;
import com.dev.monitor.dto.server.ServerLogResponse;
import com.dev.monitor.entity.server.Server;
import com.dev.monitor.entity.server.ServerLog;
import com.dev.monitor.exception.server.ServerLogAlreadyExistsException;
import com.dev.monitor.exception.server.ServerNotFoundException;
import com.dev.monitor.repository.server.ServerLogRepository;
import com.dev.monitor.repository.server.ServerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ServerLogService {

    private final ServerLogRepository serverLogRepository;
    private final ServerRepository serverRepository;

    public ServerLogService(ServerLogRepository serverLogRepository, ServerRepository serverRepository) {
        this.serverLogRepository = serverLogRepository;
        this.serverRepository = serverRepository;
    }

    @Transactional
    public ServerLogResponse createServerLog(ServerLogCreateRequest request) {
        Server server = resolveServer(request.server());

        if (serverLogRepository.existsByServerUuidAndChannel(server.getUuid(), request.channel())) {
            throw new ServerLogAlreadyExistsException(request.channel());
        }

        String savePath = request.savePath();
        if (savePath == null || savePath.isBlank()) {
            String safeServerName = sanitize(server.getName(), "unknown_server");
            String safeChannel = sanitize(request.channel(), "unknown_channel");
            savePath = "logs/" + safeServerName + "/" + safeChannel;
        }

        ServerLog serverLog = new ServerLog(server, request.channel(), request.pubPath(), savePath);
        ServerLog saved = serverLogRepository.save(serverLog);
        return ServerLogResponse.fromEntity(saved);
    }

    /**
     * Resolve the log channel registered for a server, creating it when it does not exist yet.
     * Returns empty when the server UUID is unknown.
     */
    @Transactional
    public Optional<ServerLog> resolveOrCreateChannel(String serverUuid, String channel, String pubPath) {
        Optional<Server> server = serverRepository.findById(serverUuid);
        if (server.isEmpty()) {
            return Optional.empty();
        }

        Optional<ServerLog> existing = serverLogRepository.findByServerUuidAndChannel(serverUuid, channel);
        if (existing.isPresent()) {
            return existing;
        }

        Server s = server.get();
        String savePath = "logs/" + sanitize(s.getName(), "unknown_server") + "/" + sanitize(channel, "unknown_channel");
        String safePubPath = (pubPath == null || pubPath.isBlank()) ? "-" : pubPath;
        return Optional.of(serverLogRepository.saveAndFlush(new ServerLog(s, channel, safePubPath, savePath)));
    }

    @Transactional(readOnly = true)
    public Optional<ServerLog> findChannel(String logUuid) {
        return serverLogRepository.findByIdWithServer(logUuid);
    }

    @Transactional(readOnly = true)
    public List<ServerLog> getAllChannels() {
        return serverLogRepository.findAllWithServer();
    }

    @Transactional(readOnly = true)
    public List<ServerLogResponse> getLogs(String serverIdentifier) {
        if (serverIdentifier != null && !serverIdentifier.isBlank()) {
            Server server = resolveServer(serverIdentifier);
            List<ServerLog> logs = serverLogRepository.findByServerUuid(server.getUuid());
            return logs.stream().map(ServerLogResponse::fromEntity).toList();
        }

        List<ServerLog> allLogs = serverLogRepository.findAllWithServer();
        return allLogs.stream().map(ServerLogResponse::fromEntity).toList();
    }

    private Server resolveServer(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Server identifier (UUID or name) is required");
        }

        // 1. Try finding by UUID
        Optional<Server> byId = serverRepository.findById(identifier);
        if (byId.isPresent()) {
            return byId.get();
        }

        // 2. Try finding by Server Name
        List<Server> byName = serverRepository.findByName(identifier);
        if (!byName.isEmpty()) {
            return byName.get(0);
        }

        throw new ServerNotFoundException(identifier);
    }

    private String sanitize(String name, String defaultName) {
        if (name == null || name.isBlank()) {
            return defaultName;
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
