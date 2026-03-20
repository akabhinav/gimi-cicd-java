package dev.gimi.core.model.cost;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A cost optimization recommendation for CI/CD resources.
 *
 * @param type              recommendation type
 * @param description       human-readable recommendation
 * @param estimatedSavings  estimated monthly savings in USD
 * @param effort            implementation effort level
 * @param resource          the specific resource to optimize
 */
public record CostRecommendation(
        RecommendationType type,
        String description,
        @JsonProperty("estimated_savings_usd") double estimatedSavings,
        EffortLevel effort,
        String resource
) {}
