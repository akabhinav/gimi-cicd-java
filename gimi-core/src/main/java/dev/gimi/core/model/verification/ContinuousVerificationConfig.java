package dev.gimi.core.model.verification;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Continuous Verification configuration for ML-based deployment health monitoring.
 *
 * <p>Surpasses Harness CV by combining multi-signal anomaly detection with
 * automated rollback decisions within configurable time windows.
 *
 * @param enabled              whether CV is active
 * @param analysisType         type of analysis (CANARY, ROLLING, BLUE_GREEN)
 * @param duration             analysis duration (e.g., "15m", "1h")
 * @param providers            metric/log provider configurations
 * @param sensitivityLevel     detection sensitivity (LOW, MEDIUM, HIGH)
 * @param failOnAnomaly        whether to fail the deployment on detected anomaly
 * @param autoRollback         whether to trigger automatic rollback on failure
 * @param rollbackTimeoutSecs  max seconds to wait for rollback to complete
 * @param baselineWindow       baseline comparison window (e.g., "24h")
 */
public record ContinuousVerificationConfig(
        boolean enabled,
        @JsonProperty("analysis_type") AnalysisType analysisType,
        String duration,
        List<VerificationProvider> providers,
        @JsonProperty("sensitivity_level") SensitivityLevel sensitivityLevel,
        @JsonProperty("fail_on_anomaly") boolean failOnAnomaly,
        @JsonProperty("auto_rollback") boolean autoRollback,
        @JsonProperty("rollback_timeout_secs") int rollbackTimeoutSecs,
        @JsonProperty("baseline_window") String baselineWindow
) {
    public ContinuousVerificationConfig {
        providers = providers == null ? List.of() : List.copyOf(providers);
        sensitivityLevel = sensitivityLevel == null ? SensitivityLevel.MEDIUM : sensitivityLevel;
        analysisType = analysisType == null ? AnalysisType.CANARY : analysisType;
        if (rollbackTimeoutSecs <= 0) rollbackTimeoutSecs = 300;
    }
}
