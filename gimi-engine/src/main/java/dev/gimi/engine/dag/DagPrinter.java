package dev.gimi.engine.dag;

import dev.gimi.core.model.Pipeline;

import java.util.List;

/**
 * Formats a pipeline's execution plan as human-readable ASCII text.
 */
public final class DagPrinter {

    private final ExecutionPlanner planner;

    /**
     * Creates a DAG printer with a default {@link ExecutionPlanner}.
     */
    public DagPrinter() {
        this(new ExecutionPlanner());
    }

    /**
     * Creates a DAG printer with the specified {@link ExecutionPlanner}.
     *
     * @param planner the execution planner to use for computing batches
     */
    public DagPrinter(ExecutionPlanner planner) {
        this.planner = planner;
    }

    /**
     * Returns a formatted ASCII representation of the pipeline's execution batches.
     *
     * <p>Each batch is displayed on its own line with a 1-based index. Batches containing
     * more than one stage are annotated with {@code (parallel)} to indicate concurrent
     * execution. Example output:
     * <pre>
     * Batch 1: [build, check]  (parallel)
     * Batch 2: [docker]
     * Batch 3: [deploy-staging]
     * </pre>
     *
     * @param pipeline the pipeline to format
     * @return the ASCII representation of the execution plan
     */
    public String toAscii(Pipeline pipeline) {
        List<List<String>> batches = planner.plan(pipeline);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < batches.size(); i++) {
            List<String> batch = batches.get(i);
            sb.append("Batch ").append(i + 1).append(": [");
            sb.append(String.join(", ", batch));
            sb.append("]");
            if (batch.size() > 1) {
                sb.append("  (parallel)");
            }
            if (i < batches.size() - 1) {
                sb.append('\n');
            }
        }

        return sb.toString();
    }
}
