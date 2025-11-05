package com.analyzer.migration.blocks.automated;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.BlockType;
import com.analyzer.migration.plan.MigrationBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes Maven commands with explicit JAVA_HOME and MAVEN_HOME control.
 * Provides better control over Maven execution environment than generic COMMAND
 * blocks.
 */
public class MavenBlock implements MigrationBlock {
    private static final Logger logger = LoggerFactory.getLogger(MavenBlock.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes

    private final String name;
    private final String goals;
    private final String javaHome;
    private final String mavenHome;
    private final String workingDirectory;
    private final Map<String, String> properties;
    private final String profiles;
    private final String mavenOpts;
    private final boolean offline;
    private final boolean captureOutput;
    private final long timeoutSeconds;
    private final String enableIf;

    private MavenBlock(Builder builder) {
        this.name = builder.name;
        this.goals = builder.goals;
        this.javaHome = builder.javaHome;
        this.mavenHome = builder.mavenHome;
        this.workingDirectory = builder.workingDirectory;
        this.properties = builder.properties;
        this.profiles = builder.profiles;
        this.mavenOpts = builder.mavenOpts;
        this.offline = builder.offline;
        this.captureOutput = builder.captureOutput;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.enableIf = builder.enableIf;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BlockResult execute(MigrationContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // Resolve variables
            String processedGoals = context.substituteVariables(goals);
            String processedWorkDir = workingDirectory != null
                    ? context.substituteVariables(workingDirectory)
                    : context.getProjectRoot().toString();

            // Build Maven command
            String mavenCmd = buildMavenCommand(context, processedGoals);

            logger.info("Executing Maven: {} in directory: {}", processedGoals, processedWorkDir);

            // Setup process with environment
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("sh", "-c", mavenCmd);
            processBuilder.directory(new File(processedWorkDir));
            processBuilder.redirectErrorStream(true);

            // Configure environment variables
            configureEnvironment(processBuilder, context);

            // Start process
            Process process = processBuilder.start();

            // Capture output if enabled
            List<String> outputLines = new ArrayList<>();
            if (captureOutput) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputLines.add(line);
                        logger.info("  | {}", line);
                    }
                }
            }

