package dev.gimi.core.model.secret;

public enum SecretType {
    TEXT,
    FILE,
    SSH_KEY,
    TLS_CERTIFICATE,
    DOCKER_CONFIG,
    TOKEN
}
