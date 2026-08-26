package com.dev.mhub.exception;

import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.dev.mhub.exception.user.EmailAlreadyExistsException;
import com.dev.mhub.exception.user.UserNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ExceptionError> handleUserNotFound(RuntimeException exception) 
    {
        ExceptionError response = new ExceptionError(
                        404,
                        "Not Found",
                        exception.getMessage(),
                        LocalDateTime.now() );
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ExceptionError> handleEmailAlreadyExists(EmailAlreadyExistsException exception) {

        ExceptionError response =
                new ExceptionError(
                        409,
                        "Conflict",
                        exception.getMessage(),
                        LocalDateTime.now()
                );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }
}
