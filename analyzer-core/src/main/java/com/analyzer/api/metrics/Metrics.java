package com.analyzer.api.metrics;

import java.util.Map;

/**
 * Marker interface for metrics-related classes.
 */
public interface Metrics {

    Number getMetric(String metricName);

    void setMetric(String metricName, Number value);

    Map<String, Double> getAllMetrics();
}
