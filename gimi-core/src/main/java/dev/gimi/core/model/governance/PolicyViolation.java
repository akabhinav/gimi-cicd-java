package dev.gimi.core.model.governance;

public record PolicyViolation(
    String rule,
    String message,
    String severity,
    String resource
) {}
