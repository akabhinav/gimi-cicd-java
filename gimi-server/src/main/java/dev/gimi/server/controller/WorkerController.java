package dev.gimi.server.controller;

import dev.gimi.core.model.WorkerNode;
import dev.gimi.core.model.WorkerStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * REST controller for worker node management.
 */
@RestController
@RequestMapping("/api/workers")
public class WorkerController {

    private static final Logger log = LoggerFactory.getLogger(WorkerController.class);

    private final DataSource dataSource;

    public WorkerController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * List all registered worker nodes.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listWorkers() {
        String sql = "SELECT id, hostname, port, status, max_concurrent_jobs, active_jobs, " +
                     "labels, last_heartbeat, registered_at, version FROM workers ORDER BY registered_at DESC";

        List<Map<String, Object>> workers = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> worker = new LinkedHashMap<>();
                worker.put("id", rs.getString("id"));
                worker.put("hostname", rs.getString("hostname"));
                worker.put("port", rs.getInt("port"));
                worker.put("status", rs.getString("status"));
                worker.put("maxConcurrentJobs", rs.getInt("max_concurrent_jobs"));
                worker.put("activeJobs", rs.getInt("active_jobs"));
                worker.put("labels", rs.getString("labels"));
                worker.put("lastHeartbeat", rs.getTimestamp("last_heartbeat") != null
                        ? rs.getTimestamp("last_heartbeat").toInstant() : null);
                worker.put("registeredAt", rs.getTimestamp("registered_at") != null
                        ? rs.getTimestamp("registered_at").toInstant() : null);
                worker.put("version", rs.getString("version"));
                workers.add(worker);
            }
        } catch (SQLException e) {
            log.error("Failed to list workers: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok(workers);
    }

    /**
     * Register a new worker node.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerWorker(@Valid @RequestBody RegisterWorkerRequest request) {
        String id = request.id() != null ? request.id() : UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        String labelsJson = "[]";
        if (request.labels() != null && !request.labels().isEmpty()) {
            labelsJson = "[" + request.labels().stream().map(l -> "\"" + l + "\"").collect(java.util.stream.Collectors.joining(",")) + "]";
        }

        String sql = "INSERT INTO workers (id, hostname, port, status, max_concurrent_jobs, active_jobs, " +
                     "labels, last_heartbeat, registered_at, version) " +
                     "VALUES (?, ?, ?, ?, ?, 0, ?::jsonb, ?, ?, ?) " +
                     "ON CONFLICT (id) DO UPDATE SET hostname = EXCLUDED.hostname, port = EXCLUDED.port, " +
                     "status = 'ONLINE', last_heartbeat = EXCLUDED.last_heartbeat, version = EXCLUDED.version";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, request.hostname());
            ps.setInt(3, request.port());
            ps.setString(4, WorkerStatus.ONLINE.name());
            ps.setInt(5, request.maxConcurrentJobs() > 0 ? request.maxConcurrentJobs() : 4);
            ps.setString(6, labelsJson);
            ps.setTimestamp(7, Timestamp.from(now));
            ps.setTimestamp(8, Timestamp.from(now));
            ps.setString(9, request.version());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to register worker: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to register worker"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("hostname", request.hostname());
        response.put("status", "ONLINE");
        response.put("registeredAt", now);

        log.info("Worker registered: id={}, hostname={}", id, request.hostname());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Worker heartbeat endpoint.
     */
    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {

        int activeJobs = 0;
        if (body != null && body.containsKey("activeJobs")) {
            activeJobs = ((Number) body.get("activeJobs")).intValue();
        }

        String sql = "UPDATE workers SET last_heartbeat = ?, active_jobs = ?, status = 'ONLINE' WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setInt(2, activeJobs);
            ps.setString(3, id);
            int updated = ps.executeUpdate();

            if (updated == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Worker not found: " + id));
            }
        } catch (SQLException e) {
            log.error("Failed to process heartbeat for worker {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Heartbeat processing failed"));
        }

        return ResponseEntity.ok(Map.of("status", "ok", "workerId", id));
    }

    /**
     * Drain a worker (stop accepting new jobs, finish current ones).
     */
    @PostMapping("/{id}/drain")
    public ResponseEntity<Map<String, Object>> drainWorker(@PathVariable String id) {
        String sql = "UPDATE workers SET status = 'DRAINING' WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            int updated = ps.executeUpdate();

            if (updated == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Worker not found: " + id));
            }
        } catch (SQLException e) {
            log.error("Failed to drain worker {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to drain worker"));
        }

        log.info("Worker {} set to draining", id);
        return ResponseEntity.ok(Map.of("id", id, "status", "DRAINING"));
    }

    /**
     * Deregister (remove) a worker node.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deregisterWorker(@PathVariable String id) {
        String sql = "DELETE FROM workers WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            int deleted = ps.executeUpdate();

            if (deleted == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Worker not found: " + id));
            }
        } catch (SQLException e) {
            log.error("Failed to deregister worker {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to deregister worker"));
        }

        log.info("Worker {} deregistered", id);
        return ResponseEntity.ok(Map.of("id", id, "status", "DEREGISTERED"));
    }

    public record RegisterWorkerRequest(
            String id,
            @NotBlank String hostname,
            int port,
            int maxConcurrentJobs,
            Set<String> labels,
            String version
    ) {}
}
