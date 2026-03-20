package dev.gimi.core.model.security;

/** Types of security scans in the STO pipeline. */
public enum SecurityScanType {
    /** Static Application Security Testing — source code analysis. */
    SAST,
    /** Dynamic Application Security Testing — runtime testing. */
    DAST,
    /** Software Composition Analysis — dependency vulnerability scanning. */
    SCA,
    /** Container image scanning. */
    CONTAINER_SCAN,
    /** Infrastructure-as-Code security scanning. */
    IAC_SCAN,
    /** Secret detection in source code. */
    SECRET_DETECTION,
    /** License compliance checking. */
    LICENSE_COMPLIANCE,
    /** API security testing. */
    API_SECURITY
}
