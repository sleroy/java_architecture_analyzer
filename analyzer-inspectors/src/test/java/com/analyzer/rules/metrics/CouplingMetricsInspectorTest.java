package com.analyzer.rules.metrics;

import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.InMemoryGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CouplingMetricsInspector.
 * Tests the calculation of coupling metrics including direct and transitive
 * afferent/efferent coupling and instability.
 */
@DisplayName("CouplingMetricsInspector Tests")
class CouplingMetricsInspectorTest {

        private InMemoryGraphRepository repository;
        private CouplingMetricsInspector inspector;

        // Test class nodes representing different coupling scenarios
        private JavaClassNode classA;
        private JavaClassNode classB;
        private JavaClassNode classC;
        private JavaClassNode classD;
        private JavaClassNode classE;
        private JavaClassNode isolatedClass;

        @BeforeEach
        void setUp() {
                repository = new InMemoryGraphRepository();
                inspector = new CouplingMetricsInspector(repository);

                // Create test classes
                classA = createJavaClassNode("com.example.ClassA");
                classB = createJavaClassNode("com.example.ClassB");
                classC = createJavaClassNode("com.example.ClassC");
                classD = createJavaClassNode("com.example.ClassD");
                classE = createJavaClassNode("com.example.ClassE");
                isolatedClass = createJavaClassNode("com.example.IsolatedClass");
        }

        @Test
        @DisplayName("Should calculate zero coupling for isolated class")
        void testIsolatedClass() {
                // Arrange: Create an isolated class with no dependencies
                repository.getOrCreateNode(isolatedClass);

                // Act
                NodeDecorator<JavaClassNode> decorator = new NodeDecorator<>(isolatedClass);
                inspector.inspect(isolatedClass, decorator);

                // Assert: All coupling metrics should be zero
                assertEquals(0, getMetric(decorator, JavaClassNode.METRIC_DIRECT_AFFERENT_COUPLING),
                                "Isolated class should have zero direct afferent coupling");
                assertEquals(0, getMetric(decorator, JavaClassNode.METRIC_DIRECT_EFFERENT_COUPLING),
                                "Isolated class should have zero direct efferent coupling");
                assertEquals(0, getMetric(decorator, JavaClassNode.METRIC_TRANSITIVE_AFFERENT_COUPLING),
                                "Isolated class should have zero transitive afferent coupling");
                assertEquals(0, getMetric(decorator, JavaClassNode.METRIC_TRANSITIVE_EFFERENT_COUPLING),
                                "Isolated class should have zero transitive efferent coupling");
                assertEquals(0.0, getMetricDouble(decorator, JavaClassNode.METRIC_INSTABILITY), 0.001,
                                "Isolated class should have zero instability");

                // Verify tag was set
                assertTrue(isolatedClass.hasTag(CouplingMetricsInspector.TAGS.COUPLING_METRICS_CALCULATED),
                                "Should have coupling metrics calculated tag");
        }

        @Test
        @DisplayName("Should calculate correct direct coupling for simple dependency")
        void testSimpleDirectCoupling() {
                // Arrange: A -> B (A depends on B)
                repository.getOrCreateNode(classA);
                repository.getOrCreateNode(classB);
                repository.getOrCreateEdge(classA, classB, "uses");

                // Act: Calculate metrics for ClassA (has outgoing dependency)
                NodeDecorator<JavaClassNode> decoratorA = new NodeDecorator<>(classA);
                inspector.inspect(classA, decoratorA);

                // Assert: ClassA metrics
                assertEquals(0, getMetric(decoratorA, JavaClassNode.METRIC_DIRECT_AFFERENT_COUPLING),
                                "ClassA should have no incoming dependencies");
                assertEquals(1, getMetric(decoratorA, JavaClassNode.METRIC_DIRECT_EFFERENT_COUPLING),
                                "ClassA should have 1 outgoing dependency");

                // Act: Calculate metrics for ClassB (has incoming dependency)
                NodeDecorator<JavaClassNode> decoratorB = new NodeDecorator<>(classB);
                inspector.inspect(classB, decoratorB);

                // Assert: ClassB metrics
                assertEquals(1, getMetric(decoratorB, JavaClassNode.METRIC_DIRECT_AFFERENT_COUPLING),
                                "ClassB should have 1 incoming dependency");
                assertEquals(0, getMetric(decoratorB, JavaClassNode.METRIC_DIRECT_EFFERENT_COUPLING),
                                "ClassB should have no outgoing dependencies");
        }

