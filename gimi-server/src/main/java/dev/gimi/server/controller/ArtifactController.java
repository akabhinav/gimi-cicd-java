package dev.gimi.server.controller;

import dev.gimi.core.model.ArtifactRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * REST controller for artifact management operations.
 */
@RestController
@RequestMapping("/api/artifacts")
public class ArtifactController {

    private static final Logger log = LoggerFactory.getLogger(ArtifactController.class);

    private final DataSource dataSource;

    public ArtifactController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * List artifacts for a specific run.
     */
    @GetMapping("/{runId}")
    public ResponseEntity<List<Map<String, Object>>> listArtifacts(@PathVariable String runId) {
        String sql = "SELECT id, run_id, stage_name, step_name, path, content_type, size_bytes, " +
                     "checksum, storage_key, created_at FROM artifacts WHERE run_id = ? ORDER BY created_at ASC";

        List<Map<String, Object>> artifacts = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> artifact = new LinkedHashMap<>();
                    artifact.put("id", rs.getString("id"));
                    artifact.put("runId", rs.getString("run_id"));
                    artifact.put("stageName", rs.getString("stage_name"));
                    artifact.put("stepName", rs.getString("step_name"));
                    artifact.put("path", rs.getString("path"));
                    artifact.put("contentType", rs.getString("content_type"));
                    artifact.put("sizeBytes", rs.getLong("size_bytes"));
                    artifact.put("checksum", rs.getString("checksum"));
                    artifact.put("storageKey", rs.getString("storage_key"));
                    artifact.put("createdAt", rs.getTimestamp("created_at") != null
                            ? rs.getTimestamp("created_at").toInstant() : null);
                    artifacts.add(artifact);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to list artifacts for run {}: {}", runId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok(artifacts);
    }

    /**
     * Download an artifact by its storage key.
     */
    @GetMapping("/download/{storageKey}")
    public ResponseEntity<byte[]> downloadArtifact(@PathVariable String storageKey) {
        String sql = "SELECT path, content_type, data FROM artifacts WHERE storage_key = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, storageKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return ResponseEntity.notFound().build();
                }

                String contentType = rs.getString("content_type");
                byte[] data = rs.getBytes("data");
                String path = rs.getString("path");

                if (data == null) {
                    return ResponseEntity.status(HttpStatus.GONE)
                            .body(null);
                }

                String filename = path != null ? path.substring(path.lastIndexOf('/') + 1) : storageKey;

                return ResponseEntity.ok()
                        .contentType(contentType != null
                                ? MediaType.parseMediaType(contentType)
                                : MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                        .body(data);
            }
        } catch (SQLException e) {
            log.error("Failed to download artifact {}: {}", storageKey, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Upload an artifact for a specific run, stage, and step.
     */
    @PostMapping("/{runId}/{stageName}/{stepName}")
    public ResponseEntity<Map<String, Object>> uploadArtifact(
            @PathVariable String runId,
            @PathVariable String stageName,
            @PathVariable String stepName,
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is empty"));
        }

        String id = UUID.randomUUID().toString();
        String storageKey = runId + "/" + stageName + "/" + stepName + "/" + file.getOriginalFilename();
        Instant now = Instant.now();

        String sql = "INSERT INTO artifacts (id, run_id, stage_name, step_name, path, content_type, " +
                     "size_bytes, storage_key, data, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, runId);
            ps.setString(3, stageName);
            ps.setString(4, stepName);
            ps.setString(5, file.getOriginalFilename());
            ps.setString(6, file.getContentType());
            ps.setLong(7, file.getSize());
            ps.setString(8, storageKey);
            ps.setBytes(9, file.getBytes());
            ps.setTimestamp(10, Timestamp.from(now));
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to upload artifact for run {}: {}", runId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload artifact"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("storageKey", storageKey);
        response.put("path", file.getOriginalFilename());
        response.put("sizeBytes", file.getSize());
        response.put("contentType", file.getContentType());
        response.put("createdAt", now);

        log.info("Artifact uploaded: id={}, run={}, stage={}, step={}", id, runId, stageName, stepName);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
