package dev.gimi.engine.log;

import dev.gimi.core.model.LogEntry;

import java.util.List;
import java.util.function.Consumer;

/**
 * Streaming log interface for real-time pipeline log delivery and history retrieval.
 */
public interface LogStreamer {

    /**
     * Publishes a log entry for persistence and real-time delivery to subscribers.
     *
     * @param entry the log entry to publish
     */
    void publish(LogEntry entry);

    /**
     * Subscribes to real-time log events for a specific pipeline run.
     *
     * @param runId    the pipeline run ID to subscribe to
     * @param listener the callback invoked for each new log entry
     */
    void subscribe(String runId, Consumer<LogEntry> listener);

    /**
     * Removes the subscription for the given pipeline run.
     *
     * @param runId the pipeline run ID to unsubscribe from
     */
    void unsubscribe(String runId);

    /**
     * Retrieves historical log entries for a pipeline run.
     *
     * @param runId the pipeline run ID
     * @param limit the maximum number of entries to return
     * @return a list of log entries ordered by timestamp
     */
    List<LogEntry> getHistory(String runId, int limit);
}
