package dev.gimi.engine.analytics;

import dev.gimi.core.execution.ExecutionRecord;
import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.model.analytics.*;
import dev.gimi.engine.history.ExecutionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DORA Metrics and Pipeline Analytics engine.
 *
 * <p>Computes all four DORA metrics with automatic performance tier classification.
 * Also provides deep pipeline analytics: bottleneck detection, trend analysis,
 * and per-stage failure breakdowns.
 */
public class DoraMetricsService {

    private static final Logger LOG = LoggerFactory.getLogger(DoraMetricsService.class);

    private final ExecutionStore executionStore;

    public DoraMetricsService(ExecutionStore executionStore) {
        this.executionStore = executionStore;
    }

    /**
     * Computes DORA metrics for a pipeline over a given period.
     */
    public DoraMetrics computeDoraMetrics(String pipelineName, String environment,
                                           Instant periodStart, Instant periodEnd) {
        List<ExecutionRecord> records = executionStore.getRecent(10000).stream()
                .filter(r -> r.pipelineName().equals(pipelineName))
                .filter(r -> r.startedAt() != null && !r.startedAt().isBefore(periodStart)
                        && !r.startedAt().isAfter(periodEnd))
                .toList();

        long days = Math.max(1, Duration.between(periodStart, periodEnd).toDays());
        double deploymentFrequency = (double) records.size() / days;

        // Lead time: median duration from start to finish
        long leadTime = computeMedian(records.stream()
                .filter(r -> r.startedAt() != null && r.finishedAt() != null)
                .map(r -> Duration.between(r.startedAt(), r.finishedAt()).toSeconds())
                .toList());

        // Change failure rate
        long totalDeploys = records.size();
        long failedDeploys = records.stream()
                .filter(r -> r.status() == ExecutionStatus.FAILED)
                .count();
        double changeFailureRate = totalDeploys > 0 ? (double) failedDeploys / totalDeploys : 0;

        // Mean time to recover: median time from failure to next success
        long mttr = computeMttr(records);

        DoraPerformanceLevel level = classifyPerformance(
                deploymentFrequency, leadTime, changeFailureRate, mttr);

        LOG.info("DORA metrics for {}: freq={:.2f}/day, lead={}s, cfr={:.1f}%, mttr={}s, level={}",
                pipelineName, deploymentFrequency, leadTime,
                changeFailureRate * 100, mttr, level);

        return new DoraMetrics(pipelineName, environment, periodStart, periodEnd,
                deploymentFrequency, leadTime, changeFailureRate, mttr, level);
    }

