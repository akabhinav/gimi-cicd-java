package dev.gimi.core.model.analytics;

import java.time.Instant;

/**
 * DORA (DevOps Research and Assessment) metrics for engineering velocity tracking.
 *
 * <p>Goes beyond Harness by tracking per-service, per-team, and per-environment DORA
 * metrics with trend analysis and benchmarking against industry standards.
 *
 * @param pipelineName         the pipeline name
 * @param environment          the target environment
 * @param periodStart          start of the measurement period
 * @param periodEnd            end of the measurement period
 * @param deploymentFrequency  deploys per day in the period
 * @param leadTimeForChanges   median seconds from commit to production
 * @param changeFailureRate    percentage of deployments causing failures (0.0-1.0)
 * @param meanTimeToRecover    median seconds to recover from failure
 * @param performanceLevel     DORA performance tier
 */
public record DoraMetrics(
        String pipelineName,
        String environment,
        Instant periodStart,
        Instant periodEnd,
        double deploymentFrequency,
        long leadTimeForChanges,
        double changeFailureRate,
        long meanTimeToRecover,
        DoraPerformanceLevel performanceLevel
) {}
