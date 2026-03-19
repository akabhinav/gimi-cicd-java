package dev.gimi.core.model.governance;

import java.time.Instant;
import java.util.*;

public record PolicyEvaluation(
    String id,
    String policyId,
    String policyName,
    PolicyAction action,
    boolean passed,
    String message,
    List<PolicyViolation> violations,
    Map<String, Object> input,
    Instant evaluatedAt
) {
    public PolicyEvaluation {
        Objects.requireNonNull(id);
        Objects.requireNonNull(policyId);
        violations = violations == null ? List.of() : List.copyOf(violations);
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
    }
}
