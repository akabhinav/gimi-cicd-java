package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * A pipeline step that executes a shell command.
 *
 * @param name       the name of this step
 * @param run        the shell command to execute
 * @param timeout    the maximum duration for this step
 * @param workingDir the working directory in which to run the command
 * @param env        additional environment variables for the command
 */
public record ShellStep(
        String name,
        String run,
        String timeout,
        @JsonProperty("working_dir") String workingDir,
        Map<String, String> env
) implements Step {

    /**
     * Creates a {@code ShellStep} with a defensive copy of the environment map.
     */
    public ShellStep {
        env = env == null ? Map.of() : Map.copyOf(env);
    }
}
