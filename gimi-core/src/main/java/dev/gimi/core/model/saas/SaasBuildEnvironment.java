package dev.gimi.core.model.saas;

import java.util.List;
import java.util.Map;

/**
 * Managed build environment configuration for SaaS-hosted builds.
 * Similar to Harness CI Cloud — pre-configured VMs with common tools.
 */
public record SaasBuildEnvironment(
        String id,
        String name,
        SaasMachineType machineType,
        SaasOsType os,
        String architecture,
        List<String> preInstalledTools,
        Map<String, String> environmentVariables,
        boolean dockerEnabled,
        boolean gpuEnabled
) {
    public SaasBuildEnvironment {
        preInstalledTools = preInstalledTools == null ? List.of() : List.copyOf(preInstalledTools);
        environmentVariables = environmentVariables == null ? Map.of() : Map.copyOf(environmentVariables);
        architecture = architecture == null ? "amd64" : architecture;
    }

    public enum SaasMachineType {
        SMALL("2 vCPU, 4 GB RAM", 2, 4096),
        MEDIUM("4 vCPU, 8 GB RAM", 4, 8192),
        LARGE("8 vCPU, 16 GB RAM", 8, 16384),
        XLARGE("16 vCPU, 32 GB RAM", 16, 32768),
        GPU("8 vCPU, 16 GB RAM, 1 GPU", 8, 16384);

        private final String description;
        private final int vcpus;
        private final int memoryMb;

        SaasMachineType(String description, int vcpus, int memoryMb) {
            this.description = description;
            this.vcpus = vcpus;
            this.memoryMb = memoryMb;
        }

        public String description() { return description; }
        public int vcpus() { return vcpus; }
        public int memoryMb() { return memoryMb; }
    }

    public enum SaasOsType {
        LINUX_UBUNTU_2204,
        LINUX_UBUNTU_2404,
        MACOS_14,
        MACOS_15,
        WINDOWS_2022,
        WINDOWS_2025
    }
}
