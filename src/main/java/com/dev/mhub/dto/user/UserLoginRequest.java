package com.dev.mhub.dto.user;

import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest (
    @NotBlank 
    String email, 
    
    @NotBlank String 
    password
) {}
