package dev.gimi.core.model.analytics;

/**
 * DORA performance tiers based on the Accelerate State of DevOps Report.
 */
public enum DoraPerformanceLevel {
    /** Deploy on demand, <1h lead time, 0-15% failure rate, <1h recovery. */
    ELITE,
    /** Daily-weekly deploys, 1d-1w lead time, 16-30% failure rate, <1d recovery. */
    HIGH,
    /** Weekly-monthly deploys, 1w-1m lead time, 16-30% failure rate, 1d-1w recovery. */
    MEDIUM,
    /** Monthly-6mo deploys, 1m-6m lead time, 16-30% failure rate, 1w-1m recovery. */
    LOW
}
