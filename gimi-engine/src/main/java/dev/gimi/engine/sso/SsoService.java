package dev.gimi.engine.sso;

import dev.gimi.core.auth.sso.*;
import java.util.*;

public interface SsoService {
    String getAuthorizationUrl(String providerId, String state, String redirectUri);
    SsoAuthResult handleCallback(String providerId, String code, String redirectUri);
    SsoSession refreshSession(String sessionId);
    void revokeSession(String sessionId);
    List<SsoProvider> listProviders();
    Optional<SsoProvider> getProvider(String id);
    SsoProvider createProvider(SsoProvider provider);
    void deleteProvider(String id);

    record SsoAuthResult(String userId, String username, String email,
                         Set<String> roles, SsoSession session, boolean newUser) {}
}
