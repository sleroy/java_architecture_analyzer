package com.analyzer.core.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jgrapht.Graph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.AttributeType;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.nio.graphml.GraphMLExporter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Contains the complete analysis result with graph and insights.
 */
public final class GraphAnalysisResult {

    // GraphML attribute name constants
    private static final String ATTR_NAME = "node_name";
    private static final String ATTR_CLASS_NAME = "className";
    private static final String ATTR_REQUIRED_TAGS_COUNT = "requiredTagsCount";
    private static final String ATTR_PRODUCED_TAGS_COUNT = "producedTagsCount";
    private static final String ATTR_REQUIRED_TAGS_LIST = "requiredTagsList";
    private static final String ATTR_PRODUCED_TAGS_LIST = "producedTagsList";
    private static final String ATTR_TAG = "tag";
    private static final String ATTR_PRODUCER = "producer";
    private static final String ATTR_CONSUMER = "consumer";
    private final Graph<InspectorNode, TagDependencyEdge> graph;
    private final Map<String, InspectorNode> inspectorNodes;
    private final Map<String, Set<String>> tagProducers;
    private final Map<String, Set<String>> tagConsumers;
    private final Set<String> allTags;
    private final Set<String> unusedTags;
    private final Map<String, List<String>> potentialDuplicates;
    private final List<List<String>> complexChains;

