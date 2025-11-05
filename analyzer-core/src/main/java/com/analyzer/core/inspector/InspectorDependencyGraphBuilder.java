package com.analyzer.core.inspector;

import com.analyzer.core.graph.GraphAnalysisResult;
import com.analyzer.core.graph.InspectorNode;
import com.analyzer.core.graph.TagDependencyEdge;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.api.inspector.InspectorDependencies;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builder class that analyzes inspector dependencies and creates graphs showing
 * relationships between inspectors through their tag dependencies.
 * 
 * <p>
 * This class helps identify:
 * </p>
 * <ul>
 * <li>Unused tags that are produced but never consumed</li>
 * <li>Semantically duplicated tags with similar meanings</li>
 * <li>Complex dependency chains that could be simplified</li>
 * <li>Orphaned inspectors with no dependencies</li>
 * </ul>
 */
public class InspectorDependencyGraphBuilder {

    private static final Logger logger = LoggerFactory.getLogger(InspectorDependencyGraphBuilder.class);

    private final InspectorRegistry inspectorRegistry;
    private final boolean includeUnusedTags;
    private final boolean includeSemanticsAnalysis;
    private final int minChainLength;

    /**
     * Creates a new graph builder with the specified configuration.
     * 
     * @param inspectorRegistry        Registry containing all inspectors to analyze
     * @param includeUnusedTags        Whether to analyze unused tags
     * @param includeSemanticsAnalysis Whether to detect semantic duplicates
     * @param minChainLength           Minimum chain length to consider complex
     */
    public InspectorDependencyGraphBuilder(InspectorRegistry inspectorRegistry,
            boolean includeUnusedTags,
            boolean includeSemanticsAnalysis,
            int minChainLength) {
        this.inspectorRegistry = Objects.requireNonNull(inspectorRegistry);
        this.includeUnusedTags = includeUnusedTags;
        this.includeSemanticsAnalysis = includeSemanticsAnalysis;
        this.minChainLength = minChainLength;
    }

    /**
     * Builds the dependency graph and performs analysis.
     * 
     * @return Complete analysis result with graph and insights
     */
    public GraphAnalysisResult buildDependencyGraph() {
        logger.info("Building inspector dependency graph...");

        // Get all inspectors
        List<Inspector> allInspectors = inspectorRegistry.getAllInspectors();
        logger.debug("Analyzing {} inspectors", allInspectors.size());

        // Build dependency information
        Map<String, InspectorNode> inspectorNodes = buildInspectorNodes(allInspectors);
        Map<String, Set<String>> tagProducers = buildTagProducerMap(inspectorNodes);
        Map<String, Set<String>> tagConsumers = buildTagConsumerMap(inspectorNodes);

        // Create JGraphT graph
        Graph<InspectorNode, TagDependencyEdge> graph = buildJGraphTGraph(inspectorNodes, tagProducers, tagConsumers);

        // Perform analysis
        Set<String> allTags = getAllTags(tagProducers, tagConsumers);
        Set<String> unusedTags = includeUnusedTags ? findUnusedTags(tagProducers, tagConsumers)
                : Collections.emptySet();
        Map<String, List<String>> potentialDuplicates = includeSemanticsAnalysis ? findSemanticDuplicates(allTags)
                : Collections.emptyMap();
        List<List<String>> complexChains = findComplexDependencyChains(graph, inspectorNodes);

        logger.info("Graph analysis completed: {} inspectors, {} dependencies, {} tags",
                inspectorNodes.size(), graph.edgeSet().size(), allTags.size());

        return new GraphAnalysisResult(graph, inspectorNodes, tagProducers, tagConsumers,
                allTags, unusedTags, potentialDuplicates, complexChains);
    }

    /**
     * Builds inspector nodes with their dependency information.
     */
    private Map<String, InspectorNode> buildInspectorNodes(List<Inspector> inspectors) {
        Map<String, InspectorNode> nodes = new LinkedHashMap<>();

        for (Inspector inspector : inspectors) {
            String inspectorName = inspector.getName();
            String className = inspector.getClass().getSimpleName();

            // Get dependencies using the resolver
            RequiredTags dependencies = InspectorDependencyResolver.getDependencies(inspector);
            Set<String> requiredTags = new HashSet<>(Arrays.asList(dependencies.toArray()));

            // Get produced tags from annotation
            Set<String> producedTags = getProducedTags(inspector.getClass());

            InspectorNode node = new InspectorNode(inspectorName, className, requiredTags, producedTags);
            nodes.put(inspectorName, node);

            logger.debug("Inspector {}: requires {} tags, produces {} tags",
                    inspectorName, requiredTags.size(), producedTags.size());
        }

        return nodes;
    }

