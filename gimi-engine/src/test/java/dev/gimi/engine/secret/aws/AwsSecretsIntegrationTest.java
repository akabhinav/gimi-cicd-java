package dev.gimi.engine.secret.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gimi.core.model.secret.*;
import dev.gimi.engine.secret.vault.DefaultVaultSecretManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AWS Secrets Manager integration wiring.
 * Tests the DefaultVaultSecretManager's ability to route operations
 * to the correct AWS providers, and validates store/secret lifecycle.
 *
 * Note: actual AWS API calls are not made — tests validate the wiring,
 * store type validation, and error handling. Integration tests with
 * LocalStack would cover actual AWS API interactions.
 */
class AwsSecretsIntegrationTest {

    private DefaultVaultSecretManager manager;

    @BeforeEach
    void setUp() {
        manager = new DefaultVaultSecretManager(new ObjectMapper());
    }

    // ── Store Management ────────────────────────────────────────────────

    @Test
    void createAwsSecretsManagerStore_succeeds() {
        SecretStore store = awsSmStore("aws-sm-1");
        SecretStore created = manager.createStore(store);

        assertThat(created.id()).isEqualTo("aws-sm-1");
        assertThat(created.type()).isEqualTo(SecretStoreType.AWS_SECRETS_MANAGER);
    }

    @Test
    void createAwsKmsStore_succeeds() {
        SecretStore store = awsKmsStore("aws-kms-1");
        SecretStore created = manager.createStore(store);

        assertThat(created.id()).isEqualTo("aws-kms-1");
        assertThat(created.type()).isEqualTo(SecretStoreType.AWS_KMS);
    }

    @Test
    void listStores_includesAwsStores() {
        manager.createStore(awsSmStore("aws-sm-1"));
        manager.createStore(awsKmsStore("aws-kms-1"));

        List<SecretStore> stores = manager.listStores();
        // local store is created by default + 2 AWS stores
        assertThat(stores).hasSizeGreaterThanOrEqualTo(3);
        assertThat(stores.stream().map(SecretStore::type))
                .contains(SecretStoreType.AWS_SECRETS_MANAGER, SecretStoreType.AWS_KMS, SecretStoreType.LOCAL);
    }

    @Test
    void getStore_existingAwsStore_returnsIt() {
        manager.createStore(awsSmStore("aws-sm-1"));

        Optional<SecretStore> result = manager.getStore("aws-sm-1");
        assertThat(result).isPresent();
        assertThat(result.get().config()).containsKey("region");
    }

    @Test
    void deleteStore_removesAwsStore() {
        manager.createStore(awsSmStore("aws-sm-1"));
        manager.deleteStore("aws-sm-1");

        assertThat(manager.getStore("aws-sm-1")).isEmpty();
    }

    // ── AWS Secrets Manager Error Handling ───────────────────────────────

