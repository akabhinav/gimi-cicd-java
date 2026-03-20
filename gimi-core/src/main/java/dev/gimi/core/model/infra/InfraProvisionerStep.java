package dev.gimi.core.model.infra;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Infrastructure-as-Code provisioner step supporting Terraform, Pulumi, CloudFormation, and CDK.
 *
 * <p>First-class IaC support surpassing Harness by including drift detection,
 * cost estimation, plan approval gates, and state management.
 *
 * @param name              step name
 * @param provisioner       IaC tool type
 * @param action            provisioning action to perform
 * @param workingDir        working directory containing IaC files
 * @param varFile           path to variable file
 * @param variables         inline variables
 * @param backendConfig     backend/state storage configuration
 * @param autoApprove       skip approval for apply actions
 * @param destroyOnRollback destroy infra on pipeline rollback
 * @param connectorRef      cloud connector reference for auth
 * @param enableDriftDetect enable infrastructure drift detection
 * @param enableCostEstimate enable cost estimation before apply
 */
public record InfraProvisionerStep(
        String name,
        ProvisionerType provisioner,
        ProvisionerAction action,
        @JsonProperty("working_dir") String workingDir,
        @JsonProperty("var_file") String varFile,
        Map<String, String> variables,
        @JsonProperty("backend_config") Map<String, String> backendConfig,
        @JsonProperty("auto_approve") boolean autoApprove,
        @JsonProperty("destroy_on_rollback") boolean destroyOnRollback,
        @JsonProperty("connector_ref") String connectorRef,
        @JsonProperty("enable_drift_detect") boolean enableDriftDetect,
        @JsonProperty("enable_cost_estimate") boolean enableCostEstimate
) {
    public InfraProvisionerStep {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        backendConfig = backendConfig == null ? Map.of() : Map.copyOf(backendConfig);
        provisioner = provisioner == null ? ProvisionerType.TERRAFORM : provisioner;
        action = action == null ? ProvisionerAction.PLAN : action;
    }
}
