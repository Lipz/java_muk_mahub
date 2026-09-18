package com.dev.monitor.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dev.monitor.dto.user.UserRegisterRequest;
import com.dev.monitor.dto.user.UserResponse;
import com.dev.monitor.services.UserService;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class UserController {
    
    private final UserService userService;
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRegisterRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse> > getAllUsers() {
        return ResponseEntity.ok(userService.getUsers());
    }

    // @GetMapping("/{id}")
    // public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
    //     return ResponseEntity.ok(
    //             userService.getUser(id)
    //     );
    // }

}
