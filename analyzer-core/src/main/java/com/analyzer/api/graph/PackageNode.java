package com.analyzer.api.graph;

import com.analyzer.core.graph.NodeTypeRegistry;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Graph node representing a Java package with aggregated metrics.
 * 
 * <p>
 * PackageNode aggregates metrics from all classes within the package,
 * providing a higher-level view of coupling and quality metrics.
 * </p>
 * 
 * <p>
 * Key Metrics:
 * </p>
 * <ul>
 * <li><b>Class Count</b> - Number of classes in this package</li>
 * <li><b>Direct Afferent Coupling (Ca)</b> - Number of packages that directly
 * depend on this package</li>
 * <li><b>Direct Efferent Coupling (Ce)</b> - Number of packages this package
 * directly depends on</li>
 * <li><b>Transitive Coupling</b> - All packages reachable through
 * dependencies</li>
 * <li><b>Instability (I)</b> - Ce / (Ca + Ce), range [0.0, 1.0]</li>
 * <li><b>Abstractness (A)</b> - (Interfaces + Abstract Classes) / Total
 * Classes</li>
 * <li><b>Distance from Main Sequence (D)</b> - |A + I - 1|, ideal is 0.0</li>
 * </ul>
 * 
 * @author Java Architecture Analyzer
 * @since Coupling Metrics Enhancement - Package Level
 */
public class PackageNode extends BaseGraphNode {

    // Property keys
    public static final String PROP_PACKAGE_NAME = "packageName";

    // Basic metrics
    public static final String METRIC_CLASS_COUNT = "classCount";
    public static final String METRIC_INTERFACE_COUNT = "interfaceCount";
    public static final String METRIC_ABSTRACT_CLASS_COUNT = "abstractClassCount";
    public static final String METRIC_CONCRETE_CLASS_COUNT = "concreteClassCount";

    // Direct coupling metrics (1-hop dependencies at package level)
    public static final String METRIC_DIRECT_AFFERENT_COUPLING = "directAfferentCoupling";
    public static final String METRIC_DIRECT_EFFERENT_COUPLING = "directEfferentCoupling";

    // Transitive coupling metrics (all reachable packages, cycle-aware)
    public static final String METRIC_TRANSITIVE_AFFERENT_COUPLING = "transitiveAfferentCoupling";
    public static final String METRIC_TRANSITIVE_EFFERENT_COUPLING = "transitiveEfferentCoupling";

    // Quality metrics
    public static final String METRIC_INSTABILITY = "instability"; // Ce / (Ca + Ce)
    public static final String METRIC_ABSTRACTNESS = "abstractness"; // (Interfaces + Abstract) / Total
    public static final String METRIC_DISTANCE = "distance"; // |A + I - 1|

    @JsonIgnore
    private final Set<String> classIds = new HashSet<>();

    /**
     * Creates a PackageNode with the specified package name.
     * 
     * @param packageName The fully qualified package name (e.g.,
     *                    "com.example.service")
     */
    public PackageNode(final String packageName) {
        super(packageName, NodeTypeRegistry.getTypeId(PackageNode.class));
        setProperty(PROP_PACKAGE_NAME, packageName);
    }

    /**
     * Gets the package name.
     * 
     * @return Package name
     */
    public String getPackageName() {
        return getStringProperty(PROP_PACKAGE_NAME, getId());
    }

    /**
     * Adds a class to this package.
     * 
     * @param classId The ID of the JavaClassNode
     */
    public void addClass(String classId) {
        classIds.add(classId);
    }

    /**
     * Gets all class IDs in this package.
     * 
     * @return Set of class IDs
     */
    @JsonIgnore
    public Set<String> getClassIds() {
        return new HashSet<>(classIds);
    }

    /**
     * Gets the number of classes in this package.
     * 
     * @return Class count
     */
    public int getClassCount() {
        final Number metric = getMetrics().getMetric(METRIC_CLASS_COUNT);
        return metric != null ? metric.intValue() : 0;
    }

    public void setClassCount(final int classCount) {
        getMetrics().setMetric(METRIC_CLASS_COUNT, classCount);
    }

    /**
     * Gets the number of interfaces in this package.
     * 
     * @return Interface count
     */
    public int getInterfaceCount() {
        final Number metric = getMetrics().getMetric(METRIC_INTERFACE_COUNT);
        return metric != null ? metric.intValue() : 0;
    }

    public void setInterfaceCount(final int interfaceCount) {
        getMetrics().setMetric(METRIC_INTERFACE_COUNT, interfaceCount);
    }

    /**
     * Gets the number of abstract classes in this package.
     * 
     * @return Abstract class count
     */
    public int getAbstractClassCount() {
        final Number metric = getMetrics().getMetric(METRIC_ABSTRACT_CLASS_COUNT);
        return metric != null ? metric.intValue() : 0;
    }

    public void setAbstractClassCount(final int abstractClassCount) {
        getMetrics().setMetric(METRIC_ABSTRACT_CLASS_COUNT, abstractClassCount);
    }

