package dev.gimi.cli;

import dev.gimi.cli.commands.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * gimi -- CLI-first CI/CD pipeline engine.
 *
 * <p>This is the root picocli command and main entry point for the gimi CLI.
 * It registers all available subcommands and displays usage information when
 * invoked without arguments.
 */
@Command(
    name = "gimi",
    mixinStandardHelpOptions = true,
    version = "gimi 0.1.0",
    description = "CLI-first CI/CD pipeline engine",
    subcommands = {
        InitCommand.class,
        ValidateCommand.class,
        RunCommand.class,
        GraphCommand.class,
        ListCommand.class,
        LogsCommand.class,
        EnvCommand.class,
        ConfigCommand.class
    }
)
public class GimiCli implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    /**
     * Main entry point for the gimi CLI application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new GimiCli()).execute(args);
        System.exit(exitCode);
    }
}
