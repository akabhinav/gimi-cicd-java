package dev.gimi.engine.validator;

import dev.gimi.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineValidatorTest {

    private PipelineValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PipelineValidator();
    }

    private Pipeline pipelineWith(List<Stage> stages) {
        return new Pipeline("1", "test", null, null, null, null, stages);
    }

    private Stage simpleStage(String name) {
        return new Stage(name, null, null, null, null,
                List.of(new ShellStep("step", "echo hi", null, null, null)),
                null, null, null, null, null);
    }

    private Stage stageWithDeps(String name, List<String> deps) {
        return new Stage(name, deps, null, null, null,
                List.of(new ShellStep("step", "echo hi", null, null, null)),
                null, null, null, null, null);
    }

    @Test
    void validPipelineShouldHaveNoErrors() {
        Pipeline pipeline = pipelineWith(List.of(
                simpleStage("build"),
                stageWithDeps("test", List.of("build"))
        ));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).isEmpty();
    }

    @Test
    void shouldReportMissingStages() {
        Pipeline pipeline = pipelineWith(List.of());

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.message().contains("at least one stage"));
    }

    @Test
    void shouldReportDuplicateStageNames() {
        Pipeline pipeline = pipelineWith(List.of(
                simpleStage("build"),
                simpleStage("build")
        ));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).anyMatch(e -> e.message().contains("Duplicate stage name"));
    }

    @Test
    void shouldReportInvalidDependencyReferences() {
        Pipeline pipeline = pipelineWith(List.of(
                simpleStage("build"),
                stageWithDeps("test", List.of("nonexistent"))
        ));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).anyMatch(e -> e.message().contains("unknown stage"));
    }

    @Test
    void shouldReportCircularDependencies() {
        Stage stageA = stageWithDeps("A", List.of("B"));
        Stage stageB = stageWithDeps("B", List.of("A"));

        Pipeline pipeline = pipelineWith(List.of(stageA, stageB));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).anyMatch(e -> e.message().contains("Circular dependency"));
    }

    @Test
    void shouldReportUnsupportedVersion() {
        Pipeline pipeline = new Pipeline("2", "test", null, null, null, null,
                List.of(simpleStage("build")));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).anyMatch(e -> e.message().contains("Unsupported pipeline version"));
    }

    @Test
    void shouldReportApprovalStageWithoutApprovers() {
        Stage approvalStage = new Stage("approval-gate", null, null, null, null,
                List.of(), StageType.APPROVAL, null, null, null, null);

        Pipeline pipeline = pipelineWith(List.of(approvalStage));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).anyMatch(e -> e.message().contains("approvers"));
    }

    @Test
    void approvalStageWithApproversShouldBeValid() {
        ApprovalConfig config = new ApprovalConfig(null, "Please approve");
        Stage approvalStage = new Stage("approval-gate", null, null, null, null,
                List.of(), StageType.APPROVAL, null, null, config, null);

        Pipeline pipeline = pipelineWith(List.of(approvalStage));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).noneMatch(e -> e.message().contains("approvers"));
    }

    @Test
    void shouldReportShellStepWithEmptyRun() {
        Stage stage = new Stage("build", null, null, null, null,
                List.of(new ShellStep("compile", "", null, null, null)),
                null, null, null, null, null);

        Pipeline pipeline = pipelineWith(List.of(stage));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).anyMatch(e -> e.message().contains("non-empty 'run' field"));
    }

    @Test
    void shouldReportShellStepWithNullRun() {
        Stage stage = new Stage("build", null, null, null, null,
                List.of(new ShellStep("compile", null, null, null, null)),
                null, null, null, null, null);

        Pipeline pipeline = pipelineWith(List.of(stage));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).anyMatch(e -> e.message().contains("non-empty 'run' field"));
    }

    @Test
    void shouldReportDockerStepWithoutDockerfileOrImage() {
        Stage stage = new Stage("docker-build", null, null, null, null,
                List.of(new DockerStep("build-img", null, null, null, null)),
                null, null, null, null, null);

        Pipeline pipeline = pipelineWith(List.of(stage));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).anyMatch(e -> e.message().contains("dockerfile") || e.message().contains("image"));
    }

    @Test
    void dockerStepWithImageShouldBeValid() {
        Stage stage = new Stage("docker-build", null, null, null, null,
                List.of(new DockerStep("run-img", null, "nginx:latest", null, null)),
                null, null, null, null, null);

        Pipeline pipeline = pipelineWith(List.of(stage));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).noneMatch(e -> e.message().contains("dockerfile") && e.message().contains("image"));
    }

    @Test
    void shouldReportInvalidStageNameFormat() {
        Stage stage = new Stage("invalid name!", null, null, null, null,
                List.of(new ShellStep("step", "echo hi", null, null, null)),
                null, null, null, null, null);

        Pipeline pipeline = pipelineWith(List.of(stage));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).anyMatch(e -> e.message().contains("not a valid identifier"));
    }

    @Test
    void validStageNamesShouldPass() {
        Pipeline pipeline = pipelineWith(List.of(
                simpleStage("build"),
                simpleStage("test-unit"),
                simpleStage("deploy_prod")
        ));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).noneMatch(e -> e.message().contains("not a valid identifier"));
    }

    @Test
    void validationErrorShouldContainFieldInfo() {
        Pipeline pipeline = pipelineWith(List.of());

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).isNotEmpty();
        ValidationError error = errors.get(0);
        assertThat(error.field()).isNotNull();
        assertThat(error.message()).isNotBlank();
        assertThat(error.hint()).isNotBlank();
    }

    @Test
    void shouldHandleThreeStageCycle() {
        Stage a = stageWithDeps("A", List.of("C"));
        Stage b = stageWithDeps("B", List.of("A"));
        Stage c = stageWithDeps("C", List.of("B"));

        Pipeline pipeline = pipelineWith(List.of(a, b, c));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).anyMatch(e -> e.message().contains("Circular dependency"));
    }

    @Test
    void shouldAcceptDiamondDependencyWithoutCycle() {
        Stage start = simpleStage("start");
        Stage left = stageWithDeps("left", List.of("start"));
        Stage right = stageWithDeps("right", List.of("start"));
        Stage end = stageWithDeps("end", List.of("left", "right"));

        Pipeline pipeline = pipelineWith(List.of(start, left, right, end));

        List<ValidationError> errors = validator.validate(pipeline);

        assertThat(errors).noneMatch(e -> e.message().contains("Circular"));
    }
}
