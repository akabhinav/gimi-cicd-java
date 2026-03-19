package dev.gimi.server.controller;

import dev.gimi.core.auth.sso.*;
import dev.gimi.engine.sso.SsoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/sso")
public class SsoController {

    private final SsoService ssoService;

    public SsoController(SsoService ssoService) {
        this.ssoService = ssoService;
    }

    @GetMapping("/providers")
    public ResponseEntity<List<SsoProvider>> listProviders() {
        return ResponseEntity.ok(ssoService.listProviders());
    }

    @PostMapping("/providers")
    public ResponseEntity<SsoProvider> createProvider(@RequestBody SsoProvider provider) {
        return ResponseEntity.ok(ssoService.createProvider(provider));
    }

    @DeleteMapping("/providers/{id}")
    public ResponseEntity<Void> deleteProvider(@PathVariable String id) {
        ssoService.deleteProvider(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/authorize/{providerId}")
    public ResponseEntity<Map<String, String>> getAuthorizationUrl(
            @PathVariable String providerId,
            @RequestParam String redirectUri) {
        String state = UUID.randomUUID().toString();
        String url = ssoService.getAuthorizationUrl(providerId, state, redirectUri);
        return ResponseEntity.ok(Map.of("authorizationUrl", url, "state", state));
    }

    @PostMapping("/callback/{providerId}")
    public ResponseEntity<Map<String, Object>> handleCallback(
            @PathVariable String providerId,
            @RequestParam String code,
            @RequestParam String redirectUri) {
        SsoService.SsoAuthResult result = ssoService.handleCallback(providerId, code, redirectUri);
        Map<String, Object> response = new HashMap<>();
        response.put("userId", result.userId());
        response.put("username", result.username());
        response.put("email", result.email());
        response.put("roles", result.roles());
        response.put("sessionId", result.session().id());
        response.put("newUser", result.newUser());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions/{sessionId}/refresh")
    public ResponseEntity<SsoSession> refreshSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(ssoService.refreshSession(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId) {
        ssoService.revokeSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
