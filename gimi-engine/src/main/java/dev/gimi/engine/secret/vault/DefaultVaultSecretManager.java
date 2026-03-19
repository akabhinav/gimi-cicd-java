package dev.gimi.engine.secret.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gimi.core.model.secret.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultVaultSecretManager implements VaultSecretManager {
    private static final Logger log = LoggerFactory.getLogger(DefaultVaultSecretManager.class);

    private final Map<String, SecretStore> stores = new ConcurrentHashMap<>();
    private final Map<String, ManagedSecret> managedSecrets = new ConcurrentHashMap<>();
    private final Map<String, String> localSecretValues = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public DefaultVaultSecretManager(ObjectMapper mapper) {
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder().build();
        // Add default local store
        stores.put("local", new SecretStore("local", "Local Secret Store",
            SecretStoreType.LOCAL, Map.of(), true, Instant.now()));
    }

    @Override
    public String getSecret(String storeId, String path) {
        SecretStore store = stores.get(storeId);
        if (store == null) throw new IllegalArgumentException("Secret store not found: " + storeId);

        return switch (store.type()) {
            case LOCAL -> localSecretValues.get(storeId + ":" + path);
            case HASHICORP_VAULT -> getFromVault(store, path);
            case AWS_SECRETS_MANAGER -> getFromAwsSecretsManager(store, path);
            case AWS_KMS -> getFromAwsKms(store, path);
            case GCP_SECRET_MANAGER -> getFromGcpSecretManager(store, path);
            case AZURE_KEY_VAULT -> getFromAzureKeyVault(store, path);
        };
    }

    @Override
    public void putSecret(String storeId, String path, String value) {
        SecretStore store = stores.get(storeId);
        if (store == null) throw new IllegalArgumentException("Secret store not found: " + storeId);

        switch (store.type()) {
            case LOCAL -> localSecretValues.put(storeId + ":" + path, value);
            case HASHICORP_VAULT -> putToVault(store, path, value);
            case AWS_SECRETS_MANAGER -> putToAwsSecretsManager(store, path, value);
            default -> throw new UnsupportedOperationException("Write not supported for: " + store.type());
        }
    }

    @Override
    public void deleteSecret(String storeId, String path) {
        SecretStore store = stores.get(storeId);
        if (store == null) return;
        if (store.type() == SecretStoreType.LOCAL) {
            localSecretValues.remove(storeId + ":" + path);
        }
    }

    @Override
    public List<String> listSecrets(String storeId, String prefix) {
        SecretStore store = stores.get(storeId);
        if (store == null) return List.of();
        if (store.type() == SecretStoreType.LOCAL) {
            String keyPrefix = storeId + ":" + (prefix != null ? prefix : "");
            return localSecretValues.keySet().stream()
                .filter(k -> k.startsWith(keyPrefix))
                .map(k -> k.substring(storeId.length() + 1))
                .toList();
        }
        return List.of();
    }

    @Override
    public ManagedSecret create(ManagedSecret secret, String value) {
        managedSecrets.put(secret.id(), secret);
        String storeId = secret.storeId() != null ? secret.storeId() : "local";
        putSecret(storeId, secret.externalPath() != null ? secret.externalPath() : secret.id(), value);
        return secret;
    }

    @Override
    public ManagedSecret update(String id, String value) {
        ManagedSecret existing = managedSecrets.get(id);
        if (existing == null) throw new IllegalArgumentException("Secret not found: " + id);
        ManagedSecret updated = new ManagedSecret(existing.id(), existing.name(), existing.description(),
            existing.secretType(), existing.storeId(), existing.externalPath(), existing.metadata(),
            existing.version() + 1, existing.createdAt(), Instant.now(), existing.expiresAt(), existing.createdBy());
        managedSecrets.put(id, updated);
        String storeId = updated.storeId() != null ? updated.storeId() : "local";
        putSecret(storeId, updated.externalPath() != null ? updated.externalPath() : id, value);
        return updated;
    }

    @Override
    public String read(String id) {
        ManagedSecret meta = managedSecrets.get(id);
        if (meta == null) throw new IllegalArgumentException("Secret not found: " + id);
        String storeId = meta.storeId() != null ? meta.storeId() : "local";
        return getSecret(storeId, meta.externalPath() != null ? meta.externalPath() : id);
    }

    @Override
    public void delete(String id) {
        ManagedSecret meta = managedSecrets.remove(id);
        if (meta != null) {
            String storeId = meta.storeId() != null ? meta.storeId() : "local";
            deleteSecret(storeId, meta.externalPath() != null ? meta.externalPath() : id);
        }
    }

    @Override
    public Optional<ManagedSecret> getMeta(String id) { return Optional.ofNullable(managedSecrets.get(id)); }

    @Override
    public List<ManagedSecret> listManaged() { return List.copyOf(managedSecrets.values()); }

    @Override
    public SecretStore createStore(SecretStore store) { stores.put(store.id(), store); return store; }

    @Override
    public List<SecretStore> listStores() { return List.copyOf(stores.values()); }

    @Override
    public Optional<SecretStore> getStore(String id) { return Optional.ofNullable(stores.get(id)); }

    @Override
    public void deleteStore(String id) { stores.remove(id); }

    // --- Backend integrations ---

    @SuppressWarnings("unchecked")
    private String getFromVault(SecretStore store, String path) {
        try {
            String vaultUrl = store.config().get("url");
            String token = store.config().get("token");
            String mountPath = store.config().getOrDefault("mountPath", "secret");

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(vaultUrl + "/v1/" + mountPath + "/data/" + path))
                .header("X-Vault-Token", token).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            Map<String, Object> secretData = (Map<String, Object>) data.get("data");
            return (String) secretData.getOrDefault("value", secretData.toString());
        } catch (Exception e) {
            log.error("Vault read failed for path {}: {}", path, e.getMessage());
            throw new RuntimeException("Vault read failed", e);
        }
    }

    private void putToVault(SecretStore store, String path, String value) {
        try {
            String vaultUrl = store.config().get("url");
            String token = store.config().get("token");
            String mountPath = store.config().getOrDefault("mountPath", "secret");

            String json = mapper.writeValueAsString(Map.of("data", Map.of("value", value)));
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(vaultUrl + "/v1/" + mountPath + "/data/" + path))
                .header("X-Vault-Token", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("Vault write failed for path {}: {}", path, e.getMessage());
            throw new RuntimeException("Vault write failed", e);
        }
    }

    private String getFromAwsSecretsManager(SecretStore store, String path) {
        // AWS SDK integration - delegates to AWS Secrets Manager API
        log.info("AWS Secrets Manager: reading {}", path);
        throw new UnsupportedOperationException("AWS Secrets Manager requires AWS SDK configuration");
    }

    private void putToAwsSecretsManager(SecretStore store, String path, String value) {
        log.info("AWS Secrets Manager: writing {}", path);
        throw new UnsupportedOperationException("AWS Secrets Manager requires AWS SDK configuration");
    }

    private String getFromAwsKms(SecretStore store, String path) {
        log.info("AWS KMS: decrypting {}", path);
        throw new UnsupportedOperationException("AWS KMS requires AWS SDK configuration");
    }

    private String getFromGcpSecretManager(SecretStore store, String path) {
        log.info("GCP Secret Manager: reading {}", path);
        throw new UnsupportedOperationException("GCP Secret Manager requires GCP SDK configuration");
    }

    private String getFromAzureKeyVault(SecretStore store, String path) {
        log.info("Azure Key Vault: reading {}", path);
        throw new UnsupportedOperationException("Azure Key Vault requires Azure SDK configuration");
    }
}
