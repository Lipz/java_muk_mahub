package com.dev.mhub.dto.user;

import com.dev.mhub.entity.User;
import com.dev.mhub.entity.UserStatus;

public record UserResponse(
    Long id,
    String name, 
    String email,
    UserStatus status,
    Boolean isAdmin
){}
