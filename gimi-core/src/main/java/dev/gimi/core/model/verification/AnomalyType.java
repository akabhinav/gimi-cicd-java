package dev.gimi.core.model.verification;

/** Types of anomalies detected during verification. */
public enum AnomalyType {
    METRIC_SPIKE,
    ERROR_RATE,
    LATENCY_DEGRADATION,
    THROUGHPUT_DROP,
    LOG_CLUSTER,
    MEMORY_LEAK,
    CPU_SATURATION,
    CUSTOM
}
