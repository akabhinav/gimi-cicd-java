package dev.gimi.core.model.security;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Security Testing Orchestration (STO) configuration.
 *
 * <p>Integrates SAST, DAST, SCA, container scanning, and secret detection into
 * CI/CD pipelines with intelligent deduplication and prioritization.
 *
 * @param scanType          the type of security scan
 * @param tool              the scanning tool to use
 * @param target            the scan target (repo path, URL, image name)
 * @param severityThreshold minimum severity to fail the pipeline
 * @param failOnViolation   whether to fail the pipeline on findings
 * @param excludePaths      paths to exclude from scanning
 * @param policyRefs        policy references for compliance evaluation
 * @param connectorRef      connector for tool authentication
 * @param settings          tool-specific configuration settings
 */
public record SecurityScanConfig(
        @JsonProperty("scan_type") SecurityScanType scanType,
        String tool,
        String target,
        @JsonProperty("severity_threshold") SecuritySeverity severityThreshold,
        @JsonProperty("fail_on_violation") boolean failOnViolation,
        @JsonProperty("exclude_paths") List<String> excludePaths,
        @JsonProperty("policy_refs") List<String> policyRefs,
        @JsonProperty("connector_ref") String connectorRef,
        Map<String, String> settings
) {
    public SecurityScanConfig {
        excludePaths = excludePaths == null ? List.of() : List.copyOf(excludePaths);
        policyRefs = policyRefs == null ? List.of() : List.copyOf(policyRefs);
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        severityThreshold = severityThreshold == null ? SecuritySeverity.HIGH : severityThreshold;
    }
}
