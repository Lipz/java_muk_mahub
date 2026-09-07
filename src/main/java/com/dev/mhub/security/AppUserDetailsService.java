package com.dev.mhub.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.dev.mhub.entity.User;
import com.dev.mhub.repository.UserRepository;

/**
 * CustomsUserDetailService
 */
@Service
public class AppUserDetailsService  implements UserDetailsService{

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmailIgnoreCase(email)
            .map(AppUserDetails::new)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}