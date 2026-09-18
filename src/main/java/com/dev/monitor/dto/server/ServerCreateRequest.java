package com.dev.monitor.dto.server;

import com.dev.monitor.entity.server.ServerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ServerCreateRequest(
    @NotBlank(message = "System ID is required")
    String systemId,

    @NotBlank(message = "Server name is required")
    @Size(max = 255, message = "Server name must not exceed 255 characters")
    String name,

    @NotBlank(message = "Server IP is required")
    @Size(max = 45, message = "Server IP must not exceed 45 characters")
    String ip,

    @NotNull(message = "Server type is required")
    ServerType serverType
) {}
