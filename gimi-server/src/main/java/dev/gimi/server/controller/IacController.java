package dev.gimi.server.controller;

import dev.gimi.core.model.iac.*;
import dev.gimi.engine.iac.IacManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST controller for Infrastructure-as-Code Management.
 * Provides workspace CRUD, plan/apply execution, drift detection,
 * cost estimation, policy enforcement, and state management.
 */
@RestController
@RequestMapping("/api/iac")
public class IacController {

    private final IacManagementService iacService = new IacManagementService();

    // ── Workspace CRUD ──────────────────────────────────────────────────

    @PostMapping("/workspaces")
    public ResponseEntity<IacWorkspace> createWorkspace(@RequestBody Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        IacWorkspace workspace = new IacWorkspace(
                id,
                (String) body.get("name"),
                (String) body.get("description"),
                (String) body.get("projectId"),
                parseEnum(body.get("provider"), IacProviderType.class, IacProviderType.TERRAFORM),
                (String) body.get("repository"),
                (String) body.getOrDefault("branch", "main"),
                (String) body.getOrDefault("rootPath", "."),
                toStringMap(body.get("variables")),
                toStringMap(body.get("backendConfig")),
                IacWorkspaceStatus.INACTIVE,
                null,
                (String) body.get("connectorRef"),
                Boolean.TRUE.equals(body.get("autoApprove")),
                body.getOrDefault("driftDetectionEnabled", true).equals(true),
                body.getOrDefault("costEstimationEnabled", true).equals(true),
                body.getOrDefault("policyEnforcementEnabled", true).equals(true),
                (String) body.get("approverRole"),
                Instant.now(), null
        );
        return ResponseEntity.ok(iacService.createWorkspace(workspace));
    }

    @GetMapping("/workspaces")
    public ResponseEntity<List<IacWorkspace>> listWorkspaces(
            @RequestParam(required = false) String projectId) {
        return ResponseEntity.ok(iacService.listWorkspaces(projectId));
    }

    @GetMapping("/workspaces/{id}")
    public ResponseEntity<IacWorkspace> getWorkspace(@PathVariable String id) {
        return iacService.getWorkspace(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/workspaces/{id}")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable String id) {
        return iacService.deleteWorkspace(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // ── Run Execution ───────────────────────────────────────────────────

    @PostMapping("/workspaces/{workspaceId}/runs")
    public ResponseEntity<IacRunResult> executeRun(
            @PathVariable String workspaceId,
            @RequestBody Map<String, Object> body) {
        IacRunRequest request = new IacRunRequest(
                workspaceId,
                parseEnum(body.get("action"), IacAction.class, IacAction.PLAN),
                toStringMap(body.get("variableOverrides")),
                (String) body.get("commitSha"),
                Boolean.TRUE.equals(body.get("autoApprove")),
                (String) body.getOrDefault("triggeredBy", "api")
        );
        IacRunResult result = iacService.executeRun(request);
        return result.status() == IacRunStatus.FAILED && "Workspace not found: ".concat(workspaceId).equals(result.errorMessage())
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(result);
    }

    @GetMapping("/workspaces/{workspaceId}/runs")
    public ResponseEntity<List<IacRunResult>> getRunHistory(@PathVariable String workspaceId) {
        return ResponseEntity.ok(iacService.getRunHistory(workspaceId));
    }

    @GetMapping("/workspaces/{workspaceId}/runs/latest")
    public ResponseEntity<IacRunResult> getLatestRun(@PathVariable String workspaceId) {
        return iacService.getLatestRun(workspaceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Drift Detection ─────────────────────────────────────────────────

    @PostMapping("/workspaces/{workspaceId}/drift")
    public ResponseEntity<IacDriftReport> detectDrift(@PathVariable String workspaceId) {
        return ResponseEntity.ok(iacService.detectDrift(workspaceId));
    }

    @GetMapping("/workspaces/{workspaceId}/drift")
    public ResponseEntity<IacDriftReport> getLastDriftReport(@PathVariable String workspaceId) {
        return iacService.getLastDriftReport(workspaceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── State Management ────────────────────────────────────────────────

    @PostMapping("/workspaces/{workspaceId}/state/snapshot")
    public ResponseEntity<IacStateSnapshot> captureStateSnapshot(@PathVariable String workspaceId) {
        try {
            return ResponseEntity.ok(iacService.captureStateSnapshot(workspaceId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/workspaces/{workspaceId}/state/history")
    public ResponseEntity<List<IacStateSnapshot>> getStateHistory(@PathVariable String workspaceId) {
        return ResponseEntity.ok(iacService.getStateHistory(workspaceId));
    }

    @PostMapping("/workspaces/{workspaceId}/state/rollback/{snapshotId}")
    public ResponseEntity<IacStateSnapshot> rollbackState(
            @PathVariable String workspaceId,
            @PathVariable String snapshotId) {
        return iacService.rollbackState(workspaceId, snapshotId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, String> toStringMap(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            Map<String, String> result = new HashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
            return result;
        }
        return Map.of();
    }

    private <E extends Enum<E>> E parseEnum(Object value, Class<E> enumClass, E defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Enum.valueOf(enumClass, value.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}
