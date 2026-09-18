package com.dev.monitor.dto.server;

import com.dev.monitor.entity.server.ServerStorage;

import java.time.LocalDateTime;

public record ServerStorageResponse(
    String uuid,
    String serverId,
    String channel,
    LocalDateTime recordTimestamp,
    String payload
) {
    public static ServerStorageResponse fromEntity(ServerStorage storage) {
        return new ServerStorageResponse(
            storage.getUuid(),
            storage.getServer() != null ? storage.getServer().getUuid() : null,
            storage.getChannel(),
            storage.getRecordTimestamp(),
            storage.getPayload()
        );
    }
}
