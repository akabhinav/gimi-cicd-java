package dev.gimi.cli.commands;

import dev.gimi.cli.output.TerminalOutput;
import dev.gimi.core.model.Pipeline;
import dev.gimi.engine.parser.PipelineParser;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lists available pipeline definitions in the current directory.
 *
 * <p>Scans for {@code *.yaml} and {@code *.yml} files, attempts to parse each
 * as a pipeline definition, and lists valid pipelines with their file paths.
 */
@Command(
    name = "list",
    mixinStandardHelpOptions = true,
    description = "List available pipelines"
)
public class ListCommand implements Runnable {

    @Override
    public void run() {
        PipelineParser parser = new PipelineParser();
        Path dir = Path.of(".");
        boolean found = false;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.{yaml,yml}")) {
            for (Path path : stream) {
                try {
                    Pipeline pipeline = parser.parseFile(path);
                    System.out.printf("  %-30s %s%n", pipeline.name(), path.getFileName());
                    found = true;
                } catch (Exception e) {
                    // Skip files that are not valid pipeline definitions
                }
            }
        } catch (IOException e) {
            TerminalOutput.error("Failed to scan directory: " + e.getMessage());
            System.exit(1);
        }

        if (!found) {
            TerminalOutput.warning("No valid pipeline files found in the current directory.");
            TerminalOutput.warning("Hint: Run 'gimi init' to create a sample pipeline.");
        }
    }
}
