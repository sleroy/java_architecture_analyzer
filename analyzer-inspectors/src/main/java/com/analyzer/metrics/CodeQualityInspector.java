package com.analyzer.rules.metrics;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.inspector.InspectorDependencies;

import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.bedrock.AbstractBedrockInspectorAbstract;
import com.analyzer.api.resource.ResourceResolver;

/**
 * Concrete implementation of AbstractBedrockInspectorAbstract that uses AI to
 * assess code
 * quality.
 * This inspector demonstrates how to use AWS Bedrock models for code analysis
 * by asking the AI to evaluate code quality on a scale from 1-10.
 * <p>
 * The inspector analyzes various aspects of code quality including:
 * - Code readability and clarity
 * - Following Java best practices
 * - Proper naming conventions
 * - Code organization and structure
 * - Appropriate use of design patterns
 */
@InspectorDependencies(produces = { CodeQualityInspector.TAG_CODE_QUALITY_AI })
public class CodeQualityInspector extends AbstractBedrockInspectorAbstract {

    public static final String NAME = "Code Quality (AI)";
    public static final String TAG_CODE_QUALITY_AI = "code_quality_ai";

    /**
     * Creates a CodeQualityInspector with the specified
     * ResourceResolver.
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

    public String getColumnName() {
        return TAG_CODE_QUALITY_AI;
    }

    @Override
    protected String buildPrompt(String content, ProjectFile clazz) {
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
    protected void parseResponse(String response, ProjectFile clazz, NodeDecorator<ProjectFile> projectFileDecorator) {
        if (response == null || response.trim().isEmpty()) {
            projectFileDecorator.error("Empty response from AI model");
            return;
        }

        // Parse numeric response, defaulting to 5 (average) if parsing fails
        double score = parseNumericResponse(response, 5.0);

        // Validate score range
        if (score < 1.0 || score > 10.0) {
            logger.warn("AI model returned out-of-range score {} for class {}, clamping to valid range",
                    score, clazz.getProperty("fullyQualifiedName"));
            score = Math.max(1.0, Math.min(10.0, score));
        }

        // Round to one decimal place for consistent formatting
        double roundedScore = Math.round(score * 10.0) / 10.0;

        logger.debug("Code quality assessment for {}: {}/10",
                clazz.getProperty("fullyQualifiedName"), roundedScore);

        projectFileDecorator.setProperty(getColumnName(), roundedScore);
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
