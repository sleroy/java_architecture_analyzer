package com.analyzer.cli;

import com.analyzer.migration.state.MigrationStateManager;
import com.analyzer.migration.state.model.MigrationExecutionState;
import com.analyzer.migration.state.model.MigrationState;
import com.analyzer.migration.state.model.PhaseExecutionRecord;
import com.analyzer.migration.state.model.TaskExecutionDetail;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * CLI command to display migration execution history.
 * Shows execution history from the state file with optional filtering and
 * detail levels.
 */
@Command(name = "migration-history", description = "Display migration execution history", mixinStandardHelpOptions = true)
public class MigrationHistoryCommand implements Callable<Integer> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Option(names = { "--project" }, description = "Project root path", required = true)
    private String projectPath;

    @Option(names = { "--plan" }, description = "Filter by specific migration plan")
    private String planFilter;

    @Option(names = { "--verbose" }, description = "Show detailed output including task details")
    private boolean verbose;

    @Option(names = { "--last" }, description = "Show only the last N executions")
    private Integer lastN;

    @Override
    public Integer call() {
        try {
            MigrationStateManager stateManager = new MigrationStateManager(Paths.get(projectPath));

            if (!stateManager.exists()) {
                System.out.println("No migration state file found at: " + projectPath);
                System.out.println("Run a migration first to create execution history.");
                return 0;
            }

            MigrationState state = stateManager.loadState();

            if (state.getMigrations().isEmpty()) {
                System.out.println("No migration executions found.");
                return 0;
            }

            displayHeader(state);

            if (planFilter != null) {
                // Display history for specific plan
                MigrationExecutionState migrationState = state.getMigration(planFilter);
                if (migrationState == null) {
                    System.err.println("Migration plan not found: " + planFilter);
                    return 1;
                }
                displayMigrationHistory(planFilter, migrationState);
            } else {
                // Display history for all migrations
                for (String planKey : state.getMigrations().keySet()) {
                    MigrationExecutionState migrationState = state.getMigration(planKey);
                    displayMigrationHistory(planKey, migrationState);
                    System.out.println();
                }
            }

            return 0;
        } catch (IOException e) {
            System.err.println("Error reading migration state: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private void displayHeader(MigrationState state) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  Migration Execution History");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("Project:      " + state.getProjectRoot());
        System.out.println("Last Updated: " + formatInstant(state.getLastUpdated()));
        System.out.println("Migrations:   " + state.getMigrations().size());
        System.out.println();
    }

    private void displayMigrationHistory(String planKey, MigrationExecutionState migrationState) {
        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.println("Migration: " + migrationState.getPlanName());
        System.out.println("───────────────────────────────────────────────────────────────");
        System.out.println();
        System.out.println("Plan Key:    " + planKey);
        System.out.println("Version:     " + migrationState.getPlanVersion());
        System.out.println("Status:      " + migrationState.getStatus());

        if (migrationState.getStartedAt() != null) {
            System.out.println("Started:     " + formatInstant(migrationState.getStartedAt()));
        }

        if (migrationState.getLastExecuted() != null) {
            System.out.println("Last Run:    " + formatInstant(migrationState.getLastExecuted()));
        }

        System.out.println("Completed:   " + migrationState.getCompletedPhases().size() + " phases");
        if (!migrationState.getFailedPhases().isEmpty()) {
            System.out.println("Failed:      " + migrationState.getFailedPhases().size() + " phases");
        }

        if (migrationState.getExecutionHistory() == null || migrationState.getExecutionHistory().isEmpty()) {
            System.out.println();
            System.out.println("No execution history recorded yet.");
            return;
        }

        System.out.println();
        displayExecutionHistory(migrationState.getExecutionHistory());
    }

    private void displayExecutionHistory(List<PhaseExecutionRecord> history) {
        System.out.println("Execution History:");
        System.out.println();

        // Filter and sort history
        List<PhaseExecutionRecord> filteredHistory = history;
        if (lastN != null && lastN > 0) {
            filteredHistory = history.stream()
                    .sorted(Comparator.comparing(PhaseExecutionRecord::getTimestamp).reversed())
                    .limit(lastN)
                    .collect(Collectors.toList());
        }

        for (PhaseExecutionRecord record : filteredHistory) {
            displayPhaseRecord(record);
        }
    }

    private void displayPhaseRecord(PhaseExecutionRecord record) {
        String statusIcon = getStatusIcon(record.getStatus());
        String timestamp = formatInstant(record.getTimestamp());
        String duration = formatDuration(record.getDurationMs());

        System.out.printf("%s [%s] Phase: %s%n", statusIcon, timestamp, record.getPhaseName());
        System.out.printf("   Duration:  %s%n", duration);
        System.out.printf("   Tasks:     %d completed, %d failed%n",
                record.getTasksCompleted(), record.getTasksFailed());

        if (record.getSummary() != null && !record.getSummary().isEmpty()) {
            System.out.printf("   Summary:   %s%n", record.getSummary());
        }

        if (verbose && record.getTaskDetails() != null && !record.getTaskDetails().isEmpty()) {
            System.out.println("   Task Details:");
            for (TaskExecutionDetail task : record.getTaskDetails()) {
                displayTaskDetail(task);
            }
        }

        System.out.println();
    }

    private void displayTaskDetail(TaskExecutionDetail task) {
        String statusIcon = getStatusIcon(task.getStatus());
        String duration = task.getDurationMs() != null ? formatDuration(task.getDurationMs()) : "N/A";

        System.out.printf("      %s Task: %s (ID: %s)%n", statusIcon, task.getTaskName(), task.getTaskId());
        System.out.printf("         Duration: %s%n", duration);

        if (task.getOutputSummary() != null && !task.getOutputSummary().isEmpty()) {
            System.out.printf("         Summary:  %s%n", task.getOutputSummary());
        }

        if (task.getErrorMessage() != null && !task.getErrorMessage().isEmpty()) {
            System.out.printf("         Error:    %s%n", task.getErrorMessage());
        }

        if (verbose && task.getOutputDetail() != null && !task.getOutputDetail().isEmpty()) {
            System.out.println("         Detailed Output:");
            // Indent each line of detailed output
            String[] lines = task.getOutputDetail().split("\n");
            for (String line : lines) {
                System.out.println("            " + line);
            }
        }
    }

    private String getStatusIcon(com.analyzer.migration.state.model.ExecutionStatus status) {
        switch (status) {
            case SUCCESS:
                return "✓";
            case FAILED:
                return "✗";
            case IN_PROGRESS:
                return "◐";
            case INTERRUPTED:
                return "⊗";
            case PENDING:
            default:
                return "○";
        }
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        return FORMATTER.format(instant);
    }

    private String formatDuration(Long durationMs) {
        if (durationMs == null || durationMs == 0) {
            return "0s";
        }

        Duration duration = Duration.ofMillis(durationMs);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        long millis = duration.toMillisPart();

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || minutes > 0 || hours > 0) {
            sb.append(seconds).append("s");
        } else {
            sb.append(millis).append("ms");
        }

        return sb.toString().trim();
    }
}
