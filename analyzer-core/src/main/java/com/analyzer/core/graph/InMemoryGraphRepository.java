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
    private final Map<String, GraphNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, GraphEdge> edges = new ConcurrentHashMap<>();

    // Index for efficient edge lookups by source-target-type combination
    private final Map<String, GraphEdge> edgeIndex = new ConcurrentHashMap<>();

    // Index for efficient class lookups by FQN
    private final Map<String, JavaClassNode> classFqnIndex = new ConcurrentHashMap<>();

    @Override
    public GraphNode getOrCreateNode(GraphNode node) {
        Objects.requireNonNull(node, "Node cannot be null");
        Objects.requireNonNull(node.getId(), "Node ID cannot be null");

        GraphNode existingNode = nodes.get(node.getId());
        if (existingNode != null) {
            logger.debug("Returning existing node with ID: {}", node.getId());
            return existingNode;
        }

        nodes.put(node.getId(), node);
        if (node instanceof JavaClassNode) {
            JavaClassNode classNode = (JavaClassNode) node;
            classFqnIndex.put(classNode.getFullyQualifiedName(), classNode);
        }
        logger.debug("Added new node with ID: {} and type: {}", node.getId(), node.getNodeType());
        return node;
    }

    @Override
    public GraphEdge getOrCreateEdge(GraphNode source, GraphNode target, String edgeType) {
        Objects.requireNonNull(source, "Source node cannot be null");
        Objects.requireNonNull(target, "Target node cannot be null");
        Objects.requireNonNull(edgeType, "Edge type cannot be null");

        String edgeKey = createEdgeKey(source.getId(), target.getId(), edgeType);
        GraphEdge existingEdge = edgeIndex.get(edgeKey);

        if (existingEdge != null) {
            logger.debug("Returning existing edge: {} -> {} ({})", source.getId(), target.getId(), edgeType);
            return existingEdge;
        }

        // Ensure both nodes are in the repository
        getOrCreateNode(source);
        getOrCreateNode(target);

        // Create new edge with auto-generated ID
        GraphEdge newEdge = new GraphEdge(source, target, edgeType);
        edges.put(newEdge.getId(), newEdge);
        edgeIndex.put(edgeKey, newEdge);

        logger.debug("Added new edge: {} -> {} ({}) with ID: {}",
                source.getId(), target.getId(), edgeType, newEdge.getId());
        return newEdge;
    }

    @Override
    public void addNode(GraphNode node) {
        Objects.requireNonNull(node, "Node cannot be null");
        Objects.requireNonNull(node.getId(), "Node ID cannot be null");
        nodes.put(node.getId(), node);
        if (node instanceof JavaClassNode) {
            JavaClassNode classNode = (JavaClassNode) node;
            classFqnIndex.put(classNode.getFullyQualifiedName(), classNode);
        }
    }

    @Override
    public Optional<GraphNode> getNodeById(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    @Override
    public Optional<GraphEdge> getEdge(String edgeId) {
        return Optional.ofNullable(edges.get(edgeId));
    }

    @Override
    public Collection<GraphNode> getNodesByType(Set<String> nodeTypes) {
        if (nodeTypes == null || nodeTypes.isEmpty()) {
            return getNodes();
        }

        return nodes.values().stream()
                .filter(node -> nodeTypes.contains(node.getNodeType()))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<GraphEdge> getEdgesByType(Set<String> edgeTypes) {
        if (edgeTypes == null || edgeTypes.isEmpty()) {
            return getAllEdges();
        }

        return edges.values().stream()
                .filter(edge -> edgeTypes.contains(edge.getEdgeType()))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<GraphNode> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    @Override
    public Collection<GraphEdge> getAllEdges() {
        return Collections.unmodifiableCollection(edges.values());
    }

    @Override
    public Graph<GraphNode, GraphEdge> buildGraph(Set<String> nodeTypes, Set<String> edgeTypes) {
        logger.debug("Building graph with node types: {} and edge types: {}", nodeTypes, edgeTypes);

        // Create a new directed multigraph
        Graph<GraphNode, GraphEdge> graph = new DirectedMultigraph<>(GraphEdge.class);

        // Add filtered nodes
        Collection<GraphNode> filteredNodes = getNodesByType(nodeTypes);
        Set<String> nodeIds = new HashSet<>();

        for (GraphNode node : filteredNodes) {
            graph.addVertex(node);
            nodeIds.add(node.getId());
        }

        // Add filtered edges (only if both source and target nodes are in the graph)
        Collection<GraphEdge> filteredEdges = getEdgesByType(edgeTypes);
        int addedEdges = 0;

        for (GraphEdge edge : filteredEdges) {
            if (nodeIds.contains(edge.getSource().getId()) &&
                    nodeIds.contains(edge.getTarget().getId())) {
                graph.addEdge(edge.getSource(), edge.getTarget(), edge);
                addedEdges++;
            }
        }

        logger.debug("Built graph with {} nodes and {} edges", graph.vertexSet().size(), addedEdges);
        return graph;
    }

    @Override
    public void clear() {
        logger.info("Clearing graph repository");
        nodes.clear();
        edges.clear();
        edgeIndex.clear();
    }

    @Override
    public int getNodeCount() {
        return nodes.size();
    }

    @Override
    public int getEdgeCount() {
        return edges.size();
    }

    @Override
    public Optional<JavaClassNode> findClassByFqn(String fqn) {
        return Optional.ofNullable(classFqnIndex.get(fqn));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends GraphNode> Collection<T> getNodesByClass(Class<T> nodeClass) {
        return (Collection<T>) nodes.values().stream()
                .filter(nodeClass::isInstance)
                .collect(Collectors.toList());
    }

    /**
     * Creates a unique key for edge indexing based on source ID, target ID, and
     * edge type.
     * This allows efficient lookup of existing edges.
     */
    private String createEdgeKey(String sourceId, String targetId, String edgeType) {
        return sourceId + "|" + targetId + "|" + edgeType;
    }

    /**
     * Gets repository statistics for debugging purposes.
     */
    public String getStats() {
        Map<String, Long> nodeTypeCounts = nodes.values().stream()
                .collect(Collectors.groupingBy(GraphNode::getNodeType, Collectors.counting()));

        Map<String, Long> edgeTypeCounts = edges.values().stream()
                .collect(Collectors.groupingBy(GraphEdge::getEdgeType, Collectors.counting()));

        return String.format("GraphRepository Stats - Nodes: %d (types: %s), Edges: %d (types: %s)",
                getNodeCount(), nodeTypeCounts, getEdgeCount(), edgeTypeCounts);
    }
}
