package com.dev.mhub.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.dev.mhub.entity.User;
import com.dev.mhub.entity.UserStatus;


public class AppUserDetails implements UserDetails {

    private final User user;

    public AppUserDetails(User user) {
        this.user = user;
    }

    public User getUser() { return user; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.isAdmin()
            ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                      new SimpleGrantedAuthority("ROLE_USER"))
            : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override public String getPassword() { return user.getPassword(); }
    @Override public String getUsername() { return user.getEmail(); }
    @Override public boolean isEnabled() { return user.getStatus() == UserStatus.ACTIVE; }

}
