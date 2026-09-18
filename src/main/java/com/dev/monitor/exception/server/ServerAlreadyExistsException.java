package com.dev.monitor.exception.server;

public class ServerAlreadyExistsException extends RuntimeException {
    public ServerAlreadyExistsException(String name) {
        super("Server already exists with name: " + name + " in this system");
    }
}
