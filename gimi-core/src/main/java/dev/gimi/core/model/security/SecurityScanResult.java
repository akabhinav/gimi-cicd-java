package dev.gimi.core.model.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of a security scan execution.
 *
 * @param runId                the pipeline run ID
 * @param scanType             type of scan performed
 * @param tool                 tool used
 * @param totalFindings        total number of findings
 * @param findingsBySeverity   findings grouped by severity
 * @param newFindings          findings not seen in previous scans
 * @param fixedFindings        findings resolved since last scan
 * @param topFindings          top priority findings for remediation
 * @param passed               whether the scan passed the configured thresholds
 * @param scannedAt            scan timestamp
 * @param durationMs           scan duration in milliseconds
 */
public record SecurityScanResult(
        String runId,
        SecurityScanType scanType,
        String tool,
        int totalFindings,
        Map<SecuritySeverity, Integer> findingsBySeverity,
        int newFindings,
        int fixedFindings,
        List<SecurityFinding> topFindings,
        boolean passed,
        Instant scannedAt,
        long durationMs
) {
    public SecurityScanResult {
        findingsBySeverity = findingsBySeverity == null ? Map.of() : Map.copyOf(findingsBySeverity);
        topFindings = topFindings == null ? List.of() : List.copyOf(topFindings);
    }
}