        @Test
        @DisplayName("Should calculate instability correctly")
        void testInstabilityCalculation() {
                // Arrange: Create three scenarios
                // 1. A -> B (A is unstable: only outgoing)
                // 2. B <- A, B <- C (B is stable: only incoming)
                // 3. D -> E <- D (E is balanced)

                repository.getOrCreateNode(classA);
                repository.getOrCreateNode(classB);
                repository.getOrCreateNode(classC);

                repository.getOrCreateEdge(classA, classB, "uses");
                repository.getOrCreateEdge(classC, classB, "uses");

                // Act & Assert: ClassA (only outgoing, maximally unstable)
                NodeDecorator<JavaClassNode> decoratorA = new NodeDecorator<>(classA);
                inspector.inspect(classA, decoratorA);
                assertEquals(1.0, getMetricDouble(decoratorA, JavaClassNode.METRIC_INSTABILITY), 0.001,
                                "ClassA should be maximally unstable (I=1.0)");

                // Act & Assert: ClassB (only incoming, maximally stable)
                NodeDecorator<JavaClassNode> decoratorB = new NodeDecorator<>(classB);
                inspector.inspect(classB, decoratorB);
                assertEquals(0.0, getMetricDouble(decoratorB, JavaClassNode.METRIC_INSTABILITY), 0.001,
                                "ClassB should be maximally stable (I=0.0)");

                // Act & Assert: ClassC (only outgoing, unstable)
                NodeDecorator<JavaClassNode> decoratorC = new NodeDecorator<>(classC);
                inspector.inspect(classC, decoratorC);
                assertEquals(1.0, getMetricDouble(decoratorC, JavaClassNode.METRIC_INSTABILITY), 0.001,
                                "ClassC should be unstable (I=1.0)");
        }

        @Test
        @DisplayName("Should calculate transitive efferent coupling correctly")
        void testTransitiveEfferentCoupling() {
                // Arrange: Create a chain A -> B -> C -> D
                repository.getOrCreateNode(classA);
                repository.getOrCreateNode(classB);
                repository.getOrCreateNode(classC);
                repository.getOrCreateNode(classD);

                repository.getOrCreateEdge(classA, classB, "uses");
                repository.getOrCreateEdge(classB, classC, "uses");
                repository.getOrCreateEdge(classC, classD, "uses");

                // Act: Calculate metrics for ClassA (should reach B, C, D)
                NodeDecorator<JavaClassNode> decoratorA = new NodeDecorator<>(classA);
                inspector.inspect(classA, decoratorA);

                // Assert
                assertEquals(1, getMetric(decoratorA, JavaClassNode.METRIC_DIRECT_EFFERENT_COUPLING),
                                "ClassA should have 1 direct dependency");
                assertEquals(3, getMetric(decoratorA, JavaClassNode.METRIC_TRANSITIVE_EFFERENT_COUPLING),
                                "ClassA should be able to reach 3 classes transitively (B, C, D)");

                // Act: Calculate metrics for ClassB (should reach C, D)
                NodeDecorator<JavaClassNode> decoratorB = new NodeDecorator<>(classB);
                inspector.inspect(classB, decoratorB);

                // Assert
                assertEquals(2, getMetric(decoratorB, JavaClassNode.METRIC_TRANSITIVE_EFFERENT_COUPLING),
                                "ClassB should be able to reach 2 classes transitively (C, D)");
        }

        @Test
        @DisplayName("Should calculate transitive afferent coupling correctly")
        void testTransitiveAfferentCoupling() {
                // Arrange: Create a chain A -> B -> C -> D
                repository.getOrCreateNode(classA);
                repository.getOrCreateNode(classB);
                repository.getOrCreateNode(classC);
                repository.getOrCreateNode(classD);

                repository.getOrCreateEdge(classA, classB, "uses");
                repository.getOrCreateEdge(classB, classC, "uses");
                repository.getOrCreateEdge(classC, classD, "uses");

                // Act: Calculate metrics for ClassD (should be reachable from A, B, C)
                NodeDecorator<JavaClassNode> decoratorD = new NodeDecorator<>(classD);
                inspector.inspect(classD, decoratorD);

                // Assert
                assertEquals(1, getMetric(decoratorD, JavaClassNode.METRIC_DIRECT_AFFERENT_COUPLING),
                                "ClassD should have 1 direct dependent");
                assertEquals(3, getMetric(decoratorD, JavaClassNode.METRIC_TRANSITIVE_AFFERENT_COUPLING),
                                "ClassD should be reachable from 3 classes (A, B, C)");

                // Act: Calculate metrics for ClassC (should be reachable from A, B)
                NodeDecorator<JavaClassNode> decoratorC = new NodeDecorator<>(classC);
                inspector.inspect(classC, decoratorC);

                // Assert
                assertEquals(2, getMetric(decoratorC, JavaClassNode.METRIC_TRANSITIVE_AFFERENT_COUPLING),
                                "ClassC should be reachable from 2 classes (A, B)");
        }

