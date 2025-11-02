package com.analyzer.migration.blocks.ai;

/**
 * Enumeration of supported response formats for AI blocks.
 * Determines how the AI response should be parsed and processed.
 */
public enum ResponseFormat {
    /**
     * Return the raw AI response text as-is.
     * Most flexible option - no parsing or validation.
     */
    TEXT,

    /**
     * Parse and validate response as JSON.
     * Ensures the response is valid JSON and returns it as a parsed object.
     * Useful when the AI is expected to return structured data.
     */
    JSON,

    /**
     * Parse response using model-specific structured parsing.
     * Extracts specific fields based on the model type and response format.
     * Most intelligent option for getting clean, structured data.
     */
    STRUCTURED
}
