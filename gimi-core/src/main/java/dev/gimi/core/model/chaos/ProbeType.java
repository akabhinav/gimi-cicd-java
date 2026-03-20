package dev.gimi.core.model.chaos;

/** Types of steady-state probes for chaos experiments. */
public enum ProbeType {
    HTTP,
    CMD,
    PROMETHEUS,
    K8S,
    TCP,
    CUSTOM
}
