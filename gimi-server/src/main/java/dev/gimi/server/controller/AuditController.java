package dev.gimi.server.controller;

import dev.gimi.core.model.audit.AuditCategory;
import dev.gimi.core.model.audit.AuditEvent;
import dev.gimi.core.model.tenant.ResourceScope;
import dev.gimi.engine.audit.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<AuditEvent>> queryAuditEvents(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        ResourceScope scope = accountId != null ?
            ResourceScope.project(accountId, organizationId, projectId) : null;
        AuditCategory cat = category != null ? AuditCategory.valueOf(category.toUpperCase()) : null;
        Instant fromTs = from != null ? Instant.parse(from) : null;
        Instant toTs = to != null ? Instant.parse(to) : null;

        var query = new AuditService.AuditQuery(scope, userId, resourceType, resourceId, cat, fromTs, toTs, limit, offset);
        return ResponseEntity.ok(auditService.query(query));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> countAuditEvents(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String category) {
        ResourceScope scope = accountId != null ? ResourceScope.account(accountId) : null;
        AuditCategory cat = category != null ? AuditCategory.valueOf(category.toUpperCase()) : null;
        var query = new AuditService.AuditQuery(scope, null, null, null, cat, null, null, 0, 0);
        return ResponseEntity.ok(Map.of("count", auditService.count(query)));
    }
}
