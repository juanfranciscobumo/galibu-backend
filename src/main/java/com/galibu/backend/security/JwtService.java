package com.galibu.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessMinutes;
    private final long refreshDays;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.access-minutes:60}") long accessMinutes,
        @Value("${app.jwt.refresh-days:30}") long refreshDays
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;
    }

    public String generateAccessToken(String userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("role", role)
            .claim("typ", "access")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(accessMinutes * 60)))
            .signWith(key)
            .compact();
    }

    public String generateRefreshToken(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId)
            .claim("typ", "refresh")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(refreshDays * 24 * 60 * 60)))
            .signWith(key)
            .compact();
    }

    public Claims parseValidAccessToken(String token) {
        Claims claims = parse(token);
        if (!"access".equals(claims.get("typ", String.class))) {
            throw new JwtException("Not an access token");
        }
        return claims;
    }

    public Claims parseValidRefreshToken(String token) {
        Claims claims = parse(token);
        if (!"refresh".equals(claims.get("typ", String.class))) {
            throw new JwtException("Not a refresh token");
        }
        return claims;
    }

    private Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public long getAccessExpiresInSeconds() {
        return accessMinutes * 60;
    }
}
