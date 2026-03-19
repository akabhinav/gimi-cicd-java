package dev.gimi.server.controller;

import dev.gimi.core.model.secret.*;
import dev.gimi.engine.secret.vault.VaultSecretManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/secrets")
public class SecretManagerController {

    private final VaultSecretManager secretManager;

    public SecretManagerController(VaultSecretManager secretManager) {
        this.secretManager = secretManager;
    }

    // --- Secret Stores ---
    @GetMapping("/stores")
    public ResponseEntity<List<SecretStore>> listStores() {
        return ResponseEntity.ok(secretManager.listStores());
    }

    @PostMapping("/stores")
    public ResponseEntity<SecretStore> createStore(@RequestBody SecretStore store) {
        return ResponseEntity.ok(secretManager.createStore(store));
    }

    @DeleteMapping("/stores/{id}")
    public ResponseEntity<Void> deleteStore(@PathVariable String id) {
        secretManager.deleteStore(id);
        return ResponseEntity.noContent().build();
    }

    // --- Managed Secrets ---
    @GetMapping
    public ResponseEntity<List<ManagedSecret>> listSecrets() {
        return ResponseEntity.ok(secretManager.listManaged());
    }

    @PostMapping
    public ResponseEntity<ManagedSecret> createSecret(@RequestBody Map<String, String> body) {
        String id = UUID.randomUUID().toString();
        ManagedSecret secret = new ManagedSecret(id, body.get("name"), body.get("description"),
            SecretType.valueOf(body.getOrDefault("type", "TEXT").toUpperCase()),
            body.getOrDefault("storeId", "local"), body.get("path"),
            Map.of(), 1, Instant.now(), null, null, body.get("createdBy"));
        return ResponseEntity.ok(secretManager.create(secret, body.get("value")));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ManagedSecret> getSecretMeta(@PathVariable String id) {
        return secretManager.getMeta(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ManagedSecret> updateSecret(@PathVariable String id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(secretManager.update(id, body.get("value")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSecret(@PathVariable String id) {
        secretManager.delete(id);
        return ResponseEntity.noContent().build();
    }
}
