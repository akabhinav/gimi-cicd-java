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
 * AWS Secrets Manager integration using the AWS JSON REST API directly.
 *
 * <p>Uses SigV4 signing with Java HttpClient — no AWS SDK dependency required.
 * Full lifecycle management for secrets in AWS Secrets Manager including:
 * <ul>
 *   <li>Create, read, update, delete secrets</li>
 *   <li>Secret versioning with staging labels</li>
 *   <li>Automatic rotation with configurable schedules</li>
 *   <li>Secret listing with filtering</li>
 *   <li>Tag-based organization</li>
 *   <li>Secret restore from deletion</li>
 *   <li>Resource policy management for cross-account access</li>
 * </ul>
 *
 * <p>Store config keys:
 * <ul>
 *   <li>{@code region} — AWS region (e.g., us-east-1)</li>
 *   <li>{@code accessKeyId} — AWS access key</li>
 *   <li>{@code secretAccessKey} — AWS secret key</li>
 *   <li>{@code endpoint} — custom endpoint URI for LocalStack/testing (optional)</li>
 *   <li>{@code kmsKeyId} — KMS key ID for encryption (optional)</li>
 *   <li>{@code prefix} — path prefix for secret names (optional)</li>
 * </ul>
 */
public class AwsSecretsManagerProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AwsSecretsManagerProvider.class);
    private static final String SERVICE = "secretsmanager";
    private static final String TARGET_PREFIX = "secretsmanager.";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public AwsSecretsManagerProvider(ObjectMapper mapper) {
        this.httpClient = HttpClient.newBuilder().build();
        this.mapper = mapper;
    }

    /**
     * Retrieves a secret value from AWS Secrets Manager.
     */
    @SuppressWarnings("unchecked")
    public String getSecret(SecretStore store, String path) {
        String secretName = prefixPath(store, path);
        Map<String, Object> payload = Map.of("SecretId", secretName);

        Map<String, Object> response = callApi(store, "GetSecretValue", payload);
        LOG.info("Retrieved secret from AWS Secrets Manager: name={}, versionId={}",
                secretName, response.get("VersionId"));

        String secretString = (String) response.get("SecretString");
        if (secretString != null) return secretString;

        // Binary secret — return as Base64
        return (String) response.get("SecretBinary");
    }

    /**
     * Retrieves a specific version of a secret.
     */
    public String getSecretVersion(SecretStore store, String path, String versionId) {
        String secretName = prefixPath(store, path);
        Map<String, Object> payload = Map.of(
                "SecretId", secretName,
                "VersionId", versionId
        );

        Map<String, Object> response = callApi(store, "GetSecretValue", payload);
        LOG.info("Retrieved secret version: name={}, version={}", secretName, versionId);
        return (String) response.get("SecretString");
    }

    /**
     * Creates or updates a secret in AWS Secrets Manager.
     */
    public void putSecret(SecretStore store, String path, String value) {
        String secretName = prefixPath(store, path);

        // Try PutSecretValue first (update existing)
        try {
            Map<String, Object> payload = Map.of(
                    "SecretId", secretName,
                    "SecretString", value
            );
            callApi(store, "PutSecretValue", payload);
            LOG.info("Updated secret in AWS Secrets Manager: {}", secretName);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFoundException")) {
                // Secret doesn't exist — create it
                createSecret(store, secretName, value);
            } else {
                throw e;
            }
        }
    }

    /**
     * Creates a new secret.
     */
    private void createSecret(SecretStore store, String secretName, String value) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("Name", secretName);
        payload.put("SecretString", value);

        String kmsKeyId = store.config().get("kmsKeyId");
        if (kmsKeyId != null && !kmsKeyId.isEmpty()) {
            payload.put("KmsKeyId", kmsKeyId);
        }

        callApi(store, "CreateSecret", payload);
        LOG.info("Created secret in AWS Secrets Manager: {}", secretName);
    }

    /**
     * Deletes a secret from AWS Secrets Manager.
     */
    public void deleteSecret(SecretStore store, String path, boolean forceDelete, int recoveryDays) {
        String secretName = prefixPath(store, path);
        Map<String, Object> payload = new HashMap<>();
        payload.put("SecretId", secretName);

        if (forceDelete) {
            payload.put("ForceDeleteWithoutRecovery", true);
        } else {
            payload.put("RecoveryWindowInDays", Math.max(7, Math.min(30, recoveryDays)));
        }

        callApi(store, "DeleteSecret", payload);
        LOG.info("Deleted secret: name={}, forceDelete={}", secretName, forceDelete);
    }

    /**
     * Lists secrets, optionally filtered by prefix.
     */
    @SuppressWarnings("unchecked")
    public List<String> listSecrets(SecretStore store, String prefix) {
        String fullPrefix = prefixPath(store, prefix != null ? prefix : "");
        Map<String, Object> payload = new HashMap<>();
        payload.put("MaxResults", 100);

        if (fullPrefix != null && !fullPrefix.isEmpty()) {
            payload.put("Filters", List.of(
                    Map.of("Key", "name", "Values", List.of(fullPrefix))
            ));
        }

        Map<String, Object> response = callApi(store, "ListSecrets", payload);
        List<Map<String, Object>> secretList = (List<Map<String, Object>>)
                response.getOrDefault("SecretList", List.of());

        List<String> names = secretList.stream()
                .map(s -> (String) s.get("Name"))
                .map(name -> removePrefixFromPath(store, name))
                .toList();

        LOG.info("Listed {} secrets with prefix '{}'", names.size(), fullPrefix);
        return names;
    }

    /**
     * Describes a secret's metadata without retrieving the value.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> describeSecret(SecretStore store, String path) {
        String secretName = prefixPath(store, path);
        Map<String, Object> payload = Map.of("SecretId", secretName);

        try {
            Map<String, Object> response = callApi(store, "DescribeSecret", payload);

            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("arn", String.valueOf(response.getOrDefault("ARN", "")));
            meta.put("name", String.valueOf(response.getOrDefault("Name", "")));
            meta.put("description", String.valueOf(response.getOrDefault("Description", "")));
            meta.put("kmsKeyId", String.valueOf(response.getOrDefault("KmsKeyId", "")));
            meta.put("rotationEnabled", String.valueOf(response.getOrDefault("RotationEnabled", false)));
            if (response.containsKey("LastRotatedDate")) {
                meta.put("lastRotatedDate", String.valueOf(response.get("LastRotatedDate")));
            }
            if (response.containsKey("LastChangedDate")) {
                meta.put("lastChangedDate", String.valueOf(response.get("LastChangedDate")));
            }
            if (response.containsKey("CreatedDate")) {
                meta.put("createdDate", String.valueOf(response.get("CreatedDate")));
            }

            LOG.info("Described secret: {}, rotation={}", secretName, meta.get("rotationEnabled"));
            return meta;
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFoundException")) {
                return Map.of();
            }
            throw e;
        }
    }

    /**
     * Configures automatic rotation for a secret.
     */
    public void configureRotation(SecretStore store, String path,
                                   String rotationLambdaArn, int rotationDays) {
        String secretName = prefixPath(store, path);
        Map<String, Object> payload = Map.of(
                "SecretId", secretName,
                "RotationLambdaARN", rotationLambdaArn,
                "RotationRules", Map.of("AutomaticallyAfterDays", rotationDays)
        );

        callApi(store, "RotateSecret", payload);
        LOG.info("Configured rotation for {}: lambda={}, days={}",
                secretName, rotationLambdaArn, rotationDays);
    }

    /**
     * Triggers an immediate rotation of a secret.
     */
    public String rotateSecretNow(SecretStore store, String path) {
        String secretName = prefixPath(store, path);
        Map<String, Object> payload = Map.of("SecretId", secretName);

        Map<String, Object> response = callApi(store, "RotateSecret", payload);
        String versionId = (String) response.get("VersionId");
        LOG.info("Triggered rotation for {}: newVersion={}", secretName, versionId);
        return versionId;
    }

    /**
     * Adds or updates tags on a secret.
     */
    public void tagSecret(SecretStore store, String path, Map<String, String> tags) {
        String secretName = prefixPath(store, path);
        List<Map<String, String>> tagList = tags.entrySet().stream()
                .map(e -> Map.of("Key", e.getKey(), "Value", e.getValue()))
                .toList();

        Map<String, Object> payload = Map.of(
                "SecretId", secretName,
                "Tags", tagList
        );

        callApi(store, "TagResource", payload);
        LOG.info("Tagged secret {}: {} tags applied", secretName, tags.size());
    }

    /**
     * Restores a previously deleted secret in the recovery window.
     */
    public void restoreSecret(SecretStore store, String path) {
        String secretName = prefixPath(store, path);
        callApi(store, "RestoreSecret", Map.of("SecretId", secretName));
        LOG.info("Restored deleted secret: {}", secretName);
    }

    /**
     * Updates a secret's resource policy for cross-account access.
     */
    public void putResourcePolicy(SecretStore store, String path, String policyJson) {
        String secretName = prefixPath(store, path);
        callApi(store, "PutResourcePolicy", Map.of(
                "SecretId", secretName,
                "ResourcePolicy", policyJson
        ));
        LOG.info("Applied resource policy to secret: {}", secretName);
    }

    // ── Private Helpers ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> callApi(SecretStore store, String action,
                                         Map<String, Object> payload) {
        String region = store.config().getOrDefault("region", "us-east-1");
        String endpoint = store.config().getOrDefault("endpoint",
                "https://secretsmanager." + region + ".amazonaws.com");
        String accessKey = store.config().get("accessKeyId");
        String secretKey = store.config().get("secretAccessKey");

        try {
            String body = mapper.writeValueAsString(payload);
            URI uri = URI.create(endpoint);

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/x-amz-json-1.1");
            headers.put("X-Amz-Target", TARGET_PREFIX + action);

            // Sign the request
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
                String errorBody = response.body();
                LOG.error("AWS Secrets Manager API error: action={}, status={}, body={}",
                        action, response.statusCode(), errorBody);
                throw new RuntimeException("AWS Secrets Manager " + action +
                        " failed (HTTP " + response.statusCode() + "): " + errorBody);
            }

            if (response.body() == null || response.body().isEmpty()) {
                return Map.of();
            }
            return mapper.readValue(response.body(), Map.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("AWS Secrets Manager API call failed: action={}", action, e);
            throw new RuntimeException("AWS Secrets Manager " + action + " failed", e);
        }
    }

    private String prefixPath(SecretStore store, String path) {
        String prefix = store.config().getOrDefault("prefix", "");
        if (prefix.isEmpty()) return path;
        return prefix.endsWith("/") ? prefix + path : prefix + "/" + path;
    }

    private String removePrefixFromPath(SecretStore store, String name) {
        String prefix = store.config().getOrDefault("prefix", "");
        if (prefix.isEmpty()) return name;
        String fullPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        return name.startsWith(fullPrefix) ? name.substring(fullPrefix.length()) : name;
    }
}
