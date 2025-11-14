package com.analyzer.rules.metrics;

import com.analyzer.api.graph.*;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.NodeTypeRegistry;
import com.analyzer.core.inspector.InspectorTargetType;
import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

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
@InspectorDependencies(need = CouplingMetricsInspector.class, produces = "java.package.coupling_metrics.calculated", requiresAllNodesProcessed = true)
public class PackageCouplingMetricsInspector implements Inspector<PackageNode> {

    public static final String EDGE_DEPENDS = "depends";
    private static final Logger logger = LoggerFactory.getLogger(PackageCouplingMetricsInspector.class);
    private final GraphRepository graphRepository;
    private final PackageNodeRepository packageNodeRepository;

    // Cache for package graph - built once since this runs as a global inspector
    private Graph<GraphNode, GraphEdge> packageGraphCache;

    @Inject
    public PackageCouplingMetricsInspector(
            final GraphRepository graphRepository,
            final PackageNodeRepository packageNodeRepository) {
        this.graphRepository = graphRepository;
        this.packageNodeRepository = packageNodeRepository;
    }

    @Override
    public void inspect(final PackageNode node, final NodeDecorator<PackageNode> decorator) {
        // Lazy-initialize the package dependency graph
        if (packageGraphCache == null) {
            logger.info("Building package-level dependency graph for metrics calculation...");
            buildPackageDependencyGraph();
        }

        // Calculate metrics for this specific package node
        calculatePackageMetrics(node, decorator);
    }

    @Override
    public String getName() {
        return "Package Coupling Metrics Inspector";
    }

    @Override
    public InspectorTargetType getTargetType() {
        return InspectorTargetType.PACKAGE;
    }

    /**
     * Builds the package-level dependency graph by aggregating class-level
     * dependencies.
     * PackageNodes are assumed to already exist (created by collectors during Phase
     * 2).
     */
    private void buildPackageDependencyGraph() {
        // Get class-level coupling graph
        final Graph<GraphNode, GraphEdge> classGraph = graphRepository.buildGraph(
                Set.of("JavaClass"),
                Set.of("extends", "implements", "uses"));

        // Track package-to-package dependencies
        final Set<PackageDependency> packageDependencies = new HashSet<>();

        // Aggregate class dependencies to package dependencies
        for (final GraphEdge edge : classGraph.edgeSet()) {
            final GraphNode source = classGraph.getEdgeSource(edge);
            final GraphNode target = classGraph.getEdgeTarget(edge);

            if (source instanceof final JavaClassNode sourceClass
                    && target instanceof final JavaClassNode targetClass) {

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
        for (final PackageDependency dep : packageDependencies) {
            final Optional<PackageNode> sourcePackage = packageNodeRepository.getByPackageName(dep.sourcePackage);
            final Optional<PackageNode> targetPackage = packageNodeRepository.getByPackageName(dep.targetPackage);

            if (sourcePackage.isPresent() && targetPackage.isPresent()) {
                graphRepository.getOrCreateEdge(sourcePackage.get(), targetPackage.get(), EDGE_DEPENDS);
            }
        }

        // Build the package graph
        packageGraphCache = graphRepository.buildGraph(
                Set.of(NodeTypeRegistry.getAllTypes().get(PackageNode.class)),
                Set.of(EDGE_DEPENDS));

        logger.info("Package dependency graph built with {} edges", packageDependencies.size());
    }

    /**
     * Calculates all metrics for a specific package.
     */
    private void calculatePackageMetrics(final PackageNode packageNode, final NodeDecorator<PackageNode> decorator) {
        // 1. Abstractness metric
        final int totalClasses = packageNode.getClassCount();
        final int abstractComponents = packageNode.getInterfaceCount() + packageNode.getAbstractClassCount();
        final double abstractness = totalClasses > 0 ? (double) abstractComponents / totalClasses : 0.0;

        // 2. Direct coupling (already counted by graph edges)
        final int directAfferent = packageGraphCache.incomingEdgesOf(packageNode).size();
        final int directEfferent = packageGraphCache.outgoingEdgesOf(packageNode).size();

        // 3. Transitive coupling
        final int transitiveEfferent = calculateTransitiveEfferent(packageGraphCache, packageNode);
        final int transitiveAfferent = calculateTransitiveAfferent(packageGraphCache, packageNode);

        // 4. Instability metric: I = Ce / (Ca + Ce)
        final double instability = calculateInstability(directEfferent, directAfferent);

        // 5. Distance from main sequence: D = |A + I - 1|
        final double distance = Math.abs(abstractness + instability - 1.0);

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
    private int calculateTransitiveEfferent(final Graph<GraphNode, GraphEdge> graph, final PackageNode startNode) {
        final Set<PackageNode> reachableNodes = new HashSet<>();
        final Queue<PackageNode> queue = new LinkedList<>();
        final Set<PackageNode> visited = new HashSet<>();

        queue.add(startNode);
        visited.add(startNode);

        while (!queue.isEmpty()) {
            final PackageNode current = queue.poll();

            for (final GraphEdge edge : graph.outgoingEdgesOf(current)) {
                final GraphNode targetNode = edge.getTarget();

                if (targetNode instanceof final PackageNode target) {

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
    private int calculateTransitiveAfferent(final Graph<GraphNode, GraphEdge> graph, final PackageNode startNode) {
        final Set<PackageNode> reachableNodes = new HashSet<>();
        final Queue<PackageNode> queue = new LinkedList<>();
        final Set<PackageNode> visited = new HashSet<>();

        queue.add(startNode);
        visited.add(startNode);

        while (!queue.isEmpty()) {
            final PackageNode current = queue.poll();

            for (final GraphEdge edge : graph.incomingEdgesOf(current)) {
                final GraphNode sourceNode = edge.getSource();

                if (sourceNode instanceof final PackageNode source) {

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
    private double calculateInstability(final int ce, final int ca) {
        final int totalCoupling = ca + ce;

        if (totalCoupling == 0) {
            return 0.0;
        }

        return (double) ce / totalCoupling;
    }

    public enum TAGS {
        ;
        public static final String PACKAGE_METRICS_CALCULATED = "java.package.coupling_metrics.calculated";
    }

    /**
     * Helper class to represent package-to-package dependencies.
     */
    private static class PackageDependency {
        final String sourcePackage;
        final String targetPackage;

        PackageDependency(final String sourcePackage, final String targetPackage) {
            this.sourcePackage = sourcePackage;
            this.targetPackage = targetPackage;
        }

        @Override
        public int hashCode() {
            return sourcePackage.hashCode() * 31 + targetPackage.hashCode();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            final PackageDependency that = (PackageDependency) o;
            return sourcePackage.equals(that.sourcePackage) && targetPackage.equals(that.targetPackage);
        }
    }
}
