package dev.gimi.server.controller;

import dev.gimi.core.auth.Permission;
import dev.gimi.core.auth.Role;
import dev.gimi.core.auth.rbac.*;
import dev.gimi.core.model.tenant.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/rbac")
public class RbacController {

    private final Map<String, RoleBinding> roleBindings = new ConcurrentHashMap<>();
    private final Map<String, ResourceGroup> resourceGroups = new ConcurrentHashMap<>();

    @PostMapping("/role-bindings")
    public ResponseEntity<RoleBinding> createRoleBinding(@RequestBody Map<String, String> body) {
        String id = UUID.randomUUID().toString();
        ResourceScope scope = ResourceScope.project(
            body.get("accountId"),
            body.get("organizationId"),
            body.get("projectId")
        );
        RoleBinding binding = new RoleBinding(id, body.get("userId"),
            Role.valueOf(body.get("role").toUpperCase()), scope,
            body.get("resourceGroupId"), Instant.now(), null);
        roleBindings.put(id, binding);
        return ResponseEntity.ok(binding);
    }

    @GetMapping("/role-bindings")
    public ResponseEntity<List<RoleBinding>> listRoleBindings(
            @RequestParam(required = false) String userId) {
        var bindings = roleBindings.values().stream()
            .filter(b -> userId == null || b.userId().equals(userId))
            .toList();
        return ResponseEntity.ok(bindings);
    }

    @DeleteMapping("/role-bindings/{id}")
    public ResponseEntity<Void> deleteRoleBinding(@PathVariable String id) {
        roleBindings.remove(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resource-groups")
    public ResponseEntity<ResourceGroup> createResourceGroup(@RequestBody Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        ResourceScope scope = ResourceScope.account((String) body.get("accountId"));
        @SuppressWarnings("unchecked")
        Set<String> types = body.containsKey("resourceTypes") ?
            new HashSet<>((List<String>) body.get("resourceTypes")) : Set.of();
        ResourceGroup rg = new ResourceGroup(id, (String) body.get("name"), scope, types, Set.of(),
            Boolean.TRUE.equals(body.get("allResources")));
        resourceGroups.put(id, rg);
        return ResponseEntity.ok(rg);
    }

    @GetMapping("/resource-groups")
    public ResponseEntity<Collection<ResourceGroup>> listResourceGroups() {
        return ResponseEntity.ok(resourceGroups.values());
    }

    @GetMapping("/permissions/effective")
    public ResponseEntity<Map<String, Object>> getEffectivePermissions(
            @RequestParam String userId,
            @RequestParam String accountId,
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false) String projectId) {
        ResourceScope scope = ResourceScope.project(accountId, organizationId, projectId);
        List<RoleBinding> userBindings = roleBindings.values().stream()
            .filter(b -> b.userId().equals(userId)).toList();
        ScopedPermissionEvaluator evaluator = new ScopedPermissionEvaluator(userBindings);
        Set<Permission> perms = evaluator.effectivePermissions(scope);
        return ResponseEntity.ok(Map.of("userId", userId, "scope", scope, "permissions", perms));
    }
}
