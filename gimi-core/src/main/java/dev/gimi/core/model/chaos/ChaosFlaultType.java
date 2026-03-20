package dev.gimi.core.model.chaos;

/** Types of chaos faults that can be injected. */
public enum ChaosFlaultType {
    /** Inject network latency. */
    NETWORK_LATENCY,
    /** Simulate network packet loss. */
    NETWORK_LOSS,
    /** Kill a pod/container. */
    POD_KILL,
    /** Consume CPU resources. */
    CPU_STRESS,
    /** Consume memory resources. */
    MEMORY_STRESS,
    /** Simulate disk I/O pressure. */
    DISK_STRESS,
    /** Simulate DNS failure. */
    DNS_FAILURE,
    /** Inject HTTP error codes. */
    HTTP_FAULT,
    /** Simulate clock skew. */
    TIME_CHAOS,
    /** Kill a node/VM. */
    NODE_DRAIN,
    /** Block network traffic between services. */
    NETWORK_PARTITION,
    /** Custom fault type. */
    CUSTOM
}
