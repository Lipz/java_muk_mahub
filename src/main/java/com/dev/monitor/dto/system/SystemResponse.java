package com.dev.monitor.dto.system;

import com.dev.monitor.entity.system.SystemEntity;

import java.time.LocalDateTime;

public record SystemResponse(
    String uuid,
    String name,
    LocalDateTime createdAt
) {
    public static SystemResponse fromEntity(SystemEntity entity) {
        return new SystemResponse(
            entity.getUuid(),
            entity.getName(),
            entity.getCreatedAt()
        );
    }
}
