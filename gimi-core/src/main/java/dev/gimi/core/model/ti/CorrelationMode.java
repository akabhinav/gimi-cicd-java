package dev.gimi.core.model.ti;

/**
 * Mode for correlating source code changes to affected test cases.
 */
public enum CorrelationMode {
    /** Static file path mapping (source → test by naming convention). */
    FILE_MAPPING,
    /** Call graph analysis (traces imports/dependencies at compile time). */
    CALL_GRAPH,
    /** ML model trained on historical test results and code changes. */
    ML
}
