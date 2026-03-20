package dev.gimi.server.controller;

import dev.gimi.core.auth.ApiKey;
import dev.gimi.core.auth.Role;
import dev.gimi.core.auth.User;
import dev.gimi.server.security.JwtTokenProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Authentication and user management REST controller.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider tokenProvider,
                          PasswordEncoder passwordEncoder,
                          DataSource dataSource) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.dataSource = dataSource;
    }

    /**
     * Authenticate with username/password and return a JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = loadUserByUsername(request.username());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials"));
        }

        String token = tokenProvider.generateToken(user);

        updateLastLogin(user.id());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", token);
        response.put("username", user.username());
        response.put("roles", user.roles().stream().map(Role::name).toList());
        response.put("expiresIn", 86400);

        return ResponseEntity.ok(response);
    }

    /**
     * Register a new user. Only ADMIN can create users, except for the first user.
     */
    @PostMapping("/register")
    public synchronized ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        boolean hasUsers = countUsers() > 0;

        if (hasUsers) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getAuthorities().stream()
                    .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only administrators can register new users"));
            }
        }

        if (loadUserByUsername(request.username()) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Username already exists"));
        }

        // Double-check after synchronization to prevent race condition on first user
        if (!hasUsers && countUsers() > 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Initial admin user already created. Contact an administrator."));
        }

        String id = UUID.randomUUID().toString();
        String passwordHash = passwordEncoder.encode(request.password());
        Set<Role> roles = request.roles() != null && !request.roles().isEmpty()
                ? request.roles()
                : (hasUsers ? Set.of(Role.VIEWER) : Set.of(Role.ADMIN));
        Instant now = Instant.now();

        String rolesJson = "[" + roles.stream().map(r -> "\"" + r.name() + "\"").collect(Collectors.joining(",")) + "]";

        String sql = "INSERT INTO users (id, username, password_hash, email, roles, enabled, created_at) " +
                     "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, request.username());
            ps.setString(3, passwordHash);
            ps.setString(4, request.email());
            ps.setString(5, rolesJson);
            ps.setBoolean(6, true);
            ps.setTimestamp(7, Timestamp.from(now));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to create user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create user"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("username", request.username());
        response.put("email", request.email());
        response.put("roles", roles.stream().map(Role::name).toList());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Create an API key for programmatic access. Returns the raw key once.
     */
    @PostMapping("/api-keys")
    public ResponseEntity<Map<String, Object>> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User owner = loadUserByUsername(username);

        if (owner == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not found"));
        }

        String id = UUID.randomUUID().toString();
        String rawKey = "gimi_" + UUID.randomUUID().toString().replace("-", "");
        String keyHash = passwordEncoder.encode(rawKey);
        Set<String> scopes = request.scopes() != null ? request.scopes() : Set.of();
        Instant now = Instant.now();
        Instant expiresAt = request.expiresInDays() > 0
                ? now.plusSeconds(request.expiresInDays() * 86400L)
                : null;

        ApiKey apiKey = new ApiKey(id, request.name(), keyHash, owner.id(), scopes,
                true, now, expiresAt, null);

        String scopesJson = "[" + apiKey.scopes().stream()
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(",")) + "]";

        String sql = "INSERT INTO api_keys (id, name, key_hash, owner_id, scopes, enabled, created_at, expires_at) " +
                     "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, apiKey.id());
            ps.setString(2, apiKey.name());
            ps.setString(3, apiKey.keyHash());
            ps.setString(4, apiKey.ownerId());
            ps.setString(5, scopesJson);
            ps.setBoolean(6, apiKey.enabled());
            ps.setTimestamp(7, Timestamp.from(apiKey.createdAt()));
            ps.setTimestamp(8, apiKey.expiresAt() != null ? Timestamp.from(apiKey.expiresAt()) : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to create API key: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create API key"));
        }

        String apiToken = tokenProvider.generateApiToken(apiKey);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", apiKey.id());
        response.put("name", apiKey.name());
        response.put("key", rawKey);
        response.put("token", apiToken);
        response.put("scopes", apiKey.scopes());
        response.put("expiresAt", apiKey.expiresAt());
        response.put("warning", "Store the key securely. It will not be shown again.");

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get current authenticated user info.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User user = loadUserByUsername(username);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.id());
        response.put("username", user.username());
        response.put("email", user.email());
        response.put("roles", user.roles().stream().map(Role::name).toList());
        response.put("enabled", user.enabled());
        response.put("createdAt", user.createdAt());
        response.put("lastLogin", user.lastLogin());

        return ResponseEntity.ok(response);
    }

    private User loadUserByUsername(String username) {
        String sql = "SELECT id, username, password_hash, email, roles, enabled, created_at, last_login " +
                     "FROM users WHERE username = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Set<Role> roles = new HashSet<>();
                String rolesStr = rs.getString("roles");
                if (rolesStr != null && !rolesStr.isBlank()) {
                    // Parse JSONB array like ["ADMIN"] or CSV like "ADMIN,VIEWER"
                    String cleaned = rolesStr.replaceAll("[\\[\\]\"\\s]", "");
                    if (!cleaned.isEmpty()) {
                        Arrays.stream(cleaned.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .map(Role::valueOf)
                                .forEach(roles::add);
                    }
                }
                return new User(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("email"),
                        roles,
                        rs.getBoolean("enabled"),
                        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                        rs.getTimestamp("last_login") != null ? rs.getTimestamp("last_login").toInstant() : null
                );
            }
        } catch (SQLException e) {
            log.error("Failed to load user '{}': {}", username, e.getMessage());
            return null;
        }
    }

    private long countUsers() {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.warn("Failed to count users: {}", e.getMessage());
        }
        return 0;
    }

    private void updateLastLogin(String userId) {
        String sql = "UPDATE users SET last_login = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Failed to update last login for user {}: {}", userId, e.getMessage());
        }
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Email String email,
            Set<Role> roles
    ) {}

    public record CreateApiKeyRequest(
            @NotBlank String name,
            Set<String> scopes,
            int expiresInDays
    ) {}
}