    /**
     * Computes comprehensive pipeline analytics.
     */
    public PipelineAnalytics computeAnalytics(String pipelineName,
                                               Instant periodStart, Instant periodEnd) {
        List<ExecutionRecord> records = executionStore.getRecent(10000).stream()
                .filter(r -> r.pipelineName().equals(pipelineName))
                .filter(r -> r.startedAt() != null && !r.startedAt().isBefore(periodStart)
                        && !r.startedAt().isAfter(periodEnd))
                .toList();

        long totalRuns = records.size();
        long succeeded = records.stream().filter(r -> r.status() == ExecutionStatus.PASSED).count();
        double successRate = totalRuns > 0 ? (double) succeeded / totalRuns : 0;

        List<Long> durations = records.stream()
                .filter(r -> r.startedAt() != null && r.finishedAt() != null)
                .map(r -> Duration.between(r.startedAt(), r.finishedAt()).toMillis())
                .sorted()
                .toList();

        long avgDuration = durations.isEmpty() ? 0 : (long) durations.stream().mapToLong(l -> l).average().orElse(0);
        long p50 = percentile(durations, 50);
        long p95 = percentile(durations, 95);
        long p99 = percentile(durations, 99);

        // Stage bottleneck analysis
        Map<String, List<Long>> stageDurations = new HashMap<>();
        Map<String, Integer> stageFailures = new HashMap<>();
        for (ExecutionRecord record : records) {
            if (record.stages() != null) {
                for (var sr : record.stages()) {
                    if (sr.startedAt() != null && sr.finishedAt() != null) {
                        stageDurations.computeIfAbsent(sr.name(), k -> new ArrayList<>())
                                .add(Duration.between(sr.startedAt(), sr.finishedAt()).toMillis());
                    }
                    if (sr.status() == ExecutionStatus.FAILED) {
                        stageFailures.merge(sr.name(), 1, Integer::sum);
                    }
                }
            }
        }

        List<StageBottleneck> bottlenecks = stageDurations.entrySet().stream()
                .map(e -> {
                    long avg = (long) e.getValue().stream().mapToLong(l -> l).average().orElse(0);
                    long max = e.getValue().stream().mapToLong(l -> l).max().orElse(0);
                    double pct = avgDuration > 0 ? (double) avg / avgDuration * 100 : 0;
                    return new StageBottleneck(e.getKey(), avg, max, pct);
                })
                .sorted(Comparator.comparingLong(StageBottleneck::avgDurationMs).reversed())
                .limit(10)
                .toList();

        // Daily run counts
        Map<LocalDate, int[]> dailyCounts = new TreeMap<>();
        for (ExecutionRecord r : records) {
            if (r.startedAt() != null) {
                LocalDate date = r.startedAt().atZone(ZoneOffset.UTC).toLocalDate();
                int[] counts = dailyCounts.computeIfAbsent(date, k -> new int[4]);
                counts[0]++;
                if (r.status() == ExecutionStatus.PASSED) counts[1]++;
                else if (r.status() == ExecutionStatus.FAILED) counts[2]++;
                else if (r.status() == ExecutionStatus.CANCELLED) counts[3]++;
            }
        }
        List<DailyRunCount> dailyRunCounts = dailyCounts.entrySet().stream()
                .map(e -> new DailyRunCount(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2], e.getValue()[3]))
                .toList();

        return new PipelineAnalytics(pipelineName, totalRuns, successRate,
                avgDuration, p50, p95, p99, bottlenecks, stageFailures,
                Map.of(), dailyRunCounts, periodStart, periodEnd);
    }

    private DoraPerformanceLevel classifyPerformance(double freq, long leadTimeSecs,
                                                      double cfr, long mttrSecs) {
        if (freq >= 1 && leadTimeSecs < 3600 && cfr <= 0.15 && mttrSecs < 3600) {
            return DoraPerformanceLevel.ELITE;
        }
        if (freq >= 0.14 && leadTimeSecs < 604800 && cfr <= 0.30 && mttrSecs < 86400) {
            return DoraPerformanceLevel.HIGH;
        }
        if (freq >= 0.033 && leadTimeSecs < 2592000 && mttrSecs < 604800) {
            return DoraPerformanceLevel.MEDIUM;
        }
        return DoraPerformanceLevel.LOW;
    }

    private long computeMedian(List<Long> values) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    private long computeMttr(List<ExecutionRecord> records) {
        List<Long> recoveryTimes = new ArrayList<>();
        Instant lastFailure = null;
        for (ExecutionRecord r : records.stream()
                .sorted(Comparator.comparing(ExecutionRecord::startedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList()) {
            if (r.status() == ExecutionStatus.FAILED && r.finishedAt() != null) {
                lastFailure = r.finishedAt();
            } else if (r.status() == ExecutionStatus.PASSED && lastFailure != null && r.startedAt() != null) {
                recoveryTimes.add(Duration.between(lastFailure, r.startedAt()).toSeconds());
                lastFailure = null;
            }
        }
        return computeMedian(recoveryTimes);
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = Math.min((int) Math.ceil(p / 100.0 * sorted.size()) - 1, sorted.size() - 1);
        return sorted.get(Math.max(0, idx));
    }
}
