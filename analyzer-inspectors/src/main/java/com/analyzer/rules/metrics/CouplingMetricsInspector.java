package com.analyzer.rules.metrics;

import com.analyzer.api.graph.GraphEdge;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.rules.graph.BinaryClassCouplingGraphInspector;
import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Inspector that calculates comprehensive coupling metrics for Java classes.
 * 
 * <p>
 * Metrics Calculated:
 * </p>
 * <ul>
 * <li><b>Direct Afferent Coupling (Ca_direct)</b> - Number of classes that
 * directly depend on this class</li>
 * <li><b>Direct Efferent Coupling (Ce_direct)</b> - Number of classes this
 * class directly depends on</li>
 * <li><b>Transitive Afferent Coupling (Ca_transitive)</b> - Total classes that
 * can reach this class (cycle-aware)</li>
 * <li><b>Transitive Efferent Coupling (Ce_transitive)</b> - Total classes
 * reachable from this class (cycle-aware)</li>
 * <li><b>Instability (I)</b> - Ce / (Ca + Ce), measures resistance to change,
 * range [0.0, 1.0]</li>
 * </ul>
 * 
 * <p>
 * All edge types (extends, implements, uses) are included in calculations.
 * </p>
 * 
 * @author Java Architecture Analyzer
 * @since Coupling Metrics Enhancement
 */
@InspectorDependencies(need = { BinaryClassCouplingGraphInspector.class }, produces = {
        "java.class.coupling_metrics.calculated" })
public class CouplingMetricsInspector implements Inspector<JavaClassNode> {

    private static final Logger logger = LoggerFactory.getLogger(CouplingMetricsInspector.class);

    private final GraphRepository graphRepository;

    // Lazy-initialized graph cache
    private Graph<GraphNode, GraphEdge> cachedGraph;

    public static class TAGS {
        public static final String COUPLING_METRICS_CALCULATED = "java.class.coupling_metrics.calculated";
    }

