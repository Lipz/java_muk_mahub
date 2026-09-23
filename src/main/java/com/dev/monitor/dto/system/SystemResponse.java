package com.dev.monitor.dto.system;

import com.dev.monitor.entity.system.SystemEntity;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record SystemResponse(
    String uuid,
    String name,
    OffsetDateTime createdAt
) {
    /**
     * createdAt is rendered in the configured display zone with its offset
     * intact, so the value reads as local time while staying an unambiguous
     * instant. Storage remains timestamptz.
     */
    public static SystemResponse fromEntity(SystemEntity entity, ZoneId zone) {
        return new SystemResponse(
            entity.getUuid(),
            entity.getName(),
            entity.getCreatedAt() == null
                ? null
                : entity.getCreatedAt().atZone(zone).toOffsetDateTime()
        );
    }
}
