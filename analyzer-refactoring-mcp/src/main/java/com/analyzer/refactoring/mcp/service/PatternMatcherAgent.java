package com.analyzer.refactoring.mcp.service;

import com.analyzer.core.bedrock.BedrockApiClient;
import com.analyzer.core.bedrock.BedrockApiException;
import com.analyzer.core.bedrock.BedrockConfig;
import com.analyzer.core.bedrock.BedrockResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-powered agent that semantically matches user pattern descriptions
 * against available patterns (templates and cached scripts).
 * 
 * Uses Bedrock for intelligent matching instead of simple keyword matching,
 * enabling better reuse of existing patterns and reducing generation costs.
 * 
 * Benefits:
 * - Semantic understanding of pattern intent
 * - Better cache reuse (90% cost savings)
 * - Learns over time as cache grows
 * - Small classification cost (~$0.001) vs full generation (~$0.01)
 */
@Service
public class PatternMatcherAgent {

    private static final Logger logger = LoggerFactory.getLogger(PatternMatcherAgent.class);

    private final BedrockApiClient bedrockClient;
    private final int confidenceThreshold;
    private final boolean enabled;

    public PatternMatcherAgent(
            @Value("${pattern.matcher.model:anthropic.claude-3-5-sonnet-20241022-v2:0}") String model,
            @Value("${pattern.matcher.temperature:0.0}") double temperature,
            @Value("${pattern.matcher.confidence-threshold:80}") int confidenceThreshold,
            @Value("${pattern.matcher.enabled:true}") boolean enabled,
            @Value("${groovy.bedrock.timeout.seconds:30}") int timeoutSeconds,
            @Value("${groovy.bedrock.aws.region}") String awsRegion,
            @Value("${groovy.bedrock.aws.access-key}") String awsAccessKey,
            @Value("${groovy.bedrock.aws.secret-key}") String awsSecretKey) {

        this.confidenceThreshold = confidenceThreshold;
        this.enabled = enabled;

        // Configure Bedrock client for pattern matching
        BedrockConfig config = createBedrockConfig(
                model, temperature, timeoutSeconds,
                awsRegion, awsAccessKey, awsSecretKey);

        this.bedrockClient = new BedrockApiClient(config);

        logger.info("PatternMatcherAgent initialized: enabled={}, model={}, confidenceThreshold={}",
                enabled, model, confidenceThreshold);
    }

    /**
     * Match natural language query to actual tag names using AI.
     * 
     * @param naturalLanguageQuery User's natural language query (e.g., "stateless
     *                             EJBs")
     * @param availableTags        Set of all tag names in the database
     * @return List of matched tag names
     */
    public List<String> matchTagsToQuery(String naturalLanguageQuery, java.util.Set<String> availableTags) {
        if (!enabled) {
            logger.debug("PatternMatcherAgent disabled, returning empty list");
            return new ArrayList<>();
        }

        if (availableTags.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            logger.info("AI matching query '{}' against {} tags", naturalLanguageQuery, availableTags.size());

            String prompt = buildTagMatchingPrompt(naturalLanguageQuery, availableTags);
            BedrockResponse response = bedrockClient.invokeModel(prompt);

            List<String> matchedTags = parseTagMatchResult(response.getText(), availableTags);

            logger.info("AI matched to {} tags: {}", matchedTags.size(), matchedTags);
            return matchedTags;

        } catch (BedrockApiException e) {
            logger.error("Bedrock API error during tag matching", e);
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("Unexpected error during tag matching", e);
            return new ArrayList<>();
        }
    }

    /**
     * Match natural language query to actual metric names using AI.
     * 
     * @param naturalLanguageQuery User's natural language query (e.g., "complexity
     *                             metrics")
     * @param availableMetrics     Set of all metric names in the database
     * @return List of matched metric names
     */
    public List<String> matchMetricsToQuery(String naturalLanguageQuery, java.util.Set<String> availableMetrics) {
        if (!enabled) {
            logger.debug("PatternMatcherAgent disabled, returning empty list");
            return new ArrayList<>();
        }

        if (availableMetrics.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            logger.info("AI matching query '{}' against {} metrics", naturalLanguageQuery, availableMetrics.size());

            String prompt = buildMetricMatchingPrompt(naturalLanguageQuery, availableMetrics);
            BedrockResponse response = bedrockClient.invokeModel(prompt);

            List<String> matchedMetrics = parseMetricMatchResult(response.getText(), availableMetrics);

            logger.info("AI matched to {} metrics: {}", matchedMetrics.size(), matchedMetrics);
            return matchedMetrics;

        } catch (BedrockApiException e) {
            logger.error("Bedrock API error during metric matching", e);
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("Unexpected error during metric matching", e);
            return new ArrayList<>();
        }
    }

