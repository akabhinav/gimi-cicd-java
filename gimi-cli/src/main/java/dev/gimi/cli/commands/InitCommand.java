package dev.gimi.cli.commands;

import dev.gimi.cli.output.TerminalOutput;
import dev.gimi.core.exception.GimiException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Initializes a new gimi pipeline project in the current directory.
 *
 * <p>Creates the {@code .gimi/} configuration directory, a default
 * {@code config.properties} file, a sample {@code gimi-pipeline.yaml},
 * and adds {@code .gimi/history.db} to {@code .gitignore}.
 */
@Command(
    name = "init",
    mixinStandardHelpOptions = true,
    description = "Initialize a new gimi pipeline project"
)
public class InitCommand implements Runnable {

    @Option(names = {"--template", "-t"}, description = "Pipeline template name")
    String template;

    private static final String DEFAULT_PIPELINE_YAML = """
            version: "1"
            name: my-pipeline

            variables:
              APP_NAME: my-app

            stages:
              - name: build
                steps:
                  - name: compile
                    type: shell
                    run: echo "Building ${APP_NAME}..."

              - name: test
                depends_on:
                  - build
                steps:
                  - name: unit-tests
                    type: shell
                    run: echo "Running tests..."
            """;

    @Override
    public void run() {
        try {
            Path gimiDir = Path.of(".gimi");
            Files.createDirectories(gimiDir);

            // Create default config.properties
            Path configFile = gimiDir.resolve("config.properties");
            if (!Files.exists(configFile)) {
                Properties defaults = new Properties();
                defaults.setProperty("log.level", "INFO");
                defaults.setProperty("execution.timeout", "3600");
                defaults.setProperty("output.color", "true");
                try (var out = Files.newOutputStream(configFile)) {
                    defaults.store(out, "gimi configuration");
                }
            }

            // Create gimi-pipeline.yaml
            Path pipelineFile = Path.of("gimi-pipeline.yaml");
            if (!Files.exists(pipelineFile)) {
                String content;
                if (template != null) {
                    content = loadTemplate(template);
                } else {
                    content = DEFAULT_PIPELINE_YAML;
                }
                Files.writeString(pipelineFile, content);
            }

            // Add .gimi/history.db to .gitignore
            Path gitignore = Path.of(".gitignore");
            String gitignoreEntry = ".gimi/history.db";
            if (Files.exists(gitignore)) {
                String existing = Files.readString(gitignore);
                if (!existing.contains(gitignoreEntry)) {
                    Files.writeString(gitignore, existing + "\n" + gitignoreEntry + "\n");
                }
            } else {
                Files.writeString(gitignore, gitignoreEntry + "\n");
            }

            TerminalOutput.success("Initialized gimi project in " + Path.of(".").toAbsolutePath().normalize());

        } catch (GimiException e) {
            TerminalOutput.error(e.getMessage());
            if (e.hint() != null) {
                TerminalOutput.warning("Hint: " + e.hint());
            }
            System.exit(1);
        } catch (IOException e) {
            TerminalOutput.error("Failed to initialize project: " + e.getMessage());
            System.exit(1);
        }
    }

    private String loadTemplate(String name) throws IOException {
        String resourcePath = "/templates/" + name + ".yaml";
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                TerminalOutput.error("Template not found: " + name);
                TerminalOutput.warning("Hint: Available templates are bundled in the CLI classpath under /templates/");
                System.exit(1);
                return ""; // unreachable
            }
            return new String(is.readAllBytes());
        }
    }
}
