package dev.gimi.server.controller;

import dev.gimi.core.model.analytics.DoraMetrics;
import dev.gimi.core.model.analytics.PipelineAnalytics;
import dev.gimi.engine.analytics.DoraMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * REST controller for DORA metrics and pipeline analytics.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final DoraMetricsService doraService;

    public AnalyticsController(DoraMetricsService doraService) {
        this.doraService = doraService;
    }

    @GetMapping("/dora/{pipelineName}")
    public ResponseEntity<DoraMetrics> getDoraMetrics(
            @PathVariable String pipelineName,
            @RequestParam(defaultValue = "") String environment,
            @RequestParam(defaultValue = "30") int days) {
        Instant end = Instant.now();
        Instant start = end.minus(days, ChronoUnit.DAYS);
        return ResponseEntity.ok(doraService.computeDoraMetrics(pipelineName, environment, start, end));
    }

    @GetMapping("/pipeline/{pipelineName}")
    public ResponseEntity<PipelineAnalytics> getPipelineAnalytics(
            @PathVariable String pipelineName,
            @RequestParam(defaultValue = "30") int days) {
        Instant end = Instant.now();
        Instant start = end.minus(days, ChronoUnit.DAYS);
        return ResponseEntity.ok(doraService.computeAnalytics(pipelineName, start, end));
    }
}
