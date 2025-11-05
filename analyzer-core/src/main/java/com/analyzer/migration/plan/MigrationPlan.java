package com.analyzer.migration.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Top-level migration plan containing all phases and tasks.
 * This is the main entry point for defining a complete migration strategy.
 */
public class MigrationPlan {
    private final String name;
    private final String description;
    private final String version;
    private final List<Phase> phases;

    private MigrationPlan(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.version = builder.version;
        this.phases = new ArrayList<>(builder.phases);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getVersion() {
        return version;
    }

    public List<Phase> getPhases() {
        return Collections.unmodifiableList(phases);
    }

    /**
     * Get all tasks across all phases in execution order
     */
    public List<Task> getAllTasks() {
        return phases.stream()
                .sorted((p1, p2) -> Integer.compare(p1.getOrder(), p2.getOrder()))
                .flatMap(phase -> phase.getTasks().stream())
                .toList();
    }

    /**
     * Find a task by its ID across all phases
     */
    public Task getTaskById(String taskId) {
        return getAllTasks().stream()
                .filter(task -> task.getId().equals(taskId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get total number of tasks in the plan
     */
    public int getTotalTaskCount() {
        return phases.stream()
                .mapToInt(phase -> phase.getTasks().size())
                .sum();
    }

    public static class Builder {
        private final String name;
        private String description;
        private String version = "1.0.0";
        private List<Phase> phases = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder addPhase(Phase phase) {
            this.phases.add(phase);
            return this;
        }

        public Builder phases(List<Phase> phases) {
            this.phases.addAll(phases);
            return this;
        }

        public MigrationPlan build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Migration plan name is required");
            }
            if (phases.isEmpty()) {
                throw new IllegalStateException("Migration plan must have at least one phase");
            }
            return new MigrationPlan(this);
        }
    }
}
