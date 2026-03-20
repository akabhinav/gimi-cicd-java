package dev.gimi.core.model.chaos;

import java.time.Instant;

/**
 * Result of a chaos experiment execution.
 *
 * @param experimentName  the experiment name
 * @param faultType       fault type injected
 * @param verdict         experiment pass/fail
 * @param steadyStateMet  whether steady state was maintained
 * @param resilienceScore resilience score (0.0-100.0)
 * @param startedAt       experiment start
 * @param finishedAt      experiment end
 * @param errorMessage    error details if failed
 */
public record ChaosResult(
        String experimentName,
        ChaosFlaultType faultType,
        ChaosVerdict verdict,
        boolean steadyStateMet,
        double resilienceScore,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage
) {}
