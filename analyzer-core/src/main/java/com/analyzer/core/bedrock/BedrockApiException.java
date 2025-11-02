package com.analyzer.core.bedrock;

/**
 * Exception thrown when Bedrock API calls fail.
 * This includes HTTP errors, parsing errors, rate limiting, and other
 * API-related issues.
 */
public class BedrockApiException extends Exception {

    /**
     * Creates a new BedrockApiException with the specified message.
     * 
     * @param message the detail message explaining the API error
     */
    public BedrockApiException(String message) {
        super(message);
    }

    /**
     * Creates a new BedrockApiException with the specified message and cause.
     * 
     * @param message the detail message explaining the API error
     * @param cause   the underlying cause of the API error
     */
    public BedrockApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