    /**
     * Gets the tags produced by an inspector from its @InspectorDependencies
     * annotation.
     */
    private Set<String> getProducedTags(Class<? extends Inspector> inspectorClass) {
        Set<String> producedTags = new HashSet<>();

        // Walk up the inheritance chain to collect all produced tags
        Class<?> currentClass = inspectorClass;
        while (currentClass != null && Inspector.class.isAssignableFrom(currentClass)) {
            InspectorDependencies annotation = currentClass.getAnnotation(InspectorDependencies.class);
            if (annotation != null) {
                Collections.addAll(producedTags, annotation.produces());
            }
            currentClass = currentClass.getSuperclass();
        }

        return producedTags;
    }

    /**
     * Builds a map from tags to the inspectors that produce them.
     */
    private Map<String, Set<String>> buildTagProducerMap(Map<String, InspectorNode> nodes) {
        Map<String, Set<String>> tagProducers = new HashMap<>();

        for (InspectorNode node : nodes.values()) {
            for (String producedTag : node.producedTags()) {
                tagProducers.computeIfAbsent(producedTag, k -> new HashSet<>()).add(node.inspectorName());
            }
        }

        return tagProducers;
    }

    /**
     * Builds a map from tags to the inspectors that consume them.
     */
    private Map<String, Set<String>> buildTagConsumerMap(Map<String, InspectorNode> nodes) {
        Map<String, Set<String>> tagConsumers = new HashMap<>();

        for (InspectorNode node : nodes.values()) {
            for (String requiredTag : node.requiredTags()) {
                tagConsumers.computeIfAbsent(requiredTag, k -> new HashSet<>()).add(node.inspectorName());
            }
        }

        return tagConsumers;
    }

    /**
     * Builds a JGraphT directed graph of inspector dependencies.
     */
    private Graph<InspectorNode, TagDependencyEdge> buildJGraphTGraph(
            Map<String, InspectorNode> nodes,
            Map<String, Set<String>> tagProducers,
            Map<String, Set<String>> tagConsumers) {

        Graph<InspectorNode, TagDependencyEdge> graph = new DirectedMultigraph<>(TagDependencyEdge.class);

        // Add all nodes
        for (InspectorNode node : nodes.values()) {
            graph.addVertex(node);
        }

        // Group tags by producer-consumer pairs to create consolidated edges
        Map<ProducerConsumerPair, Set<String>> edgeTagsMap = new HashMap<>();

        // Group all tags by producer-consumer pairs
        for (Map.Entry<String, Set<String>> entry : tagConsumers.entrySet()) {
            String tag = entry.getKey();
            Set<String> consumers = entry.getValue();
            Set<String> producers = tagProducers.getOrDefault(tag, Collections.emptySet());

            for (String producerName : producers) {
                InspectorNode producer = nodes.get(producerName);
                for (String consumerName : consumers) {
                    InspectorNode consumer = nodes.get(consumerName);
                    if (producer != null && consumer != null && !producer.equals(consumer)) {
                        ProducerConsumerPair pair = new ProducerConsumerPair(producer, consumer);
                        edgeTagsMap.computeIfAbsent(pair, k -> new HashSet<>()).add(tag);
                    }
                }
            }
        }

        // Create consolidated edges with sets of tags
        for (Map.Entry<ProducerConsumerPair, Set<String>> entry : edgeTagsMap.entrySet()) {
            ProducerConsumerPair pair = entry.getKey();
            Set<String> tags = entry.getValue();
            
            TagDependencyEdge edge = new TagDependencyEdge(tags, pair.producer(), pair.consumer());
            graph.addEdge(pair.producer(), pair.consumer(), edge);
        }

        return graph;
    }

