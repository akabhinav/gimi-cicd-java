package dev.gimi.core.model.iac;

/** Supported IaC provider types for workspace management. */
public enum IacProviderType {
    TERRAFORM,
    TERRAGRUNT,
    PULUMI,
    CLOUDFORMATION,
    CDK,
    ANSIBLE,
    CROSSPLANE,
    OPENTOFU
}
