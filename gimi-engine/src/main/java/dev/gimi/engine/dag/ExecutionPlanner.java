package dev.gimi.engine.dag;

import dev.gimi.core.exception.ExecutionException;
import dev.gimi.core.model.Pipeline;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts a {@link Pipeline} into ordered execution batches.
 *
 * <p>Stages within the same batch have no mutual dependencies and can be executed in parallel.
 * Batches are ordered so that all dependencies of stages in batch N are contained in batches
 * 0 through N-1.
 */
public final class ExecutionPlanner {

    private final DagBuilder dagBuilder;

    /**
     * Creates an execution planner with a default {@link DagBuilder}.
     */
    public ExecutionPlanner() {
        this(new DagBuilder());
    }

    /**
     * Creates an execution planner with the specified {@link DagBuilder}.
     *
     * @param dagBuilder the DAG builder to use for constructing the dependency graph
     */
    public ExecutionPlanner(DagBuilder dagBuilder) {
        this.dagBuilder = dagBuilder;
    }

    /**
     * Plans the execution of all stages in the pipeline as ordered batches.
     *
     * <p>Stages are grouped into batches where each batch contains stages whose dependencies
     * have all been satisfied by previous batches. Stages within the same batch can be executed
     * in parallel.
     *
     * @param pipeline the pipeline to plan
     * @return an ordered list of batches, where each batch is a list of stage names
     */
    public List<List<String>> plan(Pipeline pipeline) {
        DirectedAcyclicGraph<String, DefaultEdge> dag = dagBuilder.build(pipeline);

        // Assign each stage to a batch based on its longest path from a root
        Map<String, Integer> batchIndex = new HashMap<>();
        TopologicalOrderIterator<String, DefaultEdge> iterator = new TopologicalOrderIterator<>(dag);

        while (iterator.hasNext()) {
            String stage = iterator.next();
            int maxPredecessorBatch = -1;
            for (DefaultEdge edge : dag.incomingEdgesOf(stage)) {
                String predecessor = dag.getEdgeSource(edge);
                maxPredecessorBatch = Math.max(maxPredecessorBatch, batchIndex.getOrDefault(predecessor, 0));
            }
            batchIndex.put(stage, maxPredecessorBatch + 1);
        }

        // Group stages by batch index
        int maxBatch = batchIndex.values().stream().mapToInt(Integer::intValue).max().orElse(-1);
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i <= maxBatch; i++) {
            batches.add(new ArrayList<>());
        }
        for (Map.Entry<String, Integer> entry : batchIndex.entrySet()) {
            batches.get(entry.getValue()).add(entry.getKey());
        }

        return batches;
    }

    /**
     * Plans execution for a single target stage, optionally including its transitive dependencies.
     *
     * <p>When {@code includeDeps} is {@code true}, returns ordered batches containing all
     * transitive dependencies followed by the target stage itself. When {@code false}, returns
     * a single batch containing only the target stage.
     *
     * @param pipeline    the pipeline containing the stage
     * @param stageName   the name of the target stage
     * @param includeDeps whether to include transitive dependencies in the plan
     * @return an ordered list of batches for executing the target stage
     * @throws ExecutionException if the specified stage name is not found in the pipeline
     */
    public List<List<String>> planForStage(Pipeline pipeline, String stageName, boolean includeDeps) {
        DirectedAcyclicGraph<String, DefaultEdge> dag = dagBuilder.build(pipeline);

        if (!dag.containsVertex(stageName)) {
            throw new ExecutionException(
                    "Stage '" + stageName + "' not found in pipeline '" + pipeline.name() + "'",
                    "Check the stage name and ensure it is defined in the pipeline"
            );
        }

        if (!includeDeps) {
            return List.of(List.of(stageName));
        }

        // Collect all transitive dependencies via BFS/DFS on ancestors
        Set<String> required = new HashSet<>();
        collectAncestors(dag, stageName, required);
        required.add(stageName);

        // Filter the full plan to only include required stages
        List<List<String>> fullPlan = plan(pipeline);
        List<List<String>> filteredPlan = new ArrayList<>();
        for (List<String> batch : fullPlan) {
            List<String> filteredBatch = new ArrayList<>();
            for (String stage : batch) {
                if (required.contains(stage)) {
                    filteredBatch.add(stage);
                }
            }
            if (!filteredBatch.isEmpty()) {
                filteredPlan.add(filteredBatch);
            }
        }

        return filteredPlan;
    }

    private void collectAncestors(DirectedAcyclicGraph<String, DefaultEdge> dag, String vertex, Set<String> ancestors) {
        for (DefaultEdge edge : dag.incomingEdgesOf(vertex)) {
            String predecessor = dag.getEdgeSource(edge);
            if (ancestors.add(predecessor)) {
                collectAncestors(dag, predecessor, ancestors);
            }
        }
    }
}
