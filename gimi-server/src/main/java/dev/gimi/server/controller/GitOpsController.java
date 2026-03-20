package dev.gimi.server.controller;

import dev.gimi.core.model.gitops.GitOpsConfig;
import dev.gimi.core.model.gitops.GitOpsSyncResult;
import dev.gimi.engine.gitops.GitOpsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for GitOps deployment management.
 */
@RestController
@RequestMapping("/api/gitops")
public class GitOpsController {

    private final GitOpsService gitOpsService;

    public GitOpsController(GitOpsService gitOpsService) {
        this.gitOpsService = gitOpsService;
    }

    @PostMapping("/configs/{name}")
    public ResponseEntity<GitOpsConfig> registerConfig(@PathVariable String name,
                                                        @RequestBody GitOpsConfig config) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gitOpsService.registerConfig(name, config));
    }

    @GetMapping("/configs")
    public ResponseEntity<List<GitOpsConfig>> listConfigs() {
        return ResponseEntity.ok(gitOpsService.listConfigs());
    }

    @GetMapping("/configs/{name}")
    public ResponseEntity<GitOpsConfig> getConfig(@PathVariable String name) {
        return gitOpsService.getConfig(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/sync/{configName}")
    public ResponseEntity<List<GitOpsSyncResult>> triggerSync(
            @PathVariable String configName,
            @RequestBody Map<String, String> body) {
        String commitSha = body.getOrDefault("commitSha", "HEAD");
        return ResponseEntity.ok(gitOpsService.triggerSync(configName, commitSha));
    }

    @GetMapping("/drift/{configName}")
    public ResponseEntity<List<GitOpsSyncResult>> detectDrift(@PathVariable String configName) {
        return ResponseEntity.ok(gitOpsService.detectDrift(configName));
    }
}
