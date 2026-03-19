package dev.gimi.core.model;

/**
 * A trigger that fires on a cron schedule.
 *
 * @param schedule the cron expression defining when the trigger fires
 * @param timezone the timezone for evaluating the cron expression
 */
public record CronTrigger(
        String schedule,
        String timezone
) implements Trigger {
}
