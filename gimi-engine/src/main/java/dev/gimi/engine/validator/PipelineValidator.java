package dev.gimi.engine.validator;

import dev.gimi.core.model.CanaryStrategy;
import dev.gimi.core.model.DeployStrategy;
import dev.gimi.core.model.DockerStep;
import dev.gimi.core.model.Pipeline;
import dev.gimi.core.model.ShellStep;
import dev.gimi.core.model.Stage;
import dev.gimi.core.model.StageType;
import dev.gimi.core.model.Step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates a {@link Pipeline} definition and collects all errors.
 *
 * <p>This validator performs structural and semantic checks on a parsed pipeline,
 * including version validation, stage name uniqueness, dependency graph integrity
 * (no dangling references or cycles), approval configuration, step completeness,
 * and canary strategy correctness.
 *
 * <p>Usage:
 * <pre>{@code
 * PipelineValidator validator = new PipelineValidator();
 * List<ValidationError> errors = validator.validate(pipeline);
 * if (!errors.isEmpty()) {
 *     // handle errors
 * }
 * }</pre>
 */
public final class PipelineValidator {

    private static final Pattern STAGE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /**
     * Validates the given {@link Pipeline} and returns a list of all validation errors found.
     *
     * <p>An empty list indicates the pipeline is valid. Each {@link ValidationError} in
     * the returned list describes a specific issue, the affected stage and field, and a
     * hint for resolving the problem.
     *
     * @param pipeline the pipeline to validate
     * @return an unmodifiable list of validation errors; empty if the pipeline is valid
     */
    public List<ValidationError> validate(Pipeline pipeline) {
        List<ValidationError> errors = new ArrayList<>();

        checkVersion(pipeline, errors);
        checkStagesNotEmpty(pipeline, errors);

        if (pipeline.stages().isEmpty()) {
            return List.copyOf(errors);
        }

        Set<String> stageNames = collectStageNames(pipeline, errors);
        checkDependsOnReferences(pipeline, stageNames, errors);
        checkCircularDependencies(pipeline, stageNames, errors);
        checkApprovalStages(pipeline, errors);
        checkSteps(pipeline, errors);
        checkCanaryStrategies(pipeline, errors);
        checkStageNameFormat(pipeline, errors);

        return List.copyOf(errors);
    }

    private void checkVersion(Pipeline pipeline, List<ValidationError> errors) {
        if (!"1".equals(pipeline.version())) {
            errors.add(new ValidationError(
                    null,
                    "version",
                    "Unsupported pipeline version '" + pipeline.version() + "'",
                    "Currently only version '1' is supported"
            ));
        }
    }

    private void checkStagesNotEmpty(Pipeline pipeline, List<ValidationError> errors) {
        if (pipeline.stages() == null || pipeline.stages().isEmpty()) {
            errors.add(new ValidationError(
                    null,
                    "stages",
                    "Pipeline must have at least one stage",
                    "Add at least one stage to the 'stages' list"
            ));
        }
    }

    private Set<String> collectStageNames(Pipeline pipeline, List<ValidationError> errors) {
        Set<String> seen = new HashSet<>();
        Set<String> all = new HashSet<>();

        for (Stage stage : pipeline.stages()) {
            all.add(stage.name());
            if (!seen.add(stage.name())) {
                errors.add(new ValidationError(
                        stage.name(),
                        "name",
                        "Duplicate stage name '" + stage.name() + "'",
                        "Each stage must have a unique name"
                ));
            }
        }
        return all;
    }

    private void checkDependsOnReferences(Pipeline pipeline, Set<String> stageNames,
                                           List<ValidationError> errors) {
        for (Stage stage : pipeline.stages()) {
            for (String dep : stage.dependsOn()) {
                if (!stageNames.contains(dep)) {
                    errors.add(new ValidationError(
                            stage.name(),
                            "depends_on",
                            "Stage '" + stage.name() + "' depends on unknown stage '" + dep + "'",
                            "Available stages: " + stageNames.stream().sorted()
                                    .collect(Collectors.joining(", "))
                    ));
                }
            }
        }
    }

