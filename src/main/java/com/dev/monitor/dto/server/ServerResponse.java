package com.dev.monitor.dto.server;

import com.dev.monitor.entity.server.Server;
import com.dev.monitor.entity.server.ServerLog;
import com.dev.monitor.entity.server.ServerResource;
import com.dev.monitor.entity.server.ServerStorage;
import com.dev.monitor.entity.server.ServerStorageSummary;
import com.dev.monitor.entity.server.ServerType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;


public record ServerResponse(
    String uuid,
    String systemId,
    String systemName,
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
     * Host uptime as of the latest resource scrape. Null if the server has
     * never reported. Read it together with online/lastSeenAt: for an
     * offline server this is the value from its last scrape, not now.
     */
    Long uptimeSeconds,
    /** Latest resource scrape (CPU, memory, swap, network). Null if none received. */
    Resources resources,
    /** Latest storage scrape: host rollup plus every mount. Null if none received. */
    Storage storage,
    List<LogItem> logs,
    OffsetDateTime createdAt
) {
    /**
     * The host rollup from the latest scrape, with the mounts it was
     * computed from.
     *
     * recordedAt is carried because storage publishes hourly: without it a
     * client cannot tell a reading from a minute ago from one taken before
     * the agent died.
     */
    public record Storage(
        Long totalBytes,
        Long usedBytes,
        Long freeBytes,
        Long reservedBytes,
        Float usedPct,
        Boolean partial,
        OffsetDateTime recordedAt,
        List<MountItem> mounts
    ) {
        /**
         * Builds the storage block, or null when the server has never
         * reported. Mounts and summary come from the same scrape, so either
         * may be present without the other if the agent omitted one.
         */
        public static Storage of(ServerStorageSummary summary, List<ServerStorage> mounts,
                                 ZoneId zone) {
            List<MountItem> items = mounts == null
                ? List.of()
                : mounts.stream().map(MountItem::fromEntity).toList();

            if (summary == null && items.isEmpty()) {
                return null;
            }
            if (summary == null) {
                // Mounts but no rollup: report what we have rather than
                // dropping the mounts entirely.
                return new Storage(null, null, null, null, null, null,
                    Zoned.at(mounts.get(0).getRecordTimestamp(), zone), items);
            }
            return new Storage(
                summary.getTotalBytes(),
                summary.getUsedBytes(),
                summary.getFreeBytes(),
                summary.getReservedBytes(),
                summary.getUsedPct(),
                summary.getPartial(),
                Zoned.at(summary.getRecordTimestamp(), zone),
                items
            );
        }
    }

    /**
     * The newest resource scrape, as the agent reported it.
     *
     * recordedAt is carried for the same reason as on Storage: the values
     * of a server that stopped reporting are frozen, and only the timestamp
     * says how old they are. Every metric is nullable because a partial
     * payload still writes a row.
     */
    public record Resources(
        OffsetDateTime recordedAt,
        Short cpuCount,
        Float cpuPct,
        Float load1,
        Float load5,
        Float load15,
        Long memTotalBytes,
        Long memUsedBytes,
        Long memAvailableBytes,
        Float memUsedPct,
        /** True when the agent derived memory use rather than reading it. */
        Boolean memEstimated,
        Long swapTotalBytes,
        Long swapUsedBytes,
        Float swapUsedPct,
        Long netRxBps,
        Long netTxBps
    ) {
        public static Resources of(ServerResource r, ZoneId zone) {
            if (r == null) {
                return null;
            }
            return new Resources(
                Zoned.at(r.getRecordTimestamp(), zone),
                r.getCpuCount(),
                r.getCpuUsagePct(),
                r.getLoadAvg1m(),
                r.getLoadAvg5m(),
                r.getLoadAvg15m(),
                r.getMemTotalBytes(),
                r.getMemUsedBytes(),
                r.getMemAvailableBytes(),
                r.getMemUsedPct(),
                r.getMemEstimated(),
                r.getSwapTotalBytes(),
                r.getSwapUsedBytes(),
                r.getSwapUsedPct(),
                r.getNetRxBps(),
                r.getNetTxBps()
            );
        }
    }

    /**
     * One filesystem. A mount the agent could not measure arrives with an
     * error and null metrics -- and sometimes no device or fstype either.
     * That is a monitoring signal, so it is reported rather than filtered.
     */
    public record MountItem(
        String mountPoint,
        String device,
        String fstype,
        Long totalBytes,
        Long usedBytes,
        Long freeBytes,
        Long reservedBytes,
        Float usedPct,
        String error
    ) {
        public static MountItem fromEntity(ServerStorage s) {
            return new MountItem(
                s.getMountPoint(),
                s.getDevice(),
                s.getFstype(),
                s.getTotalBytes(),
                s.getUsedBytes(),
                s.getFreeBytes(),
                s.getReservedBytes(),
                s.getUsedPct(),
                s.getError()
            );
        }
    }

    public record LogItem(
        String uuid,
        String channel,
        String pubPath,
        String savePath
    )
    {
        public static LogItem fromEntity(ServerLog log) {
            return new LogItem(
                log.getUuid(),
                log.getChannel(),
                log.getPubPath(),
                log.getSavePath()
            );
        }
    }

    /** Presence and storage unknown; use the overload where lookups are available. */
    public static ServerResponse fromEntity(Server server, ZoneId zone) {
        return fromEntity(server, null, null, null, null, zone);
    }

    public static ServerResponse fromEntity(Server server, Boolean online, Instant lastSeenAt,
                                            ServerResource latestResource, Storage storage,
                                            ZoneId zone) {
        return new ServerResponse(
            server.getUuid(),
            server.getSystem() != null ? server.getSystem().getUuid() : null,
            server.getSystem() != null ? server.getSystem().getName() : null,
            server.getName(),
            server.getIp(),
            server.getServerType(),
            server.getDescription(),
            online,
            Zoned.at(lastSeenAt, zone),
            latestResource == null ? null : latestResource.getUptimeSeconds(),
            Resources.of(latestResource, zone),
            storage,
            server.getLogs() == null
                ? List.of()
                : server.getLogs().stream().map(LogItem::fromEntity).toList(),
            Zoned.at(server.getCreatedAt(), zone)
        );
    }
}
