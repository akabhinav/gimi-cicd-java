package dev.gimi.core.execution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable record of a complete pipeline execution run.
 *
 * @param runId        the unique identifier for this run
 * @param pipelineName the name of the pipeline that was executed
 * @param status       the overall execution status
 * @param startedAt    the instant the run started
 * @param finishedAt   the instant the run finished
 * @param stages       an unmodifiable list of stage results
 * @param trigger      the trigger that initiated the run (e.g. "manual", "push")
 * @param environment  the target environment for the run
 */
public record ExecutionRecord(
        String runId,
        String pipelineName,
        ExecutionStatus status,
        Instant startedAt,
        Instant finishedAt,
        List<StageResult> stages,
        String trigger,
        String environment
) {

    /**
     * Compact constructor that creates a defensive copy of the stages list.
     */
    public ExecutionRecord {
        stages = List.copyOf(stages);
    }

    /**
     * A mutable builder for constructing {@link ExecutionRecord} instances.
     */
    public static final class Builder {

        private final String runId;
        private final String pipelineName;
        private ExecutionStatus status;
        private Instant startedAt;
        private Instant finishedAt;
        private final List<StageResult> stages = new ArrayList<>();
        private String trigger;
        private String environment;

        /**
         * Creates a new builder with the required run identifier and pipeline name.
         *
         * @param runId        the unique run identifier
         * @param pipelineName the pipeline name
         */
        public Builder(String runId, String pipelineName) {
            this.runId = Objects.requireNonNull(runId, "runId must not be null");
            this.pipelineName = Objects.requireNonNull(pipelineName, "pipelineName must not be null");
        }

        /**
         * Sets the execution status.
         *
         * @param status the execution status
         * @return this builder
         */
        public Builder status(ExecutionStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Sets the start time.
         *
         * @param startedAt the instant the run started
         * @return this builder
         */
        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        /**
         * Sets the finish time.
         *
         * @param finishedAt the instant the run finished
         * @return this builder
         */
        public Builder finishedAt(Instant finishedAt) {
            this.finishedAt = finishedAt;
            return this;
        }

        /**
         * Adds a stage result to this execution record.
         *
         * @param stageResult the stage result to add
         * @return this builder
         * @throws NullPointerException if {@code stageResult} is null
         */
        public Builder addStageResult(StageResult stageResult) {
            Objects.requireNonNull(stageResult, "stageResult must not be null");
            this.stages.add(stageResult);
            return this;
        }

        /**
         * Sets the trigger that initiated the run.
         *
         * @param trigger the trigger description
         * @return this builder
         */
        public Builder trigger(String trigger) {
            this.trigger = trigger;
            return this;
        }

        /**
         * Sets the target environment.
         *
         * @param environment the environment name
         * @return this builder
         */
        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Builds an immutable {@link ExecutionRecord} from the current builder state.
         *
         * @return a new {@link ExecutionRecord}
         */
        public ExecutionRecord build() {
            return new ExecutionRecord(
                    runId, pipelineName, status, startedAt, finishedAt,
                    stages, trigger, environment
            );
        }
    }
}
