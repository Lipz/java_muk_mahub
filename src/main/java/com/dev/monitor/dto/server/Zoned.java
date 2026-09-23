package com.dev.monitor.dto.server;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Renders stored instants in the configured display zone.
 *
 * Entities and columns stay absolute (Instant / timestamptz); this is the
 * single point where a response is expressed in local time. The result is an
 * OffsetDateTime, never a LocalDateTime: the offset must survive into the
 * JSON ("2026-09-22T15:57:46+07:00") so the value remains an unambiguous
 * instant. Dropping it would leave a wall-clock string that any client
 * outside the display zone would silently misread.
 */
final class Zoned {

    private Zoned() {
    }

    static OffsetDateTime at(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).toOffsetDateTime();
    }
}
