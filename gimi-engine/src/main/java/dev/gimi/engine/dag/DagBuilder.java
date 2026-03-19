package dev.gimi.engine.dag;

import dev.gimi.core.exception.ValidationException;
import dev.gimi.core.model.Pipeline;
import dev.gimi.core.model.Stage;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a JGraphT {@link DirectedAcyclicGraph} from a {@link Pipeline} definition.
 *
 * <p>Each stage name becomes a vertex, and dependency relationships become directed edges.
 * The {@code parallelWith} field is treated as a shared-dependency grouping: if stage B
 * declares {@code parallelWith = "A"}, then B inherits the same incoming dependencies as A
 * rather than depending on A itself.
 */
public final class DagBuilder {

    /**
     * Builds a directed acyclic graph from the given pipeline.
     *
     * <p>Vertices correspond to stage names. An edge from vertex A to vertex B means
     * that stage A must complete before stage B can start.
     *
     * @param pipeline the pipeline whose stages define the graph
     * @return a {@link DirectedAcyclicGraph} representing the stage dependency graph
     * @throws ValidationException if the dependency graph contains a cycle
     */
    public DirectedAcyclicGraph<String, DefaultEdge> build(Pipeline pipeline) {
        DirectedAcyclicGraph<String, DefaultEdge> dag = new DirectedAcyclicGraph<>(DefaultEdge.class);

        List<Stage> stages = pipeline.stages();

        // Index stages by name for lookup
        Map<String, Stage> stagesByName = new HashMap<>();
        for (Stage stage : stages) {
            stagesByName.put(stage.name(), stage);
        }

        // Add all stage names as vertices
        for (Stage stage : stages) {
            dag.addVertex(stage.name());
        }

        // Add edges based on dependencies and parallelWith
        try {
            for (Stage stage : stages) {
                // Direct dependencies: edge from each dependency to this stage
                for (String dep : stage.dependsOn()) {
                    dag.addEdge(dep, stage.name());
                }

                // parallelWith: treat as shared-dependency grouping.
                // If stage B declares parallelWith = "A", then B gets the same
                // incoming dependencies as A (but no edge between A and B).
                if (stage.parallelWith() != null) {
                    Stage partner = stagesByName.get(stage.parallelWith());
                    if (partner != null) {
                        for (String dep : partner.dependsOn()) {
                            if (!dag.containsEdge(dep, stage.name())) {
                                dag.addEdge(dep, stage.name());
                            }
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw new ValidationException(List.of("Cycle detected in pipeline stage dependencies: " + e.getMessage()));
        }

        return dag;
    }
}
