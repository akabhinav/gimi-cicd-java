package dev.gimi.core.auth.sso;

import java.time.Instant;
import java.util.*;

public record SsoSession(
    String id,
    String userId,
    String providerId,
    String externalSubject,
    String email,
    Map<String, String> claims,
    String accessToken,
    String refreshToken,
    Instant createdAt,
    Instant expiresAt
) {
    public SsoSession {
        Objects.requireNonNull(id);
        Objects.requireNonNull(userId);
        claims = claims == null ? Map.of() : Map.copyOf(claims);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
