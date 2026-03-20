package dev.gimi.core.model.cost;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Cloud cost tracking for CI/CD pipeline resource consumption.
 *
 * <p>Surpasses Harness CCM by tracking cost per pipeline, per stage, per worker
 * with automated optimization recommendations.
 *
 * @param runId              pipeline run ID
 * @param pipelineName       pipeline name
 * @param environment        target environment
 * @param cloudProvider      cloud provider (AWS, GCP, Azure)
 * @param computeCostUsd     compute cost in USD
 * @param storageCostUsd     storage cost in USD
 * @param networkCostUsd     network egress cost in USD
 * @param totalCostUsd       total cost in USD
 * @param resourceUsage      resource usage metrics
 * @param costPerStage       cost breakdown per stage
 * @param recommendations    cost optimization recommendations
 * @param recordedAt         timestamp
 */
public record CloudCostRecord(
        @JsonProperty("run_id") String runId,
        @JsonProperty("pipeline_name") String pipelineName,
        String environment,
        @JsonProperty("cloud_provider") String cloudProvider,
        @JsonProperty("compute_cost_usd") double computeCostUsd,
        @JsonProperty("storage_cost_usd") double storageCostUsd,
        @JsonProperty("network_cost_usd") double networkCostUsd,
        @JsonProperty("total_cost_usd") double totalCostUsd,
        @JsonProperty("resource_usage") Map<String, Double> resourceUsage,
        @JsonProperty("cost_per_stage") Map<String, Double> costPerStage,
        java.util.List<CostRecommendation> recommendations,
        Instant recordedAt
) {
    public CloudCostRecord {
        resourceUsage = resourceUsage == null ? Map.of() : Map.copyOf(resourceUsage);
        costPerStage = costPerStage == null ? Map.of() : Map.copyOf(costPerStage);
        recommendations = recommendations == null ? java.util.List.of() : java.util.List.copyOf(recommendations);
    }
}
