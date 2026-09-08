package com.dev.mhub.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AdUserDetails implements UserDetails {
    private final String username;
    private final String displayName;
    private final String email;
    private final List<String> group;
    private final Collection<? extends GrantedAuthority> authorities;

    public AdUserDetails(String username, String displayName, String email,
                        List<String> group, 
                        Collection<? extends GrantedAuthority> authorities
    ){
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.group = List.copyOf(group) ;
        this.authorities = authorities;
    }

    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public List<String> getGroups() { return this.group; }

    public boolean isAdmin() {
        return authorities.stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public String getUsername() { return username; }

    @Override
    public String getPassword() { return null; }
}
