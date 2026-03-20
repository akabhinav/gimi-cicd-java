package dev.gimi.core.model.iac;

import java.util.Map;

/**
 * Represents a single resource change in an IaC plan or apply.
 */
public record IacResourceChange(
        String address,
        String resourceType,
        String resourceName,
        String provider,
        ChangeAction action,
        Map<String, AttributeChange> attributeChanges
) {
    public IacResourceChange {
        attributeChanges = attributeChanges == null ? Map.of() : Map.copyOf(attributeChanges);
    }

    public enum ChangeAction {
        CREATE,
        UPDATE,
        DELETE,
        RECREATE,
        READ,
        NO_OP,
        IMPORT
    }

    public record AttributeChange(
            String attribute,
            String oldValue,
            String newValue,
            boolean sensitive,
            boolean forcesReplacement
    ) {}
}
