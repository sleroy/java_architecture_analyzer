package com.analyzer.ai;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for AI backend providers.
 * Supports different CLI-based AI tools like Amazon Q and Google Gemini.
 */
public interface AiBackend {

    /**
     * Execute a prompt using the AI backend CLI tool.
     *
     * @param prompt           The prompt to send to the AI
     * @param workingDirectory The working directory for the AI process
     * @param timeoutSeconds   Timeout in seconds for the AI operation
     * @return The AI response as a string
     * @throws IOException          If an I/O error occurs
     * @throws InterruptedException If the operation is interrupted
     */
    String executePrompt(String prompt, Path workingDirectory, int timeoutSeconds)
            throws IOException, InterruptedException;

    /**
     * Check if the AI backend CLI tool is available on the system.
     *
     * @return true if the CLI tool is installed and accessible, false otherwise
     */
    boolean isAvailable();

    /**
     * Get the backend type.
     *
     * @return The AiBackendType enum value
     */
    AiBackendType getType();

    /**
     * Get the CLI command used by this backend (for logging/debugging).
     *
     * @return The CLI command string
     */
    String getCliCommand();
}