    private void checkCircularDependencies(Pipeline pipeline, Set<String> stageNames,
                                            List<ValidationError> errors) {
        // Build adjacency list: stage -> stages it depends on
        Map<String, List<String>> graph = new HashMap<>();
        for (Stage stage : pipeline.stages()) {
            graph.put(stage.name(), stage.dependsOn().stream()
                    .filter(stageNames::contains)
                    .toList());
        }

        // DFS-based cycle detection
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();

        for (String stageName : graph.keySet()) {
            if (!visited.contains(stageName)) {
                if (hasCycle(stageName, graph, visited, inStack)) {
                    errors.add(new ValidationError(
                            null,
                            "depends_on",
                            "Circular dependency detected among stages",
                            "Review the 'depends_on' fields to remove cycles in the dependency graph"
                    ));
                    return; // Report cycle once
                }
            }
        }
    }

    private boolean hasCycle(String node, Map<String, List<String>> graph,
                             Set<String> visited, Set<String> inStack) {
        visited.add(node);
        inStack.add(node);

        List<String> neighbors = graph.getOrDefault(node, List.of());
        for (String neighbor : neighbors) {
            if (inStack.contains(neighbor)) {
                return true;
            }
            if (!visited.contains(neighbor) && hasCycle(neighbor, graph, visited, inStack)) {
                return true;
            }
        }

        inStack.remove(node);
        return false;
    }

    private void checkApprovalStages(Pipeline pipeline, List<ValidationError> errors) {
        for (Stage stage : pipeline.stages()) {
            if (stage.stageType() == StageType.APPROVAL && stage.approvers() == null) {
                errors.add(new ValidationError(
                        stage.name(),
                        "approvers",
                        "Approval stage '" + stage.name() + "' must have approvers configured",
                        "Add an 'approvers' block with notification channels and a message"
                ));
            }
        }
    }

    private void checkSteps(Pipeline pipeline, List<ValidationError> errors) {
        for (Stage stage : pipeline.stages()) {
            for (Step step : stage.steps()) {
                if (step instanceof ShellStep shell) {
                    if (shell.run() == null || shell.run().isBlank()) {
                        errors.add(new ValidationError(
                                stage.name(),
                                "steps[" + shell.name() + "].run",
                                "Shell step '" + shell.name() + "' must have a non-empty 'run' field",
                                "Provide the shell command to execute in the 'run' field"
                        ));
                    }
                } else if (step instanceof DockerStep docker) {
                    boolean hasDockerfile = docker.dockerfile() != null && !docker.dockerfile().isBlank();
                    boolean hasImage = docker.image() != null && !docker.image().isBlank();
                    if (!hasDockerfile && !hasImage) {
                        errors.add(new ValidationError(
                                stage.name(),
                                "steps[" + docker.name() + "].dockerfile|image",
                                "Docker step '" + docker.name()
                                        + "' must have a non-empty 'dockerfile' or 'image' field",
                                "Provide either a 'dockerfile' path to build or an 'image' name to pull"
                        ));
                    }
                }
            }
        }
    }

    private void checkCanaryStrategies(Pipeline pipeline, List<ValidationError> errors) {
        for (Stage stage : pipeline.stages()) {
            DeployStrategy strategy = stage.strategy();
            if (strategy instanceof CanaryStrategy canary) {
                List<Integer> increments = canary.increments();
                if (!increments.isEmpty() && increments.getLast() != 100) {
                    errors.add(new ValidationError(
                            stage.name(),
                            "strategy.increments",
                            "Canary increments must end at 100, but last value is "
                                    + increments.getLast(),
                            "Adjust the increments list so the final value is 100 "
                                    + "(e.g., [10, 25, 50, 100])"
                    ));
                }
            }
        }
    }

    private void checkStageNameFormat(Pipeline pipeline, List<ValidationError> errors) {
        for (Stage stage : pipeline.stages()) {
            if (stage.name() == null || !STAGE_NAME_PATTERN.matcher(stage.name()).matches()) {
                errors.add(new ValidationError(
                        stage.name(),
                        "name",
                        "Stage name '" + stage.name()
                                + "' is not a valid identifier",
                        "Stage names must contain only alphanumeric characters, hyphens, and underscores"
                ));
            }
        }
    }
}
