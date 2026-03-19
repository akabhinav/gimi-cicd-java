package dev.gimi.engine;

import dev.gimi.core.model.Pipeline;
import dev.gimi.core.execution.ExecutionRecord;
import dev.gimi.engine.dag.DagPrinter;
import dev.gimi.engine.dag.ExecutionPlanner;
import dev.gimi.engine.executor.StepExecutorRegistry;
import dev.gimi.engine.history.ExecutionStore;
import dev.gimi.engine.orchestrator.PipelineOrchestrator;
import dev.gimi.engine.parser.PipelineParser;
import dev.gimi.engine.validator.PipelineValidator;
import dev.gimi.engine.validator.ValidationError;
import dev.gimi.engine.variable.ExecutionContext;

import java.nio.file.Path;
import java.util.List;

/**
 * Main entry point and facade for the gimi pipeline engine.
 *
 * <p>Provides a unified API for parsing, validating, planning, and executing pipelines.
 * This class is the primary interface for consumers of the engine library.</p>
 */
public class GimiEngine {

    private final PipelineParser parser;
    private final PipelineValidator validator;
    private final ExecutionPlanner planner;
    private final DagPrinter dagPrinter;
    private final PipelineOrchestrator orchestrator;

    /**
     * Create a new engine with default configuration and no execution history storage.
     */
    public GimiEngine() {
        this(null);
    }

    /**
     * Create a new engine with default configuration and the given execution store.
     *
     * @param store execution history store (may be null)
     */
    public GimiEngine(ExecutionStore store) {
        this.parser = new PipelineParser();
        this.validator = new PipelineValidator();
        this.planner = new ExecutionPlanner();
        this.dagPrinter = new DagPrinter();
        this.orchestrator = new PipelineOrchestrator(StepExecutorRegistry.createDefault(), store);
    }

    /**
     * Parse a YAML string into a Pipeline model.
     *
     * @param yaml the YAML content
     * @return the parsed pipeline
     * @throws dev.gimi.core.exception.ParseException if parsing fails
     */
    public Pipeline parse(String yaml) {
        return parser.parse(yaml);
    }

    /**
     * Parse a pipeline YAML file.
     *
     * @param file path to the YAML file
     * @return the parsed pipeline
     * @throws dev.gimi.core.exception.ParseException if parsing fails
     */
    public Pipeline parseFile(Path file) {
        return parser.parseFile(file);
    }

    /**
     * Validate a pipeline and return any errors found.
     *
     * @param pipeline the pipeline to validate
     * @return list of validation errors (empty if valid)
     */
    public List<ValidationError> validate(Pipeline pipeline) {
        return validator.validate(pipeline);
    }

    /**
     * Execute a pipeline with the given context.
     *
     * @param pipeline the pipeline to execute
     * @param ctx      the execution context
     * @return the execution record with results
     */
    public ExecutionRecord execute(Pipeline pipeline, ExecutionContext ctx) {
        return orchestrator.execute(pipeline, ctx);
    }

    /**
     * Plan the execution order for a pipeline, returning batches of parallel stages.
     *
     * @param pipeline the pipeline to plan
     * @return ordered list of batches (each batch is a list of stage names)
     */
    public List<List<String>> plan(Pipeline pipeline) {
        return planner.plan(pipeline);
    }

    /**
     * Generate an ASCII representation of the pipeline execution graph.
     *
     * @param pipeline the pipeline to graph
     * @return ASCII DAG representation
     */
    public String graph(Pipeline pipeline) {
        return dagPrinter.toAscii(pipeline);
    }

    /**
     * Get the pipeline parser for direct access.
     *
     * @return the pipeline parser
     */
    public PipelineParser parser() {
        return parser;
    }

    /**
     * Get the pipeline validator for direct access.
     *
     * @return the pipeline validator
     */
    public PipelineValidator validator() {
        return validator;
    }
}
