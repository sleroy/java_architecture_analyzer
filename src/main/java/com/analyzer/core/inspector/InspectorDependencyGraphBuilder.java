package com.analyzer.core;

import com.analyzer.core.graph.GraphAnalysisResult;
import com.analyzer.core.graph.InspectorNode;
import com.analyzer.core.graph.TagDependencyEdge;
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

        // Add edges for each tag dependency
        for (Map.Entry<String, Set<String>> entry : tagConsumers.entrySet()) {
            String tag = entry.getKey();
            Set<String> consumers = entry.getValue();
            Set<String> producers = tagProducers.getOrDefault(tag, Collections.emptySet());

            // Create edges from each producer to each consumer
            for (String producerName : producers) {
                InspectorNode producer = nodes.get(producerName);
                for (String consumerName : consumers) {
                    InspectorNode consumer = nodes.get(consumerName);
                    if (producer != null && consumer != null && !producer.equals(consumer)) {
                        TagDependencyEdge edge = new TagDependencyEdge(tag, producer, consumer);
                        graph.addEdge(producer, consumer, edge);
                    }
                }
            }
        }

        return graph;
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
     * Finds potential semantic duplicates by analyzing tag names.
     */
    private Map<String, List<String>> findSemanticDuplicates(Set<String> allTags) {
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
     * Normalizes tag names for semantic comparison.
     */
    private String normalizeTagName(String tag) {
        return tag.toLowerCase()
                .replaceAll("[._-]", "")
                .replaceAll("detected?", "")
                .replaceAll("found", "")
                .replaceAll("usage", "")
                .replaceAll("pattern", "")
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
                .collect(Collectors.toList());
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
