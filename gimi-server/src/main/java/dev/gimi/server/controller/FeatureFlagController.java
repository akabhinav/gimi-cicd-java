package dev.gimi.server.controller;

import dev.gimi.core.model.ff.FeatureFlag;
import dev.gimi.engine.ff.FeatureFlagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for feature flag management.
 */
@RestController
@RequestMapping("/api/feature-flags")
public class FeatureFlagController {

    private final FeatureFlagService ffService;

    public FeatureFlagController(FeatureFlagService ffService) {
        this.ffService = ffService;
    }

    @PostMapping
    public ResponseEntity<FeatureFlag> createFlag(@RequestBody FeatureFlag flag) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ffService.createFlag(flag));
    }

    @GetMapping
    public ResponseEntity<List<FeatureFlag>> listFlags() {
        return ResponseEntity.ok(ffService.listFlags());
    }

    @GetMapping("/{key}")
    public ResponseEntity<FeatureFlag> getFlag(@PathVariable String key) {
        return ffService.getFlag(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{key}")
    public ResponseEntity<FeatureFlag> updateFlag(@PathVariable String key, @RequestBody FeatureFlag flag) {
        return ResponseEntity.ok(ffService.updateFlag(flag));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> deleteFlag(@PathVariable String key) {
        ffService.deleteFlag(key);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{key}/evaluate")
    public ResponseEntity<Map<String, String>> evaluate(
            @PathVariable String key,
            @RequestParam(defaultValue = "default") String environment,
            @RequestBody Map<String, String> context) {
        String value = ffService.evaluate(key, environment, context);
        return ResponseEntity.ok(Map.of("key", key, "value", value != null ? value : ""));
    }
}
