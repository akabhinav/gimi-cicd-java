package dev.gimi.core.model;

import java.util.List;

/**
 * A trigger that fires on Git events such as pushes, pull requests, or tags.
 *
 * @param events   the list of Git events that activate this trigger
 * @param branches the list of branch patterns to match
 * @param paths    the path filter for file-based trigger matching
 */
public record GitTrigger(
        List<GitEvent> events,
        List<String> branches,
        PathFilter paths
) implements Trigger {

    /**
     * Creates a {@code GitTrigger} with defensive copies of collections.
     */
    public GitTrigger {
        events = events == null ? List.of() : List.copyOf(events);
        branches = branches == null ? List.of() : List.copyOf(branches);
    }
}
