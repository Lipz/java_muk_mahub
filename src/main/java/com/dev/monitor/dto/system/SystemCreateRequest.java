package com.dev.monitor.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SystemCreateRequest(
    @NotBlank(message = "System name is required")
    @Size(max = 255, message = "System name must not exceed 255 characters")
    String name
) {}
