package com.analyzer.rules.metrics;

import com.analyzer.api.graph.GraphEdge;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.graph.PackageNode;
import com.analyzer.api.graph.PackageNodeRepository;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.export.NodeDecorator;
import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * Inspector that calculates package-level coupling metrics by aggregating
 * class-level dependencies.
 * 
 * <p>
 * This inspector creates PackageNode instances for each unique package and
 * calculates:
 * </p>
 * <ul>
 * <li><b>Class Counts</b> - Total, interface, abstract, and concrete class
 * counts</li>
 * <li><b>Direct Package Coupling</b> - Packages with direct inter-package
 * dependencies</li>
 * <li><b>Transitive Package Coupling</b> - All reachable packages through
 * dependencies</li>
 * <li><b>Instability (I)</b> - Ce / (Ca + Ce)</li>
 * <li><b>Abstractness (A)</b> - (Interfaces + Abstract Classes) / Total
 * Classes</li>
 * <li><b>Distance from Main Sequence (D)</b> - |A + I - 1|</li>
 * </ul>
 * 
 * @author Java Architecture Analyzer
 * @since Coupling Metrics Enhancement - Package Level
 */
@InspectorDependencies(need = { CouplingMetricsInspector.class }, produces = {
        "java.package.coupling_metrics.calculated" })
public class PackageCouplingMetricsInspector implements Inspector<PackageNode> {

    private static final Logger logger = LoggerFactory.getLogger(PackageCouplingMetricsInspector.class);

    private final GraphRepository graphRepository;
    private final PackageNodeRepository packageNodeRepository;

    // Cache for package graph
    private Graph<GraphNode, GraphEdge> packageGraphCache;

    public static class TAGS {
        public static final String PACKAGE_METRICS_CALCULATED = "java.package.coupling_metrics.calculated";
    }

    @Inject
    public PackageCouplingMetricsInspector(
            GraphRepository graphRepository,
            PackageNodeRepository packageNodeRepository) {
        this.graphRepository = graphRepository;
        this.packageNodeRepository = packageNodeRepository;
    }

    @Override
    public String getName() {
        return "Package Coupling Metrics Inspector";
    }

    @Override
    public void inspect(PackageNode node, NodeDecorator<PackageNode> decorator) {
        // Lazy-initialize the package dependency graph
        if (packageGraphCache == null) {
            logger.info("Building package-level dependency graph for metrics calculation...");
            buildPackageDependencyGraph();
        }

        // Calculate metrics for this specific package node
        calculatePackageMetrics(node, decorator);
    }

    /**
     * Builds the package-level dependency graph by aggregating class-level
     * dependencies.
     * PackageNodes are assumed to already exist (created by collectors during Phase
     * 2).
     */
    private void buildPackageDependencyGraph() {
        // Get class-level coupling graph
        Graph<GraphNode, GraphEdge> classGraph = graphRepository.buildGraph(
                Set.of("JavaClass"),
                Set.of("extends", "implements", "uses"));

        // Track package-to-package dependencies
        Set<PackageDependency> packageDependencies = new HashSet<>();

        // Aggregate class dependencies to package dependencies
        for (GraphEdge edge : classGraph.edgeSet()) {
            GraphNode source = classGraph.getEdgeSource(edge);
            GraphNode target = classGraph.getEdgeTarget(edge);

            if (source instanceof JavaClassNode && target instanceof JavaClassNode) {
                JavaClassNode sourceClass = (JavaClassNode) source;
                JavaClassNode targetClass = (JavaClassNode) target;

                String sourcePackage = sourceClass.getPackageName();
                String targetPackage = targetClass.getPackageName();

                if (sourcePackage == null || sourcePackage.trim().isEmpty()) {
                    sourcePackage = "(default)";
                }
                if (targetPackage == null || targetPackage.trim().isEmpty()) {
                    targetPackage = "(default)";
                }

                // Only create edge if packages are different
                if (!sourcePackage.equals(targetPackage)) {
                    packageDependencies.add(new PackageDependency(sourcePackage, targetPackage));
                }
            }
        }

        // Create package-level edges in graph repository
        // PackageNodes should already exist from Phase 2 collectors
        for (PackageDependency dep : packageDependencies) {
            Optional<PackageNode> sourcePackage = packageNodeRepository.getByPackageName(dep.sourcePackage);
            Optional<PackageNode> targetPackage = packageNodeRepository.getByPackageName(dep.targetPackage);

            if (sourcePackage.isPresent() && targetPackage.isPresent()) {
                graphRepository.getOrCreateEdge(sourcePackage.get(), targetPackage.get(), "depends");
            }
        }

        // Build the package graph
        packageGraphCache = graphRepository.buildGraph(
                Set.of("Package"),
                Set.of("depends"));

        logger.info("Package dependency graph built with {} edges", packageDependencies.size());
    }

