package dev.gimi.engine.history;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.gimi.core.execution.ExecutionRecord;
import dev.gimi.core.execution.ExecutionStatus;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for PostgresExecutionStore using Testcontainers.
 */
@Testcontainers
class PostgresExecutionStoreIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("gimi_test")
            .withUsername("test")
            .withPassword("test");

    private static HikariDataSource dataSource;
    private PostgresExecutionStore store;

    @BeforeAll
    static void setupDataSource() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(config);

        // Create schema
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE executions (
                    run_id        TEXT        PRIMARY KEY,
                    pipeline_name TEXT        NOT NULL,
                    status        TEXT        NOT NULL,
                    trigger       TEXT,
                    environment   TEXT,
                    started_at    TIMESTAMPTZ,
                    finished_at   TIMESTAMPTZ,
                    data          JSONB       NOT NULL DEFAULT '{}'::jsonb
                )
            """);
        }
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        store = new PostgresExecutionStore(dataSource);
        // Clean table between tests
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM executions");
        }
    }

    @Test
    void saveAndRetrieve() {
        ExecutionRecord record = new ExecutionRecord.Builder("run-1", "build-pipeline")
                .status(ExecutionStatus.PASSED)
                .startedAt(Instant.parse("2024-01-01T00:00:00Z"))
                .finishedAt(Instant.parse("2024-01-01T00:05:00Z"))
                .trigger("manual")
                .environment("staging")
                .build();

        store.save(record);

        Optional<ExecutionRecord> result = store.getRun("run-1");
        assertThat(result).isPresent();
        assertThat(result.get().runId()).isEqualTo("run-1");
        assertThat(result.get().pipelineName()).isEqualTo("build-pipeline");
        assertThat(result.get().status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(result.get().trigger()).isEqualTo("manual");
    }

    @Test
    void upsertUpdatesExistingRecord() {
        ExecutionRecord initial = new ExecutionRecord.Builder("run-2", "deploy")
                .status(ExecutionStatus.RUNNING)
                .startedAt(Instant.parse("2024-01-01T00:00:00Z"))
                .build();
        store.save(initial);

        ExecutionRecord updated = new ExecutionRecord.Builder("run-2", "deploy")
                .status(ExecutionStatus.PASSED)
                .startedAt(Instant.parse("2024-01-01T00:00:00Z"))
                .finishedAt(Instant.parse("2024-01-01T00:10:00Z"))
                .build();
        store.save(updated);

        Optional<ExecutionRecord> result = store.getRun("run-2");
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(ExecutionStatus.PASSED);
        assertThat(result.get().finishedAt()).isNotNull();
    }

    @Test
    void getRecentReturnsOrderedResults() {
        for (int i = 0; i < 5; i++) {
            store.save(new ExecutionRecord.Builder("run-" + i, "pipeline")
                    .status(ExecutionStatus.PASSED)
                    .startedAt(Instant.parse("2024-01-0" + (i + 1) + "T00:00:00Z"))
                    .build());
        }

        List<ExecutionRecord> recent = store.getRecent(3);
        assertThat(recent).hasSize(3);
        // Should be ordered by started_at DESC
        assertThat(recent.get(0).runId()).isEqualTo("run-4");
        assertThat(recent.get(2).runId()).isEqualTo("run-2");
    }

    @Test
    void getRunReturnsEmptyForMissing() {
        Optional<ExecutionRecord> result = store.getRun("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void getByPipelineFiltersCorrectly() {
        store.save(new ExecutionRecord.Builder("r1", "build")
                .status(ExecutionStatus.PASSED)
                .startedAt(Instant.now())
                .build());
        store.save(new ExecutionRecord.Builder("r2", "deploy")
                .status(ExecutionStatus.PASSED)
                .startedAt(Instant.now())
                .build());
        store.save(new ExecutionRecord.Builder("r3", "build")
                .status(ExecutionStatus.FAILED)
                .startedAt(Instant.now())
                .build());

        List<ExecutionRecord> results = store.getByPipeline("build", 10);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.pipelineName().equals("build"));
    }
}
