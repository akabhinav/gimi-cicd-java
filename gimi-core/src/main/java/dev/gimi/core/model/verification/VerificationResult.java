package dev.gimi.core.model.verification;

import java.time.Instant;
import java.util.List;

/**
 * Result of a Continuous Verification analysis.
 *
 * @param runId           the pipeline run ID
 * @param stageName       the stage being verified
 * @param overallRisk     computed overall risk score (0.0 = safe, 1.0 = critical)
 * @param verdict         the verification verdict
 * @param metricResults   per-metric analysis results
 * @param anomalies       detected anomalies
 * @param rollbackTriggered  whether an auto-rollback was triggered
 * @param analysisStarted    when analysis began
 * @param analysisFinished   when analysis completed
 */
public record VerificationResult(
        String runId,
        String stageName,
        double overallRisk,
        VerificationVerdict verdict,
        List<MetricAnalysis> metricResults,
        List<Anomaly> anomalies,
        boolean rollbackTriggered,
        Instant analysisStarted,
        Instant analysisFinished
) {
    public VerificationResult {
        metricResults = metricResults == null ? List.of() : List.copyOf(metricResults);
        anomalies = anomalies == null ? List.of() : List.copyOf(anomalies);
    }
}
