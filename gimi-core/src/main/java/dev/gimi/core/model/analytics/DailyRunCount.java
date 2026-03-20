package dev.gimi.core.model.analytics;

import java.time.LocalDate;

/**
 * Daily pipeline run count for trend tracking.
 *
 * @param date       the date
 * @param total      total runs
 * @param succeeded  successful runs
 * @param failed     failed runs
 * @param cancelled  cancelled runs
 */
public record DailyRunCount(
        LocalDate date,
        int total,
        int succeeded,
        int failed,
        int cancelled
) {}
