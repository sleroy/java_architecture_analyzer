package com.analyzer.inspectors.rules.bedrock;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.inspectors.core.bedrock.BedrockInspector;
import com.analyzer.resource.ResourceResolver;

/**
 * Concrete implementation of BedrockInspector that uses AI to assess code
 * quality.
 * This inspector demonstrates how to use AWS Bedrock models for code analysis
 * by asking the AI to evaluate code quality on a scale from 1-10.
 * 
 * The inspector analyzes various aspects of code quality including:
 * - Code readability and clarity
 * - Following Java best practices
 * - Proper naming conventions
 * - Code organization and structure
 * - Appropriate use of design patterns
 */
public class CodeQualityInspector extends BedrockInspector {

    public static final String NAME = "Code Quality (AI)";
    public static final String COLUMN_NAME = "code_quality_ai";

    /**
     * Creates a CodeQualityInspector with the specified ResourceResolver.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     */
    public CodeQualityInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getColumnName() {
        return COLUMN_NAME;
    }

    @Override
    public String getDescription() {
        return "Uses AWS Bedrock AI models to assess Java code quality on a scale from 1-10";
    }

    @Override
    protected String buildPrompt(String content, Clazz clazz) {
        String basePrompt = "You are a senior Java developer performing a code quality assessment. " +
                "Please analyze the provided Java code and give it a quality score from 1 to 10, where:\n" +
                "- 1-3: Poor quality (major issues with readability, structure, or best practices)\n" +
                "- 4-6: Average quality (some issues but generally acceptable)\n" +
                "- 7-8: Good quality (well-written with minor improvements possible)\n" +
                "- 9-10: Excellent quality (exemplary code following all best practices)\n\n" +
                "Consider these factors:\n" +
                "- Code readability and clarity\n" +
                "- Proper naming conventions\n" +
                "- Method and class size appropriateness\n" +
                "- Following Java coding standards\n" +
                "- Appropriate use of language features\n" +
                "- Code organization and structure\n\n" +
                "Respond with ONLY the numeric score (1-10), no explanation needed.";

        return buildContextualPrompt(basePrompt, content, clazz);
    }

    @Override
    protected InspectorResult parseResponse(String response, Clazz clazz) {
        if (response == null || response.trim().isEmpty()) {
            return InspectorResult.error(getName(), "Empty response from AI model");
        }

        // Parse numeric response, defaulting to 5 (average) if parsing fails
        double score = parseNumericResponse(response, 5.0);

        // Validate score range
        if (score < 1.0 || score > 10.0) {
            logger.warn("AI model returned out-of-range score {} for class {}, clamping to valid range",
                    score, clazz.getFullyQualifiedName());
            score = Math.max(1.0, Math.min(10.0, score));
        }

        // Round to one decimal place for consistent formatting
        double roundedScore = Math.round(score * 10.0) / 10.0;

        logger.debug("Code quality assessment for {}: {}/10",
                clazz.getFullyQualifiedName(), roundedScore);

        return new InspectorResult(getName(), roundedScore);
    }

    @Override
    protected double parseNumericResponse(String response, double defaultValue) {
        if (response == null || response.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            // Clean the response to extract just the number
            String cleaned = response.trim()
                    .replaceAll("[^0-9.]", "") // Remove all non-numeric characters except decimal point
                    .replaceAll("\\.+", "."); // Replace multiple dots with single dot

            if (cleaned.isEmpty()) {
                return defaultValue;
            }

            // Handle case where there might be multiple numbers, take the first one
            String[] parts = cleaned.split("\\s+");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                double parsed = Double.parseDouble(parts[0]);

                // If the number seems too large, it might be a percentage (e.g., 75 instead of
                // 7.5)
                if (parsed > 10.0 && parsed <= 100.0) {
                    parsed = parsed / 10.0;
                }

                return parsed;
            }
        } catch (NumberFormatException e) {
            logger.debug("Failed to parse AI response as number: '{}', using default value", response);
        }

        return defaultValue;
    }
}
