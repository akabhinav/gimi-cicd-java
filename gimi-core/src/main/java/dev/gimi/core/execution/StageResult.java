package dev.gimi.core.execution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of a stage execution containing one or more step results.
 *
 * @param name       the name of the stage
 * @param status     the final execution status of the stage
 * @param startedAt  the instant the stage started
 * @param finishedAt the instant the stage finished
 * @param steps      an unmodifiable list of step results
 */
public record StageResult(
        String name,
        ExecutionStatus status,
        Instant startedAt,
        Instant finishedAt,
        List<StepResult> steps
) {

    /**
     * Compact constructor that creates a defensive copy of the steps list.
     */
    public StageResult {
        steps = List.copyOf(steps);
    }

    /**
     * A mutable builder for constructing {@link StageResult} instances.
     */
    public static final class Builder {

        private String name;
        private ExecutionStatus status;
        private Instant startedAt;
        private Instant finishedAt;
        private final List<StepResult> steps = new ArrayList<>();

        /**
         * Creates a new empty builder.
         */
        public Builder() {
        }

        /**
         * Sets the stage name.
         *
         * @param name the stage name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
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
         * @param startedAt the instant the stage started
         * @return this builder
         */
        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        /**
         * Sets the finish time.
         *
         * @param finishedAt the instant the stage finished
         * @return this builder
         */
        public Builder finishedAt(Instant finishedAt) {
            this.finishedAt = finishedAt;
            return this;
        }

        /**
         * Adds a step result to this stage.
         *
         * @param stepResult the step result to add
         * @return this builder
         * @throws NullPointerException if {@code stepResult} is null
         */
        public Builder addStepResult(StepResult stepResult) {
            Objects.requireNonNull(stepResult, "stepResult must not be null");
            this.steps.add(stepResult);
            return this;
        }

        /**
         * Builds an immutable {@link StageResult} from the current builder state.
         *
         * @return a new {@link StageResult}
         */
        public StageResult build() {
            return new StageResult(name, status, startedAt, finishedAt, steps);
        }
    }
}
