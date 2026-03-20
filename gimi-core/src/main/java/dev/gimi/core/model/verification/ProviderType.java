package dev.gimi.core.model.verification;

/** Supported verification metric/log provider types. */
public enum ProviderType {
    PROMETHEUS,
    DATADOG,
    NEW_RELIC,
    SPLUNK,
    ELASTIC,
    CLOUDWATCH,
    STACKDRIVER,
    DYNATRACE,
    CUSTOM
}
