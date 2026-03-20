package dev.gimi.core.model.iac;

import java.util.List;

/**
 * Cost estimate for an IaC plan, breaking down costs per resource.
 */
public record IacCostEstimate(
        double monthlyCostBefore,
        double monthlyCostAfter,
        double monthlyCostDelta,
        String currency,
        List<IacResourceCost> resourceCosts,
        String pricingSource
) {
    public IacCostEstimate {
        currency = currency == null ? "USD" : currency;
        resourceCosts = resourceCosts == null ? List.of() : List.copyOf(resourceCosts);
        pricingSource = pricingSource == null ? "infracost" : pricingSource;
    }

    public record IacResourceCost(
            String resourceType,
            String resourceName,
            double monthlyCost,
            double hourlyCost,
            String component
    ) {}
}
