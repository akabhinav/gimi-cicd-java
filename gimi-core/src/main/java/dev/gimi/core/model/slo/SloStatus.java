package dev.gimi.core.model.slo;

/**
 * Current status of an SLO including error budget information.
 *
 * @param sloId                 the SLO ID
 * @param currentPercentage     current SLI percentage
 * @param targetPercentage      target SLO percentage
 * @param errorBudgetRemaining  remaining error budget as percentage (0.0-100.0)
 * @param errorBudgetBurnRate   current burn rate multiplier
 * @param isHealthy             whether the SLO is meeting its target
 * @param deploymentGated       whether deployments are gated due to budget exhaustion
 */
public record SloStatus(
        String sloId,
        double currentPercentage,
        double targetPercentage,
        double errorBudgetRemaining,
        double errorBudgetBurnRate,
        boolean isHealthy,
        boolean deploymentGated
) {}
