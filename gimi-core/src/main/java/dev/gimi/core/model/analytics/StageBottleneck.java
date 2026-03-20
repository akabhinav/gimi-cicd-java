package dev.gimi.core.model.analytics;

/**
 * Identifies pipeline stage bottlenecks for optimization.
 *
 * @param stageName       the stage name
 * @param avgDurationMs   average duration in milliseconds
 * @param maxDurationMs   maximum observed duration
 * @param pctOfTotal      percentage of total pipeline time consumed
 */
public record StageBottleneck(
        String stageName,
        long avgDurationMs,
        long maxDurationMs,
        double pctOfTotal
) {}
