package com.dev.monitor.services;

import com.dev.monitor.dto.server.ServerResponse;
import com.dev.monitor.repository.server.ServerRepository;
import com.dev.monitor.repository.server.ServerResourceRepository;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Opens a live resource stream: the last few minutes of raw scrapes first, then every new
 * scrape, with no gap and no duplicate between the two (see ResourceStreamBroadcaster).
 */
@Service
public class ResourceStreamService {

    private static final Logger log = LoggerFactory.getLogger(ResourceStreamService.class);

    private final ServerRepository serverRepository;
    private final ServerResourceRepository resourceRepository;
    private final ResourceStreamBroadcaster broadcaster;
    private final ZoneId displayZone;
    private final Duration backfill;

    public ResourceStreamService(ServerRepository serverRepository,
                                 ServerResourceRepository resourceRepository,
                                 ResourceStreamBroadcaster broadcaster,
                                 ZoneId displayZone,
                                 @Value("${resource.stream.backfill-minutes:1}") long backfillMinutes) {
        this.serverRepository = serverRepository;
        this.resourceRepository = resourceRepository;
        this.broadcaster = broadcaster;
        this.displayZone = displayZone;
        this.backfill = Duration.ofMinutes(backfillMinutes);
    }

    /** Empty when the server does not exist. */
    public Optional<SseEmitter> open(String username, String serverId) {
        if (!serverRepository.existsById(serverId)) {
            return Optional.empty();
        }
        ResourceStreamBroadcaster.Stream stream = broadcaster.open(username, serverId);

        // Raw rows, not time_bucket averages: the live samples that follow are raw too, and
        // a minute of a 3-5s scrape is at most twenty rows
        List<ResourceStreamBroadcaster.Sample> history = List.of();
        try {
            history = resourceRepository.findLatestSpan(serverId, backfill.toSeconds())
                    .stream()
                    .map(r -> new ResourceStreamBroadcaster.Sample(
                            r.getRecordTimestamp(), ServerResponse.Resources.of(r, displayZone)))
                    .toList();
        } catch (Exception e) {
            // Still go live: tiles that fill from now beat tiles that never start
            log.warn("Resource backfill of [{}] failed: {}", serverId, e.getMessage());
        }
        stream.attach(history);
        return Optional.of(stream.emitter());
    }
}
