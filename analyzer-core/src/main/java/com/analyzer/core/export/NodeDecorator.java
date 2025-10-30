package com.analyzer.core.export;

import com.analyzer.api.graph.BaseGraphNode;
import com.analyzer.api.graph.GraphNode;
import com.analyzer.core.model.ProjectFile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Generic decorator for GraphNode that provides:
 * - Property operations (data/metrics with aggregation)
 * - Tag operations (boolean flags)
 * - Error handling
 *
 * <p>
 * This decorator separates concerns:
 * </p>
 * <ul>
 * <li><b>Properties</b>: Used for data and metrics (methodCount, complexity,
 * etc.)</li>
 * <li><b>Tags</b>: Used for boolean flags (EJB_DETECTED, METRICS_ANALYZED,
 * etc.)</li>
 * </ul>
 *
 * @param <T> The type of GraphNode being decorated
 */
public class NodeDecorator<T extends GraphNode> {

    // Complexity levels in ascending order for comparison
    private static final List<String> COMPLEXITY_LEVELS = Arrays.asList(
            "NONE", "LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final T node;

    public NodeDecorator(T node) {
        this.node = Objects.requireNonNull(node, "Node cannot be null");
    }

    /**
     * Gets the underlying node being decorated.
     *
     * @return the node
     */
    public T getNode() {
        return node;
    }

    // ========== PROPERTY OPERATIONS (Data/Metrics) ==========

    /**
     * Set a property value directly.
     *
     * @param propertyName the property name
     * @param value        the property value
     */
    public void setProperty(String propertyName, Object value) {
        ((BaseGraphNode) node).setProperty(propertyName, value);
    }

    /**
     * Set a metric value on the node.
     * Metrics should be used for numerical measurements like counts, complexity,
     * etc.
     *
     * @param metricName the metric name
     * @param value      the metric value
     */
    public void setMetric(String metricName, Number value) {
        ((BaseGraphNode) node).getMetrics().setMetric(metricName, value);
    }

    /**
     * Set a metric value to the maximum of current and new value.
     * Only updates if the new value is greater than the current value.
     *
     * @param metricName the metric name
     * @param value      the new value to compare
     */
    public void setMaxMetric(String metricName, Number value) {
        ((BaseGraphNode) node).getMetrics().setMaxMetric(metricName, value);
    }

    /**
     * Get the metrics interface for direct access to all metric operations.
     *
     * @return the metrics interface
     */
    public com.analyzer.api.metrics.Metrics getMetrics() {
        return ((BaseGraphNode) node).getMetrics();
    }

    /**
     * Set property to maximum of current and new value (for integers).
     *
     * @param propertyName the property name
     * @param value        the new value to compare
     */
    public void setMaxProperty(String propertyName, int value) {
        Integer current = getIntProperty(propertyName, 0);
        if (value > current) {
            setProperty(propertyName, value);
        }
    }

    /**
     * Set property to maximum of current and new value (for doubles).
     *
     * @param propertyName the property name
     * @param value        the new value to compare
     */
    public void setMaxProperty(String propertyName, double value) {
        Double current = getDoubleProperty(propertyName, 0.0);
        if (value > current) {
            setProperty(propertyName, value);
        }
    }

    /**
     * Set property to higher complexity level between current and new.
     * Supports standard complexity progression: NONE < LOW < MEDIUM < HIGH <
     * CRITICAL
     *
     * @param propertyName the property name
     * @param complexity   the new complexity level
     */
    public void setMaxComplexityProperty(String propertyName, String complexity) {
        String current = getStringProperty(propertyName, "NONE");
        String max = computeMaxComplexity(current, complexity);
        setProperty(propertyName, max);
    }

    /**
     * Boolean OR for property (any inspector sets true = stays true).
     *
     * @param propertyName the property name
     * @param value        the new boolean value to OR with current
     */
    public void orProperty(String propertyName, boolean value) {
        boolean current = getBooleanProperty(propertyName, false);
        setProperty(propertyName, current || value);
    }

    /**
     * Boolean AND for property (all must be true).
     *
     * @param propertyName the property name
     * @param value        the new boolean value to AND with current
     */
    public void andProperty(String propertyName, boolean value) {
        boolean current = getBooleanProperty(propertyName, true);
        setProperty(propertyName, current && value);
    }

    // ========== TAG OPERATIONS (Boolean Flags) ==========

    /**
     * Enable a tag (set boolean flag to true).
     *
     * @param tagName the tag to enable
     */
    public void enableTag(String tagName) {
        node.enableTag(tagName);
    }

    /**
     * Disable a tag (remove boolean flag).
     *
     * @param tagName the tag to disable
     */
    public void disableTag(String tagName) {
        node.removeTag(tagName);
    }

    /**
     * Check if tag is enabled.
     *
     * @param tagName the tag to check
     * @return true if the tag is enabled
     */
    public boolean hasTag(String tagName) {
        return node.hasTag(tagName);
    }

    // ========== ERROR HANDLING ==========

    /**
     * Record an error message as a property.
     *
     * @param errorMessage the error message
     */
    public void error(String errorMessage) {
        setProperty("ERROR", errorMessage);
    }

    /**
     * Record an error from a throwable as a property.
     *
     * @param throwable the exception
     */
    public void error(Throwable throwable) {
        setProperty("ERROR", throwable.getMessage());
    }

    /**
     * Record an error with a specific property name.
     *
     * @param propertyName the property name for the error
     * @param throwable    the exception
     */
    public void errorProperty(String propertyName, Throwable throwable) {
        setProperty(propertyName, "ERROR: " + throwable.getMessage());
    }

    // ========== HELPER METHODS ==========

    private String getStringProperty(String propertyName, String defaultValue) {
        return ((BaseGraphNode) node).getProperty(propertyName, String.class, defaultValue);
    }

    private Integer getIntProperty(String propertyName, Integer defaultValue) {
        return ((BaseGraphNode) node).getProperty(propertyName, Integer.class, defaultValue);
    }

    private Double getDoubleProperty(String propertyName, Double defaultValue) {
        return ((BaseGraphNode) node).getProperty(propertyName, Double.class, defaultValue);
    }

    private Boolean getBooleanProperty(String propertyName, Boolean defaultValue) {
        return ((BaseGraphNode) node).getProperty(propertyName, Boolean.class, defaultValue);
    }

    /**
     * Determines the higher complexity level between two complexity strings.
     *
     * @param current  the current complexity level
     * @param newValue the new complexity level to compare
     * @return the higher complexity level
     */
    private String computeMaxComplexity(String current, String newValue) {
        if (current == null)
            current = "NONE";
        if (newValue == null)
            newValue = "NONE";

        int currentIndex = COMPLEXITY_LEVELS.indexOf(current.toUpperCase());
        int newIndex = COMPLEXITY_LEVELS.indexOf(newValue.toUpperCase());

        // Handle unknown complexity levels by treating them as MEDIUM
        if (currentIndex == -1)
            currentIndex = 2; // MEDIUM
        if (newIndex == -1)
            newIndex = 2; // MEDIUM

        return COMPLEXITY_LEVELS.get(Math.max(currentIndex, newIndex));
    }
}
