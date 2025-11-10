package com.analyzer.core.graph;

import com.analyzer.api.graph.GraphEdge;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.JavaClassNode;
import org.jgrapht.Graph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryGraphRepository, specifically testing incoming and
 * outgoing edge functionality
 * using JavaClassNode instances.
 */
@DisplayName("InMemoryGraphRepository - Incoming and Outgoing Edges Tests")
class InMemoryGraphRepositoryTest {

    private InMemoryGraphRepository repository;
    private JavaClassNode classA;
    private JavaClassNode classB;
    private JavaClassNode classC;
    private JavaClassNode classD;

    // Edge type constants
    private static final String EDGE_TYPE_DEPENDS_ON = "DEPENDS_ON";
    private static final String EDGE_TYPE_EXTENDS = "EXTENDS";
    private static final String EDGE_TYPE_IMPLEMENTS = "IMPLEMENTS";

    @BeforeEach
    void setUp() {
        repository = new InMemoryGraphRepository();

        // Create test JavaClassNode instances
        classA = new JavaClassNode("com.example.ClassA");
        classA.setClassType("class");
        classA.setSourceType(JavaClassNode.SOURCE_TYPE_SOURCE);

        classB = new JavaClassNode("com.example.ClassB");
        classB.setClassType("class");
        classB.setSourceType(JavaClassNode.SOURCE_TYPE_SOURCE);

        classC = new JavaClassNode("com.example.ClassC");
        classC.setClassType("interface");
        classC.setSourceType(JavaClassNode.SOURCE_TYPE_SOURCE);

        classD = new JavaClassNode("com.example.ClassD");
        classD.setClassType("class");
        classD.setSourceType(JavaClassNode.SOURCE_TYPE_SOURCE);
    }

    @Test
    @DisplayName("Should return correct outgoing edges for a node with multiple dependencies")
    void testOutgoingEdges_WithMultipleDependencies() {
        // Arrange: ClassA depends on ClassB and ClassC
        repository.getOrCreateNode(classA);
        repository.getOrCreateNode(classB);
        repository.getOrCreateNode(classC);

        GraphEdge edgeAtoB = repository.getOrCreateEdge(classA, classB, EDGE_TYPE_DEPENDS_ON);
        GraphEdge edgeAtoC = repository.getOrCreateEdge(classA, classC, EDGE_TYPE_DEPENDS_ON);

        // Act: Build graph and get outgoing edges
        Graph<GraphNode, GraphEdge> graph = repository.buildGraph(Set.of(), Set.of());
        Set<GraphEdge> outgoingEdges = graph.outgoingEdgesOf(classA);

        // Assert
        assertNotNull(outgoingEdges, "Outgoing edges should not be null");
        assertEquals(2, outgoingEdges.size(), "ClassA should have 2 outgoing edges");
        assertTrue(outgoingEdges.contains(edgeAtoB), "Should contain edge to ClassB");
        assertTrue(outgoingEdges.contains(edgeAtoC), "Should contain edge to ClassC");
    }

    @Test
    @DisplayName("Should return correct incoming edges for a node with multiple dependents")
    void testIncomingEdges_WithMultipleDependents() {
        // Arrange: ClassD is depended upon by ClassB and ClassC
        repository.getOrCreateNode(classB);
        repository.getOrCreateNode(classC);
        repository.getOrCreateNode(classD);

        GraphEdge edgeBtoD = repository.getOrCreateEdge(classB, classD, EDGE_TYPE_EXTENDS);
        GraphEdge edgeCtoD = repository.getOrCreateEdge(classC, classD, EDGE_TYPE_IMPLEMENTS);

        // Act: Build graph and get incoming edges
        Graph<GraphNode, GraphEdge> graph = repository.buildGraph(Set.of(), Set.of());
        Set<GraphEdge> incomingEdges = graph.incomingEdgesOf(classD);

        // Assert
        assertNotNull(incomingEdges, "Incoming edges should not be null");
        assertEquals(2, incomingEdges.size(), "ClassD should have 2 incoming edges");
        assertTrue(incomingEdges.contains(edgeBtoD), "Should contain edge from ClassB");
        assertTrue(incomingEdges.contains(edgeCtoD), "Should contain edge from ClassC");
    }

    @Test
    @DisplayName("Should return empty set for outgoing edges when node has no dependencies")
    void testOutgoingEdges_WithNoDependencies() {
        // Arrange: ClassD has no outgoing edges (it's a sink node)
        repository.getOrCreateNode(classB);
        repository.getOrCreateNode(classD);

        repository.getOrCreateEdge(classB, classD, EDGE_TYPE_EXTENDS);

        // Act: Build graph and get outgoing edges for ClassD
        Graph<GraphNode, GraphEdge> graph = repository.buildGraph(Set.of(), Set.of());
        Set<GraphEdge> outgoingEdges = graph.outgoingEdgesOf(classD);

        // Assert
        assertNotNull(outgoingEdges, "Outgoing edges should not be null");
        assertTrue(outgoingEdges.isEmpty(), "ClassD should have no outgoing edges");
    }

