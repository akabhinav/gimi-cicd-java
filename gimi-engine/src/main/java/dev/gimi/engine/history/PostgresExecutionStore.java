package dev.gimi.engine.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import dev.gimi.core.execution.ExecutionRecord;
import dev.gimi.core.execution.ExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL-backed implementation of {@link ExecutionStore}.
 *
 * <p>Stores execution records in the {@code executions} table using JSONB for the
 * full record data and indexed columns for common query predicates.
 */
public final class PostgresExecutionStore implements ExecutionStore {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresExecutionStore.class);

    private static final String UPSERT_SQL = """
            INSERT INTO executions (run_id, pipeline_name, status, trigger, environment, started_at, finished_at, data)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (run_id) DO UPDATE SET
                status      = EXCLUDED.status,
                finished_at = EXCLUDED.finished_at,
                data        = EXCLUDED.data
            """;

    private static final String SELECT_BY_ID_SQL = """
            SELECT data FROM executions WHERE run_id = ?
            """;

    private static final String SELECT_RECENT_SQL = """
            SELECT data FROM executions ORDER BY started_at DESC LIMIT ?
            """;

    private static final String SELECT_BY_PIPELINE_SQL = """
            SELECT data FROM executions WHERE pipeline_name = ? ORDER BY started_at DESC LIMIT ?
            """;

    private final HikariDataSource dataSource;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new store backed by the given HikariCP data source.
     *
     * @param dataSource the connection pool to use
     */
    public PostgresExecutionStore(HikariDataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void save(ExecutionRecord record) {
        Objects.requireNonNull(record, "record must not be null");

        String json = toJson(record);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {

            ps.setString(1, record.runId());
            ps.setString(2, record.pipelineName());
            ps.setString(3, record.status().name());
            ps.setString(4, record.trigger());
            ps.setString(5, record.environment());
            ps.setTimestamp(6, toTimestamp(record.startedAt()));
            ps.setTimestamp(7, toTimestamp(record.finishedAt()));
            ps.setString(8, json);

            ps.executeUpdate();
            LOG.debug("Saved execution record: runId={}, status={}", record.runId(), record.status());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save execution record: " + record.runId(), e);
        }
    }

    @Override
    public List<ExecutionRecord> getRecent(int limit) {
        List<ExecutionRecord> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_RECENT_SQL)) {

            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(fromJson(rs.getString("data")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch recent executions", e);
        }

        return results;
    }

    @Override
    public Optional<ExecutionRecord> getRun(String runId) {
        Objects.requireNonNull(runId, "runId must not be null");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {

            ps.setString(1, runId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(fromJson(rs.getString("data")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch execution run: " + runId, e);
        }

        return Optional.empty();
    }

    /**
     * Returns execution records for a specific pipeline, ordered by start time descending.
     *
     * @param pipelineName the pipeline name to filter by
     * @param limit        the maximum number of records to return
     * @return a list of matching execution records
     */
    public List<ExecutionRecord> getByPipeline(String pipelineName, int limit) {
        Objects.requireNonNull(pipelineName, "pipelineName must not be null");
        List<ExecutionRecord> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_PIPELINE_SQL)) {

            ps.setString(1, pipelineName);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(fromJson(rs.getString("data")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch executions for pipeline: " + pipelineName, e);
        }

        return results;
    }

    private String toJson(ExecutionRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize ExecutionRecord", e);
        }
    }

    private ExecutionRecord fromJson(String json) {
        try {
            return objectMapper.readValue(json, ExecutionRecord.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize ExecutionRecord", e);
        }
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
