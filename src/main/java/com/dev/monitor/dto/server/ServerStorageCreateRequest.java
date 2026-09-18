package com.dev.monitor.dto.server;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ServerStorageCreateRequest(
    @NotBlank(message = "Server ID is required")
    String serverId,

    @NotBlank(message = "Channel is required")
    @Size(max = 100, message = "Channel must not exceed 100 characters")
    String channel,

    @NotNull(message = "Record timestamp is required")
    LocalDateTime recordTimestamp,

    @NotBlank(message = "Payload JSON is required")
    String payload
) {}
