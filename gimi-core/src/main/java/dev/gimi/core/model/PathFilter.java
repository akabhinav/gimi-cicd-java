package dev.gimi.core.model;

import java.util.List;

/**
 * A filter that matches file paths by inclusion and exclusion patterns.
 *
 * @param include the list of path patterns to include
 * @param exclude the list of path patterns to exclude
 */
public record PathFilter(
        List<String> include,
        List<String> exclude
) {

    /**
     * Creates a {@code PathFilter} with defensive copies of the provided lists.
     */
    public PathFilter {
        include = include == null ? List.of() : List.copyOf(include);
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
    }
}