    @Test
    @DisplayName("Should return empty set for incoming edges when node has no dependents")
    void testIncomingEdges_WithNoDependents() {
        // Arrange: ClassA has no incoming edges (it's a source node)
        repository.getOrCreateNode(classA);
        repository.getOrCreateNode(classB);

        repository.getOrCreateEdge(classA, classB, EDGE_TYPE_DEPENDS_ON);

        // Act: Build graph and get incoming edges for ClassA
        Graph<GraphNode, GraphEdge> graph = repository.buildGraph(Set.of(), Set.of());
        Set<GraphEdge> incomingEdges = graph.incomingEdgesOf(classA);

        // Assert
        assertNotNull(incomingEdges, "Incoming edges should not be null");
        assertTrue(incomingEdges.isEmpty(), "ClassA should have no incoming edges");
    }

    @Test
    @DisplayName("Should correctly handle node with both incoming and outgoing edges")
    void testBothIncomingAndOutgoingEdges() {
        // Arrange: Create a chain where ClassB is in the middle
        // ClassA -> ClassB -> ClassC
        repository.getOrCreateNode(classA);
        repository.getOrCreateNode(classB);
        repository.getOrCreateNode(classC);

        GraphEdge edgeAtoB = repository.getOrCreateEdge(classA, classB, EDGE_TYPE_DEPENDS_ON);
        GraphEdge edgeBtoC = repository.getOrCreateEdge(classB, classC, EDGE_TYPE_DEPENDS_ON);

        // Act: Build graph and get both incoming and outgoing edges for ClassB
        Graph<GraphNode, GraphEdge> graph = repository.buildGraph(Set.of(), Set.of());
        Set<GraphEdge> incomingEdges = graph.incomingEdgesOf(classB);
        Set<GraphEdge> outgoingEdges = graph.outgoingEdgesOf(classB);

        // Assert
        assertEquals(1, incomingEdges.size(), "ClassB should have 1 incoming edge");
        assertTrue(incomingEdges.contains(edgeAtoB), "Should contain edge from ClassA");

        assertEquals(1, outgoingEdges.size(), "ClassB should have 1 outgoing edge");
        assertTrue(outgoingEdges.contains(edgeBtoC), "Should contain edge to ClassC");
    }

    @Test
    @DisplayName("Should correctly handle multiple edge types between nodes")
    void testMultipleEdgeTypes() {
        // Arrange: ClassA has multiple types of relationships with other classes
        repository.getOrCreateNode(classA);
        repository.getOrCreateNode(classB);
        repository.getOrCreateNode(classC);
        repository.getOrCreateNode(classD);

        GraphEdge dependsEdge = repository.getOrCreateEdge(classA, classB, EDGE_TYPE_DEPENDS_ON);
        GraphEdge extendsEdge = repository.getOrCreateEdge(classA, classC, EDGE_TYPE_EXTENDS);
        GraphEdge implementsEdge = repository.getOrCreateEdge(classA, classD, EDGE_TYPE_IMPLEMENTS);

        // Act: Build graph and get outgoing edges
        Graph<GraphNode, GraphEdge> graph = repository.buildGraph(Set.of(), Set.of());
        Set<GraphEdge> outgoingEdges = graph.outgoingEdgesOf(classA);

        // Assert
        assertEquals(3, outgoingEdges.size(), "ClassA should have 3 outgoing edges of different types");
        assertTrue(outgoingEdges.contains(dependsEdge), "Should contain DEPENDS_ON edge");
        assertTrue(outgoingEdges.contains(extendsEdge), "Should contain EXTENDS edge");
        assertTrue(outgoingEdges.contains(implementsEdge), "Should contain IMPLEMENTS edge");

        // Verify edge types
        long dependsCount = outgoingEdges.stream()
                .filter(e -> EDGE_TYPE_DEPENDS_ON.equals(e.getEdgeType()))
                .count();
        long extendsCount = outgoingEdges.stream()
                .filter(e -> EDGE_TYPE_EXTENDS.equals(e.getEdgeType()))
                .count();
        long implementsCount = outgoingEdges.stream()
                .filter(e -> EDGE_TYPE_IMPLEMENTS.equals(e.getEdgeType()))
                .count();

        assertEquals(1, dependsCount, "Should have exactly 1 DEPENDS_ON edge");
        assertEquals(1, extendsCount, "Should have exactly 1 EXTENDS edge");
        assertEquals(1, implementsCount, "Should have exactly 1 IMPLEMENTS edge");
    }

