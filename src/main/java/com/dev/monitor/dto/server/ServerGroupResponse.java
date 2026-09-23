package com.dev.monitor.dto.server;

import com.dev.monitor.entity.server.Server;
import com.dev.monitor.entity.server.ServerResource;
import com.dev.monitor.entity.server.ServerStorageSummary;
import com.dev.monitor.entity.server.ServerType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record ServerGroupResponse(
    String systemId,
    String systemName,
    List<ServerItem> server
) {
    public record ServerItem(
        String uuid,
        String name,
        String ip,
        ServerType serverType,
        /** Free-text note; null when not set. */
        String description,
        /** true reporting, false not reporting, null presence could not be determined. */
        Boolean online,
        /** When the hub last received a scrape; null if never, or unknown. */
        OffsetDateTime lastSeenAt,
        /**
         * Host uptime as of the latest resource scrape. Null if the server
         * has never reported. Read it together with online/lastSeenAt: for
         * an offline server this is the value from its last scrape, not the
         * uptime now.
         */
        Long uptimeSeconds,
        /** Most recent host storage rollup; null if none has been received. */
        StorageSummary storage,
        OffsetDateTime createdAt
    ) {
        /** Presence and storage unknown; prefer the full overload. */
        public static ServerItem fromEntity(Server server, ZoneId zone) {
            return fromEntity(server, null, null, null, null, zone);
        }

        public static ServerItem fromEntity(Server server,
                                            Boolean online,
                                            Instant lastSeenAt,
                                            ServerResource latestResource,
                                            ServerStorageSummary summary,
                                            ZoneId zone) {
            return new ServerItem(
                server.getUuid(),
                server.getName(),
                server.getIp(),
                server.getServerType(),
                server.getDescription(),
                online,
                Zoned.at(lastSeenAt, zone),
                latestResource == null ? null : latestResource.getUptimeSeconds(),
                StorageSummary.fromEntity(summary, zone),
                Zoned.at(server.getCreatedAt(), zone)
            );
        }
    }

    /**
     * Host-level storage rollup as of the latest scrape.
     *
     * recordedAt is carried deliberately: storage is published hourly, so a
     * client needs to know how stale these numbers are. Without it, an agent
     * that died yesterday looks identical to one that reported a minute ago.
     */
    public record StorageSummary(
        Long totalBytes,
        Long usedBytes,
        Long freeBytes,
        Long reservedBytes,
        Float usedPct,
        Boolean partial,
        OffsetDateTime recordedAt
    ) {
        static StorageSummary fromEntity(ServerStorageSummary s, ZoneId zone) {
            if (s == null) {
                return null;
            }
            return new StorageSummary(
                s.getTotalBytes(),
                s.getUsedBytes(),
                s.getFreeBytes(),
                s.getReservedBytes(),
                s.getUsedPct(),
                s.getPartial(),
                Zoned.at(s.getRecordTimestamp(), zone)
            );
        }
    }
}
