package dev.gimi.engine.iac;

import dev.gimi.core.model.iac.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class IacManagementServiceTest {

    private IacManagementService service;

    @BeforeEach
    void setUp() {
        service = new IacManagementService();
    }

    // ── Workspace CRUD ──────────────────────────────────────────────────

    @Test
    void createWorkspace_returnsWorkspace() {
        IacWorkspace ws = createTestWorkspace("ws-1", "prod-infra");
        IacWorkspace result = service.createWorkspace(ws);

        assertThat(result.id()).isEqualTo("ws-1");
        assertThat(result.name()).isEqualTo("prod-infra");
        assertThat(result.provider()).isEqualTo(IacProviderType.TERRAFORM);
    }

    @Test
    void getWorkspace_existingId_returnsWorkspace() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        Optional<IacWorkspace> result = service.getWorkspace("ws-1");

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("prod-infra");
    }

    @Test
    void getWorkspace_unknownId_returnsEmpty() {
        assertThat(service.getWorkspace("unknown")).isEmpty();
    }

    @Test
    void listWorkspaces_filtersByProject() {
        service.createWorkspace(createTestWorkspace("ws-1", "infra-a", "proj-1"));
        service.createWorkspace(createTestWorkspace("ws-2", "infra-b", "proj-2"));
        service.createWorkspace(createTestWorkspace("ws-3", "infra-c", "proj-1"));

        List<IacWorkspace> result = service.listWorkspaces("proj-1");
        assertThat(result).hasSize(2);

        List<IacWorkspace> all = service.listWorkspaces(null);
        assertThat(all).hasSize(3);
    }

    @Test
    void deleteWorkspace_existingId_returnsTrue() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        assertThat(service.deleteWorkspace("ws-1")).isTrue();
        assertThat(service.getWorkspace("ws-1")).isEmpty();
    }

    @Test
    void deleteWorkspace_unknownId_returnsFalse() {
        assertThat(service.deleteWorkspace("unknown")).isFalse();
    }

    // ── Run Execution ───────────────────────────────────────────────────

    @Test
    void executePlan_returnsResourceChanges() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        IacRunRequest request = new IacRunRequest("ws-1", IacAction.PLAN, Map.of(), null, false, "test");

        IacRunResult result = service.executeRun(request);

        assertThat(result.action()).isEqualTo(IacAction.PLAN);
        assertThat(result.status()).isEqualTo(IacRunStatus.AWAITING_APPROVAL);
        assertThat(result.resourceChanges()).isNotEmpty();
        assertThat(result.planSummary()).contains("add");
    }

    @Test
    void executePlan_withAutoApprove_succeeds() {
        IacWorkspace ws = new IacWorkspace("ws-1", "infra", null, null,
                IacProviderType.TERRAFORM, null, "main", ".",
                Map.of(), Map.of(), IacWorkspaceStatus.INACTIVE, null, null,
                true, true, true, true, null, Instant.now(), null);
        service.createWorkspace(ws);

        IacRunRequest request = new IacRunRequest("ws-1", IacAction.PLAN, Map.of(), null, true, "test");
        IacRunResult result = service.executeRun(request);

        assertThat(result.status()).isEqualTo(IacRunStatus.SUCCEEDED);
    }

    @Test
    void executeApply_createsStateSnapshot() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        IacRunRequest request = new IacRunRequest("ws-1", IacAction.APPLY, Map.of(), null, true, "test");

        IacRunResult result = service.executeRun(request);

        assertThat(result.status()).isEqualTo(IacRunStatus.SUCCEEDED);
        assertThat(result.outputs()).containsKey("instance_ip");
        assertThat(service.getStateHistory("ws-1")).hasSize(1);
    }

    @Test
    void executeDestroy_succeeds() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        IacRunRequest request = new IacRunRequest("ws-1", IacAction.DESTROY, Map.of(), null, true, "test");

        IacRunResult result = service.executeRun(request);

        assertThat(result.status()).isEqualTo(IacRunStatus.SUCCEEDED);
        assertThat(result.resourcesDestroyed()).isEqualTo(5);
    }

    @Test
    void executeInit_succeeds() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        IacRunRequest request = new IacRunRequest("ws-1", IacAction.INIT, Map.of(), null, false, "test");

        IacRunResult result = service.executeRun(request);
        assertThat(result.status()).isEqualTo(IacRunStatus.SUCCEEDED);
        assertThat(result.planSummary()).contains("Initialization");
    }

    @Test
    void executeValidate_succeeds() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        IacRunRequest request = new IacRunRequest("ws-1", IacAction.VALIDATE, Map.of(), null, false, "test");

        IacRunResult result = service.executeRun(request);
        assertThat(result.status()).isEqualTo(IacRunStatus.SUCCEEDED);
    }

    @Test
    void executeCostEstimate_returnsCosts() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        IacRunRequest request = new IacRunRequest("ws-1", IacAction.COST_ESTIMATE, Map.of(), null, false, "test");

        IacRunResult result = service.executeRun(request);

        assertThat(result.costEstimate()).isNotNull();
        assertThat(result.costEstimate().monthlyCostDelta()).isGreaterThan(0);
        assertThat(result.costEstimate().resourceCosts()).isNotEmpty();
    }

    @Test
    void executeImport_returnsImportedCount() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        IacRunRequest request = new IacRunRequest("ws-1", IacAction.IMPORT, Map.of(), null, false, "test");

        IacRunResult result = service.executeRun(request);
        assertThat(result.status()).isEqualTo(IacRunStatus.SUCCEEDED);
        assertThat(result.resourcesImported()).isEqualTo(1);
    }

    @Test
    void executeRun_unknownWorkspace_returnsFailed() {
        IacRunRequest request = new IacRunRequest("unknown", IacAction.PLAN, Map.of(), null, false, "test");
        IacRunResult result = service.executeRun(request);
        assertThat(result.status()).isEqualTo(IacRunStatus.FAILED);
    }

    @Test
    void getRunHistory_returnsAllRuns() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        service.executeRun(new IacRunRequest("ws-1", IacAction.INIT, Map.of(), null, false, "test"));
        service.executeRun(new IacRunRequest("ws-1", IacAction.PLAN, Map.of(), null, true, "test"));

        List<IacRunResult> history = service.getRunHistory("ws-1");
        assertThat(history).hasSize(2);
    }

    @Test
    void getLatestRun_returnsLastRun() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        service.executeRun(new IacRunRequest("ws-1", IacAction.INIT, Map.of(), null, false, "test"));
        service.executeRun(new IacRunRequest("ws-1", IacAction.VALIDATE, Map.of(), null, false, "test"));

        Optional<IacRunResult> latest = service.getLatestRun("ws-1");
        assertThat(latest).isPresent();
        assertThat(latest.get().action()).isEqualTo(IacAction.VALIDATE);
    }

    // ── Drift Detection ─────────────────────────────────────────────────

    @Test
    void detectDrift_returnsReport() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        IacDriftReport report = service.detectDrift("ws-1");

        assertThat(report.workspaceId()).isEqualTo("ws-1");
        assertThat(report.detectedAt()).isNotNull();
    }

    @Test
    void detectDrift_unknownWorkspace_returnsEmptyReport() {
        IacDriftReport report = service.detectDrift("unknown");
        assertThat(report.driftDetected()).isFalse();
    }

    @Test
    void executeDriftDetect_viaRunRequest() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        IacRunRequest request = new IacRunRequest("ws-1", IacAction.DRIFT_DETECT, Map.of(), null, false, "test");

        IacRunResult result = service.executeRun(request);
        assertThat(result.status()).isEqualTo(IacRunStatus.SUCCEEDED);
        assertThat(result.driftReport()).isNotNull();
    }

    // ── State Management ────────────────────────────────────────────────

    @Test
    void captureStateSnapshot_createsSnapshot() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        IacStateSnapshot snapshot = service.captureStateSnapshot("ws-1");

        assertThat(snapshot.workspaceId()).isEqualTo("ws-1");
        assertThat(snapshot.version()).isEqualTo("v1");
        assertThat(snapshot.managedResources()).isNotEmpty();
    }

    @Test
    void captureStateSnapshot_incrementsVersion() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        service.captureStateSnapshot("ws-1");
        IacStateSnapshot second = service.captureStateSnapshot("ws-1");

        assertThat(second.version()).isEqualTo("v2");
        assertThat(service.getStateHistory("ws-1")).hasSize(2);
    }

    @Test
    void captureStateSnapshot_unknownWorkspace_throws() {
        assertThatThrownBy(() -> service.captureStateSnapshot("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rollbackState_validSnapshot_returns() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        IacStateSnapshot snapshot = service.captureStateSnapshot("ws-1");

        Optional<IacStateSnapshot> result = service.rollbackState("ws-1", snapshot.id());
        assertThat(result).isPresent();
        assertThat(result.get().version()).isEqualTo("v1");
    }

    @Test
    void rollbackState_unknownSnapshot_returnsEmpty() {
        service.createWorkspace(createTestWorkspace("ws-1", "prod-infra"));
        service.captureStateSnapshot("ws-1");

        assertThat(service.rollbackState("ws-1", "nonexistent")).isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private IacWorkspace createTestWorkspace(String id, String name) {
        return createTestWorkspace(id, name, "proj-default");
    }

    private IacWorkspace createTestWorkspace(String id, String name, String projectId) {
        return new IacWorkspace(
                id, name, "Test workspace", projectId,
                IacProviderType.TERRAFORM, "https://github.com/org/infra.git",
                "main", ".", Map.of("region", "us-east-1"),
                Map.of("bucket", "tf-state"), IacWorkspaceStatus.INACTIVE,
                null, "aws-connector", false,
                true, true, true, "admin",
                Instant.now(), null
        );
    }
}