    /**
     * Build prompt for tag matching.
     */
    private String buildTagMatchingPrompt(String query, java.util.Set<String> availableTags) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert at matching natural language queries to database tags.\n\n");
        prompt.append("USER QUERY: ").append(query).append("\n\n");
        prompt.append("AVAILABLE TAGS:\n");

        List<String> sortedTags = new ArrayList<>(availableTags);
        java.util.Collections.sort(sortedTags);
        for (String tag : sortedTags) {
            prompt.append("- ").append(tag).append("\n");
        }

        prompt.append("\nTASK: Return ONLY the tag names that match the user's query, one per line.\n");
        prompt.append("Return NONE if no tags match.\n");
        prompt.append("Do not include explanations, just the tag names.\n\n");

        prompt.append("Examples:\n");
        prompt.append("Query: 'stateless EJBs' → ejb.type\n");
        prompt.append("Query: 'complex classes' → complexity.high\n");
        prompt.append("Query: 'service layer' → layer\n");

        return prompt.toString();
    }

    /**
     * Build prompt for metric matching.
     */
    private String buildMetricMatchingPrompt(String query, java.util.Set<String> availableMetrics) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert at matching natural language queries to software metrics.\n\n");
        prompt.append("USER QUERY: ").append(query).append("\n\n");
        prompt.append("AVAILABLE METRICS:\n");

        List<String> sortedMetrics = new ArrayList<>(availableMetrics);
        java.util.Collections.sort(sortedMetrics);
        for (String metric : sortedMetrics) {
            prompt.append("- ").append(metric).append("\n");
        }

        prompt.append("\nTASK: Return ONLY the metric names that match the user's query, one per line.\n");
        prompt.append("Return NONE if no metrics match.\n");
        prompt.append("Do not include explanations, just the metric names.\n\n");

        prompt.append("Examples:\n");
        prompt.append("Query: 'complexity metrics' → cyclomatic_complexity, cognitive_complexity\n");
        prompt.append("Query: 'lines of code' → loc, sloc\n");
        prompt.append("Query: 'coupling metrics' → afferent_coupling, efferent_coupling\n");

        return prompt.toString();
    }

    /**
     * Parse tag match results from Bedrock response.
     */
    private List<String> parseTagMatchResult(String response, java.util.Set<String> availableTags) {
        List<String> matchedTags = new ArrayList<>();

        if (response.trim().equalsIgnoreCase("NONE")) {
            return matchedTags;
        }

        // Split by lines and extract tag names
        String[] lines = response.split("\\n");
        for (String line : lines) {
            String cleaned = line.trim().replaceAll("^[\\-\\*]\\s*", "");
            if (!cleaned.isEmpty() && availableTags.contains(cleaned)) {
                matchedTags.add(cleaned);
            }
        }

        return matchedTags;
    }

    /**
     * Parse metric match results from Bedrock response.
     */
    private List<String> parseMetricMatchResult(String response, java.util.Set<String> availableMetrics) {
        List<String> matchedMetrics = new ArrayList<>();

        if (response.trim().equalsIgnoreCase("NONE")) {
            return matchedMetrics;
        }

        // Split by lines and extract metric names
        String[] lines = response.split("\\n");
        for (String line : lines) {
            String cleaned = line.trim().replaceAll("^[\\-\\*]\\s*", "");
            if (!cleaned.isEmpty() && availableMetrics.contains(cleaned)) {
                matchedMetrics.add(cleaned);
            }
        }

        return matchedMetrics;
    }

    /**
     * Find the best matching pattern using AI semantic analysis.
     * 
     * @param userPattern       The user's pattern description
     * @param nodeType          The OpenRewrite node type
     * @param availablePatterns List of available patterns to match against
     * @return Optional containing the best match, or empty if no good match found
     */
    public Optional<MatchResult> findBestMatch(
            String userPattern,
            String nodeType,
            List<AvailablePattern> availablePatterns) {

        if (!enabled) {
            logger.debug("PatternMatcherAgent disabled, skipping semantic matching");
            return Optional.empty();
        }

        if (availablePatterns.isEmpty()) {
            logger.debug("No available patterns to match against");
            return Optional.empty();
        }

        try {
            logger.info("AI matching for pattern: '{}' against {} available patterns",
                    userPattern, availablePatterns.size());

            String prompt = buildMatchingPrompt(userPattern, nodeType, availablePatterns);
            BedrockResponse response = bedrockClient.invokeModel(prompt);

            Optional<MatchResult> result = parseMatchResult(response.getText(), availablePatterns);

            if (result.isPresent()) {
                logger.info("AI match found: {} (confidence: {}%)",
                        result.get().getPattern().getName(),
                        result.get().getConfidence());
            } else {
                logger.info("No AI match found above confidence threshold ({}%)", confidenceThreshold);
            }

            return result;

        } catch (BedrockApiException e) {
            logger.error("Bedrock API error during pattern matching", e);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error during pattern matching", e);
            return Optional.empty();
        }
    }

    /**
     * Build the prompt for Bedrock to perform semantic pattern matching.
     */
    private String buildMatchingPrompt(
            String userPattern,
            String nodeType,
            List<AvailablePattern> availablePatterns) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert at matching software design pattern descriptions.\n\n");

        prompt.append("USER'S PATTERN REQUEST:\n");
        prompt.append("Description: ").append(userPattern).append("\n");
        prompt.append("Target Node Type: ").append(nodeType).append("\n\n");

        prompt.append("AVAILABLE PATTERNS TO MATCH:\n");
        for (int i = 0; i < availablePatterns.size(); i++) {
            AvailablePattern pattern = availablePatterns.get(i);
            prompt.append(String.format("[%d] %s\n", i, pattern.getName()));
            prompt.append(String.format("    Type: %s\n", pattern.getType()));
            if (pattern.getDescription() != null) {
                prompt.append(String.format("    Description: %s\n", pattern.getDescription()));
            }
            if (!pattern.getKeywords().isEmpty()) {
                prompt.append(String.format("    Keywords: %s\n", String.join(", ", pattern.getKeywords())));
            }
            prompt.append("\n");
        }

        prompt.append("TASK:\n");
        prompt.append("Determine which pattern (if any) best matches the user's request.\n");
        prompt.append("Consider semantic meaning, not just keyword overlap.\n");
        prompt.append("If no pattern is a good match (confidence <").append(confidenceThreshold)
                .append("%), recommend generating a new one.\n\n");

        prompt.append("RESPONSE FORMAT (return ONLY this format, no other text):\n");
        prompt.append("MATCH: <pattern-index-or-NONE>\n");
        prompt.append("CONFIDENCE: <0-100>\n");
        prompt.append("REASON: <one sentence explanation>\n\n");

        prompt.append("Example responses:\n");
        prompt.append(
                "MATCH: 0\nCONFIDENCE: 95\nREASON: User wants singleton detection, pattern 0 is singleton-pattern.\n\n");
        prompt.append(
                "MATCH: NONE\nCONFIDENCE: 30\nREASON: No existing pattern matches custom requirement for circular dependency detection.\n");

        return prompt.toString();
    }

    /**
     * Parse Bedrock's response to extract match result.
     */
    private Optional<MatchResult> parseMatchResult(
            String response,
            List<AvailablePattern> availablePatterns) {

        try {
            // Extract MATCH
            Pattern matchPattern = Pattern.compile("MATCH:\\s*(.+?)(?:\\n|$)", Pattern.CASE_INSENSITIVE);
            Matcher matchMatcher = matchPattern.matcher(response);
            if (!matchMatcher.find()) {
                logger.warn("Could not parse MATCH from response: {}", response);
                return Optional.empty();
            }
            String matchValue = matchMatcher.group(1).trim();

            // Extract CONFIDENCE
            Pattern confPattern = Pattern.compile("CONFIDENCE:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher confMatcher = confPattern.matcher(response);
            if (!confMatcher.find()) {
                logger.warn("Could not parse CONFIDENCE from response: {}", response);
                return Optional.empty();
            }
            int confidence = Integer.parseInt(confMatcher.group(1));

            // Extract REASON
            Pattern reasonPattern = Pattern.compile("REASON:\\s*(.+?)(?:\\n|$)", Pattern.CASE_INSENSITIVE);
            Matcher reasonMatcher = reasonPattern.matcher(response);
            String reason = reasonMatcher.find() ? reasonMatcher.group(1).trim() : "No reason provided";

            // Check confidence threshold
            if (confidence < confidenceThreshold) {
                logger.debug("Confidence {}% below threshold {}%, no match",
                        confidence, confidenceThreshold);
                return Optional.empty();
            }

            // Check if NONE
            if ("NONE".equalsIgnoreCase(matchValue)) {
                logger.debug("Bedrock recommends generating new pattern");
                return Optional.empty();
            }

            // Parse pattern index
            int patternIndex;
            try {
                patternIndex = Integer.parseInt(matchValue);
            } catch (NumberFormatException e) {
                logger.warn("Invalid pattern index: {}", matchValue);
                return Optional.empty();
            }

            // Validate index
            if (patternIndex < 0 || patternIndex >= availablePatterns.size()) {
                logger.warn("Pattern index {} out of bounds (0-{})",
                        patternIndex, availablePatterns.size() - 1);
                return Optional.empty();
            }

            AvailablePattern matchedPattern = availablePatterns.get(patternIndex);
            return Optional.of(new MatchResult(matchedPattern, confidence, reason));

        } catch (Exception e) {
            logger.error("Error parsing match result", e);
            return Optional.empty();
        }
    }

    /**
     * Create BedrockConfig for pattern matching.
     */
    private BedrockConfig createBedrockConfig(
            String model,
            double temperature,
            int timeoutSeconds,
            String awsRegion,
            String awsAccessKey,
            String awsSecretKey) {

        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("bedrock.model.id", model);
        properties.setProperty("bedrock.temperature", String.valueOf(temperature));
        properties.setProperty("bedrock.top.p", "0.9");
        properties.setProperty("bedrock.max.tokens", "500"); // Small for classification
        properties.setProperty("bedrock.timeout.seconds", String.valueOf(timeoutSeconds));
        properties.setProperty("bedrock.aws.region", awsRegion);
        properties.setProperty("bedrock.aws.access.key.id", awsAccessKey);
        properties.setProperty("bedrock.aws.secret.access.key", awsSecretKey);
        properties.setProperty("bedrock.rate.limit.rpm", "60");
        properties.setProperty("bedrock.retry.attempts", "1");
        properties.setProperty("bedrock.log.requests", "false");
        properties.setProperty("bedrock.log.responses", "false");
        properties.setProperty("bedrock.enabled", "true");

        try {
            java.lang.reflect.Constructor<BedrockConfig> constructor = BedrockConfig.class
                    .getDeclaredConstructor(java.util.Properties.class);
            constructor.setAccessible(true);
            return constructor.newInstance(properties);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create BedrockConfig", e);
        }
    }

    /**
     * Represents an available pattern that can be matched against.
     */
    public static class AvailablePattern {
        private final String name;
        private final String type; // "template" or "cached"
        private final String description;
        private final List<String> keywords;
        private final Object patternData; // VisitorTemplate or cache key

        public AvailablePattern(String name, String type, String description,
                List<String> keywords, Object patternData) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.keywords = keywords != null ? keywords : new ArrayList<>();
            this.patternData = patternData;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public Object getPatternData() {
            return patternData;
        }
    }

    /**
     * Result of pattern matching with confidence score.
     */
    public static class MatchResult {
        private final AvailablePattern pattern;
        private final int confidence;
        private final String reason;

        public MatchResult(AvailablePattern pattern, int confidence, String reason) {
            this.pattern = pattern;
            this.confidence = confidence;
            this.reason = reason;
        }

        public AvailablePattern getPattern() {
            return pattern;
        }

        public int getConfidence() {
            return confidence;
        }

        public String getReason() {
            return reason;
        }
    }
}
