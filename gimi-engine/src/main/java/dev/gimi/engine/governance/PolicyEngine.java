package dev.gimi.engine.governance;

import dev.gimi.core.model.governance.*;
import java.util.*;

public interface PolicyEngine {
    PolicyEvaluation evaluate(Policy policy, Map<String, Object> input);
    List<PolicyEvaluation> evaluateAll(PolicyEnforcementPoint point, Map<String, Object> input);
    Policy createPolicy(Policy policy);
    Policy updatePolicy(Policy policy);
    void deletePolicy(String id);
    Optional<Policy> getPolicy(String id);
    List<Policy> listPolicies();
    List<Policy> getPoliciesForEnforcementPoint(PolicyEnforcementPoint point);

    // Policy Sets
    PolicySet createPolicySet(PolicySet set);
    List<PolicySet> listPolicySets();
    List<PolicyEvaluation> evaluatePolicySet(String policySetId, Map<String, Object> input);
}
