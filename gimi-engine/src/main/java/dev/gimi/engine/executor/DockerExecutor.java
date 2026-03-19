package dev.gimi.engine.executor;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import dev.gimi.core.execution.ExecutionStatus;
import dev.gimi.core.execution.StepResult;
import dev.gimi.core.model.DockerStep;
import dev.gimi.core.model.Step;
import dev.gimi.engine.variable.VariableInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executor for {@link DockerStep} instances using the docker-java client library.
 *
 * <p>This executor manages the full container lifecycle: pulling images, creating and
 * starting containers, capturing stdout/stderr output, waiting for completion with
 * timeout support, and cleaning up containers after execution.
 */
public final class DockerExecutor implements StepExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(DockerExecutor.class);

    private static final long DEFAULT_TIMEOUT_SECONDS = 3600;
    private static final String WORKSPACE_CONTAINER_PATH = "/workspace";

    private final DockerClient dockerClient;
    private final long timeoutSeconds;

    /**
     * Creates a new Docker executor with default configuration connecting to the
     * local Docker daemon.
     */
    public DockerExecutor() {
        this(DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Creates a new Docker executor with the specified timeout.
     *
     * @param timeoutSeconds the maximum time in seconds to wait for container execution
     */
    public DockerExecutor(long timeoutSeconds) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        this.dockerClient = DockerClientBuilder.getInstance(config).build();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * Creates a new Docker executor with an externally provided client.
     * Primarily intended for testing.
     *
     * @param dockerClient   the Docker client to use
     * @param timeoutSeconds the maximum time in seconds to wait for container execution
     */
    public DockerExecutor(DockerClient dockerClient, long timeoutSeconds) {
        this.dockerClient = dockerClient;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    }

    @Override
    public boolean supports(Step step) {
        return step instanceof DockerStep;
    }

    @Override
    public StepResult execute(Step step, VariableInterpolator interpolator) {
        DockerStep docker = (DockerStep) step;
        long startTime = System.currentTimeMillis();
        String containerId = null;

        try {
            String image = interpolator.interpolate(docker.image());

            // Interpolate build args
            List<String> envVars = new ArrayList<>();
            for (Map.Entry<String, String> entry : docker.buildArgs().entrySet()) {
                String key = interpolator.interpolate(entry.getKey());
                String value = interpolator.interpolate(entry.getValue());
                envVars.add(key + "=" + value);
            }

            // Pull image if not present locally
            pullImageIfAbsent(image);

            // Create container
            HostConfig hostConfig = HostConfig.newHostConfig();

            CreateContainerResponse container = dockerClient.createContainerCmd(image)
                    .withEnv(envVars)
                    .withWorkingDir(WORKSPACE_CONTAINER_PATH)
                    .withHostConfig(hostConfig)
                    .exec();

            containerId = container.getId();
            LOG.info("Created container {} for step '{}' with image '{}'", containerId, docker.name(), image);

            // Start container
            dockerClient.startContainerCmd(containerId).exec();

            // Attach to container output and capture stdout/stderr
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            final String captureContainerId = containerId;
            dockerClient.logContainerCmd(captureContainerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            String payload = new String(frame.getPayload());
                            if (frame.getStreamType() == StreamType.STDOUT) {
                                stdout.append(payload);
                            } else if (frame.getStreamType() == StreamType.STDERR) {
                                stderr.append(payload);
                            }
                        }
                    })
                    .awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);

            // Wait for container to finish
            WaitContainerResultCallback waitCallback = dockerClient
                    .waitContainerCmd(containerId)
                    .exec(new WaitContainerResultCallback());

            Integer exitCode = waitCallback.awaitStatusCode(timeoutSeconds, TimeUnit.SECONDS);

            long durationMs = System.currentTimeMillis() - startTime;
            ExecutionStatus status = (exitCode != null && exitCode == 0)
                    ? ExecutionStatus.PASSED
                    : ExecutionStatus.FAILED;

            LOG.info("Container {} for step '{}' finished with exit code {} in {}ms",
                    containerId, docker.name(), exitCode, durationMs);

            return new StepResult(
                    docker.name(),
                    status,
                    exitCode,
                    stdout.toString(),
                    stderr.toString(),
                    durationMs
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long durationMs = System.currentTimeMillis() - startTime;
            LOG.warn("Docker step '{}' was interrupted", docker.name());

            // Kill the container on timeout/interrupt
            killContainer(containerId);

            return new StepResult(
                    docker.name(),
                    ExecutionStatus.CANCELLED,
                    null,
                    "",
                    "Step execution was interrupted",
                    durationMs
            );

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            LOG.error("Docker step '{}' failed: {}", docker.name(), e.getMessage(), e);

            // Kill the container on error
            killContainer(containerId);

            return new StepResult(
                    docker.name(),
                    ExecutionStatus.FAILED,
                    null,
                    "",
                    e.getMessage(),
                    durationMs
            );

        } finally {
            // Remove container on completion
            removeContainer(containerId);
        }
    }

    private void pullImageIfAbsent(String image) throws InterruptedException {
        try {
            InspectImageResponse inspectResponse = dockerClient.inspectImageCmd(image).exec();
            if (inspectResponse != null) {
                LOG.debug("Image '{}' already present locally", image);
                return;
            }
        } catch (Exception e) {
            // Image not found locally, proceed to pull
            LOG.debug("Image '{}' not found locally, pulling...", image);
        }

        dockerClient.pullImageCmd(image)
                .exec(new PullImageResultCallback())
                .awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);

        LOG.info("Pulled image '{}'", image);
    }

    private void killContainer(String containerId) {
        if (containerId == null) {
            return;
        }
        try {
            dockerClient.killContainerCmd(containerId).exec();
            LOG.debug("Killed container {}", containerId);
        } catch (Exception e) {
            LOG.debug("Failed to kill container {} (may already be stopped): {}",
                    containerId, e.getMessage());
        }
    }

    private void removeContainer(String containerId) {
        if (containerId == null) {
            return;
        }
        try {
            dockerClient.removeContainerCmd(containerId)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
            LOG.debug("Removed container {}", containerId);
        } catch (Exception e) {
            LOG.warn("Failed to remove container {}: {}", containerId, e.getMessage());
        }
    }
}
