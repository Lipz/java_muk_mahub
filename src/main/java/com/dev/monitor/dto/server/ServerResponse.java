package com.dev.monitor.dto.server;

import com.dev.monitor.entity.server.Server;
import com.dev.monitor.entity.server.ServerType;

import java.time.LocalDateTime;

public record ServerResponse(
    String uuid,
    String systemId,
    String systemName,
    String name,
    String ip,
    ServerType serverType,
    LocalDateTime createdAt
) {
    public static ServerResponse fromEntity(Server server) {
        return new ServerResponse(
            server.getUuid(),
            server.getSystem() != null ? server.getSystem().getUuid() : null,
            server.getSystem() != null ? server.getSystem().getName() : null,
            server.getName(),
            server.getIp(),
            server.getServerType(),
            server.getCreatedAt()
        );
    }
}
