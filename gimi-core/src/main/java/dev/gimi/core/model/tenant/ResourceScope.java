package dev.gimi.core.model.tenant;

import java.util.Objects;

public record ResourceScope(
    Scope scope,
    String accountId,
    String organizationId,
    String projectId
) {
    public ResourceScope {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(accountId);
    }

    public static ResourceScope account(String accountId) {
        return new ResourceScope(Scope.ACCOUNT, accountId, null, null);
    }

    public static ResourceScope organization(String accountId, String orgId) {
        return new ResourceScope(Scope.ORGANIZATION, accountId, orgId, null);
    }

    public static ResourceScope project(String accountId, String orgId, String projectId) {
        return new ResourceScope(Scope.PROJECT, accountId, orgId, projectId);
    }

    public boolean contains(ResourceScope other) {
        if (this.scope == Scope.ACCOUNT && this.accountId.equals(other.accountId)) return true;
        if (this.scope == Scope.ORGANIZATION && this.accountId.equals(other.accountId)
                && Objects.equals(this.organizationId, other.organizationId)) return true;
        return this.scope == Scope.PROJECT && this.accountId.equals(other.accountId)
                && Objects.equals(this.organizationId, other.organizationId)
                && Objects.equals(this.projectId, other.projectId);
    }
}
