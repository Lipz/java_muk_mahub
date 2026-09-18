package com.dev.monitor.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.monitor.dto.user.AuthResponse;
import com.dev.monitor.dto.user.LDAPLoginRequest;
import com.dev.monitor.dto.user.UserLoginRequest;
import com.dev.monitor.dto.user.UserRegisterRequest;
import com.dev.monitor.dto.user.UserResponse;
import com.dev.monitor.services.AuthService;

import org.springframework.http.HttpStatus;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private final AuthService authService;
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody UserLoginRequest user){
        
        AuthResponse auth = authService.login(user);
        return ResponseEntity.status(HttpStatus.OK).body(auth);
    }

    @PostMapping("/ldap")
    public ResponseEntity<AuthResponse> LDAPLogin(@Valid @RequestBody LDAPLoginRequest request){
        AuthResponse response = authService.ldapLogin(request);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/register")
    public ResponseEntity<UserResponse> Register(@Valid @RequestBody UserRegisterRequest user) {
        UserResponse newMember = authService.register(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(newMember);
    }

}
