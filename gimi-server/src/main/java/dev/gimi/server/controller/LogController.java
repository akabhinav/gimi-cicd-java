package dev.gimi.server.controller;

import dev.gimi.core.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST controller for pipeline execution log access and streaming.
 */
@RestController
@RequestMapping("/api/logs")
public class LogController {

    private static final Logger log = LoggerFactory.getLogger(LogController.class);

    private final DataSource dataSource;
    private final ExecutorService sseExecutor;

    public LogController(DataSource dataSource) {
        this.dataSource = dataSource;
        this.sseExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Get logs for a specific run with pagination.
     */
    @GetMapping("/{runId}")
    public ResponseEntity<Map<String, Object>> getLogs(
            @PathVariable String runId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        String countSql = "SELECT COUNT(*) FROM logs WHERE run_id = ?";
        String sql = "SELECT run_id, stage_name, step_name, level, message, timestamp, worker_id " +
                     "FROM logs WHERE run_id = ? ORDER BY timestamp ASC LIMIT ? OFFSET ?";

        long totalCount = 0;
        List<Map<String, Object>> entries = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setString(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalCount = rs.getLong(1);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, runId);
                ps.setInt(2, Math.min(limit, 1000));
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("runId", rs.getString("run_id"));
                        entry.put("stageName", rs.getString("stage_name"));
                        entry.put("stepName", rs.getString("step_name"));
                        entry.put("level", rs.getString("level"));
                        entry.put("message", rs.getString("message"));
                        entry.put("timestamp", rs.getTimestamp("timestamp") != null
                                ? rs.getTimestamp("timestamp").toInstant() : null);
                        entry.put("workerId", rs.getString("worker_id"));
                        entries.add(entry);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to fetch logs for run {}: {}", runId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("runId", runId);
        response.put("totalCount", totalCount);
        response.put("limit", limit);
        response.put("offset", offset);
        response.put("entries", entries);

        return ResponseEntity.ok(response);
    }

    /**
     * Server-Sent Events endpoint for real-time log streaming.
     */
    @GetMapping(value = "/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable String runId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout

        sseExecutor.submit(() -> {
            try {
                long lastTimestamp = 0;
                boolean running = true;

                while (running && !Thread.currentThread().isInterrupted()) {
                    String sql = "SELECT run_id, stage_name, step_name, level, message, timestamp, worker_id " +
                                 "FROM logs WHERE run_id = ? AND EXTRACT(EPOCH FROM timestamp) * 1000 > ? " +
                                 "ORDER BY timestamp ASC LIMIT 50";

                    try (Connection conn = dataSource.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, runId);
                        ps.setLong(2, lastTimestamp);

                        try (ResultSet rs = ps.executeQuery()) {
                            boolean hasData = false;
                            while (rs.next()) {
                                hasData = true;
                                Map<String, Object> entry = new LinkedHashMap<>();
                                entry.put("stageName", rs.getString("stage_name"));
                                entry.put("stepName", rs.getString("step_name"));
                                entry.put("level", rs.getString("level"));
                                entry.put("message", rs.getString("message"));
                                Instant ts = rs.getTimestamp("timestamp") != null
                                        ? rs.getTimestamp("timestamp").toInstant() : Instant.now();
                                entry.put("timestamp", ts);
                                lastTimestamp = ts.toEpochMilli();

                                emitter.send(SseEmitter.event()
                                        .name("log")
                                        .data(entry));
                            }
                            if (!hasData) {
                                emitter.send(SseEmitter.event()
                                        .name("heartbeat")
                                        .data(""));
                            }
                        }
                    }

                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.debug("SSE stream ended for run {}: {}", runId, e.getMessage());
            } finally {
                emitter.complete();
            }
        });

        emitter.onCompletion(() -> log.debug("SSE stream completed for run {}", runId));
        emitter.onTimeout(() -> log.debug("SSE stream timed out for run {}", runId));
        emitter.onError(e -> log.debug("SSE stream error for run {}: {}", runId, e.getMessage()));

        return emitter;
    }
}
