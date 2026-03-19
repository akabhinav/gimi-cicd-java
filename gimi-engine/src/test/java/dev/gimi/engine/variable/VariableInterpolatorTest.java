package dev.gimi.engine.variable;

import dev.gimi.core.exception.ExecutionException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariableInterpolatorTest {

    @Test
    void shouldInterpolateSimpleVariable() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of("env", "production"), Map.of(), Map.of());

        String result = interpolator.interpolate("deploy to ${env}");

        assertThat(result).isEqualTo("deploy to production");
    }

    @Test
    void shouldInterpolateMultipleVariables() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of("env", "prod", "region", "us-east"), Map.of(), Map.of());

        String result = interpolator.interpolate("deploy to ${env} in ${region}");

        assertThat(result).isEqualTo("deploy to prod in us-east");
    }

    @Test
    void shouldInterpolateSecrets() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of(), Map.of("DB_PASSWORD", "s3cr3t"), Map.of());

        String result = interpolator.interpolate("password=${DB_PASSWORD}");

        assertThat(result).isEqualTo("password=s3cr3t");
    }

    @Test
    void shouldInterpolateBuiltins() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of(), Map.of(), Map.of("GIMI_RUN_ID", "abc123"));

        String result = interpolator.interpolate("run: ${GIMI_RUN_ID}");

        assertThat(result).isEqualTo("run: abc123");
    }

    @Test
    void variablesShouldTakePrecedenceOverSecrets() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of("key", "from-vars"),
                Map.of("key", "from-secrets"),
                Map.of());

        String result = interpolator.interpolate("${key}");

        assertThat(result).isEqualTo("from-vars");
    }

    @Test
    void secretsShouldTakePrecedenceOverBuiltins() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of(),
                Map.of("key", "from-secrets"),
                Map.of("key", "from-builtins"));

        String result = interpolator.interpolate("${key}");

        assertThat(result).isEqualTo("from-secrets");
    }

    @Test
    void shouldThrowForUnresolvedVariable() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of(), Map.of(), Map.of());

        assertThatThrownBy(() -> interpolator.interpolate("${undefined_var}"))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("Unresolved variable")
                .hasMessageContaining("undefined_var");
    }

    @Test
    void shouldReturnStringWithoutPlaceholdersUnchanged() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of(), Map.of(), Map.of());

        String result = interpolator.interpolate("no variables here");

        assertThat(result).isEqualTo("no variables here");
    }

    @Test
    void shouldReturnNullForNullInput() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of(), Map.of(), Map.of());

        String result = interpolator.interpolate(null);

        assertThat(result).isNull();
    }

    @Test
    void shouldHandleEmptyString() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of(), Map.of(), Map.of());

        String result = interpolator.interpolate("");

        assertThat(result).isEmpty();
    }

    @Test
    void withAdditionalVarsShouldMergeVariables() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of("a", "1"), Map.of(), Map.of());

        VariableInterpolator merged = interpolator.withAdditionalVars(Map.of("b", "2"));

        assertThat(merged.interpolate("${a}-${b}")).isEqualTo("1-2");
    }

    @Test
    void withAdditionalVarsShouldOverrideExistingVars() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of("key", "original"), Map.of(), Map.of());

        VariableInterpolator merged = interpolator.withAdditionalVars(Map.of("key", "overridden"));

        assertThat(merged.interpolate("${key}")).isEqualTo("overridden");
    }

    @Test
    void shouldInterpolateVariableAtStartOfString() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of("cmd", "echo"), Map.of(), Map.of());

        assertThat(interpolator.interpolate("${cmd} hello")).isEqualTo("echo hello");
    }

    @Test
    void shouldInterpolateVariableAtEndOfString() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of("name", "world"), Map.of(), Map.of());

        assertThat(interpolator.interpolate("hello ${name}")).isEqualTo("hello world");
    }

    @Test
    void shouldHandleAdjacentVariables() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of("a", "hello", "b", "world"), Map.of(), Map.of());

        assertThat(interpolator.interpolate("${a}${b}")).isEqualTo("helloworld");
    }

    @Test
    void shouldHandleVariableWithSpecialRegexChars() {
        VariableInterpolator interpolator = new VariableInterpolator(
                Map.of("path", "/opt/$HOME/bin"), Map.of(), Map.of());

        String result = interpolator.interpolate("export PATH=${path}");

        assertThat(result).isEqualTo("export PATH=/opt/$HOME/bin");
    }
}
