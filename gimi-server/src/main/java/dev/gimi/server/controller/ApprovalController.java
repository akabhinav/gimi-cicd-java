package dev.gimi.server.controller;

import dev.gimi.core.model.ApprovalRequest;
import dev.gimi.core.model.ApprovalStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * REST controller for approval management operations.
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private static final Logger log = LoggerFactory.getLogger(ApprovalController.class);

    private final DataSource dataSource;

    public ApprovalController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * List all pending approval requests.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<Map<String, Object>>> listPending() {
        String sql = "SELECT id, run_id, pipeline_name, stage_name, status, requested_at, comment " +
                     "FROM approval_requests WHERE status = 'PENDING' ORDER BY requested_at ASC";

        List<Map<String, Object>> approvals = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> approval = new LinkedHashMap<>();
                approval.put("id", rs.getString("id"));
                approval.put("runId", rs.getString("run_id"));
                approval.put("pipelineName", rs.getString("pipeline_name"));
                approval.put("stageName", rs.getString("stage_name"));
                approval.put("status", rs.getString("status"));
                approval.put("requestedAt", rs.getTimestamp("requested_at") != null
                        ? rs.getTimestamp("requested_at").toInstant() : null);
                approval.put("comment", rs.getString("comment"));
                approvals.add(approval);
            }
        } catch (SQLException e) {
            log.error("Failed to list pending approvals: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok(approvals);
    }

    /**
     * Approve a pending approval request.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {

        return resolveApproval(id, ApprovalStatus.APPROVED, body);
    }

    /**
     * Reject a pending approval request.
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {

        return resolveApproval(id, ApprovalStatus.REJECTED, body);
    }

    private ResponseEntity<Map<String, Object>> resolveApproval(
            String id, ApprovalStatus newStatus, Map<String, String> body) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String resolvedBy = auth != null ? auth.getName() : "unknown";
        String comment = body != null ? body.getOrDefault("comment", "") : "";

        String checkSql = "SELECT status FROM approval_requests WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "Approval request not found: " + id));
                }
                String currentStatus = rs.getString("status");
                if (!"PENDING".equals(currentStatus)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "Approval request already resolved: " + currentStatus));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to check approval {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Database error"));
        }

        String updateSql = "UPDATE approval_requests SET status = ?, resolved_at = ?, resolved_by = ?, comment = ? " +
                            "WHERE id = ? AND status = 'PENDING'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, newStatus.name());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, resolvedBy);
            ps.setString(4, comment);
            ps.setString(5, id);
            int updated = ps.executeUpdate();

            if (updated == 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Approval was already resolved by another user"));
            }
        } catch (SQLException e) {
            log.error("Failed to resolve approval {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to resolve approval"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("status", newStatus.name());
        response.put("resolvedBy", resolvedBy);
        response.put("comment", comment);
        response.put("resolvedAt", Instant.now());

        log.info("Approval {} {} by {}", id, newStatus.name().toLowerCase(), resolvedBy);
        return ResponseEntity.ok(response);
    }
}