        @Test
        @DisplayName("Should handle diamond dependency pattern correctly")
        void testDiamondDependencyPattern() {
                // Arrange: Create diamond pattern
                // A
                // / \
                // B C
                // \ /
                // D
                repository.getOrCreateNode(classA);
                repository.getOrCreateNode(classB);
                repository.getOrCreateNode(classC);
                repository.getOrCreateNode(classD);

                repository.getOrCreateEdge(classA, classB, "uses");
                repository.getOrCreateEdge(classA, classC, "uses");
                repository.getOrCreateEdge(classB, classD, "uses");
                repository.getOrCreateEdge(classC, classD, "uses");

                // Act: Calculate metrics for ClassA (top of diamond)
                NodeDecorator<JavaClassNode> decoratorA = new NodeDecorator<>(classA);
                inspector.inspect(classA, decoratorA);

                // Assert: ClassA should reach B, C, D (but D counted only once)
                assertEquals(2, getMetric(decoratorA, JavaClassNode.METRIC_DIRECT_EFFERENT_COUPLING),
                                "ClassA should have 2 direct dependencies");
                assertEquals(3, getMetric(decoratorA, JavaClassNode.METRIC_TRANSITIVE_EFFERENT_COUPLING),
                                "ClassA should reach 3 unique classes (B, C, D)");

                // Act: Calculate metrics for ClassD (bottom of diamond)
                NodeDecorator<JavaClassNode> decoratorD = new NodeDecorator<>(classD);
                inspector.inspect(classD, decoratorD);

                // Assert: ClassD should be reachable from A, B, C (but A counted only once)
                assertEquals(2, getMetric(decoratorD, JavaClassNode.METRIC_DIRECT_AFFERENT_COUPLING),
                                "ClassD should have 2 direct dependents");
                assertEquals(3, getMetric(decoratorD, JavaClassNode.METRIC_TRANSITIVE_AFFERENT_COUPLING),
                                "ClassD should be reachable from 3 unique classes (A, B, C)");
        }

        @Test
        @DisplayName("Should handle circular dependencies correctly")
        void testCircularDependencies() {
                // Arrange: Create a cycle A -> B -> C -> A
                repository.getOrCreateNode(classA);
                repository.getOrCreateNode(classB);
                repository.getOrCreateNode(classC);

                repository.getOrCreateEdge(classA, classB, "uses");
                repository.getOrCreateEdge(classB, classC, "uses");
                repository.getOrCreateEdge(classC, classA, "uses");

                // Act: Calculate metrics for ClassA
                NodeDecorator<JavaClassNode> decoratorA = new NodeDecorator<>(classA);
                inspector.inspect(classA, decoratorA);

                // Assert: Should handle cycle without infinite loop
                assertEquals(1, getMetric(decoratorA, JavaClassNode.METRIC_DIRECT_EFFERENT_COUPLING),
                                "ClassA should have 1 direct dependency");
                assertEquals(1, getMetric(decoratorA, JavaClassNode.METRIC_DIRECT_AFFERENT_COUPLING),
                                "ClassA should have 1 direct dependent");

                // In a cycle, each node can reach all other nodes in the cycle
                assertEquals(2, getMetric(decoratorA, JavaClassNode.METRIC_TRANSITIVE_EFFERENT_COUPLING),
                                "ClassA should reach 2 other classes in cycle");
                assertEquals(2, getMetric(decoratorA, JavaClassNode.METRIC_TRANSITIVE_AFFERENT_COUPLING),
                                "ClassA should be reachable from 2 other classes in cycle");
        }

        @Test
        @DisplayName("Should handle multiple edge types (extends, implements, uses)")
        void testMultipleEdgeTypes() {
                // Arrange: Use different edge types
                repository.getOrCreateNode(classA);
                repository.getOrCreateNode(classB);
                repository.getOrCreateNode(classC);
                repository.getOrCreateNode(classD);

                repository.getOrCreateEdge(classA, classB, "extends");
                repository.getOrCreateEdge(classA, classC, "implements");
                repository.getOrCreateEdge(classA, classD, "uses");

                // Act
                NodeDecorator<JavaClassNode> decoratorA = new NodeDecorator<>(classA);
                inspector.inspect(classA, decoratorA);

                // Assert: All edge types should be counted
                assertEquals(3, getMetric(decoratorA, JavaClassNode.METRIC_DIRECT_EFFERENT_COUPLING),
                                "ClassA should count all edge types as dependencies");
        }

