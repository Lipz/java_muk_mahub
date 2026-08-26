package com.dev.mhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
            auth -> auth.requestMatchers(  
                "/api/auth/register",
                "/api/auth/login",
                "/api/v1/users",
                "/api/v1/users/*" 
            ).permitAll()
            .anyRequest().authenticated() );
        return http.build();
    }

}
