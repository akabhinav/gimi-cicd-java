package dev.gimi.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerNodeTest {

    @Test
    void shouldCreateWorkerNodeWithRequiredFields() {
        WorkerNode node = new WorkerNode("w1", "host1", 8080, null, 0, 0,
                null, null, null, null, null);

        assertThat(node.id()).isEqualTo("w1");
        assertThat(node.hostname()).isEqualTo("host1");
    }

    @Test
    void shouldDefaultStatusToOffline() {
        WorkerNode node = new WorkerNode("w1", "host1", 8080, null, 0, 0,
                null, null, null, null, null);

        assertThat(node.status()).isEqualTo(WorkerStatus.OFFLINE);
    }

    @Test
    void shouldDefaultMaxConcurrentJobsToFour() {
        WorkerNode node = new WorkerNode("w1", "host1", 8080, null, 0, 0,
                null, null, null, null, null);

        assertThat(node.maxConcurrentJobs()).isEqualTo(4);
    }

    @Test
    void shouldDefaultNegativeMaxConcurrentJobsToFour() {
        WorkerNode node = new WorkerNode("w1", "host1", 8080, null, -1, 0,
                null, null, null, null, null);

        assertThat(node.maxConcurrentJobs()).isEqualTo(4);
    }

    @Test
    void shouldPreservePositiveMaxConcurrentJobs() {
        WorkerNode node = new WorkerNode("w1", "host1", 8080, null, 8, 0,
                null, null, null, null, null);

        assertThat(node.maxConcurrentJobs()).isEqualTo(8);
    }

    @Test
    void shouldDefaultNullLabelsToEmptySet() {
        WorkerNode node = new WorkerNode("w1", "host1", 8080, null, 4, 0,
                null, null, null, null, null);

        assertThat(node.labels()).isEmpty();
    }

    @Test
    void shouldDefaultNullCapabilitiesToEmptyMap() {
        WorkerNode node = new WorkerNode("w1", "host1", 8080, null, 4, 0,
                null, null, null, null, null);

        assertThat(node.capabilities()).isEmpty();
    }

    @Test
    void shouldCreateDefensiveCopyOfLabels() {
        Set<String> labels = new HashSet<>();
        labels.add("docker");

        WorkerNode node = new WorkerNode("w1", "host1", 8080, WorkerStatus.ONLINE, 4, 0,
                labels, null, null, null, "1.0");

        labels.add("gpu");

        assertThat(node.labels()).hasSize(1);
        assertThat(node.labels()).contains("docker");
    }

    @Test
    void shouldCreateDefensiveCopyOfCapabilities() {
        Map<String, String> caps = new HashMap<>();
        caps.put("os", "linux");

        WorkerNode node = new WorkerNode("w1", "host1", 8080, WorkerStatus.ONLINE, 4, 0,
                null, caps, null, null, "1.0");

        caps.put("arch", "amd64");

        assertThat(node.capabilities()).hasSize(1);
        assertThat(node.capabilities()).containsEntry("os", "linux");
    }

    @Test
    void shouldProduceImmutableLabels() {
        WorkerNode node = new WorkerNode("w1", "host1", 8080, null, 4, 0,
                Set.of("docker"), null, null, null, null);

        assertThatThrownBy(() -> node.labels().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldProduceImmutableCapabilities() {
        WorkerNode node = new WorkerNode("w1", "host1", 8080, null, 4, 0,
                null, Map.of("k", "v"), null, null, null);

        assertThatThrownBy(() -> node.capabilities().put("new", "val"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldThrowWhenIdIsNull() {
        assertThatThrownBy(() -> new WorkerNode(null, "host1", 8080, null, 4, 0,
                null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
    }

    @Test
    void shouldThrowWhenHostnameIsNull() {
        assertThatThrownBy(() -> new WorkerNode("w1", null, 8080, null, 4, 0,
                null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("hostname");
    }

    @Test
    void shouldPreserveAllFields() {
        Instant now = Instant.now();
        WorkerNode node = new WorkerNode("w1", "host1", 9090, WorkerStatus.BUSY, 8, 3,
                Set.of("gpu"), Map.of("os", "linux"), now, now, "2.0");

        assertThat(node.port()).isEqualTo(9090);
        assertThat(node.status()).isEqualTo(WorkerStatus.BUSY);
        assertThat(node.activeJobs()).isEqualTo(3);
        assertThat(node.lastHeartbeat()).isEqualTo(now);
        assertThat(node.registeredAt()).isEqualTo(now);
        assertThat(node.version()).isEqualTo("2.0");
    }

    @Test
    void shouldSupportEquality() {
        WorkerNode n1 = new WorkerNode("w1", "host1", 8080, WorkerStatus.OFFLINE, 4, 0,
                Set.of(), Map.of(), null, null, null);
        WorkerNode n2 = new WorkerNode("w1", "host1", 8080, WorkerStatus.OFFLINE, 4, 0,
                Set.of(), Map.of(), null, null, null);

        assertThat(n1).isEqualTo(n2);
        assertThat(n1.hashCode()).isEqualTo(n2.hashCode());
    }
}
