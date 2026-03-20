package dev.gimi.engine.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.gimi.core.execution.ExecutionRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite-backed implementation of {@link ExecutionStore}.
 *
 * <p>Stores execution records in a local SQLite database file. The full
 * {@link ExecutionRecord} is serialized as JSON in the {@code data} column
 * for lossless round-tripping, while key fields are stored in dedicated
 * columns for efficient querying.
 *
 * <p><strong>WARNING:</strong> SQLite is single-writer and file-locked. It is NOT suitable
 * for concurrent workloads beyond ~100 parallel operations. For production deployments
 * with 1,000+ concurrent pipelines, use {@link PostgresExecutionStore} instead.
 * This store will log a warning at startup if used.
 */
public class SqliteExecutionStore implements ExecutionStore {

    private static final Logger LOG = LoggerFactory.getLogger(SqliteExecutionStore.class);

    private static final String DEFAULT_DB_PATH =
            System.getProperty("user.home") + "/.local/share/gimi/history.db";

    private final String dbPath;
    private final ObjectMapper mapper;

    /**
     * Creates a store using the default database path ({@code ~/.local/share/gimi/history.db}).
     */
    public SqliteExecutionStore() {
        this(DEFAULT_DB_PATH);
    }

    /**
     * Creates a store using the specified database file path.
     *
     * @param dbPath absolute path to the SQLite database file
     */
    public SqliteExecutionStore(String dbPath) {
        this.dbPath = dbPath;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        LOG.warn("=== SQLite ExecutionStore is NOT recommended for production. ===");
        LOG.warn("SQLite is single-writer and cannot handle >100 concurrent operations.");
        LOG.warn("For 10K+ concurrent pipelines, use PostgresExecutionStore instead.");
        LOG.warn("Configure gimi.server.distributed-mode=true and set postgres-url.");
        initializeDb();
    }

    /** {@inheritDoc} */
    @Override
    public void save(ExecutionRecord record) {
        String sql = """
                INSERT OR REPLACE INTO executions(run_id, pipeline_name, status, started_at, finished_at, data)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.runId());
            ps.setString(2, record.pipelineName());
            ps.setString(3, record.status() != null ? record.status().name() : null);
            ps.setString(4, record.startedAt() != null ? record.startedAt().toString() : null);
            ps.setString(5, record.finishedAt() != null ? record.finishedAt().toString() : null);
            ps.setString(6, mapper.writeValueAsString(record));
            ps.executeUpdate();
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Failed to save execution record: " + record.runId(), e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<ExecutionRecord> getRecent(int limit) {
        String sql = "SELECT data FROM executions ORDER BY started_at DESC LIMIT ?";
        List<ExecutionRecord> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.readValue(rs.getString("data"), ExecutionRecord.class));
                }
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Failed to retrieve recent execution records", e);
        }
        return results;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<ExecutionRecord> getRun(String runId) {
        String sql = "SELECT data FROM executions WHERE run_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapper.readValue(rs.getString("data"), ExecutionRecord.class));
                }
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Failed to retrieve execution record: " + runId, e);
        }
        return Optional.empty();
    }

    /**
     * Creates the executions table if it does not already exist.
     */
    private void initializeDb() {
        try {
            Path parent = Path.of(dbPath).getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create database directory", e);
        }

        String sql = """
                CREATE TABLE IF NOT EXISTS executions(
                    run_id TEXT PRIMARY KEY,
                    pipeline_name TEXT,
                    status TEXT,
                    started_at TEXT,
                    finished_at TEXT,
                    data TEXT
                )
                """;
        try (Connection conn = getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Returns a new JDBC connection to the SQLite database.
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }
}