    /**
     * Gets the number of concrete (non-abstract) classes in this package.
     * 
     * @return Concrete class count
     */
    public int getConcreteClassCount() {
        final Number metric = getMetrics().getMetric(METRIC_CONCRETE_CLASS_COUNT);
        return metric != null ? metric.intValue() : 0;
    }

    public void setConcreteClassCount(final int concreteClassCount) {
        getMetrics().setMetric(METRIC_CONCRETE_CLASS_COUNT, concreteClassCount);
    }

    /**
     * Gets the direct afferent coupling (packages depending on this package).
     * 
     * @return Direct afferent coupling count
     */
    public int getDirectAfferentCoupling() {
        final Number metric = getMetrics().getMetric(METRIC_DIRECT_AFFERENT_COUPLING);
        return metric != null ? metric.intValue() : 0;
    }

    public void setDirectAfferentCoupling(final int directAfferentCoupling) {
        getMetrics().setMetric(METRIC_DIRECT_AFFERENT_COUPLING, directAfferentCoupling);
    }

    /**
     * Gets the direct efferent coupling (packages this package depends on).
     * 
     * @return Direct efferent coupling count
     */
    public int getDirectEfferentCoupling() {
        final Number metric = getMetrics().getMetric(METRIC_DIRECT_EFFERENT_COUPLING);
        return metric != null ? metric.intValue() : 0;
    }

    public void setDirectEfferentCoupling(final int directEfferentCoupling) {
        getMetrics().setMetric(METRIC_DIRECT_EFFERENT_COUPLING, directEfferentCoupling);
    }

    /**
     * Gets the transitive afferent coupling.
     * 
     * @return Transitive afferent coupling count
     */
    public int getTransitiveAfferentCoupling() {
        final Number metric = getMetrics().getMetric(METRIC_TRANSITIVE_AFFERENT_COUPLING);
        return metric != null ? metric.intValue() : 0;
    }

    public void setTransitiveAfferentCoupling(final int transitiveAfferentCoupling) {
        getMetrics().setMetric(METRIC_TRANSITIVE_AFFERENT_COUPLING, transitiveAfferentCoupling);
    }

    /**
     * Gets the transitive efferent coupling.
     * 
     * @return Transitive efferent coupling count
     */
    public int getTransitiveEfferentCoupling() {
        final Number metric = getMetrics().getMetric(METRIC_TRANSITIVE_EFFERENT_COUPLING);
        return metric != null ? metric.intValue() : 0;
    }

    public void setTransitiveEfferentCoupling(final int transitiveEfferentCoupling) {
        getMetrics().setMetric(METRIC_TRANSITIVE_EFFERENT_COUPLING, transitiveEfferentCoupling);
    }

    /**
     * Gets the instability metric: I = Ce / (Ca + Ce).
     * Range [0.0, 1.0] where 0.0 is maximally stable and 1.0 is maximally
     * unstable.
     * 
     * @return Instability value
     */
    public double getInstability() {
        final Number metric = getMetrics().getMetric(METRIC_INSTABILITY);
        return metric != null ? metric.doubleValue() : 0.0;
    }

    public void setInstability(final double instability) {
        getMetrics().setMetric(METRIC_INSTABILITY, instability);
    }

    /**
     * Gets the abstractness metric: A = (Interfaces + Abstract Classes) / Total
     * Classes.
     * Range [0.0, 1.0] where 0.0 is fully concrete and 1.0 is fully abstract.
     * 
     * @return Abstractness value
     */
    public double getAbstractness() {
        final Number metric = getMetrics().getMetric(METRIC_ABSTRACTNESS);
        return metric != null ? metric.doubleValue() : 0.0;
    }

    public void setAbstractness(final double abstractness) {
        getMetrics().setMetric(METRIC_ABSTRACTNESS, abstractness);
    }

    /**
     * Gets the distance from main sequence: D = |A + I - 1|.
     * Ideal value is 0.0 (on the main sequence).
     * Higher values indicate the package is in the "zone of pain" (concrete and
     * unstable)
     * or "zone of uselessness" (abstract and stable).
     * 
     * @return Distance value
     */
    public double getDistance() {
        final Number metric = getMetrics().getMetric(METRIC_DISTANCE);
        return metric != null ? metric.doubleValue() : 0.0;
    }

    public void setDistance(final double distance) {
        getMetrics().setMetric(METRIC_DISTANCE, distance);
    }

    @Override
    public String getDisplayLabel() {
        return String.format("Package: %s (%d classes)", getPackageName(), getClassCount());
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;

        final PackageNode that = (PackageNode) obj;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return String.format(
                "PackageNode{name='%s', classes=%d, Ca=%d, Ce=%d, I=%.2f, A=%.2f, D=%.2f}",
                getPackageName(),
                getClassCount(),
                getDirectAfferentCoupling(),
                getDirectEfferentCoupling(),
                getInstability(),
                getAbstractness(),
                getDistance());
    }
}
