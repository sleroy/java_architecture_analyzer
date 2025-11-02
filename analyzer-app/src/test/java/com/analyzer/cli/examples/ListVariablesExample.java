package com.analyzer.cli.examples;

import picocli.CommandLine;
import com.analyzer.cli.AnalyzerCLI;

/**
 * Example demonstrating how to list variables from a migration plan.
 * 
 * This example shows the variables defined in the JBoss-to-SpringBoot migration
 * plan.
 */
public class ListVariablesExample {

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("List Variables Example");
        System.out.println("=".repeat(80));
        System.out.println();

        // Build command arguments
        String[] commandArgs = {
                "apply",
                "--project", "/tmp/test-ejb-project", // Replace with your actual project path
                "--plan", "migration-plans/jboss-to-springboot-phase0-1.yaml",
                "--list-variables"
        };

        // Execute the command
        int exitCode = new CommandLine(new AnalyzerCLI()).execute(commandArgs);

        System.out.println();
        System.out.println("Exit code: " + exitCode);
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("To run this example:");
        System.out.println("  mvn exec:java -Dexec.mainClass=\"com.analyzer.cli.examples.ListVariablesExample\"");
        System.out.println("=".repeat(80));
    }
}
