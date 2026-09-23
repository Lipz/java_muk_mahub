package com.dev.monitor.dto.server;

/**
 * One log line pushed to a live tail. {@code seq} increases by one per line of the
 * same server channel, so a client can spot a gap.
 */
public record LogStreamLine(long seq, String timestamp, String line) {}
