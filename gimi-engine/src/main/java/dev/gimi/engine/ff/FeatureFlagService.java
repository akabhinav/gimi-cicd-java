package dev.gimi.engine.ff;

import dev.gimi.core.model.ff.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Feature Flag service integrated into the CI/CD pipeline engine.
 *
 * <p>Provides flag evaluation with targeting rules, percentage rollouts,
 * and per-environment overrides — natively integrated into pipeline executions.
 */
public class FeatureFlagService {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureFlagService.class);

    private final Map<String, FeatureFlag> flags = new ConcurrentHashMap<>();

    public FeatureFlag createFlag(FeatureFlag flag) {
        flags.put(flag.key(), flag);
        LOG.info("Created feature flag: key={}, type={}", flag.key(), flag.type());
        return flag;
    }

    public Optional<FeatureFlag> getFlag(String key) {
        return Optional.ofNullable(flags.get(key));
    }

    public List<FeatureFlag> listFlags() {
        return new ArrayList<>(flags.values());
    }

    public FeatureFlag updateFlag(FeatureFlag flag) {
        flags.put(flag.key(), flag);
        LOG.info("Updated feature flag: key={}", flag.key());
        return flag;
    }

    public void deleteFlag(String key) {
        flags.remove(key);
        LOG.info("Deleted feature flag: key={}", key);
    }

    /**
     * Evaluates a feature flag for a given context.
     *
     * @param key         the flag key
     * @param environment the current environment
     * @param context     evaluation context (user attributes)
     * @return the evaluated flag value, or the default if flag not found
     */
    public String evaluate(String key, String environment, Map<String, String> context) {
        FeatureFlag flag = flags.get(key);
        if (flag == null || !flag.enabled()) {
            return flag != null ? flag.defaultValue() : null;
        }

        // Check environment-specific config
        FlagEnvironmentConfig envConfig = flag.environments().get(environment);
        if (envConfig != null) {
            if (!envConfig.enabled()) {
                return flag.defaultValue();
            }

            // Evaluate environment-specific targeting rules
            for (TargetingRule rule : envConfig.targetingRules()) {
                if (matchesRule(rule, context)) {
                    return rule.value();
                }
            }

            // Environment percentage rollout
            if (envConfig.percentageRollout() > 0 && envConfig.percentageRollout() < 100) {
                String userId = context.getOrDefault("userId", UUID.randomUUID().toString());
                if (!isInRollout(key, userId, envConfig.percentageRollout())) {
                    return flag.defaultValue();
                }
            }

            if (envConfig.value() != null) {
                return envConfig.value();
            }
        }

        // Evaluate global targeting rules
        for (TargetingRule rule : flag.targetingRules()) {
            if (matchesRule(rule, context)) {
                return rule.value();
            }
        }

        // Global percentage rollout
        if (flag.percentageRollout() > 0 && flag.percentageRollout() < 100) {
            String userId = context.getOrDefault("userId", UUID.randomUUID().toString());
            if (!isInRollout(key, userId, flag.percentageRollout())) {
                return flag.defaultValue();
            }
        }

        return flag.defaultValue();
    }

    private boolean matchesRule(TargetingRule rule, Map<String, String> context) {
        String attrValue = context.get(rule.attribute());
        if (attrValue == null) return false;

        return switch (rule.operator()) {
            case EQUALS -> rule.values().contains(attrValue);
            case NOT_EQUALS -> !rule.values().contains(attrValue);
            case CONTAINS -> rule.values().stream().anyMatch(attrValue::contains);
            case STARTS_WITH -> rule.values().stream().anyMatch(attrValue::startsWith);
            case ENDS_WITH -> rule.values().stream().anyMatch(attrValue::endsWith);
            case IN -> rule.values().contains(attrValue);
            case NOT_IN -> !rule.values().contains(attrValue);
            case REGEX -> rule.values().stream().anyMatch(attrValue::matches);
            default -> false;
        };
    }

    private boolean isInRollout(String flagKey, String userId, int percentage) {
        int hash = Math.abs((flagKey + ":" + userId).hashCode()) % 100;
        return hash < percentage;
    }
}
