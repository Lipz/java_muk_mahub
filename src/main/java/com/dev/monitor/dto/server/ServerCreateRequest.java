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
    ServerType serverType,

    // Optional: no @NotBlank, so omitting it or sending null is valid.
    @Size(max = 500, message = "Description must not exceed 500 characters")
    String description
) {
    /** Keeps callers that predate the description field compiling. */
    public ServerCreateRequest(String systemId, String name, String ip, ServerType serverType) {
        this(systemId, name, ip, serverType, null);
    }
}
