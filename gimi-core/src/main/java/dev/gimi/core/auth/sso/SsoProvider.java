package dev.gimi.core.auth.sso;

import java.util.*;

public record SsoProvider(
    String id,
    String name,
    SsoType type,
    String issuerUrl,
    String clientId,
    String clientSecret,
    String authorizationEndpoint,
    String tokenEndpoint,
    String userInfoEndpoint,
    String jwksUri,
    Set<String> scopes,
    Map<String, String> attributeMapping,
    boolean enabled
) {
    public SsoProvider {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
        scopes = scopes == null ? Set.of("openid", "profile", "email") : Set.copyOf(scopes);
        attributeMapping = attributeMapping == null ? Map.of() : Map.copyOf(attributeMapping);
    }
}
