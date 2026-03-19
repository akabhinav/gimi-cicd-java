package dev.gimi.server.security;

import dev.gimi.core.auth.ApiKey;
import dev.gimi.core.auth.Role;
import dev.gimi.core.auth.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    // Secret must be at least 32 chars for HMAC-SHA256
    private static final String TEST_SECRET = "test-secret-key-minimum-32-chars!!";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(TEST_SECRET);
    }

    private User createUser(String username, Set<Role> roles) {
        return new User("user-1", username, "hashed", "user@test.com",
                roles, true, Instant.now(), null);
    }

    @Test
    void shouldGenerateValidToken() {
        User user = createUser("admin", Set.of(Role.ADMIN));

        String token = tokenProvider.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    void shouldValidateGeneratedToken() {
        User user = createUser("admin", Set.of(Role.ADMIN));

        String token = tokenProvider.generateToken(user);
        Claims claims = tokenProvider.validateToken(token);

        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(claims.get("userId", String.class)).isEqualTo("user-1");
        assertThat(claims.get("roles", String.class)).contains("ADMIN");
        assertThat(claims.get("type", String.class)).isEqualTo("user");
    }

    @Test
    void shouldExtractUsernameFromToken() {
        User user = createUser("developer", Set.of(Role.DEVELOPER));

        String token = tokenProvider.generateToken(user);
        String username = tokenProvider.getUsernameFromToken(token);

        assertThat(username).isEqualTo("developer");
    }

    @Test
    void shouldIncludeMultipleRolesInToken() {
        User user = createUser("power-user", Set.of(Role.ADMIN, Role.OPERATOR));

        String token = tokenProvider.generateToken(user);
        Claims claims = tokenProvider.validateToken(token);

        String roles = claims.get("roles", String.class);
        assertThat(roles).contains("ADMIN");
        assertThat(roles).contains("OPERATOR");
    }

    @Test
    void shouldRejectTokenWithInvalidSignature() {
        User user = createUser("admin", Set.of(Role.ADMIN));

        String token = tokenProvider.generateToken(user);
        // Tamper with the token
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> tokenProvider.validateToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectCompletelyInvalidToken() {
        assertThatThrownBy(() -> tokenProvider.validateToken("not.a.valid.token"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldRejectEmptyToken() {
        assertThatThrownBy(() -> tokenProvider.validateToken(""))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldRejectTokenFromDifferentSecret() {
        User user = createUser("admin", Set.of(Role.ADMIN));

        String token = tokenProvider.generateToken(user);

        JwtTokenProvider otherProvider = new JwtTokenProvider(
                "different-secret-minimum-32-chars!!");

        assertThatThrownBy(() -> otherProvider.validateToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldGenerateApiKeyToken() {
        ApiKey apiKey = new ApiKey("key-1", "my-api-key", "hash",
                "owner-1", Set.of("pipeline:read", "run:view"),
                true, Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS), null);

        String token = tokenProvider.generateApiToken(apiKey);

        assertThat(token).isNotBlank();
        Claims claims = tokenProvider.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo("my-api-key");
        assertThat(claims.get("apiKeyId", String.class)).isEqualTo("key-1");
        assertThat(claims.get("ownerId", String.class)).isEqualTo("owner-1");
        assertThat(claims.get("type", String.class)).isEqualTo("apikey");
        assertThat(claims.get("scopes", String.class)).contains("pipeline:read");
    }

    @Test
    void shouldSetExpirationFromApiKeyExpiry() {
        Instant futureExpiry = Instant.now().plus(7, ChronoUnit.DAYS);
        ApiKey apiKey = new ApiKey("key-1", "key", "hash", "owner",
                Set.of(), true, Instant.now(), futureExpiry, null);

        String token = tokenProvider.generateApiToken(apiKey);
        Claims claims = tokenProvider.validateToken(token);

        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    void shouldUseDefaultExpiryWhenApiKeyHasNoExpiry() {
        ApiKey apiKey = new ApiKey("key-1", "key", "hash", "owner",
                Set.of(), true, Instant.now(), null, null);

        String token = tokenProvider.generateApiToken(apiKey);
        Claims claims = tokenProvider.validateToken(token);

        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    void shouldSetIssuedAt() {
        User user = createUser("admin", Set.of(Role.ADMIN));

        String token = tokenProvider.generateToken(user);
        Claims claims = tokenProvider.validateToken(token);

        assertThat(claims.getIssuedAt()).isNotNull();
    }

    @Test
    void shouldSetExpiration() {
        User user = createUser("admin", Set.of(Role.ADMIN));

        String token = tokenProvider.generateToken(user);
        Claims claims = tokenProvider.validateToken(token);

        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void differentUsersProduceDifferentTokens() {
        User user1 = createUser("alice", Set.of(Role.ADMIN));
        User user2 = createUser("bob", Set.of(Role.VIEWER));

        String token1 = tokenProvider.generateToken(user1);
        String token2 = tokenProvider.generateToken(user2);

        assertThat(token1).isNotEqualTo(token2);
    }
}
