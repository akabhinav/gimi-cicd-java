package dev.gimi.core.model.secret;

public enum SecretStoreType {
    LOCAL,
    HASHICORP_VAULT,
    AWS_SECRETS_MANAGER,
    AWS_KMS,
    GCP_SECRET_MANAGER,
    AZURE_KEY_VAULT
}
