package dev.gimi.server.security;

import dev.gimi.core.auth.ApiKey;
import dev.gimi.core.auth.Role;
import dev.gimi.core.auth.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * JWT token generation and validation using the JJWT library.
 */
@Component
public class JwtTokenProvider {

    private static final Duration TOKEN_EXPIRY = Duration.ofHours(24);

    private final SecretKey key;

    public JwtTokenProvider(@Value("${gimi.server.jwt-secret:change-me-in-production-min-32-chars!!}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a JWT with subject=username, roles claim, and 24h expiry.
     */
    public String generateToken(User user) {
        Instant now = Instant.now();
        String roles = user.roles().stream()
                .map(Role::name)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(user.username())
                .claim("userId", user.id())
                .claim("roles", roles)
                .claim("type", "user")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_EXPIRY)))
                .signWith(key)
                .compact();
    }

    /**
     * Creates a JWT for API keys with scopes claim.
     */
    public String generateApiToken(ApiKey apiKey) {
        Instant now = Instant.now();
        String scopes = String.join(",", apiKey.scopes());

        return Jwts.builder()
                .subject(apiKey.name())
                .claim("apiKeyId", apiKey.id())
                .claim("ownerId", apiKey.ownerId())
                .claim("scopes", scopes)
                .claim("type", "apikey")
                .issuedAt(Date.from(now))
                .expiration(apiKey.expiresAt() != null
                        ? Date.from(apiKey.expiresAt())
                        : Date.from(now.plus(TOKEN_EXPIRY)))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates a JWT token, returning the claims.
     *
     * @throws JwtException if the token is invalid or expired
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the username (subject) from a JWT token.
     */
    public String getUsernameFromToken(String token) {
        return validateToken(token).getSubject();
    }
}
