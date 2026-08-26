package com.dev.mhub.dto.user;

public record AuthResponse(
    String accessToken, 
    String tokenType, 
    long expiresInMs,
    UserResponse user) 
{
    public static AuthResponse of(String token, long expiresInMs, UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresInMs, user);
    }
}
