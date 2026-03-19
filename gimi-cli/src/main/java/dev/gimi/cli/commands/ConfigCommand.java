package dev.gimi.cli.commands;

import dev.gimi.cli.output.TerminalOutput;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Manages gimi configuration stored in {@code .gimi/config.properties}.
 *
 * <p>Provides subcommands to get and set individual configuration values.
 */
@Command(
    name = "config",
    mixinStandardHelpOptions = true,
    description = "Manage gimi configuration",
    subcommands = {
        ConfigCommand.ConfigGetSubcommand.class,
        ConfigCommand.ConfigSetSubcommand.class
    }
)
public class ConfigCommand implements Runnable {

    private static final Path CONFIG_FILE = Path.of(".gimi", "config.properties");

    @Override
    public void run() {
        TerminalOutput.info("Usage: gimi config <get|set>");
    }

    /**
     * Gets a configuration value by key from {@code .gimi/config.properties}.
     */
    @Command(
        name = "get",
        mixinStandardHelpOptions = true,
        description = "Get a configuration value"
    )
    public static class ConfigGetSubcommand implements Runnable {

        @Parameters(index = "0", description = "Configuration key")
        String key;

        @Override
        public void run() {
            Properties props = loadConfig();
            if (props == null) {
                return;
            }

            String value = props.getProperty(key);
            if (value == null) {
                TerminalOutput.warning("Key '" + key + "' is not set.");
            } else {
                System.out.println(key + " = " + value);
            }
        }
    }

    /**
     * Sets a configuration value in {@code .gimi/config.properties}.
     */
    @Command(
        name = "set",
        mixinStandardHelpOptions = true,
        description = "Set a configuration value"
    )
    public static class ConfigSetSubcommand implements Runnable {

        @Parameters(index = "0", description = "Configuration key")
        String key;

        @Parameters(index = "1", description = "Configuration value")
        String value;

        @Override
        public void run() {
            Properties props = loadConfig();
            if (props == null) {
                props = new Properties();
            }

            props.setProperty(key, value);

            try {
                Files.createDirectories(CONFIG_FILE.getParent());
                try (var out = Files.newOutputStream(CONFIG_FILE)) {
                    props.store(out, "gimi configuration");
                }
                TerminalOutput.success("Set " + key + " = " + value);
            } catch (IOException e) {
                TerminalOutput.error("Failed to write configuration: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    /**
     * Loads properties from the configuration file, or returns {@code null} if the file
     * does not exist or cannot be read.
     */
    static Properties loadConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            TerminalOutput.error("Configuration file not found: " + CONFIG_FILE);
            TerminalOutput.warning("Hint: Run 'gimi init' to create the configuration.");
            System.exit(1);
            return null; // unreachable
        }

        Properties props = new Properties();
        try (var in = Files.newInputStream(CONFIG_FILE)) {
            props.load(in);
            return props;
        } catch (IOException e) {
            TerminalOutput.error("Failed to read configuration: " + e.getMessage());
            System.exit(1);
            return null; // unreachable
        }
    }
}
