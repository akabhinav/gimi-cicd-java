package dev.gimi.engine.cost;

import dev.gimi.core.model.cost.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cloud Cost Management service for tracking and optimizing CI/CD resource costs.
 *
 * <p>Tracks cost per pipeline, per stage, per worker with automated
 * right-sizing and spot instance recommendations.
 */
public class CloudCostService {

    private static final Logger LOG = LoggerFactory.getLogger(CloudCostService.class);

    private final Map<String, CloudCostRecord> costRecords = new ConcurrentHashMap<>();
    private final Map<String, List<CloudCostRecord>> costHistory = new ConcurrentHashMap<>();

    /**
     * Records cloud cost for a pipeline run.
     */
    public CloudCostRecord recordCost(CloudCostRecord record) {
        costRecords.put(record.runId(), record);
        costHistory.computeIfAbsent(record.pipelineName(), k -> new ArrayList<>()).add(record);
        LOG.info("Recorded cost for run={}: ${:.4f} total (compute=${:.4f}, storage=${:.4f}, network=${:.4f})",
                record.runId(), record.totalCostUsd(),
                record.computeCostUsd(), record.storageCostUsd(), record.networkCostUsd());
        return record;
    }

    /**
     * Gets cost for a specific pipeline run.
     */
    public Optional<CloudCostRecord> getCost(String runId) {
        return Optional.ofNullable(costRecords.get(runId));
    }

    /**
     * Gets total cost for a pipeline across all runs.
     */
    public double getTotalCost(String pipelineName) {
        List<CloudCostRecord> history = costHistory.getOrDefault(pipelineName, List.of());
        return history.stream().mapToDouble(CloudCostRecord::totalCostUsd).sum();
    }

    /**
     * Generates cost optimization recommendations.
     */
    public List<CostRecommendation> generateRecommendations(String pipelineName) {
        List<CloudCostRecord> history = costHistory.getOrDefault(pipelineName, List.of());
        List<CostRecommendation> recommendations = new ArrayList<>();

        if (history.size() < 5) return recommendations;

        double avgCost = history.stream().mapToDouble(CloudCostRecord::totalCostUsd).average().orElse(0);
        double avgCompute = history.stream().mapToDouble(CloudCostRecord::computeCostUsd).average().orElse(0);

        // Spot instance recommendation
        if (avgCompute > 0.01) {
            recommendations.add(new CostRecommendation(
                    RecommendationType.SPOT_INSTANCE,
                    "Use spot/preemptible instances for CI workers to save up to 70% on compute",
                    avgCompute * 0.7 * 30,
                    EffortLevel.MEDIUM,
                    "ci-worker-instances"
            ));
        }

        // Cache optimization
        recommendations.add(new CostRecommendation(
                RecommendationType.CACHE_OPTIMIZATION,
                "Enable build cache sharing across pipeline runs to reduce compute time",
                avgCost * 0.2 * 30,
                EffortLevel.LOW,
                "build-cache"
        ));

        // Right-sizing
        double avgCpu = history.stream()
                .flatMap(r -> r.resourceUsage().entrySet().stream())
                .filter(e -> e.getKey().contains("cpu_avg"))
                .mapToDouble(Map.Entry::getValue)
                .average().orElse(0);

        if (avgCpu > 0 && avgCpu < 30) {
            recommendations.add(new CostRecommendation(
                    RecommendationType.RIGHT_SIZE,
                    String.format("Average CPU utilization is %.0f%% — consider smaller instance types", avgCpu),
                    avgCompute * 0.4 * 30,
                    EffortLevel.LOW,
                    "worker-instance-type"
            ));
        }

        return recommendations;
    }
}
