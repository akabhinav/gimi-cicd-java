package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A sealed interface representing a pipeline step.
 *
 * <p>Steps are distinguished by a {@code type} discriminator property in the YAML/JSON
 * representation. Permitted implementations are {@link ShellStep} and {@link DockerStep}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ShellStep.class, name = "shell"),
        @JsonSubTypes.Type(value = DockerStep.class, name = "docker")
})
public sealed interface Step permits ShellStep, DockerStep {

    /**
     * Returns the name of this step.
     *
     * @return the step name
     */
    String name();
}
