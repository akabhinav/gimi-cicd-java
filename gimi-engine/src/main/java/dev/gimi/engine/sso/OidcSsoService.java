package dev.gimi.engine.sso;

import dev.gimi.core.auth.sso.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

public class OidcSsoService implements SsoService {
    private static final Logger log = LoggerFactory.getLogger(OidcSsoService.class);
    private final Map<String, SsoProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, SsoSession> sessions = new ConcurrentHashMap<>();
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public OidcSsoService(ObjectMapper mapper) {
        this.httpClient = HttpClient.newBuilder().build();
        this.mapper = mapper;
    }

    @Override
    public String getAuthorizationUrl(String providerId, String state, String redirectUri) {
        SsoProvider provider = providers.get(providerId);
        if (provider == null) throw new IllegalArgumentException("Unknown SSO provider: " + providerId);

        String endpoint = provider.authorizationEndpoint() != null ?
            provider.authorizationEndpoint() : provider.issuerUrl() + "/authorize";

        return endpoint + "?" +
            "response_type=code" +
            "&client_id=" + URLEncoder.encode(provider.clientId(), StandardCharsets.UTF_8) +
            "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
            "&scope=" + URLEncoder.encode(String.join(" ", provider.scopes()), StandardCharsets.UTF_8) +
            "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SsoAuthResult handleCallback(String providerId, String code, String redirectUri) {
        SsoProvider provider = providers.get(providerId);
        if (provider == null) throw new IllegalArgumentException("Unknown SSO provider: " + providerId);

        try {
            // Exchange code for tokens
            String tokenEndpoint = provider.tokenEndpoint() != null ?
                provider.tokenEndpoint() : provider.issuerUrl() + "/token";

            String body = "grant_type=authorization_code" +
                "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&client_id=" + URLEncoder.encode(provider.clientId(), StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(provider.clientSecret(), StandardCharsets.UTF_8);

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> tokenData = mapper.readValue(tokenResponse.body(), Map.class);

            String accessToken = (String) tokenData.get("access_token");
            String refreshToken = (String) tokenData.get("refresh_token");
            Integer expiresIn = (Integer) tokenData.getOrDefault("expires_in", 3600);

            // Get user info
            String userInfoEndpoint = provider.userInfoEndpoint() != null ?
                provider.userInfoEndpoint() : provider.issuerUrl() + "/userinfo";

            HttpRequest userInfoRequest = HttpRequest.newBuilder()
                .uri(URI.create(userInfoEndpoint))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();

            HttpResponse<String> userInfoResponse = httpClient.send(userInfoRequest, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> userInfo = mapper.readValue(userInfoResponse.body(), Map.class);

            // Map attributes
            String externalId = (String) userInfo.getOrDefault(
                provider.attributeMapping().getOrDefault("sub", "sub"), "unknown");
            String email = (String) userInfo.getOrDefault(
                provider.attributeMapping().getOrDefault("email", "email"), "");
            String username = (String) userInfo.getOrDefault(
                provider.attributeMapping().getOrDefault("preferred_username", "preferred_username"), email);

            // Extract roles from claims
            Set<String> roles = new HashSet<>();
            Object roleClaim = userInfo.get(provider.attributeMapping().getOrDefault("roles", "roles"));
            if (roleClaim instanceof List<?> roleList) {
                roleList.forEach(r -> roles.add(r.toString()));
            }
            if (roles.isEmpty()) roles.add("VIEWER");

            // Create session
            Map<String, String> claims = new HashMap<>();
            userInfo.forEach((k, v) -> claims.put(k, v != null ? v.toString() : ""));

            String sessionId = UUID.randomUUID().toString();
            String userId = "sso-" + externalId;
            SsoSession session = new SsoSession(sessionId, userId, providerId, externalId,
                email, claims, accessToken, refreshToken, Instant.now(),
                Instant.now().plusSeconds(expiresIn));
            sessions.put(sessionId, session);

            return new SsoAuthResult(userId, username, email, roles, session, true);
        } catch (Exception e) {
            log.error("SSO callback failed for provider {}: {}", providerId, e.getMessage(), e);
            throw new RuntimeException("SSO authentication failed", e);
        }
    }

    @Override
    public SsoSession refreshSession(String sessionId) {
        SsoSession existing = sessions.get(sessionId);
        if (existing == null) throw new IllegalArgumentException("Session not found: " + sessionId);
        // In a real impl, use refresh_token to get new access_token
        SsoSession refreshed = new SsoSession(existing.id(), existing.userId(), existing.providerId(),
            existing.externalSubject(), existing.email(), existing.claims(),
            existing.accessToken(), existing.refreshToken(), existing.createdAt(),
            Instant.now().plusSeconds(3600));
        sessions.put(sessionId, refreshed);
        return refreshed;
    }

    @Override
    public void revokeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public List<SsoProvider> listProviders() {
        return List.copyOf(providers.values());
    }

    @Override
    public Optional<SsoProvider> getProvider(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    @Override
    public SsoProvider createProvider(SsoProvider provider) {
        providers.put(provider.id(), provider);
        return provider;
    }

    @Override
    public void deleteProvider(String id) {
        providers.remove(id);
    }
}
