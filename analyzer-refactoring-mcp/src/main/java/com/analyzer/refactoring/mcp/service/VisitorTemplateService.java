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

                        logger.info("Loaded template: {} with {} keywords",
                                template.getName(), template.getKeywords().size());
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
        String normalizedPattern = patternDescription.toLowerCase();

        // Try exact keyword match first
        for (VisitorTemplate template : templates.values()) {
            for (String keyword : template.getKeywords()) {
                if (normalizedPattern.contains(keyword.toLowerCase())) {
                    logger.info("Found template match: {} for pattern: {}",
                            template.getName(), patternDescription);
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

        // Extract keywords from filename (e.g., "singleton-pattern" -> ["singleton",
        // "pattern"])
        List<String> keywords = Arrays.stream(name.split("-"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        // Extract additional keywords from documentation comments
        List<String> docKeywords = extractDocKeywords(content);
        keywords.addAll(docKeywords);

        return new VisitorTemplate(name, content, keywords);
    }

    /**
     * Extract keywords from Javadoc/comments in template.
     */
    private List<String> extractDocKeywords(String content) {
        List<String> keywords = new ArrayList<>();

        // Look for keywords in documentation
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("*") || line.startsWith("//")) {
                // Extract words that might be keywords
                String[] words = line.replaceAll("[^a-zA-Z ]", "").toLowerCase().split("\\s+");
                for (String word : words) {
                    if (word.length() > 3 && !isCommonWord(word)) {
                        keywords.add(word);
                    }
                }
            }
        }

        return keywords.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Check if word is too common to be useful as keyword.
     */
    private boolean isCommonWord(String word) {
        Set<String> common = Set.of(
                "this", "that", "with", "from", "have", "been", "were",
                "they", "what", "when", "where", "which", "class", "method",
                "field", "code", "java", "detects", "identified", "considered");
        return common.contains(word);
    }

    /**
     * Represents a pre-built visitor template.
     */
    public static class VisitorTemplate {
        private final String name;
        private final String scriptSource;
        private final List<String> keywords;

        public VisitorTemplate(String name, String scriptSource, List<String> keywords) {
            this.name = name;
            this.scriptSource = scriptSource;
            this.keywords = keywords;
        }

        public String getName() {
            return name;
        }

        public String getScriptSource() {
            return scriptSource;
        }

        public List<String> getKeywords() {
            return Collections.unmodifiableList(keywords);
        }

        @Override
        public String toString() {
            return "VisitorTemplate{name='" + name + "', keywords=" + keywords + "}";
        }
    }
}
