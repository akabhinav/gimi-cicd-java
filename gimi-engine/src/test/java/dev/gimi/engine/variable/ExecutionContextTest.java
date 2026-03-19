package dev.gimi.engine.variable;

import dev.gimi.core.model.Environment;
import dev.gimi.core.model.Pipeline;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionContextTest {

    @Test
    void shouldCreateContextWithDefensiveCopies() {
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("key", "value");

        ExecutionContext ctx = new ExecutionContext(vars, Map.of(), Map.of(), null, false);
        vars.put("extra", "val");

        assertThat(ctx.variables()).hasSize(1);
        assertThat(ctx.variables()).containsEntry("key", "value");
    }

    @Test
    void withEnvironmentShouldMergeEnvironmentVariables() {
        Environment prodEnv = new Environment(Map.of("URL", "https://prod"), true, null);
        Pipeline pipeline = new Pipeline("1", "test", null, null,
                Map.of("production", prodEnv), null, null);

        ExecutionContext ctx = new ExecutionContext(
                Map.of("version", "1.0"), Map.of(), Map.of(), null, false);

        ExecutionContext withEnv = ctx.withEnvironment(pipeline, "production");

        assertThat(withEnv.variables()).containsEntry("URL", "https://prod");
        assertThat(withEnv.variables()).containsEntry("version", "1.0");
        assertThat(withEnv.environmentName()).isEqualTo("production");
    }

    @Test
    void withEnvironmentShouldOverrideExistingVariables() {
        Environment prodEnv = new Environment(Map.of("URL", "https://prod"), true, null);
        Pipeline pipeline = new Pipeline("1", "test", null, null,
                Map.of("production", prodEnv), null, null);

        ExecutionContext ctx = new ExecutionContext(
                Map.of("URL", "https://local"), Map.of(), Map.of(), null, false);

        ExecutionContext withEnv = ctx.withEnvironment(pipeline, "production");

        assertThat(withEnv.variables()).containsEntry("URL", "https://prod");
    }

    @Test
    void withEnvironmentShouldHandleUnknownEnvironment() {
        Pipeline pipeline = new Pipeline("1", "test", null, null, Map.of(), null, null);

        ExecutionContext ctx = new ExecutionContext(
                Map.of("key", "value"), Map.of(), Map.of(), null, false);

        ExecutionContext withEnv = ctx.withEnvironment(pipeline, "unknown");

        assertThat(withEnv.variables()).containsEntry("key", "value");
        assertThat(withEnv.environmentName()).isEqualTo("unknown");
    }

    @Test
    void withRuntimeVarsShouldMerge() {
        ExecutionContext ctx = new ExecutionContext(
                Map.of("a", "1"), Map.of(), Map.of(), null, false);

        ExecutionContext withVars = ctx.withRuntimeVars(Map.of("b", "2"));

        assertThat(withVars.variables()).containsEntry("a", "1");
        assertThat(withVars.variables()).containsEntry("b", "2");
    }

    @Test
    void withRuntimeVarsShouldOverrideExistingVars() {
        ExecutionContext ctx = new ExecutionContext(
                Map.of("key", "original"), Map.of(), Map.of(), null, false);

        ExecutionContext withVars = ctx.withRuntimeVars(Map.of("key", "overridden"));

        assertThat(withVars.variables()).containsEntry("key", "overridden");
    }

    @Test
    void withRuntimeVarsShouldPreserveOtherFields() {
        ExecutionContext ctx = new ExecutionContext(
                Map.of(), Map.of("secret", "val"), Map.of("builtin", "val"),
                "staging", true);

        ExecutionContext withVars = ctx.withRuntimeVars(Map.of("new", "val"));

        assertThat(withVars.secrets()).containsEntry("secret", "val");
        assertThat(withVars.builtins()).containsEntry("builtin", "val");
        assertThat(withVars.environmentName()).isEqualTo("staging");
        assertThat(withVars.dryRun()).isTrue();
    }

    @Test
    void interpolatorShouldUseAllVariableSources() {
        ExecutionContext ctx = new ExecutionContext(
                Map.of("var", "from-vars"),
                Map.of("secret", "from-secrets"),
                Map.of("builtin", "from-builtins"),
                null, false);

        VariableInterpolator interpolator = ctx.interpolator();

        assertThat(interpolator.interpolate("${var}")).isEqualTo("from-vars");
        assertThat(interpolator.interpolate("${secret}")).isEqualTo("from-secrets");
        assertThat(interpolator.interpolate("${builtin}")).isEqualTo("from-builtins");
    }

    @Test
    void shouldPreserveDryRunFlag() {
        ExecutionContext ctx = new ExecutionContext(Map.of(), Map.of(), Map.of(), null, true);
        assertThat(ctx.dryRun()).isTrue();

        ExecutionContext ctx2 = new ExecutionContext(Map.of(), Map.of(), Map.of(), null, false);
        assertThat(ctx2.dryRun()).isFalse();
    }

    @Test
    void contextShouldBeImmutable() {
        ExecutionContext ctx = new ExecutionContext(
                Map.of("k", "v"), Map.of("s", "v"), Map.of("b", "v"), "env", false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ctx.variables().put("new", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ctx.secrets().put("new", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ctx.builtins().put("new", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
