package com.analyzer.core.graph;

import com.analyzer.api.graph.GraphEdge;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.JavaClassNode;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of GraphRepository.
 * Thread-safe implementation using concurrent data structures.
 */
public class InMemoryGraphRepository implements GraphRepository {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryGraphRepository.class);

    // Primary storage
    private final Map<String, GraphNode> nodes = new ConcurrentHashMap<>(100);
    private final Map<String, GraphEdge> edges = new ConcurrentHashMap<>(100);

    // Index for efficient edge lookups by source-target-type combination
    private final Map<String, GraphEdge> edgeIndex = new ConcurrentHashMap<>(100);

    // Index for efficient class lookups by FQN
    private final Map<String, JavaClassNode> classFqnIndex = new ConcurrentHashMap<>(100);

    @Override
    public final GraphNode getOrCreateNode(final GraphNode node) {
        Objects.requireNonNull(node, "Node cannot be null");
        final String nodeId = node.getId();
        Objects.requireNonNull(nodeId, "Node ID cannot be null");

        final GraphNode existingNode = nodes.get(nodeId);
        if (null != existingNode) {
            logger.debug("Returning existing node with ID: {}", nodeId);
            return existingNode;
        }

        nodes.put(nodeId, node);
        if (node instanceof final JavaClassNode classNode) {
            classFqnIndex.put(classNode.getFullyQualifiedName(), classNode);
        }
        logger.debug("Added new node with ID: {} and type: {}", nodeId, node.getNodeType());
        return node;
    }

    @Override
    public final GraphEdge getOrCreateEdge(final GraphNode source, final GraphNode target, final String edgeType) {
        Objects.requireNonNull(source, "Source node cannot be null");
        Objects.requireNonNull(target, "Target node cannot be null");
        Objects.requireNonNull(edgeType, "Edge type cannot be null");

        final String edgeKey = createEdgeKey(source.getId(), target.getId(), edgeType);
        final GraphEdge existingEdge = edgeIndex.get(edgeKey);

        if (null != existingEdge) {
            logger.debug("Returning existing edge: {} -> {} ({})", source.getId(), target.getId(), edgeType);
            return existingEdge;
        }

        // Ensure both nodes are in the repository
        getOrCreateNode(source);
        getOrCreateNode(target);

        // Create new edge with auto-generated ID
        final GraphEdge newEdge = new GraphEdge(source, target, edgeType);
        edges.put(newEdge.getId(), newEdge);
        edgeIndex.put(edgeKey, newEdge);

        logger.debug("Added new edge: {} -> {} ({}) with ID: {}",
                source.getId(), target.getId(), edgeType, newEdge.getId());
        return newEdge;
    }

    @Override
    public final void addNode(final GraphNode node) {
        Objects.requireNonNull(node, "Node cannot be null");
        Objects.requireNonNull(node.getId(), "Node ID cannot be null");
        nodes.put(node.getId(), node);
        if (node instanceof final JavaClassNode classNode) {
            classFqnIndex.put(classNode.getFullyQualifiedName(), classNode);
        }
    }

    @Override
    public final Optional<GraphNode> getNodeById(final String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    @Override
    public final Optional<GraphEdge> getEdge(final String edgeId) {
        return Optional.ofNullable(edges.get(edgeId));
    }

    @Override
    public final Collection<GraphNode> getNodesByType(final Set<String> nodeTypes) {
        if (null == nodeTypes || nodeTypes.isEmpty()) {
            return getNodes();
        }

        final Collection<GraphNode> values = nodes.values();
        return values.stream()
                     .filter(node -> nodeTypes.contains(node.getNodeType()))
                     .toList();
    }

    @Override
    public final Collection<GraphEdge> getEdgesByType(final Set<String> edgeTypes) {
        if (null == edgeTypes || edgeTypes.isEmpty()) {
            return getAllEdges();
        }

        final Collection<GraphEdge> graphEdges = edges.values();
        return graphEdges.stream()
                         .filter(edge -> edgeTypes.contains(edge.getEdgeType()))
                         .toList();
    }

    @Override
    public final Collection<GraphNode> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    @Override
    public final Collection<GraphEdge> getAllEdges() {
        return Collections.unmodifiableCollection(edges.values());
    }

    @Override
    public final Graph<GraphNode, GraphEdge> buildGraph(final Set<String> nodeTypes, final Set<String> edgeTypes) {
        logger.debug("Building graph with node types: {} and edge types: {}", nodeTypes, edgeTypes);

        // Create a new directed multigraph
        final Graph<GraphNode, GraphEdge> graph = new DirectedMultigraph<>(GraphEdge.class);

        // Add filtered nodes
        final Collection<GraphNode> filteredNodes = getNodesByType(nodeTypes);
        final Set<String> nodeIds = new HashSet<>();

        for (final GraphNode node : filteredNodes) {
            graph.addVertex(node);
            nodeIds.add(node.getId());
        }

        // Add filtered edges (only if both source and target nodes are in the graph)
        final Collection<GraphEdge> filteredEdges = getEdgesByType(edgeTypes);
        int addedEdges = 0;

        for (final GraphEdge edge : filteredEdges) {
            if (nodeIds.contains(edge.getSource().getId()) &&
                    nodeIds.contains(edge.getTarget().getId())) {
                graph.addEdge(edge.getSource(), edge.getTarget(), edge);
                addedEdges++;
            }
        }

        logger.info("Built call graph with {} nodes and {} edges", graph.vertexSet().size(), addedEdges);
        return graph;
    }

    @Override
    public final void clear() {
        logger.info("Clearing graph repository");
        nodes.clear();
        edges.clear();
        edgeIndex.clear();
    }

    @Override
    public final int getNodeCount() {
        return nodes.size();
    }

    @Override
    public final int getEdgeCount() {
        return edges.size();
    }

    @Override
    public final Optional<JavaClassNode> findClassByFqn(final String fqn) {
        return Optional.ofNullable(classFqnIndex.get(fqn));
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T extends GraphNode> Collection<T> getNodesByClass(final Class<T> nodeClass) {
        final Collection<GraphNode> values = nodes.values();
        return (Collection<T>) values.stream()
                                     .filter(nodeClass::isInstance)
                                     .toList();
    }

    @Override
    public final List<GraphNode> findAll() {
        return new ArrayList<>(nodes.values());
    }

    @Override
    public final List<GraphNode> findNodesByType(final String processedType) {
        if (null == processedType) {
            return List.of();
        }

        return nodes.values().stream()
                    .filter(node -> processedType.equals(node.getNodeType()))
                    .toList();
    }

    @Override
    public final List<GraphNode> findNodesByTag(final String tag) {
        if (null == tag) {
            return List.of();
        }

        return nodes.values().stream()
                    .filter(node -> node.hasTag(tag))
                    .toList();
    }

    @Override
    public final List<GraphNode> findNodesByAnyTags(final List<String> processedTags) {
        if (null == processedTags || processedTags.isEmpty()) {
            return List.of();
        }

        return nodes.values().stream()
                    .filter(node -> processedTags.stream().anyMatch(node::hasTag))
                    .toList();
    }

    @Override
    public final List<GraphNode> findNodesByTypeAndAnyTags(final String processedType, final List<String> processedTags) {
        if (null == processedType || null == processedTags || processedTags.isEmpty()) {
            return List.of();
        }

        return nodes.values().stream()
                    .filter(node -> processedType.equals(node.getNodeType()))
                    .filter(node -> processedTags.stream().anyMatch(node::hasTag))
                    .toList();
    }

    /**
     * Creates a unique key for edge indexing based on source ID, target ID, and
     * edge type.
     * This allows efficient lookup of existing edges.
     */
    private String createEdgeKey(final String sourceId, final String targetId, final String edgeType) {
        return sourceId + "|" + targetId + "|" + edgeType;
    }

    /**
     * Gets repository statistics for debugging purposes.
     */
    public final String getStats() {
        final Map<String, Long> nodeTypeCounts = nodes.values().stream()
                                                      .collect(Collectors.groupingBy(GraphNode::getNodeType, Collectors.counting()));

        final Map<String, Long> edgeTypeCounts = edges.values().stream()
                                                      .collect(Collectors.groupingBy(GraphEdge::getEdgeType, Collectors.counting()));

        return String.format("GraphRepository Stats - Nodes: %d (types: %s), Edges: %d (types: %s)",
                getNodeCount(), nodeTypeCounts, getEdgeCount(), edgeTypeCounts);
    }
}
