package com.dev.monitor.services;

import com.dev.monitor.dto.server.ServerCreateRequest;
import com.dev.monitor.dto.server.ResourcePoint;
import com.dev.monitor.dto.server.ServerGroupResponse;
import com.dev.monitor.dto.server.ServerResponse;
import com.dev.monitor.entity.server.Server;
import com.dev.monitor.entity.server.ServerResource;
import com.dev.monitor.entity.server.ServerStorageSummary;
import com.dev.monitor.entity.system.SystemEntity;
import com.dev.monitor.exception.server.ServerAlreadyExistsException;
import com.dev.monitor.exception.server.ServerNotFoundException;
import com.dev.monitor.exception.system.SystemNotFoundException;
import com.dev.monitor.repository.server.ServerRepository;
import com.dev.monitor.repository.server.ServerResourceRepository;
import com.dev.monitor.repository.server.ServerStorageRepository;
import com.dev.monitor.repository.server.ServerStorageSummaryRepository;
import com.dev.monitor.repository.system.SystemRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class ServerService {

    private final ServerRepository serverRepository;
    private final SystemRepository systemRepository;
    private final ServerPresenceService presenceService;
    private final ServerStorageSummaryRepository storageSummaryRepository;
    private final ServerStorageRepository storageRepository;
    private final ServerResourceRepository resourceRepository;
    private final ZoneId displayZone;

    public ServerService(ServerRepository serverRepository,
                         SystemRepository systemRepository,
                         ServerPresenceService presenceService,
                         ServerStorageSummaryRepository storageSummaryRepository,
                         ServerStorageRepository storageRepository,
                         ServerResourceRepository resourceRepository,
                         ZoneId displayZone) {
        this.serverRepository = serverRepository;
        this.systemRepository = systemRepository;
        this.presenceService = presenceService;
        this.storageSummaryRepository = storageSummaryRepository;
        this.storageRepository = storageRepository;
        this.resourceRepository = resourceRepository;
        this.displayZone = displayZone;
    }

    /**
     * Presence and latest storage rollup for a whole page of servers,
     * fetched once rather than per server: one Redis MGET and one
     * DISTINCT ON query, whatever the server count.
     */
    private Enrichment enrichmentFor(List<Server> servers) {
        List<String> ids = servers.stream().map(Server::getUuid).toList();
        if (ids.isEmpty()) {
            return new Enrichment(Map.of(), Map.of(), Map.of(), displayZone);
        }
        Map<String, ServerStorageSummary> storage =
                storageSummaryRepository.findLatestPerServer(ids).stream()
                        .collect(Collectors.toMap(ServerStorageSummary::getServerId, s -> s));
        Map<String, ServerResource> resources =
                resourceRepository.findLatestPerServer(ids).stream()
                        .collect(Collectors.toMap(ServerResource::getServerId, r -> r));
        return new Enrichment(presenceService.lookup(ids), resources, storage, displayZone);
    }

    private record Enrichment(Map<String, ServerPresenceService.Presence> presence,
                              Map<String, ServerResource> resources,
                              Map<String, ServerStorageSummary> storage,
                              ZoneId zone) {

        ServerGroupResponse.ServerItem toItem(Server server) {
            ServerPresenceService.Presence p = presence.get(server.getUuid());
            return ServerGroupResponse.ServerItem.fromEntity(
                    server,
                    p == null ? null : p.online(),
                    p == null ? null : p.lastSeenAt(),
                    resources.get(server.getUuid()),
                    storage.get(server.getUuid()),
                    zone);
        }
    }

    /** Builds the response with current presence attached. */
    private ServerResponse toResponse(Server server) {
        ServerPresenceService.Presence presence = presenceService.lookup(server.getUuid());
        ServerResource latest = resourceRepository
                .findLatestPerServer(List.of(server.getUuid())).stream()
                .findFirst()
                .orElse(null);
        return ServerResponse.fromEntity(server, presence.online(), presence.lastSeenAt(),
                latest, latestStorage(server.getUuid()), displayZone);
    }

    /**
     * The newest storage scrape for one server: host rollup plus its mounts.
     *
     * Two queries rather than a join. Joining would repeat every rollup
     * column on each mount row, and the two are written together so they
     * already share a record_timestamp.
     */
    private ServerResponse.Storage latestStorage(String serverUuid) {
        ServerStorageSummary summary = storageSummaryRepository
                .findLatestPerServer(List.of(serverUuid)).stream()
                .findFirst()
                .orElse(null);
        return ServerResponse.Storage.of(
                summary, storageRepository.findLatestMounts(serverUuid), displayZone);
    }

    @Transactional
    public ServerResponse createServer(ServerCreateRequest request) {
        SystemEntity system = systemRepository.findById(request.systemId())
                .orElseThrow(() -> new SystemNotFoundException(request.systemId()));

        if (serverRepository.existsBySystemUuidAndName(system.getUuid(), request.name())) {
            throw new ServerAlreadyExistsException(request.name());
        }

        Server server = new Server(system, request.name(), request.ip(), request.serverType());
        server.setDescription(request.description());
        Server saved = serverRepository.save(server);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ServerGroupResponse> getAllServers(String systemId) {
        if (systemId != null && !systemId.isBlank()) {
            SystemEntity system = systemRepository.findById(systemId)
                    .orElseThrow(() -> new SystemNotFoundException(systemId));
            List<Server> servers = serverRepository.findBySystemUuid(systemId);
            Enrichment enrichment = enrichmentFor(servers);
            return List.of(new ServerGroupResponse(
                    system.getUuid(),
                    system.getName(),
                    servers.stream().map(enrichment::toItem).toList()
            ));
        }

        List<Server> servers = serverRepository.findAllWithSystem();
        Enrichment enrichment = enrichmentFor(servers);
        Map<String, String> systemNames = new LinkedHashMap<>();
        Map<String, List<ServerGroupResponse.ServerItem>> groupedServers = new LinkedHashMap<>();

        for (Server server : servers) {
            SystemEntity system = server.getSystem();
            if (system != null) {
                systemNames.putIfAbsent(system.getUuid(), system.getName());
                groupedServers.computeIfAbsent(system.getUuid(), k -> new ArrayList<>())
                        .add(enrichment.toItem(server));
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

    /** Points per history series: enough for a sparkline, cheap to ship. */
    static final int HISTORY_POINTS = 30;
    static final int HISTORY_MIN_MINUTES = 5;
    static final int HISTORY_MAX_MINUTES = 24 * 60;

    /**
     * Resource history over the last {@code minutes}, averaged into
     * {@link #HISTORY_POINTS} buckets. The window is clamped so a bad query
     * parameter cannot ask the hypertable for months of rows.
     */
    @Transactional(readOnly = true)
    public List<ResourcePoint> getResourceHistory(String uuid, int minutes) {
        if (!serverRepository.existsById(uuid)) {
            throw new ServerNotFoundException(uuid);
        }
        int window = Math.clamp(minutes, HISTORY_MIN_MINUTES, HISTORY_MAX_MINUTES);
        int bucketSeconds = window * 60 / HISTORY_POINTS;
        Instant since = Instant.now().minus(Duration.ofMinutes(window));
        return resourceRepository.findHistory(uuid, since, bucketSeconds).stream()
                .map(b -> ResourcePoint.of(b, displayZone))
                .toList();
    }

    @Transactional(readOnly = true)
    public ServerResponse getServerById(String uuid) {
        Server server = serverRepository.findByIdWithLogs(uuid)
                .orElseThrow(() -> new ServerNotFoundException(uuid));
        return toResponse(server);
    }
}
