package com.analyzer.migration.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a logical phase in the migration plan.
 * Phases group related tasks that should be executed together.
 */
public class Phase {
    private final String name;
    private final String description;
    private final List<Task> tasks;
    private final int order;

    private Phase(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.tasks = new ArrayList<>(builder.tasks);
        this.order = builder.order;
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

    public List<Task> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public int getOrder() {
        return order;
    }

    public static class Builder {
        private final String name;
        private String description;
        private List<Task> tasks = new ArrayList<>();
        private int order = 0;

        private Builder(String name) {
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder addTask(Task task) {
            this.tasks.add(task);
            return this;
        }

        public Builder tasks(List<Task> tasks) {
            this.tasks.addAll(tasks);
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Phase build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Phase name is required");
            }
            if (tasks.isEmpty()) {
                throw new IllegalStateException("Phase must have at least one task");
            }
            return new Phase(this);
        }
    }
}
