package com.analyzer.rules.ai;

import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.graph.ProjectFileRepository;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.dev.inspectors.bedrock.AbstractBedrockJavaClassInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-powered inspector that classifies Java code as either "business code" or
 * "framework code" using AWS Bedrock.
 * <p>
 * This inspector uses Large Language Models to analyze source code and
 * determine
 * whether it contains business logic (domain-specific code) or
 * framework/infrastructure
 * code (reusable technical components).
 * <p>
 * The inspector produces:
 * - A classification tag: TAG_CLASS_BUSINESS_TYPE or TAG_CLASS_FWK_TYPE
 * - Two metrics: METRIC_BUSINESS_SCORE and METRIC_FRAMEWORK_SCORE (0.0-1.0)
 * - Property: PROP_CLASSIFICATION_REASONING (explanation)
 * <p>
 * Classification criteria:
 * <p>
 * BUSINESS CODE indicators:
 * - Domain-specific terminology (e.g., Customer, Order, Invoice, Account)
 * - Business rules and validation logic
 * - Industry-specific calculations
 * - Use cases and workflows
 * - Entity/model classes with business meaning
 * - Service classes implementing business processes
 * <p>
 * FRAMEWORK CODE indicators:
 * - Generic utility classes (StringUtils, DateUtils, etc.)
 * - Connection pooling, caching, logging
 * - Base classes, abstract classes for reuse
 * - Configuration management
 * - Data access abstractions (DAO, Repository patterns)
 * - Transaction management
 * - Generic helpers and wrappers
 *
 * @author Java Architecture Analyzer
 */
@InspectorDependencies(requires = {
        InspectorTags.TAG_SOURCE_FILE_PRESENT
}, produces = {
        BusinessVsFrameworkClassifierInspector.TAG_CLASS_BUSINESS_TYPE,
        BusinessVsFrameworkClassifierInspector.TAG_CLASS_FWK_TYPE
})
public class BusinessVsFrameworkClassifierInspector extends AbstractBedrockJavaClassInspector {

    private static final Logger logger = LoggerFactory.getLogger(BusinessVsFrameworkClassifierInspector.class);

    // Tag constants
    public static final String TAG_CLASS_BUSINESS_TYPE = "CLASS_BUSINESS_TYPE";
    public static final String TAG_CLASS_FWK_TYPE = "CLASS_FWK_TYPE";

    // Metric constants - prefixed with "metric." and using snake_case
    public static final String METRIC_BUSINESS_SCORE = "metric.business_score";
    public static final String METRIC_FRAMEWORK_SCORE = "metric.framework_score";

    // Property constants - prefixed with PROP_
    public static final String PROP_CLASSIFICATION_REASONING = "classificationReasoning";

