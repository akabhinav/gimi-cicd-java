package dev.gimi.core.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageTest {

    @Test
    void shouldDefaultStageTypeToStandard() {
        Stage stage = new Stage("build", null, null, null, null, null, null, null, null, null, null);
        assertThat(stage.stageType()).isEqualTo(StageType.STANDARD);
    }

    @Test
    void shouldDefaultOnFailureToAbort() {
        Stage stage = new Stage("build", null, null, null, null, null, null, null, null, null, null);
        assertThat(stage.onFailure()).isEqualTo(FailureAction.ABORT);
    }

    @Test
    void shouldDefaultRunOnToOnSuccess() {
        Stage stage = new Stage("build", null, null, null, null, null, null, null, null, null, null);
        assertThat(stage.runOn()).isEqualTo(RunCondition.ON_SUCCESS);
    }

    @Test
    void shouldDefaultNullDependsOnToEmptyList() {
        Stage stage = new Stage("build", null, null, null, null, null, null, null, null, null, null);
        assertThat(stage.dependsOn()).isEmpty();
    }

    @Test
    void shouldDefaultNullStepsToEmptyList() {
        Stage stage = new Stage("build", null, null, null, null, null, null, null, null, null, null);
        assertThat(stage.steps()).isEmpty();
    }

    @Test
    void shouldPreserveExplicitStageType() {
        Stage stage = new Stage("approval-gate", null, null, null, null, null,
                StageType.APPROVAL, null, null, null, null);
        assertThat(stage.stageType()).isEqualTo(StageType.APPROVAL);
    }

    @Test
    void shouldPreserveExplicitOnFailure() {
        Stage stage = new Stage("deploy", null, null, null, null, null,
                null, FailureAction.SKIP, null, null, null);
        assertThat(stage.onFailure()).isEqualTo(FailureAction.SKIP);
    }

    @Test
    void shouldPreserveExplicitRunOn() {
        Stage stage = new Stage("cleanup", null, null, null, null, null,
                null, null, RunCondition.ALWAYS, null, null);
        assertThat(stage.runOn()).isEqualTo(RunCondition.ALWAYS);
    }

    @Test
    void shouldCreateDefensiveCopyOfDependsOn() {
        List<String> deps = new ArrayList<>();
        deps.add("build");

        Stage stage = new Stage("test", deps, null, null, null, null, null, null, null, null, null);
        deps.add("lint");

        assertThat(stage.dependsOn()).hasSize(1);
        assertThat(stage.dependsOn()).containsExactly("build");
    }

    @Test
    void shouldCreateDefensiveCopyOfSteps() {
        List<Step> steps = new ArrayList<>();
        steps.add(new ShellStep("echo", "echo hi", null, null, null));

        Stage stage = new Stage("build", null, null, null, null, steps, null, null, null, null, null);
        steps.add(new ShellStep("other", "echo other", null, null, null));

        assertThat(stage.steps()).hasSize(1);
    }

    @Test
    void shouldProduceImmutableDependsOn() {
        Stage stage = new Stage("build", List.of("dep"), null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> stage.dependsOn().add("another"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldProduceImmutableSteps() {
        Stage stage = new Stage("build", null, null, null, null,
                List.of(new ShellStep("s", "echo", null, null, null)),
                null, null, null, null, null);
        assertThatThrownBy(() -> stage.steps().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldPreserveParallelWithAndEnvironment() {
        Stage stage = new Stage("deploy", null, "build", "production",
                null, null, StageType.DEPLOY, null, null, null, "30m");

        assertThat(stage.parallelWith()).isEqualTo("build");
        assertThat(stage.environment()).isEqualTo("production");
        assertThat(stage.timeout()).isEqualTo("30m");
        assertThat(stage.stageType()).isEqualTo(StageType.DEPLOY);
    }

    @Test
    void shouldSupportApprovalConfig() {
        ApprovalConfig config = new ApprovalConfig(null, "Please approve");
        Stage stage = new Stage("gate", null, null, null, null, null,
                StageType.APPROVAL, null, null, config, null);

        assertThat(stage.approvers()).isNotNull();
        assertThat(stage.approvers().message()).isEqualTo("Please approve");
    }

    @Test
    void shouldSupportEqualityForIdenticalStages() {
        Stage s1 = new Stage("build", null, null, null, null, null, null, null, null, null, null);
        Stage s2 = new Stage("build", null, null, null, null, null, null, null, null, null, null);
        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }
}
