package dev.gimi.core.model.iac;

import java.util.Map;

/**
 * Request to execute an IaC operation on a workspace.
 */
public record IacRunRequest(
        String workspaceId,
        IacAction action,
        Map<String, String> variableOverrides,
        String commitSha,
        boolean autoApprove,
        String triggeredBy
) {
    public IacRunRequest {
        variableOverrides = variableOverrides == null ? Map.of() : Map.copyOf(variableOverrides);
        action = action == null ? IacAction.PLAN : action;
    }
}
