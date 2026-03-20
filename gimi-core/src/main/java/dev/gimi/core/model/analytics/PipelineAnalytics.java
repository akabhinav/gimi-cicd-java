package dev.gimi.core.model.analytics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive pipeline analytics beyond basic DORA metrics.
 *
 * @param pipelineName        the pipeline name
 * @param totalRuns           total runs in period
 * @param successRate         success rate (0.0-1.0)
 * @param avgDurationMs       average run duration in milliseconds
 * @param p50DurationMs       median run duration
 * @param p95DurationMs       95th percentile run duration
 * @param p99DurationMs       99th percentile run duration
 * @param slowestStages       top bottleneck stages by duration
 * @param failuresByStage     failure count per stage
 * @param runsByTriggerType   runs grouped by trigger type
 * @param dailyRunCounts      daily run counts for trend visualization
 * @param periodStart         start of the measurement period
 * @param periodEnd           end of the measurement period
 */
public record PipelineAnalytics(
        String pipelineName,
        long totalRuns,
        double successRate,
        long avgDurationMs,
        long p50DurationMs,
        long p95DurationMs,
        long p99DurationMs,
        List<StageBottleneck> slowestStages,
        Map<String, Integer> failuresByStage,
        Map<String, Integer> runsByTriggerType,
        List<DailyRunCount> dailyRunCounts,
        Instant periodStart,
        Instant periodEnd
) {
    public PipelineAnalytics {
        slowestStages = slowestStages == null ? List.of() : List.copyOf(slowestStages);
        failuresByStage = failuresByStage == null ? Map.of() : Map.copyOf(failuresByStage);
        runsByTriggerType = runsByTriggerType == null ? Map.of() : Map.copyOf(runsByTriggerType);
        dailyRunCounts = dailyRunCounts == null ? List.of() : List.copyOf(dailyRunCounts);
    }
}
