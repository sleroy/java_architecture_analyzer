package com.analyzer.cli.examples;

import picocli.CommandLine;
import com.analyzer.cli.AnalyzerCLI;

/**
 * Example demonstrating how to perform a dry-run validation of a migration
 * plan.
 * 
 * This validates the plan and variables without executing any migration tasks.
 */
public class DryRunExample {

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("Dry Run Example - Validation Only");
        System.out.println("=".repeat(80));
        System.out.println();

        // Build command arguments with variable overrides
        String[] commandArgs = {
                "apply",
                "--project", "demo-ejb2-project", // Replace with your actual project path
                "--plan", "migration-plans/jboss-to-springboot-phase0-1.yaml",
                "--dry-run",
                "--verbose",
                // Override some variables
                "-Dspring_boot_version=3.2.0",
                "-Djava_version=17",
                "-Dgroup_id=com.example",
                "-Dartifact_id=my-spring-app"
        };

        // Execute the command
        int exitCode = new CommandLine(new AnalyzerCLI()).execute(commandArgs);

        System.out.println();
        System.out.println("Exit code: " + exitCode);
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("To run this example:");
        System.out.println("  mvn exec:java -Dexec.mainClass=\"com.analyzer.cli.examples.DryRunExample\"");
        System.out.println("=".repeat(80));
    }
}
