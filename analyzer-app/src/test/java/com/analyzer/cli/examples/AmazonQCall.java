package com.analyzer.cli.examples;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating robust Amazon Q CLI invocation with piped input.
 * This example shows proper process management, output capture, error handling,
 * and piping prompts to Amazon Q CLI stdin.
 */
public class AmazonQCall {

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: AmazonQCall <command> [prompt]");
            System.out.println("Examples:");
            System.out.println("  AmazonQCall login");
            System.out.println("  AmazonQCall --version");
            System.out.println("  AmazonQCall chat \"What is Java?\"");
            System.out.println("  AmazonQCall chat \"Explain Spring Boot migration from EJB\"");
            System.exit(1);
        }

        String command = args[0];
        String prompt = args.length > 1 ? args[1] : null;

        try {
            if ("chat".equals(command)) {
                if (prompt == null || prompt.trim().isEmpty()) {
                    System.err.println("Error: 'chat' command requires a prompt");
                    System.out.println("Usage: AmazonQCall chat \"Your prompt here\"");
                    System.exit(1);
                }
                executeAmazonQChat(prompt);
            } else {
                // For other commands (login, --version, etc.), execute without piping
                executeAmazonQCommand(new String[] { "q", command });
            }
        } catch (Exception e) {
            System.err.println("Failed to execute Amazon Q CLI: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Execute Amazon Q chat command with piped prompt input.
     */
    private static void executeAmazonQChat(String prompt) throws IOException, InterruptedException {
        System.out.println("Executing Amazon Q CLI chat with piped prompt");
        System.out.println("Prompt length: " + prompt.length() + " characters");
        System.out.println("Prompt preview: " + (prompt.length() > 100 ? prompt.substring(0, 100) + "..." : prompt));

        // Build the command for interactive chat mode
        ProcessBuilder processBuilder = new ProcessBuilder("q", "chat");
        // Don't merge stderr into stdout - capture them separately
        processBuilder.redirectErrorStream(false);

        // Set environment to ensure proper execution
        processBuilder.environment().put("CI", "true");

        // Start the process
        Process process = processBuilder.start();

        // Capture stdout and stderr separately
        StringBuilder stdoutOutput = new StringBuilder();
        StringBuilder stderrOutput = new StringBuilder();

        // Read stdout in a separate thread
        Thread stdoutReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdoutOutput.append(line).append("\n");
                    System.out.println("[STDOUT] " + line);
                }
            } catch (IOException e) {
                System.err.println("Stdout reading interrupted: " + e.getMessage());
            }
        });

        // Read stderr in a separate thread
        Thread stderrReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderrOutput.append(line).append("\n");
                    System.err.println("[STDERR] " + line);
                }
            } catch (IOException e) {
                System.err.println("Stderr reading interrupted: " + e.getMessage());
            }
        });

        // Write prompt to stdin in a separate thread
        Thread stdinWriter = new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {

                System.out.println("[STDIN] Writing prompt to Amazon Q CLI...");
                writer.write(prompt);
                writer.newLine();
                writer.flush();

                // Close stdin to signal end of input
                writer.close();
                System.out.println("[STDIN] Prompt sent and stdin closed");

            } catch (IOException e) {
                System.err.println("Stdin writing failed: " + e.getMessage());
            }
        });

        stdoutReader.start();
        stderrReader.start();
        stdinWriter.start();

        // Wait for process to complete with timeout
        boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            System.err.println(
                    "Amazon Q CLI timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds, forcibly terminating");
            process.destroyForcibly();
            stdoutReader.interrupt();
            stderrReader.interrupt();
            stdinWriter.interrupt();

            // Give the forcible termination a moment to complete
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            throw new IOException("Amazon Q CLI timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds");
        }

        // Wait for all threads to complete
        try {
            stdoutReader.join(5000); // Wait max 5 seconds for stdout reading to complete
            stderrReader.join(5000); // Wait max 5 seconds for stderr reading to complete
            stdinWriter.join(1000); // Wait max 1 second for stdin writing to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Process results
        processResults(process.exitValue(), stdoutOutput.toString().trim(), stderrOutput.toString().trim());
    }

    /**
     * Execute Amazon Q command without piping (for login, --version, etc.).
     */
    private static void executeAmazonQCommand(String[] command) throws IOException, InterruptedException {
        System.out.println("Executing Amazon Q CLI: " + String.join(" ", command));

        // Build the process with proper configuration
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        // Don't merge stderr into stdout - capture them separately
        processBuilder.redirectErrorStream(false);

        // Set environment to ensure proper execution
        processBuilder.environment().put("CI", "true");

        // Start the process
        Process process = processBuilder.start();

        // Capture stdout and stderr separately
        StringBuilder stdoutOutput = new StringBuilder();
        StringBuilder stderrOutput = new StringBuilder();

        // Read stdout in a separate thread
        Thread stdoutReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdoutOutput.append(line).append("\n");
                    System.out.println("[STDOUT] " + line);
                }
            } catch (IOException e) {
                System.err.println("Stdout reading interrupted: " + e.getMessage());
            }
        });

        // Read stderr in a separate thread
        Thread stderrReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderrOutput.append(line).append("\n");
                    System.err.println("[STDERR] " + line);
                }
            } catch (IOException e) {
                System.err.println("Stderr reading interrupted: " + e.getMessage());
            }
        });

        stdoutReader.start();
        stderrReader.start();

        // Wait for process to complete with timeout
        boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            System.err.println(
                    "Amazon Q CLI timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds, forcibly terminating");
            process.destroyForcibly();
            stdoutReader.interrupt();
            stderrReader.interrupt();

            // Give the forcible termination a moment to complete
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            throw new IOException("Amazon Q CLI timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds");
        }

        // Wait for both readers to complete
        try {
            stdoutReader.join(5000); // Wait max 5 seconds for stdout reading to complete
            stderrReader.join(5000); // Wait max 5 seconds for stderr reading to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Process results
        processResults(process.exitValue(), stdoutOutput.toString().trim(), stderrOutput.toString().trim());
    }

    /**
     * Process and display the execution results.
     */
    private static void processResults(int exitCode, String stdoutContent, String stderrContent) throws IOException {
        System.out.println("\n=== Amazon Q CLI Execution Results ===");
        System.out.println("Exit Code: " + exitCode);
        System.out.println("Stdout Length: " + stdoutContent.length() + " characters");
        System.out.println("Stderr Length: " + stderrContent.length() + " characters");

        if (exitCode != 0) {
            // Log stderr content at error level if process failed
            if (!stderrContent.isEmpty()) {
                System.err.println("\nStderr Output:");
                System.err.println(stderrContent);
            }

            String errorMessage = String.format("Amazon Q CLI failed with exit code %d", exitCode);
            throw new IOException(errorMessage);
        }

        // Show warnings if stderr has content even on success
        if (!stderrContent.isEmpty()) {
            System.err.println("\nWarning - Amazon Q CLI completed with stderr output:");
            System.err.println(stderrContent);
        }

        if (stdoutContent.isEmpty() && stderrContent.isEmpty()) {
            System.out.println("Amazon Q CLI completed successfully with no output");
        } else {
            System.out.println("Amazon Q CLI completed successfully");
            if (!stdoutContent.isEmpty()) {
                System.out.println("\nFinal Response:");
                System.out.println("================");
                System.out.println(stdoutContent);
                System.out.println("================");
            }
        }
    }
}
