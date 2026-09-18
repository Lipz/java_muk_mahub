package com.dev.monitor.dto.server;

import com.dev.monitor.entity.server.ServerResource;

import java.time.LocalDateTime;

public record ServerResourceResponse(
    String uuid,
    String serverId,
    String channel,
    LocalDateTime recordTimestamp,
    String payload
) {
    public static ServerResourceResponse fromEntity(ServerResource resource) {
        return new ServerResourceResponse(
            resource.getUuid(),
            resource.getServer() != null ? resource.getServer().getUuid() : null,
            resource.getChannel(),
            resource.getRecordTimestamp(),
            resource.getPayload()
        );
    }
}
