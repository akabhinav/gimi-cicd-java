package dev.gimi.cli.commands;

import dev.gimi.cli.output.TerminalOutput;
import dev.gimi.core.exception.GimiException;
import dev.gimi.core.model.Environment;
import dev.gimi.core.model.FreezeWindow;
import dev.gimi.core.model.Pipeline;
import dev.gimi.engine.parser.PipelineParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Map;

/**
 * Manages pipeline environments.
 *
 * <p>Provides subcommands to list all defined environments and show
 * detailed configuration for a specific environment.
 */
@Command(
    name = "env",
    mixinStandardHelpOptions = true,
    description = "Manage environments",
    subcommands = {
        EnvCommand.EnvListSubcommand.class,
        EnvCommand.EnvShowSubcommand.class
    }
)
public class EnvCommand implements Runnable {

    @Override
    public void run() {
        TerminalOutput.info("Usage: gimi env <list|show>");
    }

    /**
     * Lists all environments defined in the pipeline.
     */
    @Command(
        name = "list",
        mixinStandardHelpOptions = true,
        description = "List all defined environments"
    )
    public static class EnvListSubcommand implements Runnable {

        @Option(names = {"--file", "-f"}, defaultValue = "gimi-pipeline.yaml",
                description = "Path to the pipeline YAML file")
        String file;

        @Override
        public void run() {
            try {
                PipelineParser parser = new PipelineParser();
                Pipeline pipeline = parser.parseFile(Path.of(file));

                Map<String, Environment> environments = pipeline.environments();
                if (environments.isEmpty()) {
                    TerminalOutput.warning("No environments defined in " + file);
                    return;
                }

                TerminalOutput.info("Environments in '" + pipeline.name() + "':");
                for (String name : environments.keySet()) {
                    System.out.println("  " + name);
                }

            } catch (GimiException e) {
                TerminalOutput.error(e.getMessage());
                if (e.hint() != null) {
                    TerminalOutput.warning("Hint: " + e.hint());
                }
                System.exit(1);
            }
        }
    }

    /**
     * Shows detailed configuration for a specific environment.
     */
    @Command(
        name = "show",
        mixinStandardHelpOptions = true,
        description = "Show environment details"
    )
    public static class EnvShowSubcommand implements Runnable {

        @Parameters(index = "0", description = "Environment name")
        String envName;

        @Option(names = {"--file", "-f"}, defaultValue = "gimi-pipeline.yaml",
                description = "Path to the pipeline YAML file")
        String file;

        @Override
        public void run() {
            try {
                PipelineParser parser = new PipelineParser();
                Pipeline pipeline = parser.parseFile(Path.of(file));

                Environment environment = pipeline.environments().get(envName);
                if (environment == null) {
                    TerminalOutput.error("Environment '" + envName + "' not found in " + file);
                    TerminalOutput.warning("Hint: Run 'gimi env list' to see available environments.");
                    System.exit(1);
                }

                TerminalOutput.info("Environment: " + envName);
                System.out.println("  Requires approval: " + environment.requiresApproval());

                if (!environment.variables().isEmpty()) {
                    System.out.println("  Variables:");
                    for (Map.Entry<String, String> entry : environment.variables().entrySet()) {
                        System.out.println("    " + entry.getKey() + " = " + entry.getValue());
                    }
                }

                if (!environment.freezeWindows().isEmpty()) {
                    System.out.println("  Freeze windows:");
                    for (FreezeWindow fw : environment.freezeWindows()) {
                        System.out.println("    " + fw.start() + " - " + fw.end()
                                + (fw.cron() != null ? " (cron: " + fw.cron() + ")" : ""));
                    }
                }

            } catch (GimiException e) {
                TerminalOutput.error(e.getMessage());
                if (e.hint() != null) {
                    TerminalOutput.warning("Hint: " + e.hint());
                }
                System.exit(1);
            }
        }
    }
}
