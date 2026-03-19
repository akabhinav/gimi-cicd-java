package dev.gimi.core.model.connector;

public enum ConnectorType {
    // Git providers
    GITHUB, GITLAB, BITBUCKET, AZURE_REPOS,
    // Cloud providers
    AWS, GCP, AZURE, KUBERNETES,
    // Artifact registries
    DOCKER_HUB, ECR, GCR, ACR, NEXUS, ARTIFACTORY,
    // Secret managers
    HASHICORP_VAULT, AWS_SECRETS_MANAGER, AWS_KMS, GCP_KMS, AZURE_KEY_VAULT,
    // Notification/Ticketing
    JIRA, SLACK, PAGERDUTY, TEAMS,
    // Monitoring
    PROMETHEUS, DATADOG, NEW_RELIC, SPLUNK,
    // Custom
    HTTP, CUSTOM
}
