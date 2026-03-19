package dev.gimi.core.auth.rbac;

import dev.gimi.core.auth.Permission;
import dev.gimi.core.auth.Role;
import dev.gimi.core.model.tenant.ResourceScope;
import java.util.*;

public class ScopedPermissionEvaluator {

    private final List<RoleBinding> bindings;

    public ScopedPermissionEvaluator(List<RoleBinding> bindings) {
        this.bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }

    public boolean hasPermission(Permission permission, ResourceScope targetScope) {
        return bindings.stream()
            .filter(b -> !b.isExpired())
            .filter(b -> b.scope().contains(targetScope))
            .map(b -> Permission.forRole(b.role()))
            .anyMatch(perms -> perms.contains(permission));
    }

    public boolean hasPermission(Permission permission, ResourceScope targetScope,
                                  String resourceType, String resourceId,
                                  Map<String, ResourceGroup> resourceGroups) {
        return bindings.stream()
            .filter(b -> !b.isExpired())
            .filter(b -> b.scope().contains(targetScope))
            .filter(b -> {
                if (b.resourceGroupId() == null) return true;
                ResourceGroup rg = resourceGroups.get(b.resourceGroupId());
                return rg != null && rg.matches(resourceType, resourceId);
            })
            .map(b -> Permission.forRole(b.role()))
            .anyMatch(perms -> perms.contains(permission));
    }

    public Set<Permission> effectivePermissions(ResourceScope targetScope) {
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        bindings.stream()
            .filter(b -> !b.isExpired())
            .filter(b -> b.scope().contains(targetScope))
            .map(b -> Permission.forRole(b.role()))
            .forEach(result::addAll);
        return result;
    }
}
