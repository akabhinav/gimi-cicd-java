package dev.gimi.core.model.ff;

import java.util.List;

/**
 * A targeting rule for selective feature flag delivery.
 *
 * @param attribute  the user/context attribute to match (e.g., "userId", "email", "region")
 * @param operator   comparison operator
 * @param values     values to match against
 * @param value      the flag value to serve when matched
 */
public record TargetingRule(
        String attribute,
        TargetingOperator operator,
        List<String> values,
        String value
) {
    public TargetingRule {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
