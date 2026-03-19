package dev.gimi.engine.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gimi.core.model.governance.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * OPA Policy Engine that can work in two modes:
 * 1. Embedded mode: evaluates policies using built-in rule engine
 * 2. Remote OPA mode: delegates to OPA server via REST API
 */
public class OpaPolicyEngine implements PolicyEngine {
    private static final Logger log = LoggerFactory.getLogger(OpaPolicyEngine.class);

    private final Map<String, Policy> policies = new ConcurrentHashMap<>();
    private final Map<String, PolicySet> policySets = new ConcurrentHashMap<>();
    private final Map<String, List<PolicyEvaluation>> evaluationHistory = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final String opaUrl; // null = embedded mode
    private final HttpClient httpClient;

    public OpaPolicyEngine(ObjectMapper mapper, String opaUrl) {
        this.mapper = mapper;
        this.opaUrl = opaUrl;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public OpaPolicyEngine(ObjectMapper mapper) {
        this(mapper, null);
    }

    @Override
    public PolicyEvaluation evaluate(Policy policy, Map<String, Object> input) {
        String evalId = UUID.randomUUID().toString();
        try {
            if (opaUrl != null) {
                return evaluateRemote(evalId, policy, input);
            } else {
                return evaluateEmbedded(evalId, policy, input);
            }
        } catch (Exception e) {
            log.error("Policy evaluation failed: {} - {}", policy.name(), e.getMessage());
            return new PolicyEvaluation(evalId, policy.id(), policy.name(), policy.action(),
                false, "Evaluation error: " + e.getMessage(), List.of(), input, Instant.now());
        }
    }

    @SuppressWarnings("unchecked")
    private PolicyEvaluation evaluateRemote(String evalId, Policy policy, Map<String, Object> input) throws Exception {
        Map<String, Object> requestBody = Map.of("input", input, "policy", policy.rego());
        String json = mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(opaUrl + "/v1/data/" + policy.id().replace(".", "/")))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> result = mapper.readValue(response.body(), Map.class);

        boolean passed = Boolean.TRUE.equals(((Map<String, Object>) result.getOrDefault("result", Map.of())).get("allow"));
        List<String> violations = (List<String>) ((Map<String, Object>) result.getOrDefault("result", Map.of()))
            .getOrDefault("violations", List.of());

        List<PolicyViolation> policyViolations = violations.stream()
            .map(v -> new PolicyViolation(policy.name(), v, policy.action().name(), ""))
            .toList();

        return new PolicyEvaluation(evalId, policy.id(), policy.name(), policy.action(),
            passed, passed ? "Policy passed" : "Policy violated", policyViolations, input, Instant.now());
    }

    private PolicyEvaluation evaluateEmbedded(String evalId, Policy policy, Map<String, Object> input) {
        // Built-in rule engine for common policy patterns
        List<PolicyViolation> violations = new ArrayList<>();
        String rego = policy.rego();

        // Parse simple deny rules from Rego-like syntax
        // Support patterns like: deny[msg] { condition }
        if (rego.contains("deny") || rego.contains("violation")) {
            violations.addAll(evaluateRegoRules(policy, input));
        }

        boolean passed = violations.isEmpty();
        String message = passed ? "All rules passed" : violations.size() + " violation(s) found";

        PolicyEvaluation eval = new PolicyEvaluation(evalId, policy.id(), policy.name(),
            policy.action(), passed, message, violations, input, Instant.now());

        evaluationHistory.computeIfAbsent(policy.id(), k -> new ArrayList<>()).add(eval);
        return eval;
    }

