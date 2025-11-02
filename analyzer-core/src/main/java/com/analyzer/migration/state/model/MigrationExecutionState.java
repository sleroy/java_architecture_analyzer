package com.analyzer.migration.state.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * State for a single migration execution.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MigrationExecutionState {
    private String planName;
    private String planVersion;
    private String planPath;
    private ExecutionStatus status;
    private Instant startedAt;
    private Instant lastExecuted;
    private String currentPhase;
    private String nextPhase;
    private List<String> completedPhases;
    private List<String> failedPhases;
    private Map<String, Object> variables;
    private List<PhaseExecutionRecord> executionHistory;

    public MigrationExecutionState() {
        this.completedPhases = new ArrayList<>();
        this.failedPhases = new ArrayList<>();
        this.variables = new HashMap<>();
        this.executionHistory = new ArrayList<>();
    }

    public MigrationExecutionState(String planName, String planVersion, String planPath) {
        this();
        this.planName = planName;
        this.planVersion = planVersion;
        this.planPath = planPath;
        this.status = ExecutionStatus.PENDING;
    }

    // Getters and setters

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getPlanVersion() {
        return planVersion;
    }

    public void setPlanVersion(String planVersion) {
        this.planVersion = planVersion;
    }

    public String getPlanPath() {
        return planPath;
    }

    public void setPlanPath(String planPath) {
        this.planPath = planPath;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getLastExecuted() {
        return lastExecuted;
    }

    public void setLastExecuted(Instant lastExecuted) {
        this.lastExecuted = lastExecuted;
    }

    public String getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(String currentPhase) {
        this.currentPhase = currentPhase;
    }

    public String getNextPhase() {
        return nextPhase;
    }

    public void setNextPhase(String nextPhase) {
        this.nextPhase = nextPhase;
    }

    public List<String> getCompletedPhases() {
        return completedPhases;
    }

    public void setCompletedPhases(List<String> completedPhases) {
        this.completedPhases = completedPhases;
    }

    public List<String> getFailedPhases() {
        return failedPhases;
    }

    public void setFailedPhases(List<String> failedPhases) {
        this.failedPhases = failedPhases;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public List<PhaseExecutionRecord> getExecutionHistory() {
        return executionHistory;
    }

    public void setExecutionHistory(List<PhaseExecutionRecord> executionHistory) {
        this.executionHistory = executionHistory;
    }

    public void addExecutionRecord(PhaseExecutionRecord record) {
        if (this.executionHistory == null) {
            this.executionHistory = new ArrayList<>();
        }
        this.executionHistory.add(record);
    }

    public void addCompletedPhase(String phaseId) {
        if (this.completedPhases == null) {
            this.completedPhases = new ArrayList<>();
        }
        if (!this.completedPhases.contains(phaseId)) {
            this.completedPhases.add(phaseId);
        }
    }

    public void addFailedPhase(String phaseId) {
        if (this.failedPhases == null) {
            this.failedPhases = new ArrayList<>();
        }
        if (!this.failedPhases.contains(phaseId)) {
            this.failedPhases.add(phaseId);
        }
    }
}