    /**
     * Calculates all metrics for a specific package.
     */
    private void calculatePackageMetrics(PackageNode packageNode, NodeDecorator<PackageNode> decorator) {
        // 1. Abstractness metric
        int totalClasses = packageNode.getClassCount();
        int abstractComponents = packageNode.getInterfaceCount() + packageNode.getAbstractClassCount();
        double abstractness = totalClasses > 0 ? (double) abstractComponents / totalClasses : 0.0;

        // 2. Direct coupling (already counted by graph edges)
        int directAfferent = packageGraphCache.incomingEdgesOf(packageNode).size();
        int directEfferent = packageGraphCache.outgoingEdgesOf(packageNode).size();

        // 3. Transitive coupling
        int transitiveEfferent = calculateTransitiveEfferent(packageGraphCache, packageNode);
        int transitiveAfferent = calculateTransitiveAfferent(packageGraphCache, packageNode);

        // 4. Instability metric: I = Ce / (Ca + Ce)
        double instability = calculateInstability(directEfferent, directAfferent);

        // 5. Distance from main sequence: D = |A + I - 1|
        double distance = Math.abs(abstractness + instability - 1.0);

        // Store all metrics
        decorator.setMetric(PackageNode.METRIC_ABSTRACTNESS, abstractness);
        decorator.setMetric(PackageNode.METRIC_DIRECT_AFFERENT_COUPLING, directAfferent);
        decorator.setMetric(PackageNode.METRIC_DIRECT_EFFERENT_COUPLING, directEfferent);
        decorator.setMetric(PackageNode.METRIC_TRANSITIVE_AFFERENT_COUPLING, transitiveAfferent);
        decorator.setMetric(PackageNode.METRIC_TRANSITIVE_EFFERENT_COUPLING, transitiveEfferent);
        decorator.setMetric(PackageNode.METRIC_INSTABILITY, instability);
        decorator.setMetric(PackageNode.METRIC_DISTANCE, distance);

        // Mark as calculated
        decorator.enableTag(TAGS.PACKAGE_METRICS_CALCULATED);

        logger.debug("Package {}: classes={}, Ca={}, Ce={}, I={:.2f}, A={:.2f}, D={:.2f}",
                packageNode.getPackageName(),
                totalClasses,
                directAfferent, directEfferent,
                instability, abstractness, distance);
    }

    /**
     * Calculates transitive efferent coupling at package level.
     */
    private int calculateTransitiveEfferent(Graph<GraphNode, GraphEdge> graph, PackageNode startNode) {
        Set<PackageNode> reachableNodes = new HashSet<>();
        Queue<PackageNode> queue = new LinkedList<>();
        Set<PackageNode> visited = new HashSet<>();

        queue.add(startNode);
        visited.add(startNode);

        while (!queue.isEmpty()) {
            PackageNode current = queue.poll();

            for (GraphEdge edge : graph.outgoingEdgesOf(current)) {
                GraphNode targetNode = edge.getTarget();

                if (targetNode instanceof PackageNode) {
                    PackageNode target = (PackageNode) targetNode;

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
     * Calculates transitive afferent coupling at package level.
     */
    private int calculateTransitiveAfferent(Graph<GraphNode, GraphEdge> graph, PackageNode startNode) {
        Set<PackageNode> reachableNodes = new HashSet<>();
        Queue<PackageNode> queue = new LinkedList<>();
        Set<PackageNode> visited = new HashSet<>();

        queue.add(startNode);
        visited.add(startNode);

        while (!queue.isEmpty()) {
            PackageNode current = queue.poll();

            for (GraphEdge edge : graph.incomingEdgesOf(current)) {
                GraphNode sourceNode = edge.getSource();

                if (sourceNode instanceof PackageNode) {
                    PackageNode source = (PackageNode) sourceNode;

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
     * Calculates instability: I = Ce / (Ca + Ce).
     */
    private double calculateInstability(int ce, int ca) {
        int totalCoupling = ca + ce;

        if (totalCoupling == 0) {
            return 0.0;
        }

        return (double) ce / totalCoupling;
    }

    /**
     * Helper class to represent package-to-package dependencies.
     */
    private static class PackageDependency {
        final String sourcePackage;
        final String targetPackage;

        PackageDependency(String sourcePackage, String targetPackage) {
            this.sourcePackage = sourcePackage;
            this.targetPackage = targetPackage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            PackageDependency that = (PackageDependency) o;
            return sourcePackage.equals(that.sourcePackage) && targetPackage.equals(that.targetPackage);
        }

        @Override
        public int hashCode() {
            return sourcePackage.hashCode() * 31 + targetPackage.hashCode();
        }
    }
}
