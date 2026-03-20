package dev.gimi.core.model.gitops;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * GitOps configuration for declarative deployment management via ArgoCD, Flux, or native.
 *
 * <p>Surpasses Harness GitOps by offering first-class integration with ArgoCD, Flux,
 * and a native GitOps engine — with PR-based promotion, drift reconciliation, and
 * multi-cluster sync.
 *
 * @param enabled          whether GitOps mode is enabled
 * @param provider         GitOps provider
 * @param repoUrl          the Git repository URL for manifests
 * @param branch           the branch to sync from
 * @param path             the path within the repo containing manifests
 * @param syncPolicy       how to handle sync
 * @param clusters         target cluster configurations
 * @param prPromotion      whether to use PR-based environment promotion
 * @param driftDetection   whether to enable automatic drift detection
 * @param reconcileInterval drift reconciliation interval (e.g., "5m")
 * @param connectorRef     connector for Git/cluster authentication
 */
public record GitOpsConfig(
        boolean enabled,
        GitOpsProvider provider,
        @JsonProperty("repo_url") String repoUrl,
        String branch,
        String path,
        @JsonProperty("sync_policy") SyncPolicy syncPolicy,
        List<ClusterConfig> clusters,
        @JsonProperty("pr_promotion") boolean prPromotion,
        @JsonProperty("drift_detection") boolean driftDetection,
        @JsonProperty("reconcile_interval") String reconcileInterval,
        @JsonProperty("connector_ref") String connectorRef
) {
    public GitOpsConfig {
        clusters = clusters == null ? List.of() : List.copyOf(clusters);
        syncPolicy = syncPolicy == null ? SyncPolicy.MANUAL : syncPolicy;
        provider = provider == null ? GitOpsProvider.ARGOCD : provider;
    }
}
