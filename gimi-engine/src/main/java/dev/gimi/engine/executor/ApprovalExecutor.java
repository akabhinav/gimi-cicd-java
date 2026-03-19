package dev.gimi.engine.executor;

import dev.gimi.core.exception.ExecutionException;
import dev.gimi.core.model.ApprovalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Console;

/**
 * Handles approval gates for pipeline stages.
 *
 * <p>This executor prompts a human operator for approval via the system console.
 * It is <em>not</em> a {@link StepExecutor} because approval is a stage-level
 * concept rather than a step-level one.
 */
public final class ApprovalExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovalExecutor.class);

    /**
     * Requests interactive approval for the given stage.
     *
     * <p>Prompts the user on {@link System#console()} and returns {@code true}
     * if the user responds with {@code "y"} or {@code "yes"} (case-insensitive).
     *
     * @param stageName the name of the stage awaiting approval
     * @param config    the approval configuration (may contain a custom message)
     * @return {@code true} if approval was granted, {@code false} otherwise
     * @throws ExecutionException if no interactive console is available
     */
    public boolean requestApproval(String stageName, ApprovalConfig config) {
        Console console = System.console();
        if (console == null) {
            throw new ExecutionException(
                    "Cannot request approval for stage '" + stageName + "': no interactive console available",
                    "Run the pipeline from an interactive terminal to enable approval prompts"
            );
        }

        if (config.message() != null && !config.message().isBlank()) {
            LOG.info("Approval message for stage '{}': {}", stageName, config.message());
        }

        String response = console.readLine("Approve stage '%s'? [y/n]: ", stageName);
        boolean approved = response != null
                && (response.trim().equalsIgnoreCase("y") || response.trim().equalsIgnoreCase("yes"));

        if (approved) {
            LOG.info("Stage '{}' approved", stageName);
        } else {
            LOG.info("Stage '{}' rejected", stageName);
        }

        return approved;
    }
}
