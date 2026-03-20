package dev.gimi.engine.security;

import dev.gimi.core.model.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Security Testing Orchestration (STO) service.
 *
 * <p>Coordinates SAST, DAST, SCA, container scanning, and secret detection
 * within CI/CD pipelines with intelligent deduplication, baseline comparison,
 * and remediation tracking.
 */
public class SecurityScanService {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityScanService.class);

    /** Previous scan results for baseline comparison. */
    private final Map<String, SecurityScanResult> previousResults = new ConcurrentHashMap<>();

    /**
     * Executes a security scan based on the configuration.
     *
     * @param config the scan configuration
     * @param runId  the pipeline run ID
     * @return the scan result with findings
     */
    public SecurityScanResult executeScan(SecurityScanConfig config, String runId) {
        LOG.info("Starting {} scan with tool '{}' on target '{}'",
                config.scanType(), config.tool(), config.target());

        Instant start = Instant.now();

        // Delegate to appropriate scanner
        List<SecurityFinding> findings = switch (config.scanType()) {
            case SAST -> runSastScan(config);
            case DAST -> runDastScan(config);
            case SCA -> runScaScan(config);
            case CONTAINER_SCAN -> runContainerScan(config);
            case IAC_SCAN -> runIacScan(config);
            case SECRET_DETECTION -> runSecretDetection(config);
            case LICENSE_COMPLIANCE -> runLicenseCheck(config);
            case API_SECURITY -> runApiSecurityScan(config);
        };

        // Filter excluded paths
        findings = findings.stream()
                .filter(f -> f.filePath() == null || config.excludePaths().stream()
                        .noneMatch(p -> f.filePath().matches(p.replace("*", ".*"))))
                .toList();

        // Compare with previous scan for new/fixed tracking
        String baselineKey = config.scanType() + ":" + config.target();
        SecurityScanResult previous = previousResults.get(baselineKey);
        int newFindings = findings.size();
        int fixedFindings = 0;
        if (previous != null) {
            Set<String> prevIds = new HashSet<>();
            previous.topFindings().forEach(f -> prevIds.add(f.id()));
            newFindings = (int) findings.stream()
                    .filter(f -> !prevIds.contains(f.id())).count();
            Set<String> currentIds = findings.stream()
                    .map(SecurityFinding::id).collect(java.util.stream.Collectors.toSet());
            fixedFindings = (int) prevIds.stream().filter(id -> !currentIds.contains(id)).count();
        }

        // Count by severity
        Map<SecuritySeverity, Integer> bySeverity = new EnumMap<>(SecuritySeverity.class);
        for (SecurityFinding f : findings) {
            bySeverity.merge(f.severity(), 1, Integer::sum);
        }

        // Check threshold
        boolean passed = true;
        if (config.failOnViolation()) {
            for (SecuritySeverity sev : SecuritySeverity.values()) {
                if (sev.ordinal() <= config.severityThreshold().ordinal()
                        && bySeverity.getOrDefault(sev, 0) > 0) {
                    passed = false;
                    break;
                }
            }
        }

        long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();

        SecurityScanResult result = new SecurityScanResult(
                runId, config.scanType(), config.tool(),
                findings.size(), bySeverity, newFindings, fixedFindings,
                findings.stream().limit(20).toList(), passed,
                Instant.now(), durationMs
        );

        previousResults.put(baselineKey, result);

        LOG.info("Security scan complete: type={}, findings={}, new={}, fixed={}, passed={}",
                config.scanType(), findings.size(), newFindings, fixedFindings, passed);

        return result;
    }

    // Scanner stubs — in production these delegate to actual tools (Semgrep, Trivy, etc.)
    private List<SecurityFinding> runSastScan(SecurityScanConfig config) {
        LOG.info("Running SAST scan with {}", config.tool());
        return List.of();
    }

    private List<SecurityFinding> runDastScan(SecurityScanConfig config) {
        LOG.info("Running DAST scan with {}", config.tool());
        return List.of();
    }

    private List<SecurityFinding> runScaScan(SecurityScanConfig config) {
        LOG.info("Running SCA scan with {}", config.tool());
        return List.of();
    }

    private List<SecurityFinding> runContainerScan(SecurityScanConfig config) {
        LOG.info("Running container scan with {}", config.tool());
        return List.of();
    }

    private List<SecurityFinding> runIacScan(SecurityScanConfig config) {
        LOG.info("Running IaC scan with {}", config.tool());
        return List.of();
    }

    private List<SecurityFinding> runSecretDetection(SecurityScanConfig config) {
        LOG.info("Running secret detection with {}", config.tool());
        return List.of();
    }

    private List<SecurityFinding> runLicenseCheck(SecurityScanConfig config) {
        LOG.info("Running license compliance check with {}", config.tool());
        return List.of();
    }

    private List<SecurityFinding> runApiSecurityScan(SecurityScanConfig config) {
        LOG.info("Running API security scan with {}", config.tool());
        return List.of();
    }
}
