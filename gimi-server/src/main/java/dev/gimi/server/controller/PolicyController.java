package dev.gimi.server.controller;

import dev.gimi.core.model.governance.*;
import dev.gimi.engine.governance.PolicyEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyEngine policyEngine;

    public PolicyController(PolicyEngine policyEngine) {
        this.policyEngine = policyEngine;
    }

    @GetMapping
    public ResponseEntity<List<Policy>> listPolicies() {
        return ResponseEntity.ok(policyEngine.listPolicies());
    }

    @PostMapping
    public ResponseEntity<Policy> createPolicy(@RequestBody Policy policy) {
        return ResponseEntity.ok(policyEngine.createPolicy(policy));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Policy> getPolicy(@PathVariable String id) {
        return policyEngine.getPolicy(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Policy> updatePolicy(@PathVariable String id, @RequestBody Policy policy) {
        return ResponseEntity.ok(policyEngine.updatePolicy(policy));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePolicy(@PathVariable String id) {
        policyEngine.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/evaluate")
    public ResponseEntity<List<PolicyEvaluation>> evaluateAtPoint(
            @RequestParam String enforcementPoint,
            @RequestBody Map<String, Object> input) {
        PolicyEnforcementPoint point = PolicyEnforcementPoint.valueOf(enforcementPoint.toUpperCase());
        List<PolicyEvaluation> results = policyEngine.evaluateAll(point, input);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/{id}/evaluate")
    public ResponseEntity<PolicyEvaluation> evaluateSingle(
            @PathVariable String id, @RequestBody Map<String, Object> input) {
        Policy policy = policyEngine.getPolicy(id).orElse(null);
        if (policy == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(policyEngine.evaluate(policy, input));
    }

    // --- Policy Sets ---
    @GetMapping("/sets")
    public ResponseEntity<List<PolicySet>> listPolicySets() {
        return ResponseEntity.ok(policyEngine.listPolicySets());
    }

    @PostMapping("/sets")
    public ResponseEntity<PolicySet> createPolicySet(@RequestBody PolicySet set) {
        return ResponseEntity.ok(policyEngine.createPolicySet(set));
    }

    @PostMapping("/sets/{id}/evaluate")
    public ResponseEntity<List<PolicyEvaluation>> evaluatePolicySet(
            @PathVariable String id, @RequestBody Map<String, Object> input) {
        return ResponseEntity.ok(policyEngine.evaluatePolicySet(id, input));
    }
}
