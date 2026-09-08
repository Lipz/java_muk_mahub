package com.dev.mhub.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.dev.mhub.security.AdUserDetails;
import com.dev.mhub.security.AppUserDetailsService;
import com.dev.mhub.security.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity 
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final AppUserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          AppUserDetailsService userDetailsService) {
        this.jwtFilter = jwtFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    // 1. Database (Local MySQL) Authentication Provider
    @Bean
    @Primary
    public AuthenticationProvider authenticationProvider(PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public UserDetailsContextMapper userDetailsContextMapper() {
        return new UserDetailsContextMapper() {
            @Override
            public UserDetails mapUserFromContext(DirContextOperations ctx,
                                                  String username,
                                                  Collection<? extends GrantedAuthority> authorities) {
                String displayName = ctx.getStringAttribute("displayName");
                String mail = ctx.getStringAttribute("mail");
                String[] memberOf = ctx.getStringAttributes("memberOf");
                List<String> groups = (memberOf != null) ? Arrays.asList(memberOf) : List.of();

                // Ensure the user has at least ROLE_USER
                List<GrantedAuthority> grantedAuthorities = new ArrayList<>(authorities);
                if (grantedAuthorities.stream().noneMatch(a -> a.getAuthority().equals("ROLE_USER"))) {
                    grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                }

                return new AdUserDetails(
                        username,
                        displayName != null ? displayName : username,
                        mail != null ? mail : "",
                        groups,
                        grantedAuthorities
                );
            }

            @Override
            public void mapUserToContext(UserDetails user, DirContextAdapter ctx) {
                // Read-only authentication; no write-back needed
            }
        };
    }

    // 2. Active Directory / LDAP Authentication Provider
    @Bean(name = "ldapAuthenticationProvider")
    public AuthenticationProvider ldapAuthenticationProvider(
        @Value("${ldap.domain}") String domain,
        @Value("${ldap.url}") String url,
        @Value("${ldap.base}") String rootDn,
        @Value("${ldap.user-search-filter:(sAMAccountName={0})}") String searchFilter,
        UserDetailsContextMapper userDetailsContextMapper
    ){
        ActiveDirectoryLdapAuthenticationProvider provider =
                new ActiveDirectoryLdapAuthenticationProvider(domain, url, rootDn);
        provider.setSearchFilter(searchFilter);
        provider.setConvertSubErrorCodesToExceptions(true);
        provider.setUserDetailsContextMapper(userDetailsContextMapper);

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("authenticationProvider") AuthenticationProvider daoProvider) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/error").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .authenticationProvider(daoProvider)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                .accessDeniedHandler((req, res, e) ->
                    res.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")));

        return http.build();
    }
}
