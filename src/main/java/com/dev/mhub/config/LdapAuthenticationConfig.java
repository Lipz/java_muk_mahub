package com.dev.mhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.security.config.ldap.LdapBindAuthenticationManagerFactory;
@Configuration
public class LdapAuthenticationConfig {
    @Bean
    public AuthenticationManager ldapAuthenticationManager(BaseLdapPathContextSource contextSource){
        LdapBindAuthenticationManagerFactory factory = new LdapBindAuthenticationManagerFactory( contextSource );
        factory.setUserSearchFilter( "(sAMAccountName={0})" );
        factory.setUserSearchBase( "OU=GDCE,DC=customs,DC=local" );
        return factory.createAuthenticationManager();
    }
}
