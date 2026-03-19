package dev.gimi.engine.variable;

import dev.gimi.core.model.Environment;
import dev.gimi.core.model.Pipeline;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable context for pipeline execution, carrying all variable sources
 * and execution flags.
 *
 * @param variables       user-defined pipeline variables
 * @param secrets         resolved secret values keyed by secret name
 * @param builtins        built-in variables (e.g. GIMI_GIT_SHA)
 * @param environmentName the target environment name, or {@code null} if none
 * @param dryRun          whether the execution is a dry run (no side effects)
 */
public record ExecutionContext(
        Map<String, String> variables,
        Map<String, String> secrets,
        Map<String, String> builtins,
        String environmentName,
        boolean dryRun
) {

    /**
     * Creates an {@code ExecutionContext} with defensive copies of all maps.
     */
    public ExecutionContext {
        variables = Map.copyOf(variables);
        secrets = Map.copyOf(secrets);
        builtins = Map.copyOf(builtins);
    }

    /**
     * Returns a new context with environment-specific variables merged in.
     *
     * <p>Environment variables are looked up by name from the pipeline's environment
     * definitions. Environment variables take precedence over existing pipeline variables
     * with the same key.
     *
     * @param pipeline the pipeline containing environment definitions
     * @param envName  the name of the target environment
     * @return a new {@code ExecutionContext} with merged environment variables
     */
    public ExecutionContext withEnvironment(Pipeline pipeline, String envName) {
        Environment env = pipeline.environments().get(envName);
        if (env == null) {
            return new ExecutionContext(variables, secrets, builtins, envName, dryRun);
        }

        Map<String, String> merged = new HashMap<>(variables);
        merged.putAll(env.variables());
        return new ExecutionContext(merged, secrets, builtins, envName, dryRun);
    }

    /**
     * Returns a new context with additional runtime variables merged in.
     *
     * <p>Runtime variables (e.g. from {@code --var} flags) take precedence over
     * existing variables with the same key.
     *
     * @param vars the runtime variable overrides
     * @return a new {@code ExecutionContext} with the merged variables
     */
    public ExecutionContext withRuntimeVars(Map<String, String> vars) {
        Map<String, String> merged = new HashMap<>(variables);
        merged.putAll(vars);
        return new ExecutionContext(merged, secrets, builtins, environmentName, dryRun);
    }

    /**
     * Creates a {@link VariableInterpolator} from this context's variable sources.
     *
     * @return a new interpolator backed by this context's variables, secrets, and built-ins
     */
    public VariableInterpolator interpolator() {
        return new VariableInterpolator(variables, secrets, builtins);
    }
}
