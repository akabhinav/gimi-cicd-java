package dev.gimi.engine.scheduler;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import dev.gimi.core.model.CronTrigger;
import dev.gimi.core.model.Pipeline;
import dev.gimi.core.model.Trigger;
import dev.gimi.engine.lock.DistributedLock;
import dev.gimi.engine.lock.LockHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Schedules pipeline executions based on cron trigger expressions.
 *
 * <p>Uses a virtual thread that polls every 30 seconds, checking registered pipelines
 * for cron triggers that are due. A {@link DistributedLock} ensures that only one
 * server instance executes each scheduled pipeline in a multi-node deployment.
 *
 * <p>Cron expressions are parsed using the cron-utils library with UNIX cron format.
 */
public final class CronScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CronScheduler.class);

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(30);
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);
    private static final String LOCK_PREFIX = "cron-schedule:";

    private final DistributedLock distributedLock;
    private final Consumer<Pipeline> pipelineCallback;
    private final CronParser cronParser;
    private final List<Pipeline> registeredPipelines = new CopyOnWriteArrayList<>();
    private final Map<String, ZonedDateTime> lastExecutionTimes = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread schedulerThread;

    /**
     * Creates a new cron scheduler.
     *
     * @param distributedLock  the distributed lock for coordination across instances
     * @param pipelineCallback the callback invoked when a pipeline's cron trigger fires
     */
    public CronScheduler(DistributedLock distributedLock, Consumer<Pipeline> pipelineCallback) {
        this.distributedLock = Objects.requireNonNull(distributedLock, "distributedLock must not be null");
        this.pipelineCallback = Objects.requireNonNull(pipelineCallback, "pipelineCallback must not be null");
        this.cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
    }

    /**
     * Registers a pipeline for cron scheduling. Only pipelines with at least one
     * {@link CronTrigger} will be evaluated.
     *
     * @param pipeline the pipeline to register
     */
    public void register(Pipeline pipeline) {
        Objects.requireNonNull(pipeline, "pipeline must not be null");
        registeredPipelines.add(pipeline);
        LOG.debug("Registered pipeline for cron scheduling: {}", pipeline.name());
    }

    /**
     * Removes a pipeline from cron scheduling.
     *
     * @param pipelineName the name of the pipeline to unregister
     */
    public void unregister(String pipelineName) {
        registeredPipelines.removeIf(p -> p.name().equals(pipelineName));
        lastExecutionTimes.remove(pipelineName);
        LOG.debug("Unregistered pipeline from cron scheduling: {}", pipelineName);
    }

    /**
     * Starts the cron scheduler on a virtual thread.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            LOG.warn("Cron scheduler is already running");
            return;
        }

        schedulerThread = Thread.ofVirtual()
                .name("cron-scheduler")
                .start(this::pollLoop);

        LOG.info("Cron scheduler started (poll interval: {}s)", POLL_INTERVAL.toSeconds());
    }

    /**
     * Stops the cron scheduler.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        Thread thread = schedulerThread;
        if (thread != null) {
            thread.interrupt();
        }

        LOG.info("Cron scheduler stopped");
    }

    /**
     * Returns whether the scheduler is currently running.
     *
     * @return {@code true} if the scheduler is running
     */
    public boolean isRunning() {
        return running.get();
    }

    private void pollLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                checkSchedules();
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.debug("Cron scheduler interrupted");
            } catch (Exception e) {
                LOG.error("Error in cron scheduler poll loop", e);
            }
        }
    }

    private void checkSchedules() {
        ZonedDateTime now = ZonedDateTime.now();

        for (Pipeline pipeline : registeredPipelines) {
            for (Trigger trigger : pipeline.triggers()) {
                if (trigger instanceof CronTrigger cronTrigger) {
                    checkCronTrigger(pipeline, cronTrigger, now);
                }
            }
        }
    }

    private void checkCronTrigger(Pipeline pipeline, CronTrigger cronTrigger, ZonedDateTime now) {
        try {
            Cron cron = cronParser.parse(cronTrigger.schedule());
            cron.validate();

            ZoneId zone = cronTrigger.timezone() != null
                    ? ZoneId.of(cronTrigger.timezone())
                    : ZoneId.systemDefault();

            ZonedDateTime zonedNow = now.withZoneSameInstant(zone);
            ExecutionTime executionTime = ExecutionTime.forCron(cron);

            Optional<ZonedDateTime> lastExecution = executionTime.lastExecution(zonedNow);
            if (lastExecution.isEmpty()) {
                return;
            }

            ZonedDateTime lastDue = lastExecution.get();
            ZonedDateTime previousRun = lastExecutionTimes.get(pipeline.name());

            // Check if this execution time is new (hasn't been triggered yet)
            if (previousRun != null && !lastDue.isAfter(previousRun)) {
                return;
            }

            // Only trigger if the last due time is within the poll interval window
            Duration sinceDue = Duration.between(lastDue, zonedNow);
            if (sinceDue.compareTo(POLL_INTERVAL.multipliedBy(2)) > 0) {
                return;
            }

            // Acquire distributed lock to prevent duplicate execution
            String lockName = LOCK_PREFIX + pipeline.name() + ":" + lastDue.toEpochSecond();
            Optional<LockHandle> lock = distributedLock.tryAcquire(lockName, LOCK_TTL);

            if (lock.isPresent()) {
                try {
                    LOG.info("Triggering scheduled pipeline: {} (cron: {}, due: {})",
                            pipeline.name(), cronTrigger.schedule(), lastDue);

                    lastExecutionTimes.put(pipeline.name(), lastDue);
                    pipelineCallback.accept(pipeline);
                } catch (Exception e) {
                    LOG.error("Failed to trigger scheduled pipeline: {}", pipeline.name(), e);
                } finally {
                    distributedLock.release(lock.get());
                }
            } else {
                LOG.debug("Skipping scheduled pipeline {} (lock held by another instance)", pipeline.name());
            }
        } catch (Exception e) {
            LOG.error("Error evaluating cron trigger for pipeline {}: {}",
                    pipeline.name(), e.getMessage(), e);
        }
    }
}
