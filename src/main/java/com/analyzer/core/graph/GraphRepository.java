package com.analyzer.core.graph;

import com.analyzer.core.inspector.InspectorDependencies;

import org.jgrapht.Graph;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for managing graph nodes and edges.
 * Provides storage and querying capabilities for the analysis graph.
 */
public interface GraphRepository {

    /**
     * Gets or creates a node in the repository.
     * If a node with the same ID already exists, returns the existing node.
     * Otherwise, adds the new node and returns it.
     * 
     * @param node the node to get or create
     * @return the node (existing or newly created)
     */
    GraphNode getOrCreateNode(GraphNode node);

    /**
     * Gets or creates an edge between two nodes.
     * If an edge with the same source, target, and type already exists, returns the
     * existing edge.
     * Otherwise, creates a new edge with auto-generated ID and adds it to the
     * repository.
     * 
     * @param source   the source node
     * @param target   the target node
     * @param edgeType the type of relationship
     * @return the edge (existing or newly created)
     */
    GraphEdge getOrCreateEdge(GraphNode source, GraphNode target, String edgeType);

    /**
     * Gets a node by its ID.
     * 
     * @param nodeId the node ID
     * @return the node if found, empty otherwise
     */
    Optional<GraphNode> getNode(String nodeId);

    /**
     * Gets an edge by its ID.
     * 
     * @param edgeId the edge ID
     * @return the edge if found, empty otherwise
     */
    Optional<GraphEdge> getEdge(String edgeId);

    /**
     * Gets all nodes of specified types.
     * 
     * @param nodeTypes the node types to filter by (empty means all types)
     * @return collection of matching nodes
     */
    Collection<GraphNode> getNodesByType(Set<String> nodeTypes);

    /**
     * Gets all edges of specified types.
     * 
     * @param edgeTypes the edge types to filter by (empty means all types)
     * @return collection of matching edges
     */
    Collection<GraphEdge> getEdgesByType(Set<String> edgeTypes);

    /**
     * Gets all nodes in the repository.
     * 
     * @return all nodes
     */
    Collection<GraphNode> getAllNodes();

    /**
     * Gets all edges in the repository.
     * 
     * @return all edges
     */
    Collection<GraphEdge> getAllEdges();

    /**
     * Builds a JGraphT directed graph based on node and edge type filters.
     * 
     * @param nodeTypes the types of nodes to include (empty means all types)
     * @param edgeTypes the types of edges to include (empty means all types)
     * @return a JGraphT DirectedMultigraph containing the filtered nodes and edges
     */
    Graph<GraphNode, GraphEdge> buildGraph(Set<String> nodeTypes, Set<String> edgeTypes);

    /**
     * Clears all nodes and edges from the repository.
     */
    void clear();

    /**
     * Gets the total number of nodes in the repository.
     * 
     * @return node count
     */
    int getNodeCount();

    /**
     * Gets the total number of edges in the repository.
     * 
     * @return edge count
     */
    int getEdgeCount();

    /**
     * Finds a JavaClassNode by its fully qualified name.
     *
     * @param fqn the fully qualified name
     * @return the JavaClassNode if found, empty otherwise
     */
    Optional<JavaClassNode> findClassByFqn(String fqn);
}
