package dev.gimi.cli.commands;

import dev.gimi.cli.output.TerminalOutput;
import dev.gimi.core.exception.GimiException;
import dev.gimi.core.exception.ValidationException;
import dev.gimi.core.execution.ExecutionRecord;
import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StageResult;
import dev.gimi.core.execution.StepResult;
import dev.gimi.core.model.Pipeline;
import dev.gimi.engine.dag.DagBuilder;
import dev.gimi.engine.dag.DagPrinter;
import dev.gimi.engine.dag.ExecutionPlanner;
import dev.gimi.engine.parser.PipelineParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Executes a pipeline from a YAML definition file.
 *
 * <p>Supports running the full pipeline or a single stage, with options
 * for dry-run mode, environment selection, and variable overrides.
 */
@Command(
    name = "run",
    mixinStandardHelpOptions = true,
    description = "Execute a pipeline"
)
public class RunCommand implements Runnable {

    @Option(names = {"--file", "-f"}, defaultValue = "gimi-pipeline.yaml",
            description = "Path to the pipeline YAML file")
    String file;

    @Option(names = {"--stage", "-s"}, description = "Run a specific stage only")
    String stage;

    @Option(names = {"--no-deps"}, description = "Skip stage dependencies when running a single stage")
    boolean noDeps;

    @Option(names = {"--env", "-e"}, description = "Target environment name")
    String env;

    @Option(names = {"--var", "-v"}, split = "=", description = "Variable overrides (key=value)")
    Map<String, String> vars;

    @Option(names = {"--dry-run"}, description = "Print execution plan without running")
    boolean dryRun;

    @Override
    public void run() {
        try {
            PipelineParser parser = new PipelineParser();
            Pipeline pipeline = parser.parseFile(Path.of(file));

            // Validate the pipeline
            DagBuilder dagBuilder = new DagBuilder();
            dagBuilder.build(pipeline);

            ExecutionPlanner planner = new ExecutionPlanner(dagBuilder);

            // Compute execution plan
            List<List<String>> batches;
            if (stage != null) {
                batches = planner.planForStage(pipeline, stage, !noDeps);
            } else {
                batches = planner.plan(pipeline);
            }

            // Dry-run: print plan and exit
            if (dryRun) {
                TerminalOutput.info("Dry-run execution plan for '" + pipeline.name() + "':");
                if (env != null) {
                    TerminalOutput.info("Environment: " + env);
                }
                if (vars != null && !vars.isEmpty()) {
                    TerminalOutput.info("Variable overrides: " + vars);
                }
                DagPrinter dagPrinter = new DagPrinter(planner);
                System.out.println(dagPrinter.toAscii(pipeline));
                return;
            }

            // Execute the pipeline
            TerminalOutput.info("Running pipeline '" + pipeline.name() + "'...");
            if (env != null) {
                TerminalOutput.info("Environment: " + env);
            }

            // TODO: Wire up full engine execution when GimiEngine facade is available.
            // For now, display the execution plan as a placeholder.
            for (int i = 0; i < batches.size(); i++) {
                List<String> batch = batches.get(i);
                for (String stageName : batch) {
                    TerminalOutput.stageHeader(stageName);
                }
            }

            TerminalOutput.warning("Full execution engine integration is pending.");

        } catch (ValidationException e) {
            TerminalOutput.error(e.getMessage());
            for (String err : e.errors()) {
                TerminalOutput.error("  " + err);
            }
            if (e.hint() != null) {
                TerminalOutput.warning("Hint: " + e.hint());
            }
            System.exit(1);
        } catch (GimiException e) {
            TerminalOutput.error(e.getMessage());
            if (e.hint() != null) {
                TerminalOutput.warning("Hint: " + e.hint());
            }
            System.exit(1);
        }
    }
}
