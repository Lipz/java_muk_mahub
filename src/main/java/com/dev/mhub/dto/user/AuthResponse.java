package com.dev.mhub.dto.user;

public record AuthResponse(
    String accessToken, 
    long expiresInMs) 
{}
