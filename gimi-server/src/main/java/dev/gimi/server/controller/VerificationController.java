package dev.gimi.server.controller;

import dev.gimi.core.model.verification.ContinuousVerificationConfig;
import dev.gimi.core.model.verification.VerificationResult;
import dev.gimi.engine.verification.ContinuousVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for Continuous Verification.
 */
@RestController
@RequestMapping("/api/verification")
public class VerificationController {

    private final ContinuousVerificationService cvService;

    public VerificationController(ContinuousVerificationService cvService) {
        this.cvService = cvService;
    }

    @PostMapping("/start")
    public ResponseEntity<VerificationResult> startVerification(
            @RequestBody ContinuousVerificationConfig config,
            @RequestParam String runId,
            @RequestParam String stageName) {
        return ResponseEntity.ok(cvService.startVerification(config, runId, stageName));
    }

    @PostMapping("/analyze")
    public ResponseEntity<VerificationResult> analyzeMetrics(
            @RequestParam String runId,
            @RequestParam String stageName,
            @RequestBody Map<String, Double> canaryMetrics) {
        return ResponseEntity.ok(cvService.analyze(runId, stageName, canaryMetrics));
    }

    @PostMapping("/baseline")
    public ResponseEntity<Void> recordBaseline(
            @RequestParam String analysisType,
            @RequestParam String stageName,
            @RequestBody Map<String, Double> metrics) {
        cvService.recordBaseline(analysisType, stageName, metrics);
        return ResponseEntity.ok().build();
    }
}
