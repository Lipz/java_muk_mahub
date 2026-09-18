package com.dev.monitor.exception.server;

public class ServerLogAlreadyExistsException extends RuntimeException {
    public ServerLogAlreadyExistsException(String channel) {
        super("Log channel already registered for this server: " + channel);
    }
}
