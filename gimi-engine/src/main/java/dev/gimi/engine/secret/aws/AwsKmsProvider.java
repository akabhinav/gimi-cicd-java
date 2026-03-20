package dev.gimi.engine.secret.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gimi.core.model.secret.SecretStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * AWS KMS integration using the AWS JSON REST API directly.
 *
 * <p>Uses SigV4 signing with Java HttpClient — no KMS SDK dependency required.
 * Provides envelope encryption capabilities:
 * <ul>
 *   <li>Encrypt/decrypt secret values with KMS CMKs</li>
 *   <li>Data key generation for client-side envelope encryption</li>
 *   <li>Key listing and description</li>
 *   <li>Key rotation enablement</li>
 *   <li>Key alias management</li>
 * </ul>
 *
 * <p>Store config keys:
 * <ul>
 *   <li>{@code region} — AWS region</li>
 *   <li>{@code accessKeyId} — AWS access key</li>
 *   <li>{@code secretAccessKey} — AWS secret key</li>
 *   <li>{@code endpoint} — custom endpoint for testing (optional)</li>
 *   <li>{@code kmsKeyId} — default KMS key ID or alias</li>
 * </ul>
 */
public class AwsKmsProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AwsKmsProvider.class);
    private static final String SERVICE = "kms";
    private static final String TARGET_PREFIX = "TrentService.";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public AwsKmsProvider(ObjectMapper mapper) {
        this.httpClient = HttpClient.newBuilder().build();
        this.mapper = mapper;
    }

    /**
     * Encrypts a plaintext value using KMS.
     *
     * @param store     the secret store configuration
     * @param keyId     KMS key ID or alias (null to use default from config)
     * @param plaintext the value to encrypt
     * @return Base64-encoded ciphertext
     */
    public String encrypt(SecretStore store, String keyId, String plaintext) {
        String effectiveKeyId = keyId != null ? keyId : store.config().get("kmsKeyId");
        String encodedPlaintext = Base64.getEncoder().encodeToString(
                plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Map<String, Object> payload = Map.of(
                "KeyId", effectiveKeyId,
                "Plaintext", encodedPlaintext
        );

        Map<String, Object> response = callApi(store, "Encrypt", payload);
        String ciphertext = (String) response.get("CiphertextBlob");
        LOG.info("Encrypted data with KMS key: keyId={}", effectiveKeyId);
        return ciphertext;
    }

    /**
     * Decrypts a KMS-encrypted ciphertext.
     *
     * @param store            the secret store configuration
     * @param ciphertextBase64 Base64-encoded ciphertext blob
     * @return the decrypted plaintext
     */
    public String decrypt(SecretStore store, String ciphertextBase64) {
        Map<String, Object> payload = Map.of(
                "CiphertextBlob", ciphertextBase64
        );

        Map<String, Object> response = callApi(store, "Decrypt", payload);
        String plaintextBase64 = (String) response.get("Plaintext");
        String plaintext = new String(Base64.getDecoder().decode(plaintextBase64),
                java.nio.charset.StandardCharsets.UTF_8);
        LOG.info("Decrypted data with KMS key: keyId={}", response.get("KeyId"));
        return plaintext;
    }

    /**
     * Generates a data key for client-side envelope encryption.
     *
     * @param store the secret store configuration
     * @param keyId KMS key ID (null for default)
     * @return map with "plaintext" and "ciphertext" (both Base64)
     */
    public Map<String, String> generateDataKey(SecretStore store, String keyId) {
        String effectiveKeyId = keyId != null ? keyId : store.config().get("kmsKeyId");

        Map<String, Object> payload = Map.of(
                "KeyId", effectiveKeyId,
                "KeySpec", "AES_256"
        );

        Map<String, Object> response = callApi(store, "GenerateDataKey", payload);
        Map<String, String> result = Map.of(
                "plaintext", (String) response.get("Plaintext"),
                "ciphertext", (String) response.get("CiphertextBlob"),
                "keyId", effectiveKeyId
        );

        LOG.info("Generated data key: keyId={}", effectiveKeyId);
        return result;
    }

    /**
     * Lists all KMS keys accessible in the configured region.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> listKeys(SecretStore store) {
        Map<String, Object> payload = Map.of("Limit", 100);
        Map<String, Object> response = callApi(store, "ListKeys", payload);

        List<Map<String, Object>> keys = (List<Map<String, Object>>)
                response.getOrDefault("Keys", List.of());

        List<Map<String, String>> result = keys.stream()
                .map(k -> Map.of(
                        "keyId", String.valueOf(k.get("KeyId")),
                        "keyArn", String.valueOf(k.get("KeyArn"))))
                .toList();

        LOG.info("Listed {} KMS keys", result.size());
        return result;
    }

    /**
     * Describes a KMS key's metadata.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> describeKey(SecretStore store, String keyId) {
        String effectiveKeyId = keyId != null ? keyId : store.config().get("kmsKeyId");

        Map<String, Object> payload = Map.of("KeyId", effectiveKeyId);
        Map<String, Object> response = callApi(store, "DescribeKey", payload);

        Map<String, Object> meta = (Map<String, Object>)
                response.getOrDefault("KeyMetadata", Map.of());

        Map<String, String> result = new LinkedHashMap<>();
        result.put("keyId", String.valueOf(meta.getOrDefault("KeyId", "")));
        result.put("arn", String.valueOf(meta.getOrDefault("Arn", "")));
        result.put("description", String.valueOf(meta.getOrDefault("Description", "")));
        result.put("keyState", String.valueOf(meta.getOrDefault("KeyState", "")));
        result.put("keyUsage", String.valueOf(meta.getOrDefault("KeyUsage", "")));
        result.put("keySpec", String.valueOf(meta.getOrDefault("KeySpec", "")));
        result.put("enabled", String.valueOf(meta.getOrDefault("Enabled", "")));
        result.put("keyManager", String.valueOf(meta.getOrDefault("KeyManager", "")));

        LOG.info("Described KMS key: keyId={}, state={}", result.get("keyId"), result.get("keyState"));
        return result;
    }

    /**
     * Enables automatic key rotation for a KMS CMK.
     */
    public void enableKeyRotation(SecretStore store, String keyId) {
        callApi(store, "EnableKeyRotation", Map.of("KeyId", keyId));
        LOG.info("Enabled automatic rotation for KMS key: {}", keyId);
    }

    /**
     * Creates a key alias for easier reference.
     */
    public void createAlias(SecretStore store, String aliasName, String keyId) {
        callApi(store, "CreateAlias", Map.of(
                "AliasName", aliasName,
                "TargetKeyId", keyId
        ));
        LOG.info("Created KMS alias: {} -> {}", aliasName, keyId);
    }

    // ── Private Helpers ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> callApi(SecretStore store, String action,
                                         Map<String, Object> payload) {
        String region = store.config().getOrDefault("region", "us-east-1");
        String endpoint = store.config().getOrDefault("endpoint",
                "https://kms." + region + ".amazonaws.com");
        String accessKey = store.config().get("accessKeyId");
        String secretKey = store.config().get("secretAccessKey");

        try {
            String body = mapper.writeValueAsString(payload);
            URI uri = URI.create(endpoint);

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/x-amz-json-1.1");
            headers.put("X-Amz-Target", TARGET_PREFIX + action);

            if (accessKey != null && secretKey != null) {
                AwsSigV4Signer signer = new AwsSigV4Signer(accessKey, secretKey, region);
                headers = signer.sign("POST", uri, SERVICE, headers, body);
            }

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            for (Map.Entry<String, String> h : headers.entrySet()) {
                reqBuilder.header(h.getKey(), h.getValue());
            }

            HttpResponse<String> response = httpClient.send(
                    reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                LOG.error("AWS KMS API error: action={}, status={}", action, response.statusCode());
                throw new RuntimeException("AWS KMS " + action +
                        " failed (HTTP " + response.statusCode() + "): " + response.body());
            }

            if (response.body() == null || response.body().isEmpty()) {
                return Map.of();
            }
            return mapper.readValue(response.body(), Map.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("AWS KMS API call failed: action={}", action, e);
            throw new RuntimeException("AWS KMS " + action + " failed", e);
        }
    }
}
