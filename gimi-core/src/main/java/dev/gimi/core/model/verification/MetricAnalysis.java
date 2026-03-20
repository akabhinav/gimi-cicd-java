package dev.gimi.core.model.verification;

/**
 * Analysis result for a single verification metric.
 *
 * @param metricName     the metric name
 * @param baselineValue  the baseline (pre-deploy) value
 * @param canaryValue    the canary (post-deploy) value
 * @param deviationPct   percentage deviation from baseline
 * @param risk           computed risk level
 * @param verdict        pass/fail for this metric
 */
public record MetricAnalysis(
        String metricName,
        double baselineValue,
        double canaryValue,
        double deviationPct,
        RiskCategory risk,
        VerificationVerdict verdict
) {}
