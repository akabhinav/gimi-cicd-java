package dev.gimi.server.controller;

import dev.gimi.core.model.slo.ServiceLevelObjective;
import dev.gimi.core.model.slo.SloStatus;
import dev.gimi.engine.slo.SloService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Service Level Objective management.
 */
@RestController
@RequestMapping("/api/slo")
public class SloController {

    private final SloService sloService;

    public SloController(SloService sloService) {
        this.sloService = sloService;
    }

    @PostMapping
    public ResponseEntity<ServiceLevelObjective> createSlo(@RequestBody ServiceLevelObjective slo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sloService.createSlo(slo));
    }

    @GetMapping
    public ResponseEntity<List<ServiceLevelObjective>> listSlos() {
        return ResponseEntity.ok(sloService.listSlos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceLevelObjective> getSlo(@PathVariable String id) {
        return sloService.getSlo(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<SloStatus> getSloStatus(@PathVariable String id) {
        return sloService.getStatus(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<SloStatus> updateSloStatus(@PathVariable String id,
                                                      @RequestBody Map<String, Double> body) {
        double current = body.getOrDefault("currentPercentage", 0.0);
        SloStatus status = sloService.updateStatus(id, current);
        return status != null ? ResponseEntity.ok(status) : ResponseEntity.notFound().build();
    }

    @GetMapping("/gate-check")
    public ResponseEntity<Map<String, Object>> checkDeploymentGate(
            @RequestParam String serviceName,
            @RequestParam(defaultValue = "") String environment) {
        boolean allowed = sloService.isDeploymentAllowed(serviceName, environment);
        return ResponseEntity.ok(Map.of("allowed", allowed, "service", serviceName));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSlo(@PathVariable String id) {
        sloService.deleteSlo(id);
        return ResponseEntity.noContent().build();
    }
}
