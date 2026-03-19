package dev.gimi.engine.trigger;

import dev.gimi.core.model.CronTrigger;
import dev.gimi.core.model.GitEvent;
import dev.gimi.core.model.GitTrigger;
import dev.gimi.core.model.PathFilter;
import dev.gimi.core.model.Trigger;
import dev.gimi.core.model.WebhookTrigger;

import java.util.List;

/**
 * Matches incoming events against pipeline trigger configurations.
 *
 * <p>Supports Git triggers (with event type, branch glob, and path filtering),
 * cron triggers (always returns false since cron is time-based), and webhook
 * triggers (always returns true since they fire on any POST).
 */
public class TriggerMatcher {

    /**
     * Determines whether the given trigger matches the incoming event context.
     *
     * @param trigger      the pipeline trigger configuration to evaluate
     * @param event        the Git event type that occurred
     * @param branch       the branch on which the event occurred
     * @param changedPaths the list of file paths changed in the event
     * @return {@code true} if the trigger matches the event context
     */
    public boolean matches(Trigger trigger, GitEvent event, String branch, List<String> changedPaths) {
        return switch (trigger) {
            case GitTrigger git -> matchesGitTrigger(git, event, branch, changedPaths);
            case CronTrigger cron -> false;
            case WebhookTrigger webhook -> true;
        };
    }

    private boolean matchesGitTrigger(GitTrigger trigger, GitEvent event, String branch, List<String> changedPaths) {
        // Check event type
        if (!trigger.events().contains(event)) {
            return false;
        }

        // Check branch pattern
        if (!trigger.branches().isEmpty()) {
            boolean branchMatches = trigger.branches().stream()
                    .anyMatch(pattern -> matchesGlob(pattern, branch));
            if (!branchMatches) {
                return false;
            }
        }

        // Check path filter
        if (trigger.paths() != null) {
            if (!matchesPaths(trigger.paths(), changedPaths)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Simple glob matching supporting the {@code *} wildcard.
     *
     * <p>The {@code *} character matches any sequence of characters (excluding
     * path separators for single-star, or including them for double-star {@code **}).
     */
    private boolean matchesGlob(String pattern, String value) {
        // Convert glob pattern to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("**", "##DOUBLESTAR##")
                .replace("*", "[^/]*")
                .replace("##DOUBLESTAR##", ".*");
        return value.matches(regex);
    }

    /**
     * Checks whether any changed path matches the include patterns and none
     * match the exclude patterns.
     */
    private boolean matchesPaths(PathFilter filter, List<String> changedPaths) {
        if (changedPaths == null || changedPaths.isEmpty()) {
            return filter.include().isEmpty();
        }

        // If include patterns exist, at least one changed path must match
        if (!filter.include().isEmpty()) {
            boolean anyIncluded = changedPaths.stream()
                    .anyMatch(path -> filter.include().stream()
                            .anyMatch(pattern -> matchesGlob(pattern, path)));
            if (!anyIncluded) {
                return false;
            }
        }

        // If exclude patterns exist, remove any matches — if all paths are excluded, no match
        if (!filter.exclude().isEmpty()) {
            boolean allExcluded = changedPaths.stream()
                    .allMatch(path -> filter.exclude().stream()
                            .anyMatch(pattern -> matchesGlob(pattern, path)));
            if (allExcluded) {
                return false;
            }
        }

        return true;
    }
}
