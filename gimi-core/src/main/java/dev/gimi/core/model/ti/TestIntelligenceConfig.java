package dev.gimi.core.model.ti;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Test Intelligence configuration for ML-based selective test running.
 *
 * <p>Analyzes code changes to determine which tests are affected and only runs those,
 * reducing CI time by up to 80% — a key differentiator over Harness Test Intelligence™.
 *
 * @param enabled           whether test intelligence is active
 * @param language          the project language (java, python, go, js, ts)
 * @param buildTool         the build tool (maven, gradle, npm, go)
 * @param testCommand       the base test command to run
 * @param testGlobs         glob patterns to find test files
 * @param sourceGlobs       glob patterns to find source files
 * @param correlationMode   how to correlate source→test (CALL_GRAPH, FILE_MAPPING, ML)
 * @param fullRunTriggers   patterns that always trigger a full test run (e.g., build config changes)
 * @param maxTestTimeMs     max allowed test execution time before falling back to full run
 * @param minConfidence     minimum confidence threshold for ML-based test selection (0.0-1.0)
 */
public record TestIntelligenceConfig(
        boolean enabled,
        String language,
        @JsonProperty("build_tool") String buildTool,
        @JsonProperty("test_command") String testCommand,
        @JsonProperty("test_globs") List<String> testGlobs,
        @JsonProperty("source_globs") List<String> sourceGlobs,
        @JsonProperty("correlation_mode") CorrelationMode correlationMode,
        @JsonProperty("full_run_triggers") List<String> fullRunTriggers,
        @JsonProperty("max_test_time_ms") long maxTestTimeMs,
        @JsonProperty("min_confidence") double minConfidence
) {
    public TestIntelligenceConfig {
        testGlobs = testGlobs == null ? List.of() : List.copyOf(testGlobs);
        sourceGlobs = sourceGlobs == null ? List.of() : List.copyOf(sourceGlobs);
        fullRunTriggers = fullRunTriggers == null ? List.of() : List.copyOf(fullRunTriggers);
        correlationMode = correlationMode == null ? CorrelationMode.FILE_MAPPING : correlationMode;
        if (minConfidence <= 0) minConfidence = 0.7;
    }
}
