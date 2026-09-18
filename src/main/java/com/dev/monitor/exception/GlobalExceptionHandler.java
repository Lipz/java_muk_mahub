package com.dev.monitor.exception;

import java.time.LocalDateTime;

import javax.naming.CommunicationException;
import javax.security.sasl.AuthenticationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryAuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import com.dev.monitor.exception.user.EmailAlreadyExistsException;
import com.dev.monitor.exception.user.UserNotFoundException;
import com.dev.monitor.exception.system.SystemAlreadyExistsException;
import com.dev.monitor.exception.system.SystemNotFoundException;
import com.dev.monitor.exception.server.ServerAlreadyExistsException;
import com.dev.monitor.exception.server.ServerLogAlreadyExistsException;
import com.dev.monitor.exception.server.ServerNotFoundException;

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

        @ExceptionHandler({SystemAlreadyExistsException.class, ServerAlreadyExistsException.class, ServerLogAlreadyExistsException.class})
        public ResponseEntity<ExceptionError> handleAlreadyExists(RuntimeException exception) {
            ExceptionError response = new ExceptionError(
                    HttpStatus.CONFLICT.value(),
                    "Conflict",
                    exception.getMessage(),
                    LocalDateTime.now()
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        @ExceptionHandler({SystemNotFoundException.class, ServerNotFoundException.class})
        public ResponseEntity<ExceptionError> handleResourceNotFound(RuntimeException exception) {
            ExceptionError response = new ExceptionError(
                    HttpStatus.NOT_FOUND.value(),
                    "Not Found",
                    exception.getMessage(),
                    LocalDateTime.now()
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ExceptionError> handleValidationException(MethodArgumentNotValidException ex) {
            String firstError = ex.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .findFirst()
                    .orElse("Validation failed");

            ExceptionError response = new ExceptionError(
                    HttpStatus.BAD_REQUEST.value(),
                    "Bad Request",
                    firstError,
                    LocalDateTime.now()
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<ExceptionError> handleBadCredentials(BadCredentialsException ex) {
                ExceptionError response = new ExceptionError(
                        HttpStatus.UNAUTHORIZED.value(),
                        "Unauthorized",
                        "Invalid username or password",
                        LocalDateTime.now()
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

         @ExceptionHandler(ActiveDirectoryAuthenticationException.class)
        public ResponseEntity<ExceptionError> handleActiveDirectoryException(ActiveDirectoryAuthenticationException ex) {
            ExceptionError response = new ExceptionError(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Active Directory Error",
                    ex.getMessage() + (ex.getDataCode() != null ? " (Data Code: " + ex.getDataCode() + ")" : ""),
                    LocalDateTime.now()
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    
        // 6. LDAP Server unreachable / Connection Timeout
        @ExceptionHandler(CommunicationException.class)
        public ResponseEntity<ExceptionError> handleLdapCommunication(CommunicationException ex) {
            ExceptionError response = new ExceptionError(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "LDAP Service Unavailable",
                    "Unable to connect to Active Directory server. Please verify network/VPN.",
                    LocalDateTime.now()
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    
        // 7. General fallback for any other AuthenticationException
        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ExceptionError> handleAuthenticationException(AuthenticationException ex) {
            ExceptionError response = new ExceptionError(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Authentication Failed",
                    ex.getMessage(),
                    LocalDateTime.now()
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
}
