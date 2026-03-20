package dev.gimi.server.controller;

import dev.gimi.core.model.chaos.ChaosExperiment;
import dev.gimi.core.model.chaos.ChaosResult;
import dev.gimi.engine.chaos.ChaosEngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Chaos Engineering experiments.
 */
@RestController
@RequestMapping("/api/chaos")
public class ChaosController {

    private final ChaosEngineService chaosService;

    public ChaosController(ChaosEngineService chaosService) {
        this.chaosService = chaosService;
    }

    @PostMapping("/experiments")
    public ResponseEntity<ChaosResult> runExperiment(
            @RequestBody ChaosExperiment experiment,
            @RequestParam String runId) {
        return ResponseEntity.ok(chaosService.executeExperiment(experiment, runId));
    }

    @GetMapping("/experiments/{runId}/{experimentName}")
    public ResponseEntity<ChaosResult> getResult(
            @PathVariable String runId,
            @PathVariable String experimentName) {
        return chaosService.getResult(runId, experimentName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
