package com.dev.monitor.exception.system;

public class SystemNotFoundException extends RuntimeException {
    public SystemNotFoundException(String id) {
        super("System not found with ID: " + id);
    }
}
