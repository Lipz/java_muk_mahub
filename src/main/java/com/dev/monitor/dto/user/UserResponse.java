package com.dev.monitor.dto.user;

import com.dev.monitor.entity.user.UserStatus;

public record UserResponse(
    Long id,
    String name, 
    String email,
    UserStatus status,
    Boolean isAdmin
){}
