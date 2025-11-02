package com.analyzer.migration.state.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Root state object representing the entire migration state file.
 * Tracks multiple migrations for a single project.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MigrationState {
    /**
     * Schema version for the state file format.
     * Used to handle schema evolution and backward compatibility.
     * Current version: 1.0
     */
    private String schemaVersion;

    private String projectRoot;
    private Instant lastUpdated;
    private Map<String, MigrationExecutionState> migrations;

    public MigrationState() {
        this.schemaVersion = "1.0";
        this.migrations = new HashMap<>();
        this.lastUpdated = Instant.now();
    }

    public MigrationState(String projectRoot) {
        this();
        this.projectRoot = projectRoot;
    }

    // Getters and setters

    /**
     * Get the schema version of this state file.
     * 
     * @return Schema version string (e.g., "1.0")
     */
    public String getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * Set the schema version of this state file.
     * Should only be used during deserialization or schema migration.
     * 
     * @param schemaVersion Schema version string
     */
    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getProjectRoot() {
        return projectRoot;
    }

    public void setProjectRoot(String projectRoot) {
        this.projectRoot = projectRoot;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Map<String, MigrationExecutionState> getMigrations() {
        return migrations;
    }

    public void setMigrations(Map<String, MigrationExecutionState> migrations) {
        this.migrations = migrations;
    }

    /**
     * Get or create a migration execution state for a plan.
     *
     * @param planKey Key identifying the plan (typically plan filename)
     * @return Migration execution state
     */
    public MigrationExecutionState getMigration(String planKey) {
        return migrations.get(planKey);
    }

    /**
     * Add or update a migration execution state.
     *
     * @param planKey Key identifying the plan (typically plan filename)
     * @param state   Migration execution state
     */
    public void putMigration(String planKey, MigrationExecutionState state) {
        if (this.migrations == null) {
            this.migrations = new HashMap<>();
        }
        this.migrations.put(planKey, state);
        this.lastUpdated = Instant.now();
    }

    /**
     * Check if a migration exists in state.
     *
     * @param planKey Key identifying the plan
     * @return true if migration exists
     */
    public boolean hasMigration(String planKey) {
        return migrations != null && migrations.containsKey(planKey);
    }

    /**
     * Update last updated timestamp.
     */
    public void touch() {
        this.lastUpdated = Instant.now();
    }
}