    @Inject
    public CouplingMetricsInspector(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    @Override
    public String getName() {
        return "Coupling Metrics Inspector";
    }

    @Override
    public void inspect(JavaClassNode node, NodeDecorator<JavaClassNode> decorator) {
        // Lazy-initialize the graph on first call
        if (cachedGraph == null) {
            logger.info("Building coupling graph for metrics calculation...");
            cachedGraph = graphRepository.buildGraph(
                    Set.of("JavaClass"),
                    Set.of("extends", "implements", "uses"));
            logger.info("Coupling graph built with {} nodes", cachedGraph.vertexSet().size());
        }

        // Calculate metrics for this specific node
        calculateMetricsForClass(cachedGraph, node, decorator);
    }

    /**
     * Calculates all coupling metrics for a single class.
     */
    private void calculateMetricsForClass(Graph<GraphNode, GraphEdge> graph, JavaClassNode classNode,
            NodeDecorator<JavaClassNode> decorator) {
        // 1. Direct coupling metrics (1-hop)
        int directAfferent = calculateDirectAfferent(graph, classNode);
        int directEfferent = calculateDirectEfferent(graph, classNode);

        // 2. Transitive coupling metrics (all reachable, cycle-aware)
        int transitiveEfferent = calculateTransitiveEfferent(graph, classNode);
        int transitiveAfferent = calculateTransitiveAfferent(graph, classNode);

        // 3. Instability metric: Ce / (Ca + Ce)
        double instability = calculateInstability(directEfferent, directAfferent);

        // Store all metrics using the decorator
        decorator.setMetric(JavaClassNode.METRIC_DIRECT_AFFERENT_COUPLING, directAfferent);
        decorator.setMetric(JavaClassNode.METRIC_DIRECT_EFFERENT_COUPLING, directEfferent);
        decorator.setMetric(JavaClassNode.METRIC_TRANSITIVE_AFFERENT_COUPLING, transitiveAfferent);
        decorator.setMetric(JavaClassNode.METRIC_TRANSITIVE_EFFERENT_COUPLING, transitiveEfferent);
        decorator.setMetric(JavaClassNode.METRIC_INSTABILITY, instability);

        // Mark this inspector as having been executed
        decorator.enableTag(TAGS.COUPLING_METRICS_CALCULATED);

        logger.trace("Class {}: Ca_direct={}, Ce_direct={}, Ca_trans={}, Ce_trans={}, I={}",
                classNode.getFullyQualifiedName(),
                directAfferent, directEfferent,
                transitiveAfferent, transitiveEfferent,
                instability);
    }

    /**
     * Calculates direct afferent coupling - classes that directly depend on this
     * class.
     * Counts incoming edges to this node.
     */
    private int calculateDirectAfferent(Graph<GraphNode, GraphEdge> graph, JavaClassNode classNode) {
        return graph.incomingEdgesOf(classNode).size();
    }

    /**
     * Calculates direct efferent coupling - classes this class directly depends on.
     * Counts outgoing edges from this node.
     */
    private int calculateDirectEfferent(Graph<GraphNode, GraphEdge> graph, JavaClassNode classNode) {
        return graph.outgoingEdgesOf(classNode).size();
    }

    /**
     * Calculates transitive efferent coupling - all classes reachable from this
     * class.
     * Uses BFS to traverse forward through outgoing edges, avoiding cycles.
     * 
     * @param graph     The coupling graph
     * @param startNode The class to analyze
     * @return Count of unique classes reachable from this class
     */
    private int calculateTransitiveEfferent(Graph<GraphNode, GraphEdge> graph, JavaClassNode startNode) {
        Set<JavaClassNode> reachableNodes = new HashSet<>();
        Queue<JavaClassNode> queue = new LinkedList<>();
        Set<JavaClassNode> visited = new HashSet<>();

        queue.add(startNode);
        visited.add(startNode);

        while (!queue.isEmpty()) {
            JavaClassNode current = queue.poll();

            // Follow all outgoing edges
            for (GraphEdge edge : graph.outgoingEdgesOf(current)) {
                GraphNode targetNode = edge.getTarget();

                // Only process JavaClassNode targets
                if (targetNode instanceof JavaClassNode) {
                    JavaClassNode target = (JavaClassNode) targetNode;

                    if (!visited.contains(target)) {
                        visited.add(target);
                        reachableNodes.add(target);
                        queue.add(target);
                    }
                }
            }
        }

        return reachableNodes.size();
    }

    /**
     * Calculates transitive afferent coupling - all classes that can reach this
     * class.
     * Uses BFS to traverse backward through incoming edges, avoiding cycles.
     * 
     * @param graph     The coupling graph
     * @param startNode The class to analyze
     * @return Count of unique classes that can reach this class
     */
    private int calculateTransitiveAfferent(Graph<GraphNode, GraphEdge> graph, JavaClassNode startNode) {
        Set<JavaClassNode> reachableNodes = new HashSet<>();
        Queue<JavaClassNode> queue = new LinkedList<>();
        Set<JavaClassNode> visited = new HashSet<>();

        queue.add(startNode);
        visited.add(startNode);

        while (!queue.isEmpty()) {
            JavaClassNode current = queue.poll();

            // Follow all incoming edges
            for (GraphEdge edge : graph.incomingEdgesOf(current)) {
                GraphNode sourceNode = edge.getSource();

                // Only process JavaClassNode sources
                if (sourceNode instanceof JavaClassNode) {
                    JavaClassNode source = (JavaClassNode) sourceNode;

                    if (!visited.contains(source)) {
                        visited.add(source);
                        reachableNodes.add(source);
                        queue.add(source);
                    }
                }
            }
        }

        return reachableNodes.size();
    }

    /**
     * Calculates the instability metric: I = Ce / (Ca + Ce).
     * 
     * <p>
     * Instability Interpretation:
     * </p>
     * <ul>
     * <li>I = 0.0: Maximally stable (only incoming dependencies)</li>
     * <li>I = 1.0: Maximally unstable (only outgoing dependencies)</li>
     * <li>I = 0.5: Balanced coupling</li>
     * </ul>
     * 
     * @param ce Efferent coupling (outgoing dependencies)
     * @param ca Afferent coupling (incoming dependencies)
     * @return Instability value in range [0.0, 1.0], or 0.0 if no coupling exists
     */
    private double calculateInstability(int ce, int ca) {
        int totalCoupling = ca + ce;

        if (totalCoupling == 0) {
            // No coupling at all - consider this stable
            return 0.0;
        }

        return (double) ce / totalCoupling;
    }
}
