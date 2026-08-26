package com.dev.mhub.exception.user;

public class EmailAlreadyExistsException extends RuntimeException {
    
    public EmailAlreadyExistsException(String email) {
        super("Email already exists: " + email);
    }
}
