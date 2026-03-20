package dev.gimi.core.model.ti;

import java.time.Instant;
import java.util.List;

/**
 * Result of a Test Intelligence analysis run.
 *
 * @param runId             the pipeline run ID
 * @param totalTests        total number of tests in the project
 * @param selectedTests     number of tests selected to run
 * @param skippedTests      number of tests skipped
 * @param changedFiles      files that changed since last green build
 * @param selectedTestNames the selected test class/method names
 * @param confidence        average confidence score of selection
 * @param timeSavedMs       estimated time saved vs full test suite
 * @param mode              the correlation mode used
 * @param analyzedAt        timestamp of analysis
 */
public record TestSelectionResult(
        String runId,
        int totalTests,
        int selectedTests,
        int skippedTests,
        List<String> changedFiles,
        List<String> selectedTestNames,
        double confidence,
        long timeSavedMs,
        CorrelationMode mode,
        Instant analyzedAt
) {
    public TestSelectionResult {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        selectedTestNames = selectedTestNames == null ? List.of() : List.copyOf(selectedTestNames);
    }
}
