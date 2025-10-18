package com.analyzer.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.graphml.GraphMLExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
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
            for (String producedTag : node.getProducedTags()) {
                tagProducers.computeIfAbsent(producedTag, k -> new HashSet<>()).add(node.getInspectorName());
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
            for (String requiredTag : node.getRequiredTags()) {
                tagConsumers.computeIfAbsent(requiredTag, k -> new HashSet<>()).add(node.getInspectorName());
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
        currentChain.add(currentNode.getInspectorName());

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

    /**
     * Represents a node in the dependency graph (an inspector).
     */
    public static class InspectorNode {
        private final String inspectorName;
        private final String className;
        private final Set<String> requiredTags;
        private final Set<String> producedTags;

        public InspectorNode(String inspectorName, String className, Set<String> requiredTags,
                Set<String> producedTags) {
            this.inspectorName = inspectorName;
            this.className = className;
            this.requiredTags = new HashSet<>(requiredTags);
            this.producedTags = new HashSet<>(producedTags);
        }

        public String getInspectorName() {
            return inspectorName;
        }

        public String getClassName() {
            return className;
        }

        public Set<String> getRequiredTags() {
            return requiredTags;
        }

        public Set<String> getProducedTags() {
            return producedTags;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            InspectorNode that = (InspectorNode) o;
            return Objects.equals(inspectorName, that.inspectorName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inspectorName);
        }

        @Override
        public String toString() {
            return inspectorName;
        }
    }

    /**
     * Represents an edge in the dependency graph (a tag dependency).
     */
    public static class TagDependencyEdge {
        private final String tag;
        private final InspectorNode producer;
        private final InspectorNode consumer;

        public TagDependencyEdge(String tag, InspectorNode producer, InspectorNode consumer) {
            this.tag = tag;
            this.producer = producer;
            this.consumer = consumer;
        }

        public String getTag() {
            return tag;
        }

        public InspectorNode getProducer() {
            return producer;
        }

        public InspectorNode getConsumer() {
            return consumer;
        }

        @Override
        public String toString() {
            return String.format("%s -[%s]-> %s", producer.getInspectorName(), tag, consumer.getInspectorName());
        }
    }

    /**
     * Contains the complete analysis result with graph and insights.
     */
    public static class GraphAnalysisResult {
        private final Graph<InspectorNode, TagDependencyEdge> graph;
        private final Map<String, InspectorNode> inspectorNodes;
        private final Map<String, Set<String>> tagProducers;
        private final Map<String, Set<String>> tagConsumers;
        private final Set<String> allTags;
        private final Set<String> unusedTags;
        private final Map<String, List<String>> potentialDuplicates;
        private final List<List<String>> complexChains;

        public GraphAnalysisResult(Graph<InspectorNode, TagDependencyEdge> graph,
                Map<String, InspectorNode> inspectorNodes,
                Map<String, Set<String>> tagProducers,
                Map<String, Set<String>> tagConsumers,
                Set<String> allTags,
                Set<String> unusedTags,
                Map<String, List<String>> potentialDuplicates,
                List<List<String>> complexChains) {
            this.graph = graph;
            this.inspectorNodes = inspectorNodes;
            this.tagProducers = tagProducers;
            this.tagConsumers = tagConsumers;
            this.allTags = allTags;
            this.unusedTags = unusedTags;
            this.potentialDuplicates = potentialDuplicates;
            this.complexChains = complexChains;
        }

        public int getTotalInspectors() {
            return inspectorNodes.size();
        }

        public int getTotalDependencies() {
            return graph.edgeSet().size();
        }

        public Set<String> getUniqueTags() {
            return allTags;
        }

        public Set<String> getUnusedTags() {
            return unusedTags;
        }

        public Map<String, List<String>> getPotentialDuplicates() {
            return potentialDuplicates;
        }

        public List<List<String>> getComplexChains() {
            return complexChains;
        }

        /**
         * Gets the top tag producers by number of tags produced.
         */
        public LinkedHashMap<String, Integer> getTopTagProducers(int limit) {
            return inspectorNodes.values().stream()
                    .collect(Collectors.toMap(
                            InspectorNode::getInspectorName,
                            node -> node.getProducedTags().size(),
                            (existing, replacement) -> existing,
                            LinkedHashMap::new))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(limit)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (existing, replacement) -> existing,
                            LinkedHashMap::new));
        }

        /**
         * Gets the top tag consumers by number of dependencies.
         */
        public LinkedHashMap<String, Integer> getTopTagConsumers(int limit) {
            return inspectorNodes.values().stream()
                    .collect(Collectors.toMap(
                            InspectorNode::getInspectorName,
                            node -> node.getRequiredTags().size(),
                            (existing, replacement) -> existing,
                            LinkedHashMap::new))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(limit)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (existing, replacement) -> existing,
                            LinkedHashMap::new));
        }

        /**
         * Exports the graph to GraphML format.
         */
        public void exportGraphML(File outputFile) throws IOException {
            GraphMLExporter<InspectorNode, TagDependencyEdge> exporter = new GraphMLExporter<>();

            // Configure node attributes
            exporter.setVertexAttributeProvider(node -> {
                Map<String, Attribute> attributes = new HashMap<>();
                attributes.put("name", DefaultAttribute.createAttribute(node.getInspectorName()));
                attributes.put("className", DefaultAttribute.createAttribute(node.getClassName()));
                attributes.put("requiredTags",
                        DefaultAttribute.createAttribute(String.valueOf(node.getRequiredTags().size())));
                attributes.put("producedTags",
                        DefaultAttribute.createAttribute(String.valueOf(node.getProducedTags().size())));
                attributes.put("requiredTagsList",
                        DefaultAttribute.createAttribute(String.join(",", node.getRequiredTags())));
                attributes.put("producedTagsList",
                        DefaultAttribute.createAttribute(String.join(",", node.getProducedTags())));
                return attributes;
            });

            // Configure edge attributes
            exporter.setEdgeAttributeProvider(edge -> {
                Map<String, Attribute> attributes = new HashMap<>();
                attributes.put("tag", DefaultAttribute.createAttribute(edge.getTag()));
                attributes.put("producer", DefaultAttribute.createAttribute(edge.getProducer().getInspectorName()));
                attributes.put("consumer", DefaultAttribute.createAttribute(edge.getConsumer().getInspectorName()));
                return attributes;
            });

            // Configure graph attributes
            exporter.setGraphAttributeProvider(() -> {
                Map<String, Attribute> attributes = new HashMap<>();
                attributes.put("generatedBy", DefaultAttribute.createAttribute("Java Architecture Analyzer"));
                attributes.put("generatedAt", DefaultAttribute
                        .createAttribute(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
                attributes.put("totalInspectors",
                        DefaultAttribute.createAttribute(String.valueOf(getTotalInspectors())));
                attributes.put("totalDependencies",
                        DefaultAttribute.createAttribute(String.valueOf(getTotalDependencies())));
                attributes.put("uniqueTags", DefaultAttribute.createAttribute(String.valueOf(getUniqueTags().size())));
                return attributes;
            });

            try (FileWriter writer = new FileWriter(outputFile)) {
                exporter.exportGraph(graph, writer);
            }
        }

        /**
         * Exports the graph to DOT format (Graphviz).
         */
        public void exportDot(File outputFile) throws IOException {
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write("digraph InspectorDependencies {\n");
                writer.write("  rankdir=LR;\n");
                writer.write("  node [shape=box, style=filled, fillcolor=lightblue];\n");
                writer.write("  edge [fontsize=10];\n\n");

                // Write nodes
                for (InspectorNode node : graph.vertexSet()) {
                    String label = String.format("%s\\n(%d deps, %d produces)",
                            node.getInspectorName(),
                            node.getRequiredTags().size(),
                            node.getProducedTags().size());
                    writer.write(String.format("  \"%s\" [label=\"%s\"];\n",
                            node.getInspectorName(), label));
                }

                writer.write("\n");

                // Write edges
                for (TagDependencyEdge edge : graph.edgeSet()) {
                    writer.write(String.format("  \"%s\" -> \"%s\" [label=\"%s\"];\n",
                            edge.getProducer().getInspectorName(),
                            edge.getConsumer().getInspectorName(),
                            edge.getTag()));
                }

                writer.write("}\n");
            }
        }

        /**
         * Exports the analysis results to JSON format.
         */
        public void exportJson(File outputFile) throws IOException {
            Map<String, Object> jsonData = new HashMap<>();

            // Basic statistics
            jsonData.put("metadata", Map.of(
                    "generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "totalInspectors", getTotalInspectors(),
                    "totalDependencies", getTotalDependencies(),
                    "uniqueTags", getUniqueTags().size()));

            // Inspectors
            List<Map<String, Object>> inspectors = new ArrayList<>();
            for (InspectorNode node : inspectorNodes.values()) {
                Map<String, Object> inspector = new HashMap<>();
                inspector.put("name", node.getInspectorName());
                inspector.put("className", node.getClassName());
                inspector.put("requiredTags", node.getRequiredTags());
                inspector.put("producedTags", node.getProducedTags());
                inspectors.add(inspector);
            }
            jsonData.put("inspectors", inspectors);

            // Dependencies
            List<Map<String, Object>> dependencies = new ArrayList<>();
            for (TagDependencyEdge edge : graph.edgeSet()) {
                Map<String, Object> dependency = new HashMap<>();
                dependency.put("tag", edge.getTag());
                dependency.put("producer", edge.getProducer().getInspectorName());
                dependency.put("consumer", edge.getConsumer().getInspectorName());
                dependencies.add(dependency);
            }
            jsonData.put("dependencies", dependencies);

            // Analysis results
            jsonData.put("analysis", Map.of(
                    "unusedTags", unusedTags,
                    "potentialDuplicates", potentialDuplicates,
                    "complexChains", complexChains));

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(outputFile, jsonData);
        }
    }
}
