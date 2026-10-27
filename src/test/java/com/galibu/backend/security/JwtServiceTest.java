package com.galibu.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256-bits!!";

    private final JwtService jwtService = new JwtService(SECRET, 60, 30);

    @Test
    void accessTokenRoundTrip() {
        String token = jwtService.generateAccessToken("user-1", "a@test.com", "CLIENT");

        Claims claims = jwtService.parseValidAccessToken(token);

        assertEquals("user-1", claims.getSubject());
        assertEquals("a@test.com", claims.get("email", String.class));
        assertEquals("CLIENT", claims.get("role", String.class));
        assertEquals("access", claims.get("typ", String.class));
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccess() {
        String refresh = jwtService.generateRefreshToken("user-1");

        assertThrows(JwtException.class, () -> jwtService.parseValidAccessToken(refresh));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateAccessToken("user-1", "a@test.com", "CLIENT");
        String tampered = token.substring(0, token.length() - 2) + "ab";

        assertThrows(JwtException.class, () -> jwtService.parseValidAccessToken(tampered));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService other = new JwtService("another-secret-long-enough-for-hs256-bits!!!!", 60, 30);
        String token = other.generateAccessToken("user-1", "a@test.com", "CLIENT");

        assertThrows(JwtException.class, () -> jwtService.parseValidAccessToken(token));
    }

    @Test
    void refreshTokenRoundTrip() {
        String refresh = jwtService.generateRefreshToken("user-1");

        Claims claims = jwtService.parseValidRefreshToken(refresh);

        assertEquals("user-1", claims.getSubject());
        assertEquals("refresh", claims.get("typ", String.class));
        assertTrue(jwtService.getAccessExpiresInSeconds() > 0);
    }
}