            // Wait for completion
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return BlockResult.failure(
                        "Maven timeout after " + timeoutSeconds + " seconds",
                        "Goals: " + processedGoals);
            }

            int exitCode = process.exitValue();
            long executionTime = System.currentTimeMillis() - startTime;

            // Build result
            BlockResult.Builder resultBuilder = BlockResult.builder()
                    .executionTimeMs(executionTime)
                    .outputVariable("exit_code", exitCode)
                    .outputVariable("goals", processedGoals);

            if (captureOutput && !outputLines.isEmpty()) {
                resultBuilder.outputVariable("output", String.join("\n", outputLines));
                resultBuilder.outputVariable("output_lines", outputLines);
            }

            if (exitCode == 0) {
                return resultBuilder
                        .success(true)
                        .message("Maven execution successful: " + processedGoals)
                        .build();
            } else {
                return resultBuilder
                        .success(false)
                        .message("Maven execution failed with exit code " + exitCode)
                        .errorDetails(String.join("\n", outputLines))
                        .build();
            }

        } catch (IOException e) {
            return BlockResult.failure(
                    "Failed to execute Maven",
                    "Error: " + e.getMessage() + "\nGoals: " + goals);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BlockResult.failure(
                    "Maven execution interrupted",
                    "Goals: " + goals);
        } catch (Exception e) {
            return BlockResult.failure(
                    "Unexpected error during Maven execution",
                    e.getMessage());
        }
    }

    /**
     * Build the complete Maven command with all options.
     */
    private String buildMavenCommand(MigrationContext context, String processedGoals) {
        StringBuilder cmd = new StringBuilder();

        // Determine Maven executable
        String mvnExecutable = determineMavenExecutable(context);
        cmd.append(mvnExecutable);

        // Add goals
        cmd.append(" ").append(processedGoals);

        // Add properties
        if (properties != null && !properties.isEmpty()) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                String key = context.substituteVariables(entry.getKey());
                String value = context.substituteVariables(entry.getValue());
                cmd.append(" -D").append(key).append("=").append(value);
            }
        }

        // Add profiles
        if (profiles != null && !profiles.isEmpty()) {
            String processedProfiles = context.substituteVariables(profiles);
            cmd.append(" -P").append(processedProfiles);
        }

        // Add offline flag
        if (offline) {
            cmd.append(" -o");
        }

        return cmd.toString();
    }

    /**
     * Determine which Maven executable to use.
     */
    private String determineMavenExecutable(MigrationContext context) {
        if (mavenHome != null && !mavenHome.trim().isEmpty()) {
            String resolved = context.substituteVariables(mavenHome);
            Path mvnPath = Paths.get(resolved, "bin", "mvn");
            if (Files.exists(mvnPath)) {
                return mvnPath.toString();
            }
            logger.warn("Maven executable not found at: {}. Falling back to system mvn.", mvnPath);
        }
        return "mvn"; // Use system Maven from PATH
    }

    /**
     * Configure environment variables for Maven execution.
     */
    private void configureEnvironment(ProcessBuilder processBuilder, MigrationContext context) {
        Map<String, String> env = processBuilder.environment();

        // Set JAVA_HOME
        if (javaHome != null && !javaHome.trim().isEmpty()) {
            String resolvedJavaHome = context.substituteVariables(javaHome);
            env.put("JAVA_HOME", resolvedJavaHome);
            // Prepend Java bin to PATH
            String currentPath = env.getOrDefault("PATH", "");
            env.put("PATH", resolvedJavaHome + "/bin:" + currentPath);
            logger.debug("Set JAVA_HOME to: {}", resolvedJavaHome);
        }

        // Set MAVEN_HOME / M2_HOME
        if (mavenHome != null && !mavenHome.trim().isEmpty()) {
            String resolvedMavenHome = context.substituteVariables(mavenHome);
            env.put("MAVEN_HOME", resolvedMavenHome);
            env.put("M2_HOME", resolvedMavenHome);
            logger.debug("Set MAVEN_HOME to: {}", resolvedMavenHome);
        }

        // Set MAVEN_OPTS
        if (mavenOpts != null && !mavenOpts.trim().isEmpty()) {
            String resolvedOpts = context.substituteVariables(mavenOpts);
            env.put("MAVEN_OPTS", resolvedOpts);
            logger.debug("Set MAVEN_OPTS to: {}", resolvedOpts);
        }
    }

    @Override
    public BlockType getType() {
        return BlockType.MAVEN;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getEnableIf() {
        return enableIf;
    }

    @Override
    public String toMarkdownDescription() {
        StringBuilder md = new StringBuilder();
        md.append("**").append(name).append("** (Maven)\n");
        md.append("- Goals: `").append(goals).append("`\n");
        if (javaHome != null) {
            md.append("- Java Home: `").append(javaHome).append("`\n");
        }
        if (mavenHome != null) {
            md.append("- Maven Home: `").append(mavenHome).append("`\n");
        }
        if (workingDirectory != null) {
            md.append("- Working Directory: `").append(workingDirectory).append("`\n");
        }
        if (properties != null && !properties.isEmpty()) {
            md.append("- Properties: ").append(properties.size()).append("\n");
        }
        if (profiles != null) {
            md.append("- Profiles: `").append(profiles).append("`\n");
        }
        md.append("- Timeout: ").append(timeoutSeconds).append(" seconds\n");
        return md.toString();
    }

    @Override
    public boolean validate() {
        if (goals == null || goals.trim().isEmpty()) {
            logger.error("Maven goals cannot be empty");
            return false;
        }
        if (timeoutSeconds <= 0) {
            logger.error("Timeout must be positive");
            return false;
        }
        return true;
    }

    @Override
    public String[] getRequiredVariables() {
        // Could extract variables from goals, properties, etc.
        return new String[0];
    }

    public static class Builder {
        private String name;
        private String goals;
        private String javaHome;
        private String mavenHome;
        private String workingDirectory;
        private Map<String, String> properties;
        private String profiles;
        private String mavenOpts;
        private boolean offline;
        private boolean captureOutput = true;
        private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private String enableIf;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder goals(String goals) {
            this.goals = goals;
            return this;
        }

        public Builder javaHome(String javaHome) {
            this.javaHome = javaHome;
            return this;
        }

        public Builder mavenHome(String mavenHome) {
            this.mavenHome = mavenHome;
            return this;
        }

        public Builder workingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder properties(Map<String, String> properties) {
            this.properties = properties;
            return this;
        }

        public Builder profiles(String profiles) {
            this.profiles = profiles;
            return this;
        }

        public Builder mavenOpts(String mavenOpts) {
            this.mavenOpts = mavenOpts;
            return this;
        }

        public Builder offline(boolean offline) {
            this.offline = offline;
            return this;
        }

        public Builder captureOutput(boolean captureOutput) {
            this.captureOutput = captureOutput;
            return this;
        }

        public Builder timeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder enableIf(String enableIf) {
            this.enableIf = enableIf;
            return this;
        }

        public MavenBlock build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (goals == null || goals.isEmpty()) {
                throw new IllegalStateException("Goals are required");
            }
            return new MavenBlock(this);
        }
    }
}
