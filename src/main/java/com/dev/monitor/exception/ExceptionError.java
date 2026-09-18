package com.dev.monitor.exception;

import java.time.LocalDateTime;

public record ExceptionError(
        int status,
        String error,
        String message,
        LocalDateTime timestamp) {}