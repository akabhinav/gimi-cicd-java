package dev.gimi.core.model.gitops;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Target cluster configuration for GitOps deployments.
 *
 * @param name          cluster display name
 * @param server        cluster API server URL
 * @param namespace     default namespace
 * @param environment   associated environment name
 * @param connectorRef  connector for cluster authentication
 */
public record ClusterConfig(
        String name,
        String server,
        String namespace,
        String environment,
        @JsonProperty("connector_ref") String connectorRef
) {}
