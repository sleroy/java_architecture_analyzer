package com.analyzer.migration.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single migration task containing one or more execution blocks.
 * Tasks have IDs, descriptions, and dependencies on other tasks.
 */
public class Task {
    private final String id;
    private final String name;
    private final String description;
    private final List<MigrationBlock> blocks;
    private final boolean manualReviewRequired;
    private final String successCriteria;

    private Task(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.blocks = new ArrayList<>(builder.blocks);
        this.manualReviewRequired = builder.manualReviewRequired;
        this.successCriteria = builder.successCriteria;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<MigrationBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    public boolean isManualReviewRequired() {
        return manualReviewRequired;
    }

    public String getSuccessCriteria() {
        return successCriteria;
    }

    public static class Builder {
        private final String id;
        private String name;
        private String description;
        private List<MigrationBlock> blocks = new ArrayList<>();
        private boolean manualReviewRequired = false;
        private String successCriteria;

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder addBlock(MigrationBlock block) {
            this.blocks.add(block);
            return this;
        }

        public Builder blocks(List<MigrationBlock> blocks) {
            this.blocks.addAll(blocks);
            return this;
        }

        public Builder manualReviewRequired(boolean required) {
            this.manualReviewRequired = required;
            return this;
        }

        public Builder successCriteria(String criteria) {
            this.successCriteria = criteria;
            return this;
        }

        public Task build() {
            if (id == null || id.isEmpty()) {
                throw new IllegalStateException("Task ID is required");
            }
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Task name is required");
            }
            if (blocks.isEmpty()) {
                throw new IllegalStateException("Task must have at least one block");
            }
            return new Task(this);
        }
    }

    @Override
    public String toString() {
        return "Task{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", blocks=" + blocks +
                ", manualReviewRequired=" + manualReviewRequired +
                ", successCriteria='" + successCriteria + '\'' +
                '}';
    }
}
