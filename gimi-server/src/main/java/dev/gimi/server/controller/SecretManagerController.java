package dev.gimi.server.controller;

import dev.gimi.core.model.secret.*;
import dev.gimi.engine.secret.vault.DefaultVaultSecretManager;
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

    private DefaultVaultSecretManager asDefault() {
        if (secretManager instanceof DefaultVaultSecretManager d) return d;
        throw new UnsupportedOperationException("AWS operations require DefaultVaultSecretManager");
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

    // --- AWS Secrets Manager Operations ---

    @GetMapping("/stores/{storeId}/aws/describe/{path}")
    public ResponseEntity<Map<String, String>> describeAwsSecret(
            @PathVariable String storeId, @PathVariable String path) {
        return ResponseEntity.ok(asDefault().describeAwsSecret(storeId, path));
    }

    @PostMapping("/stores/{storeId}/aws/rotation")
    public ResponseEntity<Void> configureRotation(
            @PathVariable String storeId,
            @RequestBody Map<String, Object> body) {
        asDefault().configureAwsRotation(storeId,
                (String) body.get("secretName"),
                (String) body.get("rotationLambdaArn"),
                ((Number) body.getOrDefault("rotationDays", 30)).intValue());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/stores/{storeId}/aws/rotate/{path}")
    public ResponseEntity<Map<String, String>> rotateNow(
            @PathVariable String storeId, @PathVariable String path) {
        String versionId = asDefault().rotateAwsSecretNow(storeId, path);
        return ResponseEntity.ok(Map.of("versionId", versionId));
    }

    @PostMapping("/stores/{storeId}/aws/tag/{path}")
    public ResponseEntity<Void> tagSecret(
            @PathVariable String storeId, @PathVariable String path,
            @RequestBody Map<String, String> tags) {
        asDefault().tagAwsSecret(storeId, path, tags);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/stores/{storeId}/aws/restore/{path}")
    public ResponseEntity<Void> restoreSecret(
            @PathVariable String storeId, @PathVariable String path) {
        asDefault().restoreAwsSecret(storeId, path);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stores/{storeId}/aws/list")
    public ResponseEntity<List<String>> listAwsSecrets(
            @PathVariable String storeId,
            @RequestParam(required = false) String prefix) {
        return ResponseEntity.ok(secretManager.listSecrets(storeId, prefix));
    }

    // --- AWS KMS Operations ---

    @PostMapping("/stores/{storeId}/kms/encrypt")
    public ResponseEntity<Map<String, String>> kmsEncrypt(
            @PathVariable String storeId,
            @RequestBody Map<String, String> body) {
        String ciphertext = asDefault().getAwsKmsProvider()
                .encrypt(secretManager.getStore(storeId).orElseThrow(), body.get("keyId"), body.get("plaintext"));
        return ResponseEntity.ok(Map.of("ciphertext", ciphertext));
    }

    @PostMapping("/stores/{storeId}/kms/decrypt")
    public ResponseEntity<Map<String, String>> kmsDecrypt(
            @PathVariable String storeId,
            @RequestBody Map<String, String> body) {
        String plaintext = asDefault().getAwsKmsProvider()
                .decrypt(secretManager.getStore(storeId).orElseThrow(), body.get("ciphertext"));
        return ResponseEntity.ok(Map.of("plaintext", plaintext));
    }

    @PostMapping("/stores/{storeId}/kms/generate-data-key")
    public ResponseEntity<Map<String, String>> generateDataKey(
            @PathVariable String storeId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                asDefault().generateKmsDataKey(storeId, body.get("keyId")));
    }

    @GetMapping("/stores/{storeId}/kms/describe/{keyId}")
    public ResponseEntity<Map<String, String>> describeKmsKey(
            @PathVariable String storeId, @PathVariable String keyId) {
        return ResponseEntity.ok(asDefault().describeKmsKey(storeId, keyId));
    }

    @GetMapping("/stores/{storeId}/kms/keys")
    public ResponseEntity<List<Map<String, String>>> listKmsKeys(
            @PathVariable String storeId) {
        return ResponseEntity.ok(asDefault().getAwsKmsProvider()
                .listKeys(secretManager.getStore(storeId).orElseThrow()));
    }
}
