package com.analyzer.cli;

import com.analyzer.migration.loader.MigrationPlanConverter;
import com.analyzer.migration.loader.YamlMigrationPlanLoader;
import com.analyzer.migration.plan.MigrationPlan;
import com.analyzer.migration.plan.Phase;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Utility class to print migration plan phases with task counts.
 */
public class PrintMigrationPhases {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java PrintMigrationPhases <path-to-yaml-plan>");
            System.exit(1);
        }

        String planPath = args[0];

        try {
            // Create loader with converter (null repository since we're just reading plan
            // structure)
            MigrationPlanConverter converter = new MigrationPlanConverter(null);
            YamlMigrationPlanLoader loader = new YamlMigrationPlanLoader(converter);

            // Load the migration plan
            System.out.println("Loading migration plan from: " + planPath);
            System.out.println();

            MigrationPlan plan = loader.loadFromPath(Paths.get(planPath));

            // Print plan information
            System.out.println("========================================");
            System.out.println("Migration Plan: " + plan.getName());
            System.out.println("Version: " + plan.getVersion());
            System.out.println("========================================");
            System.out.println();

            // Print phases with task counts
            System.out.println("Phases:");
            System.out.println("----------------------------------------");

            int phaseNumber = 0;
            for (Phase phase : plan.getPhases()) {
                phaseNumber++;
                int taskCount = phase.getTasks() != null ? phase.getTasks().size() : 0;

                System.out.printf("%2d. %-50s (%d tasks)%n",
                        phaseNumber,
                        phase.getName(),
                        taskCount);
            }

            System.out.println("----------------------------------------");
            System.out.println("Total phases: " + plan.getPhases().size());

        } catch (IOException e) {
            System.err.println("Error loading migration plan: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
