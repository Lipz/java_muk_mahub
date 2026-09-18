package com.dev.monitor.dto.user;

public record AuthResponse(
    String accessToken, 
    long expiresInMs) 
{}
