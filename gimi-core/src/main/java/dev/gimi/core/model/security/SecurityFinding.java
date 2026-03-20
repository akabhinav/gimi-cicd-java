package dev.gimi.core.model.security;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single security finding from a scan.
 *
 * @param id           unique finding ID
 * @param severity     severity level
 * @param title        finding title
 * @param description  detailed description
 * @param filePath     affected file path
 * @param lineNumber   line number in the file
 * @param cweId        CWE (Common Weakness Enumeration) ID
 * @param cveId        CVE (Common Vulnerabilities and Exposures) ID
 * @param remediation  suggested remediation
 * @param isNew        whether this is a new finding
 */
public record SecurityFinding(
        String id,
        SecuritySeverity severity,
        String title,
        String description,
        @JsonProperty("file_path") String filePath,
        @JsonProperty("line_number") int lineNumber,
        @JsonProperty("cwe_id") String cweId,
        @JsonProperty("cve_id") String cveId,
        String remediation,
        @JsonProperty("is_new") boolean isNew
) {}
