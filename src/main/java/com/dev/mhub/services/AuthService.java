package com.dev.mhub.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.dev.mhub.dto.user.AuthResponse;
import com.dev.mhub.dto.user.UserLoginRequest;
import com.dev.mhub.dto.user.UserRegisterRequest;
import com.dev.mhub.dto.user.UserResponse;
import com.dev.mhub.entity.User;
import com.dev.mhub.entity.UserStatus;
import com.dev.mhub.exception.user.EmailAlreadyExistsException;
import com.dev.mhub.repository.UserRepository;
import com.dev.mhub.security.AppUserDetails;
import com.dev.mhub.security.AppUserDetailsService;
import com.dev.mhub.security.JwtService;

import jakarta.validation.Valid;

@Service
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AppUserDetailsService appUserDetailsService;
    private final JwtService jwtService;
    private final long expirationMs;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       AppUserDetailsService appUserDetailsService,
                       JwtService jwtService,
                       @Value("${jwt.expiration}") long expirationMs) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.expirationMs = expirationMs;
        this.appUserDetailsService = appUserDetailsService;
    }

    public UserResponse register(@Valid UserRegisterRequest user) {
        if(userRepository.existsByEmailIgnoreCase(user.email())){
            throw new EmailAlreadyExistsException(user.email());
        }
        User newUser = new User(
                        user.name(), user.email(), 
                        passwordEncoder.encode(user.password()),
                        false, 
                        UserStatus.ACTIVE);

        User savedUser  = userRepository.save(newUser);
        UserResponse responeUser = new UserResponse(
                                savedUser.getId(), 
                                savedUser.getName(), 
                                savedUser.getEmail(), 
                                savedUser.getStatus(), 
                                savedUser.isAdmin());
        return responeUser;
    }

    public AuthResponse login(@Valid UserLoginRequest request){
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        AppUserDetails userDetails = (AppUserDetails) appUserDetailsService
                                        .loadUserByUsername(request.email());


        String token = jwtService.generateToken(userDetails);
        AuthResponse tokenResponse = new AuthResponse(token,expirationMs);
        return tokenResponse;
    }

}
