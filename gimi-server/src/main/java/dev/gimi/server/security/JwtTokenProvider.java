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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * JWT token generation and validation using the JJWT library.
 */
@Component
public class JwtTokenProvider {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final Duration TOKEN_EXPIRY = Duration.ofHours(24);
    private static final int MIN_KEY_BYTES = 32; // 256 bits for HS256

    private final SecretKey key;

    public JwtTokenProvider(@Value("${gimi.server.jwt-secret:gimi-dev-secret-key-change-in-production-at-least-32-bytes!}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_KEY_BYTES) {
            LOG.warn("JWT secret is only {} bits ({} bytes); hashing to derive a 256-bit key. "
                    + "Set a secret with at least 32 bytes for production.", secretBytes.length * 8, secretBytes.length);
            try {
                secretBytes = MessageDigest.getInstance("SHA-256").digest(secretBytes);
            } catch (NoSuchAlgorithmException e) {
                secretBytes = Arrays.copyOf(secretBytes, MIN_KEY_BYTES);
            }
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
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
