package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a stage in a CI/CD pipeline.
 *
 * @param name         the name of the stage
 * @param dependsOn    the list of stage names this stage depends on
 * @param parallelWith the name of the stage to run in parallel with
 * @param environment  the target environment for this stage
 * @param strategy     the deployment strategy for this stage
 * @param steps        the list of steps to execute in this stage
 * @param stageType    the type of the stage
 * @param onFailure    the action to take when the stage fails
 * @param runOn        the condition under which the stage should run
 * @param approvers    the approval configuration for this stage
 * @param timeout      the maximum duration for this stage
 */
public record Stage(
        String name,
        @JsonProperty("depends_on") List<String> dependsOn,
        @JsonProperty("parallel_with") String parallelWith,
        String environment,
        DeployStrategy strategy,
        List<Step> steps,
        @JsonProperty("stage_type") StageType stageType,
        @JsonProperty("on_failure") FailureAction onFailure,
        @JsonProperty("run_on") RunCondition runOn,
        ApprovalConfig approvers,
        String timeout
) {

    /**
     * Creates a {@code Stage} with defensive copies of collections and sensible defaults.
     */
    public Stage {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        steps = steps == null ? List.of() : List.copyOf(steps);
        stageType = stageType == null ? StageType.STANDARD : stageType;
        onFailure = onFailure == null ? FailureAction.ABORT : onFailure;
        runOn = runOn == null ? RunCondition.ON_SUCCESS : runOn;
    }
}
