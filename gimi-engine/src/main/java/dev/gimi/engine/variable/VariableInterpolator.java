package dev.gimi.engine.variable;

import dev.gimi.core.exception.ExecutionException;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code ${...}} variable placeholders in strings.
 *
 * <p>The interpolator searches for patterns matching {@code ${name}} and replaces them
 * with values resolved from the following lookup chain (in order):
 * <ol>
 *   <li>User-defined variables</li>
 *   <li>Secrets</li>
 *   <li>Built-in variables</li>
 * </ol>
 *
 * <p>If a variable cannot be resolved from any source, an {@link ExecutionException} is thrown.
 */
public final class VariableInterpolator {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final Map<String, String> variables;
    private final Map<String, String> secrets;
    private final Map<String, String> builtins;

    /**
     * Creates a new interpolator with the given variable sources.
     *
     * @param variables user-defined pipeline variables
     * @param secrets   resolved secret values keyed by secret name
     * @param builtins  built-in variables (e.g. GIMI_GIT_SHA)
     */
    public VariableInterpolator(Map<String, String> variables,
                                Map<String, String> secrets,
                                Map<String, String> builtins) {
        this.variables = Map.copyOf(variables);
        this.secrets = Map.copyOf(secrets);
        this.builtins = Map.copyOf(builtins);
    }

    /**
     * Interpolates all {@code ${...}} variable references in the given template string.
     *
     * <p>Variables are resolved in order: user variables, then secrets, then built-ins.
     * If a variable name cannot be resolved from any source, an {@link ExecutionException}
     * is thrown.
     *
     * @param template the string potentially containing variable placeholders
     * @return the string with all placeholders resolved
     * @throws ExecutionException if a variable reference cannot be resolved
     */
    public String interpolate(String template) {
        if (template == null) {
            return null;
        }

        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String name = matcher.group(1);
            String value = resolve(name);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Returns a new interpolator that includes additional variables merged on top
     * of the existing ones.
     *
     * <p>The extra variables take precedence over existing variables with the same key.
     * Secrets and built-ins remain unchanged.
     *
     * @param extra additional variables to merge
     * @return a new {@code VariableInterpolator} with the merged variable set
     */
    public VariableInterpolator withAdditionalVars(Map<String, String> extra) {
        Map<String, String> merged = new HashMap<>(this.variables);
        merged.putAll(extra);
        return new VariableInterpolator(merged, this.secrets, this.builtins);
    }

    private String resolve(String name) {
        String value = variables.get(name);
        if (value != null) {
            return value;
        }
        value = secrets.get(name);
        if (value != null) {
            return value;
        }
        value = builtins.get(name);
        if (value != null) {
            return value;
        }
        throw new ExecutionException(
                "Unresolved variable: ${" + name + "}",
                "Define it in pipeline variables, secrets, or pass via --var"
        );
    }
}
