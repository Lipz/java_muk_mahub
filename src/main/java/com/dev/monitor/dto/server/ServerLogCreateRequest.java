package com.dev.monitor.dto.server;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ServerLogCreateRequest(
    @NotBlank(message = "Server identifier (UUID or name) is required")
    @JsonAlias({"serverId", "serverName"})
    String server,

    @NotBlank(message = "Channel is required")
    @Size(max = 100, message = "Channel must not exceed 100 characters")
    String channel,

    @NotBlank(message = "Publish path is required")
    @Size(max = 500, message = "Publish path must not exceed 500 characters")
    String pubPath,

    @Size(max = 500, message = "Save path must not exceed 500 characters")
    String savePath
) {
    public ServerLogCreateRequest(String server, String channel, String pubPath) {
        this(server, channel, pubPath, null);
    }
}
