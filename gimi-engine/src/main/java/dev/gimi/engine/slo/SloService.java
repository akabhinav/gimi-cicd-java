package dev.gimi.engine.slo;

import dev.gimi.core.model.slo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service Level Objective management and deployment gating.
 *
 * <p>Automatically blocks deployments when error budget is exhausted and
 * tracks the impact of deployments on service reliability.
 */
public class SloService {

    private static final Logger LOG = LoggerFactory.getLogger(SloService.class);

    private final Map<String, ServiceLevelObjective> slos = new ConcurrentHashMap<>();
    private final Map<String, SloStatus> statuses = new ConcurrentHashMap<>();

    public ServiceLevelObjective createSlo(ServiceLevelObjective slo) {
        slos.put(slo.id(), slo);
        statuses.put(slo.id(), new SloStatus(
                slo.id(), slo.targetPercentage(), slo.targetPercentage(),
                100.0, 0.0, true, false));
        LOG.info("Created SLO: id={}, service={}, target={}%",
                slo.id(), slo.serviceName(), slo.targetPercentage());
        return slo;
    }

    public Optional<ServiceLevelObjective> getSlo(String id) {
        return Optional.ofNullable(slos.get(id));
    }

    public List<ServiceLevelObjective> listSlos() {
        return new ArrayList<>(slos.values());
    }

    public Optional<SloStatus> getStatus(String sloId) {
        return Optional.ofNullable(statuses.get(sloId));
    }

    /**
     * Checks if deployment is allowed based on SLO error budget.
     *
     * @param serviceName the service being deployed
     * @param environment the target environment
     * @return true if deployment is allowed
     */
    public boolean isDeploymentAllowed(String serviceName, String environment) {
        for (ServiceLevelObjective slo : slos.values()) {
            if (slo.serviceName().equals(serviceName)
                    && (slo.environment() == null || slo.environment().equals(environment))
                    && slo.gateDeployments()) {
                SloStatus status = statuses.get(slo.id());
                if (status != null && status.deploymentGated()) {
                    LOG.warn("Deployment blocked for service={}, env={}: SLO {} error budget exhausted",
                            serviceName, environment, slo.id());
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Updates SLO status with current metric values.
     */
    public SloStatus updateStatus(String sloId, double currentPercentage) {
        ServiceLevelObjective slo = slos.get(sloId);
        if (slo == null) return null;

        double errorBudgetTotal = 100.0 - slo.targetPercentage();
        double errorBudgetUsed = Math.max(0, slo.targetPercentage() - currentPercentage);
        double errorBudgetRemaining = errorBudgetTotal > 0
                ? Math.max(0, (errorBudgetTotal - errorBudgetUsed) / errorBudgetTotal * 100)
                : 0;

        boolean isHealthy = currentPercentage >= slo.targetPercentage();
        boolean gated = errorBudgetRemaining <= 0 && slo.gateDeployments();

        SloStatus status = new SloStatus(sloId, currentPercentage, slo.targetPercentage(),
                errorBudgetRemaining, 0, isHealthy, gated);
        statuses.put(sloId, status);

        if (gated) {
            LOG.error("SLO {} error budget exhausted — deployments are now GATED", sloId);
        }

        return status;
    }

    public void deleteSlo(String id) {
        slos.remove(id);
        statuses.remove(id);
    }
}
