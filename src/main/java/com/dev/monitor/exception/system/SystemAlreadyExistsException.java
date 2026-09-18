package com.dev.monitor.exception.system;

public class SystemAlreadyExistsException extends RuntimeException {
    public SystemAlreadyExistsException(String name) {
        super("System already exists with name: " + name);
    }
}
