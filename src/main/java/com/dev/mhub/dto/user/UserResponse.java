package com.dev.mhub.dto.user;

import com.dev.mhub.entity.User;
import com.dev.mhub.entity.UserStatus;

public record UserResponse(
    Long id,
    String name, 
    String email,
    UserStatus status,
    Boolean isAdmin
){

    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(), 
            user.getStatus(), 
            user.isAdmin() 
        );
    }
}
