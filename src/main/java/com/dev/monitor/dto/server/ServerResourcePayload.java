package com.dev.monitor.dto.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Payload published on the resource-info Redis channel, roughly every 3-5s:
 *
 * <pre>
 * {"serverId":"be91...","timestamp":"2026-09-22T04:06:23Z","uptimeSeconds":6044623,
 *  "cpu":{"count":16,"usedPercent":1.928,"load1":1.04,"load5":0.9,"load15":0.81},
 *  "memory":{"totalBytes":33719185408,"usedBytes":11708960768,
 *            "availableBytes":22010224640,"usedPercent":34.72,"estimated":false},
 *  "swap":{"totalBytes":20971515904,"usedBytes":0,"usedPercent":0},
 *  "network":{"rxBytesPerSec":103669.046,"txBytesPerSec":239812.334}}
 * </pre>
 *
 * Every field is a boxed type and every group is nullable: a partial payload
 * should still produce a row rather than being dropped wholesale.
 *
 * ignoreUnknown on each level so a new agent field does not start rejecting
 * messages -- it shows up in the subscriber log, and we add a column for it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerResourcePayload(
    String serverId,
    Instant timestamp,
    Long uptimeSeconds,
    Cpu cpu,
    Memory memory,
    Swap swap,
    Network network
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cpu(
        Integer count,
        Double usedPercent,
        Double load1,
        Double load5,
        Double load15
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Memory(
        Long totalBytes,
        Long usedBytes,
        Long availableBytes,
        Double usedPercent,
        Boolean estimated
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Swap(
        Long totalBytes,
        Long usedBytes,
        Double usedPercent
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Network(
        Double rxBytesPerSec,
        Double txBytesPerSec
    ) {}
}
