package dev.gimi.core.model.infra;

/** Supported Infrastructure-as-Code provisioner types. */
public enum ProvisionerType {
    TERRAFORM,
    TERRAGRUNT,
    PULUMI,
    CLOUDFORMATION,
    CDK,
    ANSIBLE,
    CROSSPLANE
}
