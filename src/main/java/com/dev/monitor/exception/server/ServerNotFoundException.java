package com.dev.monitor.exception.server;

public class ServerNotFoundException extends RuntimeException {
    public ServerNotFoundException(String id) {
        super("Server not found with ID: " + id);
    }
}
