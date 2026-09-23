package com.dev.monitor.dto.server;

import java.util.List;

/**
 * First event of a live tail: the end of today's file, oldest line first. A client replaces
 * whatever it shows with these lines; the next {@code line} event has sequence {@code seq + 1}.
 */
public record LogStreamBackfill(long seq, List<String> lines) {}
