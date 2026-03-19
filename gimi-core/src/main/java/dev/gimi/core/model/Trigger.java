package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A sealed interface representing a pipeline trigger.
 *
 * <p>Triggers are distinguished by a {@code type} discriminator property in the YAML/JSON
 * representation. Permitted implementations are {@link GitTrigger}, {@link CronTrigger},
 * and {@link WebhookTrigger}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GitTrigger.class, name = "git"),
        @JsonSubTypes.Type(value = CronTrigger.class, name = "cron"),
        @JsonSubTypes.Type(value = WebhookTrigger.class, name = "webhook")
})
public sealed interface Trigger permits GitTrigger, CronTrigger, WebhookTrigger {
}
