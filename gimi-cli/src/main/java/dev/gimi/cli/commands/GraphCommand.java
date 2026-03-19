package dev.gimi.cli.commands;

import dev.gimi.cli.output.TerminalOutput;
import dev.gimi.core.exception.GimiException;
import dev.gimi.core.model.Pipeline;
import dev.gimi.engine.dag.DagPrinter;
import dev.gimi.engine.parser.PipelineParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

/**
 * Displays the pipeline execution graph as an ASCII DAG.
 *
 * <p>Parses the pipeline file and prints the execution batches,
 * showing which stages can run in parallel.
 */
@Command(
    name = "graph",
    mixinStandardHelpOptions = true,
    description = "Display pipeline execution graph"
)
public class GraphCommand implements Runnable {

    @Option(names = {"--file", "-f"}, defaultValue = "gimi-pipeline.yaml",
            description = "Path to the pipeline YAML file")
    String file;

    @Override
    public void run() {
        try {
            PipelineParser parser = new PipelineParser();
            Pipeline pipeline = parser.parseFile(Path.of(file));

            DagPrinter printer = new DagPrinter();
            String graph = printer.toAscii(pipeline);

            TerminalOutput.info("Execution graph for '" + pipeline.name() + "':");
            System.out.println(graph);

        } catch (GimiException e) {
            TerminalOutput.error(e.getMessage());
            if (e.hint() != null) {
                TerminalOutput.warning("Hint: " + e.hint());
            }
            System.exit(1);
        }
    }
}
