package dev.gimi.core.model.cost;

/** Types of cloud cost optimization recommendations. */
public enum RecommendationType {
    RIGHT_SIZE,
    SPOT_INSTANCE,
    RESERVED_INSTANCE,
    IDLE_RESOURCE,
    CACHE_OPTIMIZATION,
    PARALLELISM_TUNING,
    STORAGE_TIERING,
    NETWORK_OPTIMIZATION
}
