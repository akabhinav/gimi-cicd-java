package dev.gimi.core.constants;

/**
 * Constants for built-in pipeline variables that are automatically injected
 * into every execution environment.
 *
 * <p>These variables provide contextual information about the current Git state,
 * the pipeline run, and the execution timestamp.
 */
public final class BuiltInVariables {

    private BuiltInVariables() {
        // prevent instantiation
    }

    /** The full Git commit SHA of the current HEAD. */
    public static final String GIMI_GIT_SHA = "GIMI_GIT_SHA";

    /** The abbreviated (short) Git commit SHA of the current HEAD. */
    public static final String GIMI_GIT_SHA_SHORT = "GIMI_GIT_SHA_SHORT";

    /** The name of the current Git branch. */
    public static final String GIMI_GIT_BRANCH = "GIMI_GIT_BRANCH";

    /** The Git tag pointing to the current commit, if any. */
    public static final String GIMI_GIT_TAG = "GIMI_GIT_TAG";

    /** The author of the current Git commit. */
    public static final String GIMI_GIT_AUTHOR = "GIMI_GIT_AUTHOR";

    /** The commit message of the current Git commit. */
    public static final String GIMI_GIT_MESSAGE = "GIMI_GIT_MESSAGE";

    /** The unique identifier assigned to the current pipeline run. */
    public static final String GIMI_RUN_ID = "GIMI_RUN_ID";

    /** The name of the pipeline being executed. */
    public static final String GIMI_PIPELINE_NAME = "GIMI_PIPELINE_NAME";

    /** The ISO-8601 timestamp at which the pipeline run was initiated. */
    public static final String GIMI_TIMESTAMP = "GIMI_TIMESTAMP";
}
