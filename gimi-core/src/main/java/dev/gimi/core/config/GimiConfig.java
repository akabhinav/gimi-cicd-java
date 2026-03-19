package dev.gimi.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Immutable configuration for the Gimi CI/CD engine.
 *
 * <p>Configuration is typically loaded from a {@code .gimi/config.properties} file
 * within the project directory. If the file does not exist, sensible defaults are used.
 *
 * @param defaultEnv    the default target environment name
 * @param logLevel      the logging level (e.g. "INFO", "DEBUG")
 * @param historyDbPath the file-system path to the execution history database
 */
public record GimiConfig(
        String defaultEnv,
        String logLevel,
        String historyDbPath
) {

    private static final String KEY_DEFAULT_ENV = "gimi.default-env";
    private static final String KEY_LOG_LEVEL = "gimi.log-level";
    private static final String KEY_HISTORY_DB_PATH = "gimi.history-db-path";

    private static final String DEFAULT_ENV = "local";
    private static final String DEFAULT_LOG_LEVEL = "INFO";
    private static final String DEFAULT_HISTORY_DB_PATH = ".gimi/history.db";

    /**
     * Returns a {@link GimiConfig} populated with sensible default values.
     *
     * @return the default configuration
     */
    public static GimiConfig defaults() {
        return new GimiConfig(DEFAULT_ENV, DEFAULT_LOG_LEVEL, DEFAULT_HISTORY_DB_PATH);
    }

    /**
     * Loads configuration from a {@code config.properties} file inside the given directory.
     *
     * <p>The method looks for {@code <configDir>/config.properties}. If the file does not
     * exist, {@link #defaults()} is returned. Any property not present in the file falls
     * back to its default value.
     *
     * @param configDir the directory that contains {@code config.properties}
     *                  (typically {@code .gimi/} within the project root)
     * @return the loaded configuration, or defaults if the file is absent
     * @throws IOException if an I/O error occurs while reading the file
     */
    public static GimiConfig load(Path configDir) throws IOException {
        Path configFile = configDir.resolve("config.properties");
        if (!Files.exists(configFile)) {
            return defaults();
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(configFile)) {
            props.load(in);
        }

        return new GimiConfig(
                props.getProperty(KEY_DEFAULT_ENV, DEFAULT_ENV),
                props.getProperty(KEY_LOG_LEVEL, DEFAULT_LOG_LEVEL),
                props.getProperty(KEY_HISTORY_DB_PATH, DEFAULT_HISTORY_DB_PATH)
        );
    }
}