        @Test
        @DisplayName("Should handle complex multi-level dependency graph")
        void testComplexDependencyGraph() {
                // Arrange: Create a more complex graph
                // A -> B -> D
                // A -> C -> D
                // C -> E
                repository.getOrCreateNode(classA);
                repository.getOrCreateNode(classB);
                repository.getOrCreateNode(classC);
                repository.getOrCreateNode(classD);
                repository.getOrCreateNode(classE);

                repository.getOrCreateEdge(classA, classB, "uses");
                repository.getOrCreateEdge(classA, classC, "uses");
                repository.getOrCreateEdge(classB, classD, "uses");
                repository.getOrCreateEdge(classC, classD, "uses");
                repository.getOrCreateEdge(classC, classE, "uses");

                // Act: Test ClassA (root)
                NodeDecorator<JavaClassNode> decoratorA = new NodeDecorator<>(classA);
                inspector.inspect(classA, decoratorA);

                assertEquals(2, getMetric(decoratorA, JavaClassNode.METRIC_DIRECT_EFFERENT_COUPLING),
                                "ClassA has 2 direct dependencies");
                assertEquals(4, getMetric(decoratorA, JavaClassNode.METRIC_TRANSITIVE_EFFERENT_COUPLING),
                                "ClassA can reach 4 classes (B, C, D, E)");

                // Act: Test ClassD (converging node)
                NodeDecorator<JavaClassNode> decoratorD = new NodeDecorator<>(classD);
                inspector.inspect(classD, decoratorD);

                assertEquals(2, getMetric(decoratorD, JavaClassNode.METRIC_DIRECT_AFFERENT_COUPLING),
                                "ClassD has 2 direct dependents");
                assertEquals(3, getMetric(decoratorD, JavaClassNode.METRIC_TRANSITIVE_AFFERENT_COUPLING),
                                "ClassD is reachable from 3 classes (A, B, C)");
        }

        @Test
        @DisplayName("Should calculate balanced instability for node with both incoming and outgoing")
        void testBalancedInstability() {
                // Arrange: B has both incoming (from A) and outgoing (to C)
                // A -> B -> C
                repository.getOrCreateNode(classA);
                repository.getOrCreateNode(classB);
                repository.getOrCreateNode(classC);

                repository.getOrCreateEdge(classA, classB, "uses");
                repository.getOrCreateEdge(classB, classC, "uses");

                // Act
                NodeDecorator<JavaClassNode> decoratorB = new NodeDecorator<>(classB);
                inspector.inspect(classB, decoratorB);

                // Assert: B has 1 in, 1 out = balanced (I = 0.5)
                assertEquals(1, getMetric(decoratorB, JavaClassNode.METRIC_DIRECT_AFFERENT_COUPLING));
                assertEquals(1, getMetric(decoratorB, JavaClassNode.METRIC_DIRECT_EFFERENT_COUPLING));
                assertEquals(0.5, getMetricDouble(decoratorB, JavaClassNode.METRIC_INSTABILITY), 0.001,
                                "ClassB should have balanced instability (I=0.5)");
        }

        @Test
        @DisplayName("Should support getName method")
        void testGetName() {
                assertEquals("Coupling Metrics Inspector", inspector.getName());
        }

        @Test
        @DisplayName("Should support canProcess method for JavaClassNode")
        void testCanProcess() {
                assertTrue(inspector.canProcess(classA), "Should be able to process JavaClassNode");
        }

        // Helper methods

        private JavaClassNode createJavaClassNode(String fqn) {
                JavaClassNode node = new JavaClassNode(fqn);
                node.setClassType("class");
                node.setSourceType(JavaClassNode.SOURCE_TYPE_SOURCE);
                return node;
        }

        private int getMetric(NodeDecorator<JavaClassNode> decorator, String metricName) {
                Number metric = decorator.getNode().getMetrics().getMetric(metricName);
                assertNotNull(metric, "Metric " + metricName + " should not be null");
                return metric.intValue();
        }

        private double getMetricDouble(NodeDecorator<JavaClassNode> decorator, String metricName) {
                Number metric = decorator.getNode().getMetrics().getMetric(metricName);
                assertNotNull(metric, "Metric " + metricName + " should not be null");
                return metric.doubleValue();
        }
}
