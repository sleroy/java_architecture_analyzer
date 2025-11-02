package com.analyzer.cli.examples;

import picocli.CommandLine;
import com.analyzer.cli.AnalyzerCLI;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Example demonstrating how to use a properties file for variable
 * configuration.
 * 
 * This approach is useful for managing many variables or sharing configuration
 * across team members.
 */
public class PropertiesFileExample {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("Properties File Example");
        System.out.println("=".repeat(80));
        System.out.println();

        // Create a temporary properties file
        Path propsFile = Path.of("/tmp/migration-variables.properties");
        createSamplePropertiesFile(propsFile);

        System.out.println("Created sample properties file: " + propsFile);
        System.out.println("Content:");
        System.out.println("-".repeat(40));
        Files.lines(propsFile).forEach(line -> System.out.println("  " + line));
        System.out.println("-".repeat(40));
        System.out.println();

        // Build command arguments using the properties file
        String[] commandArgs = {
                "apply",
                "--project", "/tmp/test-ejb-project",
                "--plan", "migration-plans/jboss-to-springboot-phase0-1.yaml",
                "--variables", propsFile.toString(),
                "--dry-run",
                "--verbose",
                // You can still override specific variables via CLI
                "-Dspring_boot_version=3.2.0" // This will override the properties file value
        };

        // Execute the command
        int exitCode = new CommandLine(new AnalyzerCLI()).execute(commandArgs);

        System.out.println();
        System.out.println("Exit code: " + exitCode);
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("Variable Priority Order (highest to lowest):");
        System.out.println("  1. CLI -D flags (e.g., -Dspring_boot_version=3.2.0)");
        System.out.println("  2. CLI --variable flags");
        System.out.println("  3. Properties file (--variables option)");
        System.out.println("  4. YAML plan defaults");
        System.out.println("  5. Environment variables");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("To run this example:");
        System.out.println("  mvn exec:java -Dexec.mainClass=\"com.analyzer.cli.examples.PropertiesFileExample\"");
        System.out.println("=".repeat(80));
    }

    private static void createSamplePropertiesFile(Path path) throws IOException {
        try (FileWriter writer = new FileWriter(path.toFile())) {
            writer.write("# Migration Variables Configuration\n");
            writer.write("# Generated: " + java.time.LocalDateTime.now() + "\n\n");
            writer.write("# Spring Boot Configuration\n");
            writer.write("spring_boot_version=2.7.18\n");
            writer.write("java_version=8\n\n");
            writer.write("# Project Configuration\n");
            writer.write("group_id=com.example\n");
            writer.write("artifact_id=my-spring-app\n");
            writer.write("package_base=com.example.app\n\n");
            writer.write("# Migration Settings\n");
            writer.write("migration_branch_prefix=migration\n\n");
            writer.write("# Database Configuration (if needed)\n");
            writer.write("# DB_USER=\n");
            writer.write("# DB_PASSWORD=\n");
            writer.write("# DB_NAME=\n");
        }
        System.out.println("Properties file created successfully.");
    }
}
