package com.dev.mhub.dto.user;

import jakarta.validation.constraints.NotBlank;

public record LDAPLoginRequest(    
    @NotBlank
    String username,

    @NotBlank
    String password
) {}
