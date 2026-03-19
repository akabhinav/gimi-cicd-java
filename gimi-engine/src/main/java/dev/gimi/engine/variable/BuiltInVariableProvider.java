package dev.gimi.engine.variable;

import dev.gimi.core.constants.BuiltInVariables;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides GIMI_* built-in variables by running git commands and capturing
 * runtime context information.
 *
 * <p>Built-in variables are automatically available in every pipeline execution
 * and include information about the current Git state, the pipeline run, and
 * the execution timestamp.
 */
public final class BuiltInVariableProvider {

    /**
     * Provides all built-in variables for a pipeline execution.
     *
     * <p>This method runs several git commands to capture the current repository state
     * and combines them with the supplied runtime parameters.
     *
     * @param runId        the unique identifier for this pipeline run
     * @param pipelineName the name of the pipeline being executed
     * @return an unmodifiable map of built-in variable names to their values
     */
    public Map<String, String> provide(String runId, String pipelineName) {
        Map<String, String> vars = new LinkedHashMap<>();

        vars.put(BuiltInVariables.GIMI_GIT_SHA, runGitCommand("rev-parse", "HEAD"));
        vars.put(BuiltInVariables.GIMI_GIT_SHA_SHORT, runGitCommand("rev-parse", "--short", "HEAD"));
        vars.put(BuiltInVariables.GIMI_GIT_BRANCH, runGitCommand("rev-parse", "--abbrev-ref", "HEAD"));
        vars.put(BuiltInVariables.GIMI_GIT_TAG, runGitCommand("describe", "--tags", "--exact-match"));
        vars.put(BuiltInVariables.GIMI_GIT_AUTHOR, runGitCommand("log", "-1", "--format=%an"));
        vars.put(BuiltInVariables.GIMI_GIT_MESSAGE, runGitCommand("log", "-1", "--format=%s"));
        vars.put(BuiltInVariables.GIMI_RUN_ID, runId);
        vars.put(BuiltInVariables.GIMI_PIPELINE_NAME, pipelineName);
        vars.put(BuiltInVariables.GIMI_TIMESTAMP, Instant.now().toString());

        return Map.copyOf(vars);
    }

    private String runGitCommand(String... args) {
        try {
            String[] command = new String[args.length + 1];
            command[0] = "git";
            System.arraycopy(args, 0, command, 1, args.length);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.readLine();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 || output == null) {
                return "";
            }
            return output.trim();
        } catch (Exception e) {
            return "";
        }
    }
}
