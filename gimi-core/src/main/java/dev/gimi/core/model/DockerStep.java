package dev.gimi.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * A pipeline step that builds or runs a Docker image.
 *
 * @param name       the name of this step
 * @param dockerfile the path to the Dockerfile
 * @param image      the Docker image name
 * @param tags       the list of tags to apply to the image
 * @param buildArgs  the build arguments to pass to the Docker build
 */
public record DockerStep(
        String name,
        String dockerfile,
        String image,
        List<String> tags,
        @JsonProperty("build_args") Map<String, String> buildArgs
) implements Step {

    /**
     * Creates a {@code DockerStep} with defensive copies of collections.
     */
    public DockerStep {
        tags = tags == null ? List.of() : List.copyOf(tags);
        buildArgs = buildArgs == null ? Map.of() : Map.copyOf(buildArgs);
    }
}