    // Patterns for parsing the AI response
    private static final Pattern CLASSIFICATION_PATTERN = Pattern.compile(
            "(?i)classification\\s*:\\s*(business|framework|fwk)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern BUSINESS_SCORE_PATTERN = Pattern.compile(
            "(?i)business[\\s_-]?score\\s*[:=]?\\s*([\\d]*\\.?[\\d]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FRAMEWORK_SCORE_PATTERN = Pattern.compile(
            "(?i)framework[\\s_-]?score\\s*[:=]?\\s*([\\d]*\\.?[\\d]+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Constructor with ResourceResolver and ProjectFileRepository
     */
    public BusinessVsFrameworkClassifierInspector(ResourceResolver resourceResolver,
            ProjectFileRepository projectFileRepository) {
        super(resourceResolver, projectFileRepository);
    }

    @Override
    public String getName() {
        return "BusinessVsFrameworkClassifierInspector";
    }

    @Override
    protected String buildPrompt(String content, JavaClassNode classNode) {
        String className = classNode.getSimpleName();
        String packageName = classNode.getPackageName();

        return """
                You are a senior software architect analyzing Java code to classify it as either:
                - BUSINESS code: Domain-specific logic, business rules, use cases
                - FRAMEWORK code: Generic infrastructure, utilities, reusable components

                Analyze the following Java class and provide:
                1. Classification: BUSINESS or FRAMEWORK
                2. Business Score: 0.0 to 1.0 (how likely it's business code)
                3. Framework Score: 0.0 to 1.0 (how likely it's framework code)
                4. Brief reasoning (2-3 sentences)

                BUSINESS CODE indicators:
                - Domain-specific classes (Customer, Order, Invoice, Payment, Account)
                - Business rules and validation
                - Industry-specific calculations
                - Use cases and workflows
                - Business process logic

                FRAMEWORK CODE indicators:
                - Generic utilities (StringUtils, DateUtils, CollectionUtils)
                - Connection management, caching, logging
                - Abstract base classes for reuse
                - DAO/Repository patterns (generic data access)
                - Transaction management
                - Configuration management
                - Helper classes with no business meaning

                Class Information:
                - Class Name: %s
                - Package: %s

                Source Code:
                ```java
                %s
                ```

                Provide your analysis in this format:
                Classification: [BUSINESS or FRAMEWORK]
                Business Score: [0.0-1.0]
                Framework Score: [0.0-1.0]
                Reasoning: [Your brief explanation]
                """.formatted(className, packageName, content);
    }

    @Override
    protected void parseResponse(String response, JavaClassNode classNode,
            NodeDecorator<JavaClassNode> decorator) {
        try {
            // Extract classification
            String classification = extractClassification(response);

            // Extract scores
            double businessScore = extractBusinessScore(response);
            double frameworkScore = extractFrameworkScore(response);

            // Validate scores
            if (businessScore < 0.0 || businessScore > 1.0) {
                logger.warn("Invalid business score: {}, defaulting to 0.5", businessScore);
                businessScore = 0.5;
            }

            if (frameworkScore < 0.0 || frameworkScore > 1.0) {
                logger.warn("Invalid framework score: {}, defaulting to 0.5", frameworkScore);
                frameworkScore = 0.5;
            }

            // If no clear classification from pattern, use scores
            if (classification == null) {
                classification = (businessScore > frameworkScore) ? TAG_CLASS_BUSINESS_TYPE : TAG_CLASS_FWK_TYPE;
            }

            // Set the classification tag on JavaClassNode
            decorator.enableTag(classification);

            // Set metrics on JavaClassNode (using proper metric.* naming)
            decorator.setMetric(METRIC_BUSINESS_SCORE, businessScore);
            decorator.setMetric(METRIC_FRAMEWORK_SCORE, frameworkScore);

            // Extract reasoning if available
            String reasoning = extractReasoning(response);
            if (reasoning != null && !reasoning.isEmpty()) {
                decorator.setProperty(PROP_CLASSIFICATION_REASONING, reasoning);
            }

            logger.info("Classified {} as {} (business: {}, framework: {})",
                    classNode.getFullyQualifiedName(),
                    classification,
                    String.format("%.2f", businessScore),
                    String.format("%.2f", frameworkScore));

        } catch (Exception e) {
            logger.error("Error parsing Bedrock response for {}: {}",
                    classNode.getFullyQualifiedName(), e.getMessage(), e);
            decorator.error("Failed to parse AI classification: " + e.getMessage());
        }
    }

    /**
     * Extract classification from AI response
     */
    private String extractClassification(String response) {
        Matcher matcher = CLASSIFICATION_PATTERN.matcher(response);
        if (matcher.find()) {
            String classification = matcher.group(1).toUpperCase();
            // Normalize "FWK" and "FRAMEWORK" to TAG_CLASS_FWK_TYPE
            if ("FRAMEWORK".equals(classification) || "FWK".equals(classification)) {
                return TAG_CLASS_FWK_TYPE;
            }
            if ("BUSINESS".equals(classification)) {
                return TAG_CLASS_BUSINESS_TYPE;
            }
        }
        return null;
    }

    /**
     * Extract business score from AI response
     */
    private double extractBusinessScore(String response) {
        Matcher matcher = BUSINESS_SCORE_PATTERN.matcher(response);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                logger.debug("Failed to parse business score: {}", matcher.group(1));
            }
        }

        // Fallback: try to infer from classification
        if (response.toLowerCase().contains("classification: business")) {
            return 0.8;
        } else if (response.toLowerCase().contains("classification: framework") ||
                response.toLowerCase().contains("classification: fwk")) {
            return 0.2;
        }

        return 0.5; // Default: uncertain
    }

    /**
     * Extract framework score from AI response
     */
    private double extractFrameworkScore(String response) {
        Matcher matcher = FRAMEWORK_SCORE_PATTERN.matcher(response);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                logger.debug("Failed to parse framework score: {}", matcher.group(1));
            }
        }

        // Fallback: try to infer from classification
        if (response.toLowerCase().contains("classification: framework") ||
                response.toLowerCase().contains("classification: fwk")) {
            return 0.8;
        } else if (response.toLowerCase().contains("classification: business")) {
            return 0.2;
        }

        return 0.5; // Default: uncertain
    }

    /**
     * Extract reasoning from AI response
     */
    @Nullable
    private String extractReasoning(String response) {
        // Try to find reasoning section
        Pattern reasoningPattern = Pattern.compile(
                "(?i)reasoning\\s*[:=]\\s*(.+?)(?=\\n\\n|$)",
                Pattern.DOTALL);

        Matcher matcher = reasoningPattern.matcher(response);
        if (matcher.find()) {
            String reasoning = matcher.group(1).trim();
            // Limit reasoning length
            if (reasoning.length() > 500) {
                reasoning = reasoning.substring(0, 497) + "...";
            }
            return reasoning;
        }

        return null;
    }
}