    @Test
    @DisplayName("Should handle complex dependency graph with multiple levels")
    void testComplexDependencyGraph() {
        // Arrange: Create a complex graph
        // ClassA -> ClassB -> ClassD
        // ClassA -> ClassC -> ClassD
        repository.getOrCreateNode(classA);
        repository.getOrCreateNode(classB);
        repository.getOrCreateNode(classC);
        repository.getOrCreateNode(classD);

        GraphEdge edgeAtoB = repository.getOrCreateEdge(classA, classB, EDGE_TYPE_DEPENDS_ON);
        GraphEdge edgeAtoC = repository.getOrCreateEdge(classA, classC, EDGE_TYPE_DEPENDS_ON);
        GraphEdge edgeBtoD = repository.getOrCreateEdge(classB, classD, EDGE_TYPE_DEPENDS_ON);
        GraphEdge edgeCtoD = repository.getOrCreateEdge(classC, classD, EDGE_TYPE_DEPENDS_ON);

        // Act: Build graph
        Graph<GraphNode, GraphEdge> graph = repository.buildGraph(Set.of(), Set.of());

        // Assert: Verify ClassA (source node)
        Set<GraphEdge> classAOutgoing = graph.outgoingEdgesOf(classA);
        Set<GraphEdge> classAIncoming = graph.incomingEdgesOf(classA);
        assertEquals(2, classAOutgoing.size(), "ClassA should have 2 outgoing edges");
        assertEquals(0, classAIncoming.size(), "ClassA should have no incoming edges");

        // Assert: Verify ClassB and ClassC (intermediate nodes)
        Set<GraphEdge> classBOutgoing = graph.outgoingEdgesOf(classB);
        Set<GraphEdge> classBIncoming = graph.incomingEdgesOf(classB);
        assertEquals(1, classBOutgoing.size(), "ClassB should have 1 outgoing edge");
        assertEquals(1, classBIncoming.size(), "ClassB should have 1 incoming edge");

        Set<GraphEdge> classCOutgoing = graph.outgoingEdgesOf(classC);
        Set<GraphEdge> classCIncoming = graph.incomingEdgesOf(classC);
        assertEquals(1, classCOutgoing.size(), "ClassC should have 1 outgoing edge");
        assertEquals(1, classCIncoming.size(), "ClassC should have 1 incoming edge");

        // Assert: Verify ClassD (sink node)
        Set<GraphEdge> classDOutgoing = graph.outgoingEdgesOf(classD);
        Set<GraphEdge> classDIncoming = graph.incomingEdgesOf(classD);
        assertEquals(0, classDOutgoing.size(), "ClassD should have no outgoing edges");
        assertEquals(2, classDIncoming.size(), "ClassD should have 2 incoming edges");
        assertTrue(classDIncoming.contains(edgeBtoD), "ClassD should have incoming edge from ClassB");
        assertTrue(classDIncoming.contains(edgeCtoD), "ClassD should have incoming edge from ClassC");
    }

    @Test
    @DisplayName("Should correctly filter edges by type in built graph")
    void testEdgeFiltering() {
        // Arrange: Create edges with different types
        repository.getOrCreateNode(classA);
        repository.getOrCreateNode(classB);
        repository.getOrCreateNode(classC);

        repository.getOrCreateEdge(classA, classB, EDGE_TYPE_DEPENDS_ON);
        repository.getOrCreateEdge(classA, classC, EDGE_TYPE_EXTENDS);

        // Act: Build graph with only DEPENDS_ON edges
        Graph<GraphNode, GraphEdge> filteredGraph = repository.buildGraph(
                Set.of(),
                Set.of(EDGE_TYPE_DEPENDS_ON));

        Set<GraphEdge> outgoingEdges = filteredGraph.outgoingEdgesOf(classA);

        // Assert
        assertEquals(1, outgoingEdges.size(), "Should only include DEPENDS_ON edge");
        GraphEdge edge = outgoingEdges.iterator().next();
        assertEquals(EDGE_TYPE_DEPENDS_ON, edge.getEdgeType(), "Edge should be of type DEPENDS_ON");
        assertEquals(classB, edge.getTarget(), "Edge should point to ClassB");
    }

    @Test
    @DisplayName("Should verify edge direction correctness")
    void testEdgeDirection() {
        // Arrange: Create a directed edge from ClassA to ClassB
        repository.getOrCreateNode(classA);
        repository.getOrCreateNode(classB);
        GraphEdge edge = repository.getOrCreateEdge(classA, classB, EDGE_TYPE_DEPENDS_ON);

        // Act: Build graph
        Graph<GraphNode, GraphEdge> graph = repository.buildGraph(Set.of(), Set.of());

        // Assert: Verify edge direction
        assertEquals(classA, edge.getSource(), "Edge source should be ClassA");
        assertEquals(classB, edge.getTarget(), "Edge target should be ClassB");

        // Verify the edge appears in correct direction
        Set<GraphEdge> classAOutgoing = graph.outgoingEdgesOf(classA);
        Set<GraphEdge> classBIncoming = graph.incomingEdgesOf(classB);

        assertTrue(classAOutgoing.contains(edge), "Edge should be in ClassA's outgoing edges");
        assertTrue(classBIncoming.contains(edge), "Edge should be in ClassB's incoming edges");

        // Verify the edge does NOT appear in opposite direction
        Set<GraphEdge> classAIncoming = graph.incomingEdgesOf(classA);
        Set<GraphEdge> classBOutgoing = graph.outgoingEdgesOf(classB);

        assertFalse(classAIncoming.contains(edge), "Edge should NOT be in ClassA's incoming edges");
        assertFalse(classBOutgoing.contains(edge), "Edge should NOT be in ClassB's outgoing edges");
    }
}
