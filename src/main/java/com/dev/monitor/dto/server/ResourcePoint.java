package com.dev.monitor.dto.server;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * One bucket of resource history: the average of every scrape that landed
 * in it. Buckets with no scrapes are absent rather than zero, so a gap in
 * the series is a gap in reporting, not a dip to 0%.
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

    /** Row shape of ServerResourceRepository#findHistory. */
    public interface ResourceBucket {
        Long getEpoch();
        Double getCpuPct();
        Double getMemPct();
        Long getNetRxBps();
        Long getNetTxBps();
    }
}
