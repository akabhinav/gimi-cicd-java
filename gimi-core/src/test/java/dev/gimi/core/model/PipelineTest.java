package dev.gimi.core.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineTest {

    @Test
    void shouldCreatePipelineWithRequiredFields() {
        Pipeline pipeline = new Pipeline("1", "my-pipeline", null, null, null, null, null);

        assertThat(pipeline.version()).isEqualTo("1");
        assertThat(pipeline.name()).isEqualTo("my-pipeline");
    }

    @Test
    void shouldDefaultNullCollectionsToEmptyImmutableCollections() {
        Pipeline pipeline = new Pipeline("1", "test", null, null, null, null, null);

        assertThat(pipeline.variables()).isEmpty();
        assertThat(pipeline.secrets()).isEmpty();
        assertThat(pipeline.environments()).isEmpty();
        assertThat(pipeline.triggers()).isEmpty();
        assertThat(pipeline.stages()).isEmpty();
    }

    @Test
    void shouldThrowWhenVersionIsNull() {
        assertThatThrownBy(() -> new Pipeline(null, "test", null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("version");
    }

    @Test
    void shouldThrowWhenNameIsNull() {
        assertThatThrownBy(() -> new Pipeline("1", null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldCreateDefensiveCopyOfVariables() {
        Map<String, String> vars = new HashMap<>();
        vars.put("key", "value");

        Pipeline pipeline = new Pipeline("1", "test", vars, null, null, null, null);
        vars.put("another", "val");

        assertThat(pipeline.variables()).hasSize(1);
        assertThat(pipeline.variables()).containsEntry("key", "value");
    }

    @Test
    void shouldProduceImmutableCollections() {
        Pipeline pipeline = new Pipeline("1", "test",
                Map.of("k", "v"), Map.of(), Map.of(), List.of(), List.of());

        assertThatThrownBy(() -> pipeline.variables().put("new", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> pipeline.stages().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> pipeline.triggers().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldPreserveProvidedCollections() {
        SecretRef ref = new SecretRef("env", "MY_SECRET");
        Map<String, SecretRef> secrets = Map.of("db_pass", ref);
        Map<String, String> variables = Map.of("env", "prod");

        Pipeline pipeline = new Pipeline("1", "build", variables, secrets, null, null, null);

        assertThat(pipeline.variables()).containsEntry("env", "prod");
        assertThat(pipeline.secrets()).containsEntry("db_pass", ref);
    }

    @Test
    void shouldSupportEqualityForIdenticalRecords() {
        Pipeline p1 = new Pipeline("1", "test", null, null, null, null, null);
        Pipeline p2 = new Pipeline("1", "test", null, null, null, null, null);

        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }
}