    @Test
    void describeAwsSecret_wrongStoreType_throws() {
        // local store is not an AWS store
        assertThatThrownBy(() -> manager.describeAwsSecret("local", "test-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not an AWS Secrets Manager store");
    }

    @Test
    void configureAwsRotation_wrongStoreType_throws() {
        assertThatThrownBy(() ->
                manager.configureAwsRotation("local", "test", "arn:aws:lambda:us-east-1:123:function:rotate", 30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rotateAwsSecretNow_wrongStoreType_throws() {
        assertThatThrownBy(() -> manager.rotateAwsSecretNow("local", "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tagAwsSecret_wrongStoreType_throws() {
        assertThatThrownBy(() -> manager.tagAwsSecret("local", "test", Map.of("env", "prod")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoreAwsSecret_wrongStoreType_throws() {
        assertThatThrownBy(() -> manager.restoreAwsSecret("local", "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void describeAwsSecret_unknownStore_throws() {
        assertThatThrownBy(() -> manager.describeAwsSecret("nonexistent", "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── AWS KMS Error Handling ──────────────────────────────────────────

    @Test
    void generateKmsDataKey_wrongStoreType_throws() {
        assertThatThrownBy(() -> manager.generateKmsDataKey("local", "key-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not an AWS KMS store");
    }

    @Test
    void describeKmsKey_wrongStoreType_throws() {
        assertThatThrownBy(() -> manager.describeKmsKey("local", "key-123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getFromAwsKms_noStoredCiphertext_throws() {
        manager.createStore(awsKmsStore("aws-kms-1"));

        assertThatThrownBy(() -> manager.getSecret("aws-kms-1", "nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No encrypted value found");
    }

    // ── Local Store Still Works ─────────────────────────────────────────

    @Test
    void localStore_putAndGet_worksAsExpected() {
        manager.putSecret("local", "my-api-key", "sk-123456");
        String value = manager.getSecret("local", "my-api-key");

        assertThat(value).isEqualTo("sk-123456");
    }

    @Test
    void localStore_deleteSecret_removesValue() {
        manager.putSecret("local", "temp-key", "value");
        manager.deleteSecret("local", "temp-key");

        assertThat(manager.getSecret("local", "temp-key")).isNull();
    }

    @Test
    void localStore_listSecrets_filtersWithPrefix() {
        manager.putSecret("local", "db/password", "pass1");
        manager.putSecret("local", "db/username", "user1");
        manager.putSecret("local", "api/key", "key1");

        List<String> dbSecrets = manager.listSecrets("local", "db/");
        assertThat(dbSecrets).hasSize(2);
        assertThat(dbSecrets).allMatch(s -> s.startsWith("db/"));
    }

    // ── Managed Secrets with AWS Store Refs ──────────────────────────────

    @Test
    void managedSecret_withLocalStore_fullLifecycle() {
        ManagedSecret secret = new ManagedSecret(
                "ms-1", "db-password", "Database root password",
                SecretType.TEXT, "local", "db/root-password",
                Map.of("environment", "production"),
                1, Instant.now(), null, null, "admin"
        );

        // Create
        ManagedSecret created = manager.create(secret, "P@ssw0rd123!");
        assertThat(created.name()).isEqualTo("db-password");
        assertThat(created.version()).isEqualTo(1);

        // Read value
        String value = manager.read("ms-1");
        assertThat(value).isEqualTo("P@ssw0rd123!");

        // Get metadata
        Optional<ManagedSecret> meta = manager.getMeta("ms-1");
        assertThat(meta).isPresent();
        assertThat(meta.get().secretType()).isEqualTo(SecretType.TEXT);
        assertThat(meta.get().metadata()).containsEntry("environment", "production");

        // Update
        ManagedSecret updated = manager.update("ms-1", "NewP@ss456!");
        assertThat(updated.version()).isEqualTo(2);
        assertThat(manager.read("ms-1")).isEqualTo("NewP@ss456!");

        // List
        assertThat(manager.listManaged()).hasSize(1);

        // Delete
        manager.delete("ms-1");
        assertThat(manager.listManaged()).isEmpty();
    }

    @Test
    void managedSecret_sshKey_createsCorrectly() {
        ManagedSecret sshKey = new ManagedSecret(
                "ms-ssh", "deploy-key", "SSH deploy key for GitHub",
                SecretType.SSH_KEY, "local", "keys/deploy",
                Map.of(), 1, Instant.now(), null, null, "ci-bot"
        );

        manager.create(sshKey, "-----BEGIN RSA PRIVATE KEY-----\nMIIEowI...\n-----END RSA PRIVATE KEY-----");

        Optional<ManagedSecret> meta = manager.getMeta("ms-ssh");
        assertThat(meta).isPresent();
        assertThat(meta.get().secretType()).isEqualTo(SecretType.SSH_KEY);
        assertThat(manager.read("ms-ssh")).startsWith("-----BEGIN RSA");
    }

    @Test
    void managedSecret_dockerConfig_createsCorrectly() {
        ManagedSecret dockerCfg = new ManagedSecret(
                "ms-docker", "ecr-auth", "ECR registry auth",
                SecretType.DOCKER_CONFIG, "local", "docker/ecr",
                Map.of("registry", "123456.dkr.ecr.us-east-1.amazonaws.com"),
                1, Instant.now(), null, null, "admin"
        );

        String dockerJson = "{\"auths\":{\"123456.dkr.ecr.us-east-1.amazonaws.com\":{\"auth\":\"base64token\"}}}";
        manager.create(dockerCfg, dockerJson);

        assertThat(manager.read("ms-docker")).contains("ecr.us-east-1");
    }

    // ── Provider Access ─────────────────────────────────────────────────

    @Test
    void getAwsSecretsManagerProvider_returnsNonNull() {
        assertThat(manager.getAwsSecretsManagerProvider()).isNotNull();
    }

    @Test
    void getAwsKmsProvider_returnsNonNull() {
        assertThat(manager.getAwsKmsProvider()).isNotNull();
    }

    // ── Store Config Validation ─────────────────────────────────────────

    @Test
    void awsSmStore_configContainsRequiredKeys() {
        SecretStore store = awsSmStore("test");
        assertThat(store.config()).containsKeys("region");
        assertThat(store.type()).isEqualTo(SecretStoreType.AWS_SECRETS_MANAGER);
    }

    @Test
    void awsKmsStore_configContainsKmsKeyId() {
        SecretStore store = awsKmsStore("test");
        assertThat(store.config()).containsKeys("region", "kmsKeyId");
        assertThat(store.type()).isEqualTo(SecretStoreType.AWS_KMS);
    }

    @Test
    void secretStoreType_allValues_exist() {
        assertThat(SecretStoreType.values()).containsExactly(
                SecretStoreType.LOCAL,
                SecretStoreType.HASHICORP_VAULT,
                SecretStoreType.AWS_SECRETS_MANAGER,
                SecretStoreType.AWS_KMS,
                SecretStoreType.GCP_SECRET_MANAGER,
                SecretStoreType.AZURE_KEY_VAULT
        );
    }

    @Test
    void secretType_allValues_exist() {
        assertThat(SecretType.values()).containsExactly(
                SecretType.TEXT,
                SecretType.FILE,
                SecretType.SSH_KEY,
                SecretType.TLS_CERTIFICATE,
                SecretType.DOCKER_CONFIG,
                SecretType.TOKEN
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private SecretStore awsSmStore(String id) {
        return new SecretStore(id, "AWS Secrets Manager (" + id + ")",
                SecretStoreType.AWS_SECRETS_MANAGER,
                Map.of(
                        "region", "us-east-1",
                        "prefix", "gimi/prod",
                        "kmsKeyId", "alias/gimi-secrets"
                ),
                false, Instant.now());
    }

    private SecretStore awsKmsStore(String id) {
        return new SecretStore(id, "AWS KMS (" + id + ")",
                SecretStoreType.AWS_KMS,
                Map.of(
                        "region", "us-east-1",
                        "kmsKeyId", "arn:aws:kms:us-east-1:123456789:key/mrk-abc123"
                ),
                false, Instant.now());
    }
}
