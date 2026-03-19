package dev.gimi.cli.commands;

import dev.gimi.cli.output.TerminalOutput;
import dev.gimi.core.exception.GimiException;
import dev.gimi.core.exception.ValidationException;
import dev.gimi.core.model.Pipeline;
import dev.gimi.engine.parser.PipelineParser;
import dev.gimi.engine.dag.DagBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

/**
 * Validates a pipeline YAML file for syntax and semantic correctness.
 *
 * <p>Parses the specified file and runs validation checks including
 * DAG construction to detect dependency cycles. Reports errors in red
 * with hints, or prints a green success message if the pipeline is valid.
 */
@Command(
    name = "validate",
    mixinStandardHelpOptions = true,
    description = "Validate a pipeline YAML file"
)
public class ValidateCommand implements Runnable {

    @Option(names = {"--file", "-f"}, defaultValue = "gimi-pipeline.yaml",
            description = "Path to the pipeline YAML file")
    String file;

    @Override
    public void run() {
        try {
            PipelineParser parser = new PipelineParser();
            Pipeline pipeline = parser.parseFile(Path.of(file));

            // Validate by building the DAG (detects cycles and missing dependencies)
            DagBuilder dagBuilder = new DagBuilder();
            dagBuilder.build(pipeline);

            TerminalOutput.success("Pipeline '" + pipeline.name() + "' is valid (" + file + ")");

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
