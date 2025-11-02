package com.analyzer.core.bedrock;

/**
 * Exception thrown when Bedrock configuration is invalid or incomplete.
 * This is a checked exception that forces callers to handle configuration
 * issues explicitly.
 */
public class BedrockConfigurationException extends Exception {

    /**
     * Creates a new BedrockConfigurationException with the specified message.
     * 
     * @param message the detail message explaining the configuration issue
     */
    public BedrockConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates a new BedrockConfigurationException with the specified message and
     * cause.
     * 
     * @param message the detail message explaining the configuration issue
     * @param cause   the underlying cause of the configuration problem
     */
    public BedrockConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
