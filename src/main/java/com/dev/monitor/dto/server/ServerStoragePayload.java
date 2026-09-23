package com.dev.monitor.dto.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * Payload published hourly on the storage- Redis channel:
 *
 * <pre>
 * {"serverId":"be91...","timestamp":"2026-05-28T10:00:00Z",
 *  "server":{"totalBytes":225485783040,"usedBytes":53502541824,
 *            "freeBytes":160709131878,"reservedBytes":11274109338,
 *            "usedPercent":24.98,"partial":false},
 *  "mounts":[
 *    {"path":"/","device":"/dev/sda1","fsType":"ext4","totalBytes":214748364800,
 *     "usedBytes":52428800000,"freeBytes":151582326374,
 *     "reservedBytes":10737238426,"usedPercent":25.7},
 *    {"path":"/mnt/nas","fsType":"nfs4","error":"network filesystem not supported"},
 *    {"path":"/missing","error":"no such file or directory"}]}
 * </pre>
 *
 * One message fans out to one row per mount plus one summary row.
 *
 * A mount may carry an error instead of metrics, and an errored mount may
 * omit device and fsType entirely -- so every field below is boxed and
 * nullable. Only {@code path} is required, since it is part of the key.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerStoragePayload(
    String serverId,
    Instant timestamp,
    Summary server,
    List<Mount> mounts
) {

    /** Host-level rollup; the sum of the non-error mounts, plus `partial`. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
        Long totalBytes,
        Long usedBytes,
        Long freeBytes,
        Long reservedBytes,
        Double usedPercent,
        Boolean partial
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mount(
        String path,
        String device,
        String fsType,
        Long totalBytes,
        Long usedBytes,
        Long freeBytes,
        Long reservedBytes,
        Double usedPercent,
        String error
    ) {}
}
