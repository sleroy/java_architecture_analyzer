package com.analyzer.api.metrics;

import java.util.Map;

/**
 * Marker interface for metrics-related classes.
 */
public interface Metrics {

    Number getMetric(String metricName);

    void setMetric(String metricName, Number value);

    /**
     * Sets a metric value only if it's greater than the current value.
     * Useful for capturing the maximum complexity or priority across multiple
     * analyses.
     * 
     * @param metricName The metric name
     * @param value      The value to set (only if greater than current)
     */
    void setMaxMetric(String metricName, Number value);

    Map<String, Double> getAllMetrics();
}