    @SuppressWarnings("unchecked")
    private List<PolicyViolation> evaluateRegoRules(Policy policy, Map<String, Object> input) {
        List<PolicyViolation> violations = new ArrayList<>();
        String rego = policy.rego();

        // Built-in rule patterns for common CI/CD governance
        if (rego.contains("require_approval") && input.containsKey("pipeline")) {
            Map<String, Object> pipeline = (Map<String, Object>) input.get("pipeline");
            String env = (String) input.getOrDefault("environment", "");
            if ("production".equalsIgnoreCase(env)) {
                List<Map<String, Object>> stages = (List<Map<String, Object>>) pipeline.getOrDefault("stages", List.of());
                boolean hasApproval = stages.stream().anyMatch(s -> "APPROVAL".equals(s.get("stageType")));
                if (!hasApproval) {
                    violations.add(new PolicyViolation("require_approval",
                        "Production deployments require an approval stage", "HIGH", "pipeline"));
                }
            }
        }

        if (rego.contains("no_deploy_friday") && input.containsKey("timestamp")) {
            String ts = input.get("timestamp").toString();
            try {
                var dayOfWeek = Instant.parse(ts).atZone(java.time.ZoneId.systemDefault()).getDayOfWeek();
                if (dayOfWeek == java.time.DayOfWeek.FRIDAY || dayOfWeek == java.time.DayOfWeek.SATURDAY
                    || dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                    violations.add(new PolicyViolation("no_deploy_friday",
                        "Deployments not allowed on weekends/Fridays", "MEDIUM", "schedule"));
                }
            } catch (Exception ignored) {}
        }

        if (rego.contains("max_parallel_stages")) {
            Map<String, Object> pipeline = (Map<String, Object>) input.getOrDefault("pipeline", Map.of());
            List<Map<String, Object>> stages = (List<Map<String, Object>>) pipeline.getOrDefault("stages", List.of());
            long parallelCount = stages.stream().filter(s -> s.containsKey("parallelWith")).count();
            if (parallelCount > 5) {
                violations.add(new PolicyViolation("max_parallel_stages",
                    "Maximum 5 parallel stages allowed, found " + parallelCount, "MEDIUM", "pipeline"));
            }
        }

        if (rego.contains("require_timeout")) {
            Map<String, Object> pipeline = (Map<String, Object>) input.getOrDefault("pipeline", Map.of());
            List<Map<String, Object>> stages = (List<Map<String, Object>>) pipeline.getOrDefault("stages", List.of());
            for (Map<String, Object> stage : stages) {
                if (!stage.containsKey("timeout") || stage.get("timeout") == null) {
                    violations.add(new PolicyViolation("require_timeout",
                        "Stage '" + stage.getOrDefault("name", "unknown") + "' must have a timeout",
                        "LOW", "stage:" + stage.getOrDefault("name", "")));
                }
            }
        }

        if (rego.contains("no_latest_tag")) {
            Map<String, Object> pipeline = (Map<String, Object>) input.getOrDefault("pipeline", Map.of());
            List<Map<String, Object>> stages = (List<Map<String, Object>>) pipeline.getOrDefault("stages", List.of());
            for (Map<String, Object> stage : stages) {
                List<Map<String, Object>> steps = (List<Map<String, Object>>) stage.getOrDefault("steps", List.of());
                for (Map<String, Object> step : steps) {
                    String image = (String) step.getOrDefault("image", "");
                    if (image.endsWith(":latest") || (!image.isEmpty() && !image.contains(":"))) {
                        violations.add(new PolicyViolation("no_latest_tag",
                            "Docker image '" + image + "' must not use :latest tag",
                            "HIGH", "step:" + step.getOrDefault("name", "")));
                    }
                }
            }
        }

        return violations;
    }

    @Override
    public List<PolicyEvaluation> evaluateAll(PolicyEnforcementPoint point, Map<String, Object> input) {
        return policies.values().stream()
            .filter(Policy::enabled)
            .filter(p -> p.enforcementPoints().contains(point))
            .map(p -> evaluate(p, input))
            .collect(Collectors.toList());
    }

    @Override
    public Policy createPolicy(Policy policy) {
        policies.put(policy.id(), policy);
        log.info("Policy created: {} ({})", policy.name(), policy.id());
        return policy;
    }

    @Override
    public Policy updatePolicy(Policy policy) {
        policies.put(policy.id(), policy);
        return policy;
    }

    @Override
    public void deletePolicy(String id) {
        policies.remove(id);
    }

    @Override
    public Optional<Policy> getPolicy(String id) {
        return Optional.ofNullable(policies.get(id));
    }

    @Override
    public List<Policy> listPolicies() {
        return List.copyOf(policies.values());
    }

    @Override
    public List<Policy> getPoliciesForEnforcementPoint(PolicyEnforcementPoint point) {
        return policies.values().stream()
            .filter(Policy::enabled)
            .filter(p -> p.enforcementPoints().contains(point))
            .toList();
    }

    @Override
    public PolicySet createPolicySet(PolicySet set) {
        policySets.put(set.id(), set);
        return set;
    }

    @Override
    public List<PolicySet> listPolicySets() {
        return List.copyOf(policySets.values());
    }

    @Override
    public List<PolicyEvaluation> evaluatePolicySet(String policySetId, Map<String, Object> input) {
        PolicySet set = policySets.get(policySetId);
        if (set == null) return List.of();
        return set.policyIds().stream()
            .map(policies::get)
            .filter(Objects::nonNull)
            .filter(Policy::enabled)
            .map(p -> evaluate(p, input))
            .toList();
    }
}