    /**
     *
     */
    public GraphAnalysisResult(Graph<InspectorNode, TagDependencyEdge> graph,
                               Map<String, InspectorNode> inspectorNodes, Map<String, Set<String>> tagProducers,
                               Map<String, Set<String>> tagConsumers, Set<String> allTags,
                               Set<String> unusedTags, Map<String, List<String>> potentialDuplicates,
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

    /**
     * Gets the top tag producers by number of tags produced.
     */
    public LinkedHashMap<String, Integer> getTopTagProducers(int limit) {
        return inspectorNodes.values().stream()
                .collect(Collectors.toMap(
                        InspectorNode::inspectorName,
                        node -> node.producedTags().size(),
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
                        InspectorNode::inspectorName,
                        node -> node.requiredTags().size(),
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
     * Exports the graph to GraphML format using JGraphT's GraphMLExporter.
     */
    public void exportGraphML(File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            GraphMLExporter<InspectorNode, TagDependencyEdge> exporter = new GraphMLExporter<>();

            // Register vertex attributes BEFORE setting providers - this is crucial for
            // GraphML validity
            exporter.registerAttribute(ATTR_NAME, GraphMLExporter.AttributeCategory.NODE,
                    AttributeType.STRING, null);
            exporter.registerAttribute(ATTR_CLASS_NAME, GraphMLExporter.AttributeCategory.NODE,
                    AttributeType.STRING, null);
            exporter.registerAttribute(ATTR_REQUIRED_TAGS_LIST, GraphMLExporter.AttributeCategory.NODE,
                    AttributeType.INT, "0");
            exporter.registerAttribute(ATTR_REQUIRED_TAGS_COUNT, GraphMLExporter.AttributeCategory.NODE,
                    AttributeType.INT, "0");
            exporter.registerAttribute(ATTR_REQUIRED_TAGS_LIST, GraphMLExporter.AttributeCategory.NODE,
                    AttributeType.STRING, "");
            exporter.registerAttribute(ATTR_PRODUCED_TAGS_LIST, GraphMLExporter.AttributeCategory.NODE,
                    AttributeType.STRING, "");

            // Register edge attributes BEFORE setting providers
            exporter.registerAttribute(ATTR_TAG, GraphMLExporter.AttributeCategory.EDGE,
                    AttributeType.STRING, null);
            exporter.registerAttribute(ATTR_PRODUCER, GraphMLExporter.AttributeCategory.EDGE,
                    AttributeType.STRING, null);
            exporter.registerAttribute(ATTR_CONSUMER, GraphMLExporter.AttributeCategory.EDGE,
                    AttributeType.STRING, null);

          //  exporter.setVertexLabelAttributeName(ATTR_NAME);

            // Configure node attributes
            exporter.setVertexAttributeProvider(node -> {
                Map<String, Attribute> attributes = new LinkedHashMap<>();
                attributes.put(ATTR_NAME, DefaultAttribute.createAttribute(node.requiredTags().size()));
                attributes.put(ATTR_PRODUCED_TAGS_COUNT, DefaultAttribute.createAttribute(node.producedTags().size()));
                attributes.put(ATTR_REQUIRED_TAGS_LIST,
                        DefaultAttribute.createAttribute(String.join(", ", node.requiredTags())));
                attributes.put(ATTR_PRODUCED_TAGS_LIST,
                        DefaultAttribute.createAttribute(String.join(", ", node.producedTags())));
                return attributes;
            });

            // Configure edge attributes
            exporter.setEdgeAttributeProvider(edge -> {
                Map<String, Attribute> attributes = new LinkedHashMap<>();
                String tagList = String.join(", ", edge.getTags());
                attributes.put(ATTR_TAG, DefaultAttribute.createAttribute(tagList));
                attributes.put(ATTR_PRODUCER, DefaultAttribute.createAttribute(edge.getProducer().inspectorName()));
                attributes.put(ATTR_CONSUMER, DefaultAttribute.createAttribute(edge.getConsumer().inspectorName()));
                return attributes;
            });
            exporter.setExportVertexLabels(true);
            exporter.setExportEdgeLabels(true);

            // Configure node ID provider
            exporter.setVertexIdProvider(node -> node.inspectorName().replaceAll("[^a-zA-Z0-9_-]", "_"));

            // Configure edge ID provider
            exporter.setEdgeIdProvider(edge -> {
                String tagString = String.join("_", edge.getTags()).replaceAll("[^a-zA-Z0-9_-]", "_");
                return String.format("%s_%s_%s",
                        edge.getProducer().inspectorName().replaceAll("[^a-zA-Z0-9_-]", "_"),
                        tagString,
                        edge.getConsumer().inspectorName().replaceAll("[^a-zA-Z0-9_-]", "_"));
            });

            // Export the graph
            exporter.exportGraph(graph, writer);
        }
    }

    /**
     * Exports the graph to DOT format (Graphviz) using JGraphT's DOTExporter.
     */
    public void exportDot(File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            DOTExporter<InspectorNode, TagDependencyEdge> exporter = new DOTExporter<>();

            // Configure node attributes
            exporter.setVertexAttributeProvider(node -> {
                Map<String, Attribute> attributes = new LinkedHashMap<>();
                String label = String.format("%s\\n(%d deps, %d produces)",
                        escapeForDot(node.inspectorName()),
                        node.requiredTags().size(),
                        node.producedTags().size());
                attributes.put("label", DefaultAttribute.createAttribute(label));
                attributes.put("shape", DefaultAttribute.createAttribute("box"));
                attributes.put("style", DefaultAttribute.createAttribute("filled"));
                attributes.put("fillcolor", DefaultAttribute.createAttribute("lightblue"));
                attributes.put("fontname", DefaultAttribute.createAttribute("Arial"));
                attributes.put("fontsize", DefaultAttribute.createAttribute("10"));
                return attributes;
            });

            // Configure edge attributes
            exporter.setEdgeAttributeProvider(edge -> {
                Map<String, Attribute> attributes = new LinkedHashMap<>();
                String tagList = String.join(", ", edge.getTags());
                attributes.put("label", DefaultAttribute.createAttribute(escapeForDot(tagList)));
                attributes.put("fontsize", DefaultAttribute.createAttribute("9"));
                attributes.put("fontname", DefaultAttribute.createAttribute("Arial"));
                attributes.put("color", DefaultAttribute.createAttribute("darkblue"));
                return attributes;
            });

            // Configure node ID provider
            exporter.setVertexIdProvider(node -> escapeForDot(node.inspectorName()).replaceAll("[^a-zA-Z0-9_]", "_"));

            // Write DOT header with graph attributes manually
            writer.write("digraph InspectorDependencies {\n");
            writer.write("  rankdir=LR;\n");
            writer.write("  bgcolor=white;\n");
            writer.write("  node [fontname=\"Arial\"];\n");
            writer.write("  edge [fontname=\"Arial\"];\n");

            // Use StringWriter to capture the exporter output and modify it
            StringWriter stringWriter = new StringWriter();
            exporter.exportGraph(graph, stringWriter);
            String dotContent = stringWriter.toString();

            // Remove the default header and footer, keep only the content
            String[] lines = dotContent.split("\n");
            boolean inGraph = false;
            for (String line : lines) {
                if (line.trim().startsWith("digraph") || line.trim().equals("{")) {
                    inGraph = true;
                    continue;
                }
                if (line.trim().equals("}") && inGraph) {
                    break;
                }
                if (inGraph && !line.trim().isEmpty()) {
                    writer.write("  " + line + "\n");
                }
            }

            writer.write("}\n");
        }
    }

    /**
     * Escapes special characters for DOT format.
     */
    private String escapeForDot(String text) {
        if (text == null)
            return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
            inspector.put("name", node.inspectorName());
            inspector.put("className", node.className());
            inspector.put("requiredTags", node.requiredTags());
            inspector.put("producedTags", node.producedTags());
            inspectors.add(inspector);
        }
        jsonData.put("inspectors", inspectors);

        // Dependencies
        List<Map<String, Object>> dependencies = new ArrayList<>();
        for (TagDependencyEdge edge : graph.edgeSet()) {
            Map<String, Object> dependency = new HashMap<>();
            dependency.put("tags", edge.getTags());
            dependency.put("producer", edge.getProducer().inspectorName());
            dependency.put("consumer", edge.getConsumer().inspectorName());
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

    public Graph<InspectorNode, TagDependencyEdge> graph() {
        return graph;
    }

    public Map<String, InspectorNode> inspectorNodes() {
        return inspectorNodes;
    }

    public Map<String, Set<String>> tagProducers() {
        return tagProducers;
    }

    public Map<String, Set<String>> tagConsumers() {
        return tagConsumers;
    }

    public Set<String> allTags() {
        return allTags;
    }

    public Set<String> unusedTags() {
        return unusedTags;
    }

    public Map<String, List<String>> potentialDuplicates() {
        return potentialDuplicates;
    }

    public List<List<String>> complexChains() {
        return complexChains;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (GraphAnalysisResult) obj;
        return Objects.equals(this.graph, that.graph) &&
                Objects.equals(this.inspectorNodes, that.inspectorNodes) &&
                Objects.equals(this.tagProducers, that.tagProducers) &&
                Objects.equals(this.tagConsumers, that.tagConsumers) &&
                Objects.equals(this.allTags, that.allTags) &&
                Objects.equals(this.unusedTags, that.unusedTags) &&
                Objects.equals(this.potentialDuplicates, that.potentialDuplicates) &&
                Objects.equals(this.complexChains, that.complexChains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(graph, inspectorNodes, tagProducers, tagConsumers, allTags, unusedTags, potentialDuplicates, complexChains);
    }

    @Override
    public String toString() {
        return "GraphAnalysisResult[" +
                "graph=" + graph + ", " +
                "inspectorNodes=" + inspectorNodes + ", " +
                "tagProducers=" + tagProducers + ", " +
                "tagConsumers=" + tagConsumers + ", " +
                "allTags=" + allTags + ", " +
                "unusedTags=" + unusedTags + ", " +
                "potentialDuplicates=" + potentialDuplicates + ", " +
                "complexChains=" + complexChains + ']';
    }

}
