package dev.gimi.core.auth.rbac;

import dev.gimi.core.model.tenant.ResourceScope;
import java.util.*;

public record ResourceGroup(
    String id,
    String name,
    ResourceScope scope,
    Set<String> resourceTypes,
    Set<String> resourceIds,
    boolean allResources
) {
    public ResourceGroup {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(scope);
        resourceTypes = resourceTypes == null ? Set.of() : Set.copyOf(resourceTypes);
        resourceIds = resourceIds == null ? Set.of() : Set.copyOf(resourceIds);
    }

    public boolean matches(String resourceType, String resourceId) {
        if (allResources) return true;
        if (!resourceTypes.isEmpty() && !resourceTypes.contains(resourceType)) return false;
        return resourceIds.isEmpty() || resourceIds.contains(resourceId);
    }
}
