package com.dev.mhub.security;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.dev.mhub.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;
    private final String issuer;

    public JwtService(  @Value("${jwt.secret}") String base64Secret,
                        @Value("${jwt.expiration}") long expirationMs,
                        @Value("${jwt.issuer}") String issuer) {
        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must decode to at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }

    public long getExpirationMs() { return expirationMs; }
    
    public String generateToken(AppUserDetails user) {
        Date now = new Date();
        return Jwts.builder().setIssuer(issuer)
                .setSubject(user.getUsername())
                .claim("uid", user.getUser().getId())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }


    public String generateToken(String subject, Map<String, Object> claims){
        Instant now = Instant.now();
        return Jwts.builder()
            .setSubject(subject)
            .setClaims(claims)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(key)
            .compact();
    }
    
    public Claims parseClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key)
                                .requireIssuer(issuer)
                                .build()
                                .parseClaimsJws(token)
                                .getBody();
    }

    public String extractEmail(String token) { 
        return parseClaims(token).getSubject(); 
    }

    // public Long extractUserId();

}
