package com.dev.monitor.dto.server;

import com.dev.monitor.entity.server.Server;
import com.dev.monitor.entity.server.ServerType;

import java.time.LocalDateTime;
import java.util.List;

public record ServerGroupResponse(
    String systemId,
    String systemName,
    List<ServerItem> server
) {
    public record ServerItem(
        String uuid,
        String name,
        String ip,
        ServerType serverType,
        LocalDateTime createdAt
    ) {
        public static ServerItem fromEntity(Server server) {
            return new ServerItem(
                server.getUuid(),
                server.getName(),
                server.getIp(),
                server.getServerType(),
                server.getCreatedAt()
            );
        }
    }
}
