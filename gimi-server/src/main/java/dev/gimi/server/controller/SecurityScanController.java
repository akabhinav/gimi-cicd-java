package dev.gimi.server.controller;

import dev.gimi.core.model.security.SecurityScanConfig;
import dev.gimi.core.model.security.SecurityScanResult;
import dev.gimi.engine.security.SecurityScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Security Testing Orchestration (STO).
 */
@RestController
@RequestMapping("/api/security")
public class SecurityScanController {

    private final SecurityScanService scanService;

    public SecurityScanController(SecurityScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping("/scan")
    public ResponseEntity<SecurityScanResult> runScan(
            @RequestBody SecurityScanConfig config,
            @RequestParam String runId) {
        return ResponseEntity.ok(scanService.executeScan(config, runId));
    }
}
