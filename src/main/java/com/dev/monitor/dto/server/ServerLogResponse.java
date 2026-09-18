package com.dev.monitor.dto.server;

import com.dev.monitor.entity.server.ServerLog;

public record ServerLogResponse(
    String uuid,
    String serverId,
    String serverName,
    String channel,
    String pubPath,
    String savePath
) {
    public static ServerLogResponse fromEntity(ServerLog log) {
        return new ServerLogResponse(
            log.getUuid(),
            log.getServer() != null ? log.getServer().getUuid() : null,
            log.getServer() != null ? log.getServer().getName() : null,
            log.getChannel(),
            log.getPubPath(),
            log.getSavePath()
        );
    }
}
