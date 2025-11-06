package com.analyzer.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Amazon Q CLI backend implementation.
 * Executes the 'q chat' command with piped input.
 */
public class AmazonQBackend implements AiBackend {
    private static final Logger logger = LoggerFactory.getLogger(AmazonQBackend.class);

    @Override
    public String executePrompt(final String prompt, final Path workingDirectory, final int timeoutSeconds)
            throws IOException, InterruptedException {
        logger.debug("Executing Amazon Q CLI with piped input for prompt: {} characters, working directory: {}",
                prompt.length(), workingDirectory);

        if (!workingDirectory.toFile().isDirectory()) {
            throw new IllegalArgumentException("Working directory does not exist: " + workingDirectory);
        }

        // Build the command for non-interactive chat mode
        final ProcessBuilder processBuilder = new ProcessBuilder("q", "chat", "--no-interactive", "--trust-all-tools");
        processBuilder.redirectErrorStream(false);
        processBuilder.directory(workingDirectory.toFile());

        // Set environment to ensure proper execution
        processBuilder.environment().put("CI", "true");

        // Start the process
        final Process process = processBuilder.start();

        // Capture stdout and stderr separately
        final StringBuilder stdoutOutput = new StringBuilder();
        final StringBuilder stderrOutput = new StringBuilder();

        // Read stdout in a separate thread
        final Thread stdoutReader = new Thread(() -> {
            try (final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdoutOutput.append(line).append("\n");
                    logger.info("Amazon Q stdout: {}", line);
                }
            } catch (final IOException e) {
                logger.debug("Stdout reading interrupted: {}", e.getMessage());
            }
        });

        // Read stderr in a separate thread
        final Thread stderrReader = new Thread(() -> {
            try (final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderrOutput.append(line).append("\n");
                    logger.warn("Amazon Q stderr: {}", line);
                }
            } catch (final IOException e) {
                logger.debug("Stderr reading interrupted: {}", e.getMessage());
            }
        });

        // Write prompt to stdin in a separate thread
        final Thread stdinWriter = new Thread(() -> {
            try (final BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {

                logger.info("Writing prompt to Amazon Q CLI stdin");
                writer.write(prompt);
                writer.newLine();
                writer.flush();

                // Close stdin to signal end of input
                writer.close();
                logger.info("Prompt sent and stdin closed");

            } catch (final IOException e) {
                logger.warn("Stdin writing failed: {}", e.getMessage());
            }
        });

        stdoutReader.start();
        stderrReader.start();
        stdinWriter.start();

        // Wait for process to complete with timeout
        final boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        if (!finished) {
            logger.warn("Amazon Q CLI timed out after {} seconds, forcibly terminating", timeoutSeconds);
            process.destroyForcibly();
            stdoutReader.interrupt();
            stderrReader.interrupt();
            stdinWriter.interrupt();

            // Give the forcible termination a moment to complete
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            throw new IOException("Amazon Q CLI timed out after " + timeoutSeconds + " seconds");
        }

        // Wait for all threads to complete
        try {
            stdoutReader.join(5000);
            stderrReader.join(5000);
            stdinWriter.join(1000);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check exit code
        final int exitCode = process.exitValue();
        final String stdoutContent = stdoutOutput.toString().trim();
        final String stderrContent = stderrOutput.toString().trim();

        if (exitCode != 0) {
            if (!stderrContent.isEmpty()) {
                logger.error("Amazon Q CLI stderr output: {}", stderrContent);
            }

            final String errorMessage = String.format("Amazon Q CLI failed with exit code %d. Stdout: %s, Stderr: %s",
                    exitCode, stdoutContent, stderrContent);
            throw new IOException(errorMessage);
        }

        // Log stderr content at warn level even if process succeeded
        if (!stderrContent.isEmpty()) {
            logger.warn("Amazon Q CLI completed with stderr output: {}", stderrContent);
        }

        logger.info("Amazon Q CLI completed successfully, stdout length: {} characters, stderr length: {} characters",
                stdoutContent.length(), stderrContent.length());

        // Use stdout as the main response
        if (stdoutContent.isEmpty()) {
            if (!stderrContent.isEmpty()) {
                throw new IOException("Amazon Q CLI completed but returned no stdout output. Stderr: " + stderrContent);
            } else {
                throw new IOException("Amazon Q CLI completed but returned no output");
            }
        }

        return stdoutContent;
    }

    @Override
    public boolean isAvailable() {
        try {
            final ProcessBuilder pb = new ProcessBuilder("q", "--version");
            final Process process = pb.start();
            final boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (final IOException | InterruptedException e) {
            logger.debug("Amazon Q CLI not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public AiBackendType getType() {
        return AiBackendType.AMAZON_Q;
    }

    @Override
    public String getCliCommand() {
        return "q chat --no-interactive --trust-all-tools";
    }
}
