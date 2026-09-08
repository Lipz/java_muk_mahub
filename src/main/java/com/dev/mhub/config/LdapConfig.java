package com.dev.mhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.support.LdapContextSource;

@Configuration
public class LdapConfig {
    @Value("${ldap.url}")
    private String ldapUrl;
    @Value("${ldap.base}")
    private String ldapBase;
    @Value("${ldap.bind-dn}")
    private String bindDn;
    @Value("${ldap.bind-password}")
    private String bindPassword;

    @Bean
    public LdapContextSource ldapContextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(ldapUrl);
        contextSource.setBase(ldapBase);
        contextSource.setUserDn(bindDn);
        contextSource.setPassword(bindPassword);
        contextSource.afterPropertiesSet();
        return contextSource;
    }
}
