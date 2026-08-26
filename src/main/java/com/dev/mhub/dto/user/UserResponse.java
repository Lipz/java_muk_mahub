package com.dev.mhub.dto.user;

import java.time.LocalDateTime;

public record UserResponse(
    Long id,
    String name, 
    String email,
    Boolean status,
    Boolean isAdmin,
    LocalDateTime createdAt
) {}
