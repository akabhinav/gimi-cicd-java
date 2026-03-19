package dev.gimi.core.model;

/**
 * Defines a deployment freeze window during which deployments are prohibited.
 *
 * @param cron  the cron expression defining the recurrence of the freeze window
 * @param start the start time of the freeze window
 * @param end   the end time of the freeze window
 */
public record FreezeWindow(
        String cron,
        String start,
        String end
) {
}
