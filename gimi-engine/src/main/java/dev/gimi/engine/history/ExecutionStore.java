package dev.gimi.engine.history;

import dev.gimi.core.execution.ExecutionRecord;

import java.util.List;
import java.util.Optional;

/** Persistent storage for pipeline execution records. */
public interface ExecutionStore {

    /** Save an execution record. */
    void save(ExecutionRecord record);

    /** Get recent execution records, ordered by startedAt descending. */
    List<ExecutionRecord> getRecent(int limit);

    /** Get a specific execution record by run ID. */
    Optional<ExecutionRecord> getRun(String runId);
}
