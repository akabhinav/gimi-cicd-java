package dev.gimi.core.model.iac;

/**
 * A policy violation detected during IaC plan evaluation.
 * Integrates with OPA or Sentinel-style policies.
 */
public record IacPolicyViolation(
        String policyId,
        String policyName,
        String description,
        IacPolicySeverity severity,
        String resourceAddress,
        String resourceType,
        String remediation,
        boolean blocking
) {
    public enum IacPolicySeverity {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
