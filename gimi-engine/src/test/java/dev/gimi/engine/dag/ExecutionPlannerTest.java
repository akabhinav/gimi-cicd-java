package dev.gimi.engine.dag;

import dev.gimi.core.model.Pipeline;
import dev.gimi.core.model.ShellStep;
import dev.gimi.core.model.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionPlannerTest {

    private ExecutionPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new ExecutionPlanner();
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

    private Pipeline pipelineWith(List<Stage> stages) {
        return new Pipeline("1", "test", null, null, null, null, stages);
    }

    @Test
    void shouldPlanSingleStage() {
        Pipeline pipeline = pipelineWith(List.of(simpleStage("build")));

        List<List<String>> batches = planner.plan(pipeline);

        assertThat(batches).hasSize(1);
        assertThat(batches.get(0)).containsExactly("build");
    }

    @Test
    void shouldPlanLinearDependencies() {
        Pipeline pipeline = pipelineWith(List.of(
                simpleStage("build"),
                stageWithDeps("test", List.of("build")),
                stageWithDeps("deploy", List.of("test"))
        ));

        List<List<String>> batches = planner.plan(pipeline);

        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).containsExactly("build");
        assertThat(batches.get(1)).containsExactly("test");
        assertThat(batches.get(2)).containsExactly("deploy");
    }

    @Test
    void shouldPlanParallelStages() {
        Pipeline pipeline = pipelineWith(List.of(
                simpleStage("lint"),
                simpleStage("test"),
                simpleStage("security-scan")
        ));

        List<List<String>> batches = planner.plan(pipeline);

        assertThat(batches).hasSize(1);
        assertThat(batches.get(0)).containsExactlyInAnyOrder("lint", "test", "security-scan");
    }

    @Test
    void shouldPlanDiamondDependency() {
        // start -> left, right -> end
        Pipeline pipeline = pipelineWith(List.of(
                simpleStage("start"),
                stageWithDeps("left", List.of("start")),
                stageWithDeps("right", List.of("start")),
                stageWithDeps("end", List.of("left", "right"))
        ));

        List<List<String>> batches = planner.plan(pipeline);

        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).containsExactly("start");
        assertThat(batches.get(1)).containsExactlyInAnyOrder("left", "right");
        assertThat(batches.get(2)).containsExactly("end");
    }

    @Test
    void shouldPlanMixedDependencies() {
        // a -> b -> d
        // a -> c -> d
        // e (independent)
        Pipeline pipeline = pipelineWith(List.of(
                simpleStage("a"),
                stageWithDeps("b", List.of("a")),
                stageWithDeps("c", List.of("a")),
                stageWithDeps("d", List.of("b", "c")),
                simpleStage("e")
        ));

        List<List<String>> batches = planner.plan(pipeline);

        // Batch 0: a, e (no deps)
        // Batch 1: b, c (depend on a)
        // Batch 2: d (depends on b, c)
        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).contains("a", "e");
        assertThat(batches.get(1)).containsExactlyInAnyOrder("b", "c");
        assertThat(batches.get(2)).containsExactly("d");
    }

    @Test
    void shouldPlanForSpecificStageWithDeps() {
        Pipeline pipeline = pipelineWith(List.of(
                simpleStage("build"),
                stageWithDeps("test", List.of("build")),
                stageWithDeps("deploy", List.of("test"))
        ));

        List<List<String>> batches = planner.planForStage(pipeline, "test", true);

        // Should include build and test
        List<String> allStages = batches.stream().flatMap(List::stream).toList();
        assertThat(allStages).containsExactlyInAnyOrder("build", "test");
        assertThat(allStages).doesNotContain("deploy");
    }

    @Test
    void shouldPlanForSpecificStageWithoutDeps() {
        Pipeline pipeline = pipelineWith(List.of(
                simpleStage("build"),
                stageWithDeps("test", List.of("build")),
                stageWithDeps("deploy", List.of("test"))
        ));

        List<List<String>> batches = planner.planForStage(pipeline, "test", false);

        assertThat(batches).hasSize(1);
        assertThat(batches.get(0)).containsExactly("test");
    }

    @Test
    void shouldPreserveTopologicalOrder() {
        Pipeline pipeline = pipelineWith(List.of(
                simpleStage("a"),
                stageWithDeps("b", List.of("a")),
                stageWithDeps("c", List.of("b"))
        ));

        List<List<String>> batches = planner.plan(pipeline);

        // Flatten and check order
        List<String> flatOrder = batches.stream().flatMap(List::stream).toList();
        assertThat(flatOrder.indexOf("a")).isLessThan(flatOrder.indexOf("b"));
        assertThat(flatOrder.indexOf("b")).isLessThan(flatOrder.indexOf("c"));
    }
}