    /**
     * Helper record to represent a producer-consumer pair for edge consolidation.
     */
    private record ProducerConsumerPair(InspectorNode producer, InspectorNode consumer) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProducerConsumerPair that = (ProducerConsumerPair) o;
            return Objects.equals(producer, that.producer) && Objects.equals(consumer, that.consumer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(producer, consumer);
        }
    }

    /**
     * Gets all unique tags from producers and consumers.
     */
    private Set<String> getAllTags(Map<String, Set<String>> tagProducers, Map<String, Set<String>> tagConsumers) {
        Set<String> allTags = new HashSet<>();
        allTags.addAll(tagProducers.keySet());
        allTags.addAll(tagConsumers.keySet());
        return allTags;
    }

    /**
     * Finds tags that are produced but never consumed.
     */
    private Set<String> findUnusedTags(Map<String, Set<String>> tagProducers, Map<String, Set<String>> tagConsumers) {
        Set<String> unusedTags = new HashSet<>(tagProducers.keySet());
        unusedTags.removeAll(tagConsumers.keySet());
        return unusedTags;
    }

    /**
     * Finds potential semantic duplicates by analyzing tag names and inspector relationships.
     * This method identifies several types of semantic duplicates:
     * 1. Tags with similar normalized names (e.g., "EJB_DETECTED" vs "EJB_FOUND")
     * 2. Unconditional tags produced by the same inspector
     * 3. Tags with similar semantic meaning from the same context
     * 4. Pattern-based duplicates (e.g., different tenses, plurals)
     */
    private Map<String, List<String>> findSemanticDuplicates(Set<String> allTags) {
        Map<String, List<String>> duplicateGroups = new HashMap<>();
        
        // 1. Find basic normalized duplicates
        Map<String, List<String>> normalizedGroups = findNormalizedDuplicates(allTags);
        duplicateGroups.putAll(normalizedGroups);
        
        // 2. Find inspector-specific semantic duplicates
        Map<String, List<String>> inspectorDuplicates = findInspectorSpecificDuplicates(allTags);
        mergeSemanticGroups(duplicateGroups, inspectorDuplicates);
        
        // 3. Find pattern-based semantic duplicates
        Map<String, List<String>> patternDuplicates = findPatternBasedDuplicates(allTags);
        mergeSemanticGroups(duplicateGroups, patternDuplicates);
        
        // 4. Find contextual semantic duplicates
        Map<String, List<String>> contextualDuplicates = findContextualDuplicates(allTags);
        mergeSemanticGroups(duplicateGroups, contextualDuplicates);
        
        // 5. Find tags emitted by multiple inspectors (potential duplicate functionality)
        Map<String, List<String>> multiProducerTags = findMultiProducerTags();
        mergeSemanticGroups(duplicateGroups, multiProducerTags);

        // Log findings for analysis
        if (!duplicateGroups.isEmpty()) {
            logger.info("Found {} groups of potentially semantic duplicate tags:", duplicateGroups.size());
            duplicateGroups.forEach((key, tags) -> {
                logger.debug("  Group '{}': {}", key, tags);
            });
        }

        return duplicateGroups;
    }

    /**
     * Finds duplicates based on normalized tag names.
     */
    private Map<String, List<String>> findNormalizedDuplicates(Set<String> allTags) {
        Map<String, List<String>> duplicateGroups = new HashMap<>();
        Map<String, String> normalizedToOriginal = new HashMap<>();

        for (String tag : allTags) {
            String normalized = normalizeTagName(tag);
            if (normalizedToOriginal.containsKey(normalized)) {
                String group = normalizedToOriginal.get(normalized);
                duplicateGroups.computeIfAbsent(group, k -> new ArrayList<>()).add(tag);
            } else {
                normalizedToOriginal.put(normalized, tag);
            }
        }

        // Remove groups with only one tag
        duplicateGroups.entrySet().removeIf(entry -> entry.getValue().size() <= 1);
        return duplicateGroups;
    }

    /**
     * Finds semantic duplicates produced by the same inspector.
     * These could be unconditional tags or tags with similar meanings from the same source.
     */
    private Map<String, List<String>> findInspectorSpecificDuplicates(Set<String> allTags) {
        Map<String, List<String>> duplicateGroups = new HashMap<>();
        
        // Group tags by their producing inspectors
        Map<String, Set<String>> inspectorToTags = new HashMap<>();
        for (InspectorNode node : inspectorRegistry.getAllInspectors().stream()
                .map(inspector -> new InspectorNode(inspector.getName(), 
                    inspector.getClass().getSimpleName(),
                    new HashSet<>(), // We don't need required tags for this analysis
                    getProducedTags(inspector.getClass())))
                .toList()) {
            
            for (String tag : node.producedTags()) {
                inspectorToTags.computeIfAbsent(node.inspectorName(), k -> new HashSet<>()).add(tag);
            }
        }
        
        // Analyze each inspector's produced tags for semantic similarity
        for (Map.Entry<String, Set<String>> entry : inspectorToTags.entrySet()) {
            String inspectorName = entry.getKey();
            Set<String> producedTags = entry.getValue();
            
            if (producedTags.size() > 1) {
                // Look for semantic patterns within the same inspector
                Map<String, List<String>> inspectorGroups = findSemanticPatternsInTagSet(producedTags, inspectorName);
                duplicateGroups.putAll(inspectorGroups);
            }
        }
        
        return duplicateGroups;
    }

    /**
     * Finds semantic patterns within a set of tags from the same inspector.
     */
    private Map<String, List<String>> findSemanticPatternsInTagSet(Set<String> tags, String inspectorName) {
        Map<String, List<String>> patterns = new HashMap<>();
        List<String> tagList = new ArrayList<>(tags);
        
        for (int i = 0; i < tagList.size(); i++) {
            for (int j = i + 1; j < tagList.size(); j++) {
                String tag1 = tagList.get(i);
                String tag2 = tagList.get(j);
                
                if (areSemanticallySimilar(tag1, tag2)) {
                    String groupKey = inspectorName + "_semantic_" + Math.min(tag1.hashCode(), tag2.hashCode());
                    patterns.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(tag1);
                    patterns.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(tag2);
                }
            }
        }
        
        // Remove duplicates within groups and filter single-tag groups
        patterns.forEach((key, tagList2) -> {
            Set<String> uniqueTags = new LinkedHashSet<>(tagList2);
            tagList2.clear();
            tagList2.addAll(uniqueTags);
        });
        patterns.entrySet().removeIf(entry -> entry.getValue().size() <= 1);
        
        return patterns;
    }

    /**
     * Finds pattern-based semantic duplicates (different tenses, plurals, etc.).
     */
    private Map<String, List<String>> findPatternBasedDuplicates(Set<String> allTags) {
        Map<String, List<String>> duplicateGroups = new HashMap<>();
        
        // Define semantic transformation patterns
        Map<String, String> patterns = Map.of(
            "DETECTED", "FOUND",
            "DETECTOR", "FINDER", 
            "USAGE", "USED",
            "PATTERN", "TEMPLATE",
            "CONFIG", "CONFIGURATION",
            "IMPL", "IMPLEMENTATION",
            "MGMT", "MANAGEMENT",
            "SVC", "SERVICE"
        );
        
        List<String> tagList = new ArrayList<>(allTags);
        for (int i = 0; i < tagList.size(); i++) {
            for (int j = i + 1; j < tagList.size(); j++) {
                String tag1 = tagList.get(i);
                String tag2 = tagList.get(j);
                
                if (arePatternBasedDuplicates(tag1, tag2, patterns)) {
                    String groupKey = "pattern_" + getCommonPattern(tag1, tag2);
                    duplicateGroups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(tag1);
                    duplicateGroups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(tag2);
                }
            }
        }
        
        // Clean up groups
        duplicateGroups.forEach((key, tagList2) -> {
            Set<String> uniqueTags = new LinkedHashSet<>(tagList2);
            tagList2.clear();
            tagList2.addAll(uniqueTags);
        });
        duplicateGroups.entrySet().removeIf(entry -> entry.getValue().size() <= 1);
        
        return duplicateGroups;
    }

    /**
     * Finds contextual semantic duplicates based on domain context.
     */
    private Map<String, List<String>> findContextualDuplicates(Set<String> allTags) {
        Map<String, List<String>> duplicateGroups = new HashMap<>();
        
        // Define semantic contexts and their synonyms
        Map<String, Set<String>> contexts = Map.of(
            "ejb_context", Set.of("EJB", "ENTERPRISE_BEAN", "SESSION_BEAN", "ENTITY_BEAN", "MESSAGE_DRIVEN"),
            "web_context", Set.of("SERVLET", "JSP", "WEB", "HTTP", "WEBAPP"),
            "data_context", Set.of("DATABASE", "DB", "SQL", "JDBC", "JPA", "HIBERNATE"),
            "config_context", Set.of("CONFIG", "CONFIGURATION", "PROPERTIES", "SETTINGS", "PARAMS"),
            "transaction_context", Set.of("TRANSACTION", "TX", "TRANSACTIONAL", "COMMIT", "ROLLBACK")
        );
        
        // Group tags by context
        for (Map.Entry<String, Set<String>> contextEntry : contexts.entrySet()) {
            String contextName = contextEntry.getKey();
            Set<String> contextKeywords = contextEntry.getValue();
            
            List<String> contextTags = allTags.stream()
                .filter(tag -> contextKeywords.stream().anyMatch(keyword -> tag.toUpperCase().contains(keyword)))
                .toList();
                
            if (contextTags.size() > 1) {
                // Further analyze within context for semantic similarity
                for (int i = 0; i < contextTags.size(); i++) {
                    for (int j = i + 1; j < contextTags.size(); j++) {
                        String tag1 = contextTags.get(i);
                        String tag2 = contextTags.get(j);
                        
                        if (areContextuallySimilar(tag1, tag2, contextKeywords)) {
                            String groupKey = contextName + "_" + getContextualSimilarityKey(tag1, tag2);
                            duplicateGroups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(tag1);
                            duplicateGroups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(tag2);
                        }
                    }
                }
            }
        }
        
        // Clean up groups
        duplicateGroups.forEach((key, tagList) -> {
            Set<String> uniqueTags = new LinkedHashSet<>(tagList);
            tagList.clear();
            tagList.addAll(uniqueTags);
        });
        duplicateGroups.entrySet().removeIf(entry -> entry.getValue().size() <= 1);
        
        return duplicateGroups;
    }

    /**
     * Finds unique tags that are emitted by multiple inspectors.
     * This identifies potential duplicate functionality across different inspectors,
     * which could indicate:
     * 1. Redundant inspector implementations
     * 2. Common patterns that could be consolidated
     * 3. Inconsistent tagging strategies
     * 4. Opportunities for inspector refactoring
     */
    private Map<String, List<String>> findMultiProducerTags() {
        Map<String, List<String>> multiProducerGroups = new HashMap<>();
        
        // Build a map of tags to the inspectors that produce them
        Map<String, Set<String>> tagToProducers = new HashMap<>();
        
        for (InspectorNode node : inspectorRegistry.getAllInspectors().stream()
                .map(inspector -> new InspectorNode(inspector.getName(),
                    inspector.getClass().getSimpleName(),
                    new HashSet<>(), // We don't need required tags for this analysis
                    getProducedTags(inspector.getClass())))
                .toList()) {
            
            for (String tag : node.producedTags()) {
                tagToProducers.computeIfAbsent(tag, k -> new HashSet<>()).add(node.inspectorName());
            }
        }
        
        // Find tags produced by multiple inspectors
        Map<String, Set<String>> multiProducerTags = tagToProducers.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                ));
        
        if (!multiProducerTags.isEmpty()) {
            logger.info("Found {} tags produced by multiple inspectors:", multiProducerTags.size());
            multiProducerTags.forEach((tag, producers) -> {
                logger.debug("  Tag '{}' produced by {} inspectors: {}", tag, producers.size(), producers);
            });
        }
        
        // Group multi-producer tags by their characteristics
        Map<String, List<String>> groupedByProducerCount = groupMultiProducerTagsByCount(multiProducerTags);
        multiProducerGroups.putAll(groupedByProducerCount);
        
        // Group multi-producer tags by semantic similarity
        Map<String, List<String>> groupedBySimilarity = groupMultiProducerTagsBySimilarity(multiProducerTags);
        mergeSemanticGroups(multiProducerGroups, groupedBySimilarity);
        
        // Group tags that share common producer sets (potential inspector coupling)
        Map<String, List<String>> groupedByProducerSets = groupTagsByCommonProducers(multiProducerTags);
        mergeSemanticGroups(multiProducerGroups, groupedByProducerSets);
        
        // Identify exact tag name duplicates (inspectors producing identical tag names)
        Map<String, List<String>> exactDuplicates = findExactTagNameDuplicates(multiProducerTags);
        mergeSemanticGroups(multiProducerGroups, exactDuplicates);
        
        return multiProducerGroups;
    }

    /**
     * Finds inspectors that produce exactly identical tag names.
     * This identifies potential duplicate functionality or naming conflicts where
     * multiple inspectors produce the exact same tag, which could indicate:
     * 1. Duplicate inspector implementations
     * 2. Naming conflicts between different inspectors
     * 3. Opportunities for inspector consolidation
     * 4. Inconsistent inspector design patterns
     */
    private Map<String, List<String>> findExactTagNameDuplicates(Map<String, Set<String>> multiProducerTags) {
        Map<String, List<String>> exactDuplicateGroups = new HashMap<>();
        
        if (multiProducerTags.isEmpty()) {
            return exactDuplicateGroups;
        }
        
        logger.info("Analyzing exact tag name duplicates across inspectors:");
        
        // Analyze each tag that has multiple producers
        for (Map.Entry<String, Set<String>> entry : multiProducerTags.entrySet()) {
            String tagName = entry.getKey();
            Set<String> producers = entry.getValue();
            
            if (producers.size() > 1) {
                // Create a group for this exact tag name duplicate
                String groupKey = "exact_duplicate_" + tagName.replaceAll("[^a-zA-Z0-9_]", "_");
                List<String> inspectorList = new ArrayList<>(producers);
                
                // Add the tag name as the first element to track what tag is duplicated
                List<String> groupValue = new ArrayList<>();
                groupValue.add("TAG:" + tagName); // Prefix to distinguish from inspector names
                groupValue.addAll(inspectorList);
                
                exactDuplicateGroups.put(groupKey, groupValue);
                
                // Log the finding with severity based on number of duplicates
                String severity = producers.size() > 3 ? "CRITICAL" : producers.size() > 2 ? "HIGH" : "MEDIUM";
                logger.warn("  [{}] Tag '{}' produced by {} inspectors: {}", 
                    severity, tagName, producers.size(), producers);
                
                // Additional analysis: check if these inspectors are in similar packages (potential code duplication)
                analyzeInspectorSimilarity(tagName, producers);
            }
        }
        
        if (!exactDuplicateGroups.isEmpty()) {
            logger.info("Found {} exact tag name duplicates across inspectors", exactDuplicateGroups.size());
            
            // Provide consolidation recommendations
            provideConsolidationRecommendations(multiProducerTags);
        }
        
        return exactDuplicateGroups;
    }

    /**
     * Analyzes similarity between inspectors that produce the same tag.
     */
    private void analyzeInspectorSimilarity(String tagName, Set<String> producers) {
        // Group inspectors by package patterns
        Map<String, List<String>> packageGroups = producers.stream()
                .collect(Collectors.groupingBy(this::extractPackagePattern));
        
        // Check for inspectors in similar packages (potential code duplication)
        for (Map.Entry<String, List<String>> packageGroup : packageGroups.entrySet()) {
            if (packageGroup.getValue().size() > 1) {
                logger.debug("    Package similarity detected for tag '{}': {} inspectors in similar packages: {}", 
                    tagName, packageGroup.getValue().size(), packageGroup.getValue());
            }
        }
        
        // Check for naming pattern similarities
        List<String> inspectorNames = new ArrayList<>(producers);
        for (int i = 0; i < inspectorNames.size(); i++) {
            for (int j = i + 1; j < inspectorNames.size(); j++) {
                String inspector1 = inspectorNames.get(i);
                String inspector2 = inspectorNames.get(j);
                
                if (haveSimilarNamingPatterns(inspector1, inspector2)) {
                    logger.debug("    Naming similarity detected for tag '{}': '{}' and '{}' have similar patterns", 
                        tagName, inspector1, inspector2);
                }
            }
        }
    }

    /**
     * Extracts package pattern from inspector name for similarity analysis.
     */
    private String extractPackagePattern(String inspectorName) {
        // Simple heuristic: extract common package patterns
        if (inspectorName.toLowerCase().contains("ejb")) return "ejb";
        if (inspectorName.toLowerCase().contains("web") || inspectorName.toLowerCase().contains("servlet")) return "web";
        if (inspectorName.toLowerCase().contains("jpa") || inspectorName.toLowerCase().contains("hibernate") || inspectorName.toLowerCase().contains("database")) return "data";
        if (inspectorName.toLowerCase().contains("config") || inspectorName.toLowerCase().contains("properties")) return "config";
        if (inspectorName.toLowerCase().contains("transaction") || inspectorName.toLowerCase().contains("tx")) return "transaction";
        return "other";
    }

    /**
     * Checks if two inspector names have similar naming patterns.
     */
    private boolean haveSimilarNamingPatterns(String name1, String name2) {
        // Simple similarity check based on common prefixes/suffixes
        String normalized1 = name1.toLowerCase();
        String normalized2 = name2.toLowerCase();
        
        // Check for common endings
        String[] commonEndings = {"inspector", "detector", "analyzer", "scanner", "finder"};
        for (String ending : commonEndings) {
            if (normalized1.endsWith(ending) && normalized2.endsWith(ending)) {
                // Check if the base names are similar
                String base1 = normalized1.substring(0, normalized1.length() - ending.length());
                String base2 = normalized2.substring(0, normalized2.length() - ending.length());
                return calculateEditDistance(base1, base2) <= Math.max(1, Math.min(base1.length(), base2.length()) / 3);
            }
        }
        
        return false;
    }

    /**
     * Provides consolidation recommendations based on exact tag duplicates.
     */
    private void provideConsolidationRecommendations(Map<String, Set<String>> multiProducerTags) {
        Map<String, Integer> severityCount = new HashMap<>();
        
        for (Map.Entry<String, Set<String>> entry : multiProducerTags.entrySet()) {
            String tagName = entry.getKey();
            int producerCount = entry.getValue().size();
            
            if (producerCount > 3) {
                severityCount.merge("CRITICAL", 1, Integer::sum);
            } else if (producerCount > 2) {
                severityCount.merge("HIGH", 1, Integer::sum);
            } else {
                severityCount.merge("MEDIUM", 1, Integer::sum);
            }
        }
        
        if (severityCount.containsKey("CRITICAL")) {
            logger.warn("RECOMMENDATION: {} tags have CRITICAL duplication (4+ inspectors). Consider immediate consolidation.", 
                severityCount.get("CRITICAL"));
        }
        
        if (severityCount.containsKey("HIGH")) {
            logger.warn("RECOMMENDATION: {} tags have HIGH duplication (3+ inspectors). Review for potential consolidation.", 
                severityCount.get("HIGH"));
        }
        
        if (severityCount.containsKey("MEDIUM")) {
            logger.info("RECOMMENDATION: {} tags have MEDIUM duplication (2+ inspectors). Consider design review.", 
                severityCount.get("MEDIUM"));
        }
        
        // Provide specific recommendations
        logger.info("Consolidation strategies:");
        logger.info("  1. Create base inspector classes for common functionality");
        logger.info("  2. Use composition instead of inheritance for shared tag production");
        logger.info("  3. Implement tag delegation patterns to avoid duplication");
        logger.info("  4. Review inspector responsibilities and split if needed");
    }

    /**
     * Groups multi-producer tags by the number of inspectors that produce them.
     */
    private Map<String, List<String>> groupMultiProducerTagsByCount(Map<String, Set<String>> multiProducerTags) {
        Map<String, List<String>> groups = new HashMap<>();
        
        // Group by producer count
        Map<Integer, List<String>> countGroups = multiProducerTags.entrySet().stream()
                .collect(Collectors.groupingBy(
                    entry -> entry.getValue().size(),
                    Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));
        
        // Convert to string keys and filter significant groups
        for (Map.Entry<Integer, List<String>> entry : countGroups.entrySet()) {
            int count = entry.getKey();
            List<String> tags = entry.getValue();
            
            if (tags.size() >= 2 && count >= 2) { // At least 2 tags produced by at least 2 inspectors
                String groupKey = "multi_producer_" + count + "_inspectors";
                groups.put(groupKey, tags);
            }
        }
        
        return groups;
    }

    /**
     * Groups multi-producer tags by semantic similarity.
     */
    private Map<String, List<String>> groupMultiProducerTagsBySimilarity(Map<String, Set<String>> multiProducerTags) {
        Map<String, List<String>> groups = new HashMap<>();
        List<String> tagList = new ArrayList<>(multiProducerTags.keySet());
        
        for (int i = 0; i < tagList.size(); i++) {
            for (int j = i + 1; j < tagList.size(); j++) {
                String tag1 = tagList.get(i);
                String tag2 = tagList.get(j);
                
                if (areSemanticallySimilar(tag1, tag2)) {
                    String groupKey = "multi_producer_semantic_" + Math.min(tag1.hashCode(), tag2.hashCode());
                    groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(tag1);
                    groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(tag2);
                }
            }
        }
        
        // Clean up groups
        groups.forEach((key, tagList2) -> {
            Set<String> uniqueTags = new LinkedHashSet<>(tagList2);
            tagList2.clear();
            tagList2.addAll(uniqueTags);
        });
        groups.entrySet().removeIf(entry -> entry.getValue().size() <= 1);
        
        return groups;
    }

    /**
     * Groups tags that share common sets of producers (indicating inspector coupling).
     */
    private Map<String, List<String>> groupTagsByCommonProducers(Map<String, Set<String>> multiProducerTags) {
        Map<String, List<String>> groups = new HashMap<>();
        
        // Group tags by their producer sets
        Map<Set<String>, List<String>> producerSetGroups = multiProducerTags.entrySet().stream()
                .collect(Collectors.groupingBy(
                    Map.Entry::getValue,
                    Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));
        
        // Find groups with multiple tags (same producer set)
        for (Map.Entry<Set<String>, List<String>> entry : producerSetGroups.entrySet()) {
            Set<String> producers = entry.getKey();
            List<String> tags = entry.getValue();
            
            if (tags.size() > 1) { // Multiple tags from same producer set
                String groupKey = "common_producers_" + producers.stream()
                        .sorted()
                        .collect(Collectors.joining("_"))
                        .replaceAll("[^a-zA-Z0-9_]", "_");
                groups.put(groupKey, tags);
                
                logger.debug("Found {} tags with identical producer set {}: {}", tags.size(), producers, tags);
            }
        }
        
        return groups;
    }

    /**
     * Merges semantic groups, avoiding duplicates.
     */
    private void mergeSemanticGroups(Map<String, List<String>> target, Map<String, List<String>> source) {
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            String key = entry.getKey();
            List<String> tags = entry.getValue();
            
            // Check if any tags already exist in target groups
            boolean merged = false;
            for (Map.Entry<String, List<String>> targetEntry : target.entrySet()) {
                if (Collections.disjoint(targetEntry.getValue(), tags)) {
                    continue; // No overlap
                }
                // Merge into existing group
                Set<String> mergedTags = new LinkedHashSet<>(targetEntry.getValue());
                mergedTags.addAll(tags);
                targetEntry.getValue().clear();
                targetEntry.getValue().addAll(mergedTags);
                merged = true;
                break;
            }
            
            if (!merged) {
                target.put(key, new ArrayList<>(tags));
            }
        }
    }

    /**
     * Checks if two tags are semantically similar.
     */
    private boolean areSemanticallySimilar(String tag1, String tag2) {
        String norm1 = normalizeTagName(tag1);
        String norm2 = normalizeTagName(tag2);
        
        // Same after normalization
        if (norm1.equals(norm2)) return true;
        
        // Check edit distance for small variations
        return calculateEditDistance(norm1, norm2) <= Math.max(1, Math.min(norm1.length(), norm2.length()) / 3);
    }

    /**
     * Checks if two tags are pattern-based duplicates.
     */
    private boolean arePatternBasedDuplicates(String tag1, String tag2, Map<String, String> patterns) {
        for (Map.Entry<String, String> pattern : patterns.entrySet()) {
            String from = pattern.getKey();
            String to = pattern.getValue();
            
            if ((tag1.contains(from) && tag2.contains(to)) || 
                (tag1.contains(to) && tag2.contains(from))) {
                // Check if the rest of the tag is similar
                String base1 = tag1.replace(from, "").replace(to, "");
                String base2 = tag2.replace(from, "").replace(to, "");
                return normalizeTagName(base1).equals(normalizeTagName(base2));
            }
        }
        return false;
    }

    /**
     * Checks if two tags are contextually similar.
     */
    private boolean areContextuallySimilar(String tag1, String tag2, Set<String> contextKeywords) {
        // Remove context keywords and check similarity of remaining parts
        String clean1 = tag1;
        String clean2 = tag2;
        
        for (String keyword : contextKeywords) {
            clean1 = clean1.replace(keyword, "");
            clean2 = clean2.replace(keyword, "");
        }
        
        return areSemanticallySimilar(clean1, clean2);
    }

    /**
     * Gets a common pattern identifier for two tags.
     */
    private String getCommonPattern(String tag1, String tag2) {
        String norm1 = normalizeTagName(tag1);
        String norm2 = normalizeTagName(tag2);
        
        StringBuilder common = new StringBuilder();
        int minLength = Math.min(norm1.length(), norm2.length());
        
        for (int i = 0; i < minLength; i++) {
            if (norm1.charAt(i) == norm2.charAt(i)) {
                common.append(norm1.charAt(i));
            } else {
                break;
            }
        }
        
        return common.length() > 0 ? common.toString() : "unknown";
    }

    /**
     * Gets a contextual similarity key for two tags.
     */
    private String getContextualSimilarityKey(String tag1, String tag2) {
        return "ctx_" + Math.min(tag1.hashCode(), tag2.hashCode());
    }

    /**
     * Calculates the edit distance between two strings.
     */
    private int calculateEditDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i][j - 1], dp[i - 1][j]), dp[i - 1][j - 1]);
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }

    /**
     * Enhanced tag name normalization for semantic comparison.
     */
    private String normalizeTagName(String tag) {
        return tag.toLowerCase()
                .replaceAll("[._-]", "")
                .replaceAll("detected?", "")
                .replaceAll("found", "")
                .replaceAll("usage", "")
                .replaceAll("used", "")
                .replaceAll("pattern", "")
                .replaceAll("template", "")
                .replaceAll("config", "")
                .replaceAll("configuration", "")
                .replaceAll("impl", "")
                .replaceAll("implementation", "")
                .replaceAll("mgmt", "")
                .replaceAll("management", "")
                .replaceAll("svc", "")
                .replaceAll("service", "")
                .replaceAll("s$", "") // Remove plural 's'
                .replaceAll("\\d+", "") // Remove numbers
                .trim();
    }

    /**
     * Finds complex dependency chains longer than the minimum threshold.
     */
    private List<List<String>> findComplexDependencyChains(Graph<InspectorNode, TagDependencyEdge> graph,
            Map<String, InspectorNode> nodes) {
        List<List<String>> complexChains = new ArrayList<>();

        // Simple chain detection - could be enhanced with more sophisticated algorithms
        for (InspectorNode startNode : graph.vertexSet()) {
            if (graph.inDegreeOf(startNode) == 0) { // Start from nodes with no dependencies
                List<String> chain = new ArrayList<>();
                findChainsFrom(graph, startNode, chain, complexChains, new HashSet<>());
            }
        }

        return complexChains.stream()
                .filter(chain -> chain.size() >= minChainLength)
                .toList();
    }

    /**
     * Recursively finds dependency chains from a starting node.
     */
    private void findChainsFrom(Graph<InspectorNode, TagDependencyEdge> graph,
            InspectorNode currentNode,
            List<String> currentChain,
            List<List<String>> allChains,
            Set<InspectorNode> visited) {
        if (visited.contains(currentNode)) {
            return; // Avoid cycles
        }

        visited.add(currentNode);
        currentChain.add(currentNode.inspectorName());

        Set<TagDependencyEdge> outgoingEdges = graph.outgoingEdgesOf(currentNode);
        if (outgoingEdges.isEmpty()) {
            // End of chain
            if (currentChain.size() >= minChainLength) {
                allChains.add(new ArrayList<>(currentChain));
            }
        } else {
            // Continue chain
            for (TagDependencyEdge edge : outgoingEdges) {
                InspectorNode targetNode = graph.getEdgeTarget(edge);
                findChainsFrom(graph, targetNode, currentChain, allChains, new HashSet<>(visited));
            }
        }

        currentChain.remove(currentChain.size() - 1);
        visited.remove(currentNode);
    }


}
