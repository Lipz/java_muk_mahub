package com.dev.mhub.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
import com.dev.mhub.entity.User;
import com.dev.mhub.entity.UserStatus;

public class UserPrincipal  implements UserDetails{
    private final User user;
    public UserPrincipal(User user) { this.user = user; }
    public User getUser() { return user; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.isAdmin()
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    @Override public String getPassword() { return user.getPassword(); }
    @Override public String getUsername() { return user.getEmail(); }   // email = login identity
    @Override public boolean isEnabled()            { return user.getStatus() == UserStatus.ACTIVE; }
    @Override public boolean isAccountNonLocked()   { return user.getStatus() != UserStatus.BLOCKED; }
    @Override public boolean isAccountNonExpired()  { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

}
