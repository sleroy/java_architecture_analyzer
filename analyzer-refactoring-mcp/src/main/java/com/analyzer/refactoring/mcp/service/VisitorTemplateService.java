package com.analyzer.refactoring.mcp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing pre-built visitor templates.
 * 
 * Templates provide fast, consistent pattern detection without requiring
 * AI generation. Templates are loaded from classpath and matched against
 * pattern descriptions.
 * 
 * Benefits:
 * - Instant execution (no Bedrock call)
 * - Consistent results
 * - Lower costs
 * - Known performance characteristics
 */
@Service
public class VisitorTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(VisitorTemplateService.class);

    private static final String TEMPLATE_LOCATION = "classpath:visitor-templates/*.groovy";

    private final Map<String, VisitorTemplate> templates = new HashMap<>();
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    /**
     * Load all visitor templates from classpath on startup.
     */
    @PostConstruct
    public void loadTemplates() {
        try {
            Resource[] resources = resourceResolver.getResources(TEMPLATE_LOCATION);

            for (Resource resource : resources) {
                try {
                    String filename = resource.getFilename();
                    if (filename != null) {
                        String content = new String(
                                resource.getInputStream().readAllBytes(),
                                StandardCharsets.UTF_8);

                        VisitorTemplate template = parseTemplate(filename, content);
                        templates.put(template.getName(), template);

                        logger.info("Loaded template: {} with {} matching phrases",
                                template.getName(), template.getMatchingPhrases().size());
                    }
                } catch (IOException e) {
                    logger.error("Failed to load template: {}", resource.getFilename(), e);
                }
            }

            logger.info("Successfully loaded {} visitor templates", templates.size());

        } catch (IOException e) {
            logger.error("Failed to load visitor templates", e);
        }
    }

    /**
     * Find a matching template for the given pattern description.
     * 
     * @param patternDescription The pattern description from user
     * @param nodeType           The OpenRewrite node type
     * @return Optional containing matching template, or empty if no match
     */
    public Optional<VisitorTemplate> findTemplate(String patternDescription, String nodeType) {
        String normalizedPattern = patternDescription.toLowerCase().trim();

        // Try exact phrase match (case insensitive)
        for (VisitorTemplate template : templates.values()) {
            for (String phrase : template.getMatchingPhrases()) {
                if (normalizedPattern.contains(phrase.toLowerCase())) {
                    logger.info("Found template match: {} for pattern: {} (matched phrase: '{}')",
                            template.getName(), patternDescription, phrase);
                    return Optional.of(template);
                }
            }
        }

        logger.debug("No template match found for pattern: {}", patternDescription);
        return Optional.empty();
    }

    /**
     * Get all available templates.
     */
    public Collection<VisitorTemplate> getAllTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    /**
     * Get template by name.
     */
    public Optional<VisitorTemplate> getTemplate(String name) {
        return Optional.ofNullable(templates.get(name));
    }

    /**
     * Parse template metadata from filename and content.
     */
    private VisitorTemplate parseTemplate(String filename, String content) {
        String name = filename.replace(".groovy", "");

        List<String> matchingPhrases = new ArrayList<>();

        // Extract phrase from filename (e.g., "god-class-antipattern" -> "god class
        // antipattern")
        String filenamePhrase = name.replace("-", " ");
        matchingPhrases.add(filenamePhrase.trim());

        // Extract meaningful phrases from documentation comments
        List<String> docPhrases = extractDocPhrases(content);
        matchingPhrases.addAll(docPhrases);

        return new VisitorTemplate(name, content, matchingPhrases);
    }

    /**
     * Extract meaningful phrases from Javadoc/comments in template.
     * Looks for patterns like "Detects X" or "Identifies Y" to extract the subject.
     */
    private List<String> extractDocPhrases(String content) {
        List<String> phrases = new ArrayList<>();

        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();

            // Remove comment markers
            if (line.startsWith("*")) {
                line = line.substring(1).trim();
            } else if (line.startsWith("//")) {
                line = line.substring(2).trim();
            }

            // Look for "Detects X" or "Identifies Y" patterns
            if (line.toLowerCase().startsWith("detects ")) {
                String phrase = line.substring(8).trim();
                // Remove trailing punctuation
                phrase = phrase.replaceAll("[.:]$", "").trim();
                if (!phrase.isEmpty() && phrase.length() > 3) {
                    phrases.add(phrase.toLowerCase());
                }
            } else if (line.toLowerCase().startsWith("identifies ")) {
                String phrase = line.substring(11).trim();
                phrase = phrase.replaceAll("[.:]$", "").trim();
                if (!phrase.isEmpty() && phrase.length() > 3) {
                    phrases.add(phrase.toLowerCase());
                }
            }

            // Also capture full description lines that might be useful
            // (e.g., "God Class anti-pattern")
            if (line.length() > 10 && line.length() < 100 &&
                    !line.contains("@") && !line.contains("(") && !line.contains("{")) {
                String cleaned = line.replaceAll("[^a-zA-Z ]", "").trim().toLowerCase();
                if (cleaned.split("\\s+").length >= 2 && cleaned.split("\\s+").length <= 6) {
                    phrases.add(cleaned);
                }
            }
        }

        return phrases.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Represents a pre-built visitor template.
     */
    public static class VisitorTemplate {
        private final String name;
        private final String scriptSource;
        private final List<String> matchingPhrases;

        public VisitorTemplate(String name, String scriptSource, List<String> matchingPhrases) {
            this.name = name;
            this.scriptSource = scriptSource;
            this.matchingPhrases = matchingPhrases;
        }

        public String getName() {
            return name;
        }

        public String getScriptSource() {
            return scriptSource;
        }

        public List<String> getMatchingPhrases() {
            return Collections.unmodifiableList(matchingPhrases);
        }

        @Override
        public String toString() {
            return "VisitorTemplate{name='" + name + "', matchingPhrases=" + matchingPhrases + "}";
        }
    }
}
