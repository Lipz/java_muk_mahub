package com.dev.monitor.dto.server;

import com.dev.monitor.entity.server.ServerResource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * One point of resource history: the average of every scrape that landed in
 * a bucket, or for short windows a single raw scrape. Buckets with no scrapes
 * are absent rather than zero, so a gap in the series is a gap in reporting,
 * not a dip to 0%.
 */
public record ResourcePoint(
    OffsetDateTime t,
    Double cpuPct,
    Double memPct,
    Long netRxBps,
    Long netTxBps
) {
    public static ResourcePoint of(ResourceBucket b, ZoneId zone) {
        return new ResourcePoint(
            Zoned.at(Instant.ofEpochSecond(b.getEpoch()), zone),
            b.getCpuPct(),
            b.getMemPct(),
            b.getNetRxBps(),
            b.getNetTxBps()
        );
    }

    /** One raw scrape, unaveraged. */
    public static ResourcePoint of(ServerResource r, ZoneId zone) {
        return new ResourcePoint(
            Zoned.at(r.getRecordTimestamp(), zone),
            r.getCpuUsagePct() == null ? null : r.getCpuUsagePct().doubleValue(),
            r.getMemUsedPct() == null ? null : r.getMemUsedPct().doubleValue(),
            r.getNetRxBps(),
            r.getNetTxBps()
        );
    }

    /** Row shape of ServerResourceRepository#findHistory. */
    public interface ResourceBucket {
        Long getEpoch();
        Double getCpuPct();
        Double getMemPct();
        Long getNetRxBps();
        Long getNetTxBps();
    }
}
