package com.dev.mhub.exception;

import java.time.LocalDateTime;

import javax.naming.CommunicationException;
import javax.security.sasl.AuthenticationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryAuthenticationException;
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
