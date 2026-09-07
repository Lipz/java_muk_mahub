package com.dev.mhub.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import com.dev.mhub.dto.user.AuthResponse;
import com.dev.mhub.dto.user.UserLoginRequest;
import com.dev.mhub.dto.user.UserRegisterRequest;
import com.dev.mhub.dto.user.UserResponse;
import com.dev.mhub.services.AuthService;
import com.dev.mhub.services.UserService;

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

    @PostMapping("/register")
    public ResponseEntity<UserResponse> Register(@Valid @RequestBody UserRegisterRequest user) {
        UserResponse newMember = authService.register(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(newMember);
    }

}
