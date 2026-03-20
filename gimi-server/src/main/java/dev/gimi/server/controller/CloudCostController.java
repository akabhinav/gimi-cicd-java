package dev.gimi.server.controller;

import dev.gimi.core.model.cost.CloudCostRecord;
import dev.gimi.core.model.cost.CostRecommendation;
import dev.gimi.engine.cost.CloudCostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Cloud Cost Management.
 */
@RestController
@RequestMapping("/api/costs")
public class CloudCostController {

    private final CloudCostService costService;

    public CloudCostController(CloudCostService costService) {
        this.costService = costService;
    }

    @PostMapping
    public ResponseEntity<CloudCostRecord> recordCost(@RequestBody CloudCostRecord record) {
        return ResponseEntity.ok(costService.recordCost(record));
    }

    @GetMapping("/{runId}")
    public ResponseEntity<CloudCostRecord> getCost(@PathVariable String runId) {
        return costService.getCost(runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/total/{pipelineName}")
    public ResponseEntity<Map<String, Double>> getTotalCost(@PathVariable String pipelineName) {
        return ResponseEntity.ok(Map.of("totalCostUsd", costService.getTotalCost(pipelineName)));
    }

    @GetMapping("/recommendations/{pipelineName}")
    public ResponseEntity<List<CostRecommendation>> getRecommendations(@PathVariable String pipelineName) {
        return ResponseEntity.ok(costService.generateRecommendations(pipelineName));
    }
}
