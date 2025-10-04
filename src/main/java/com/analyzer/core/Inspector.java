package com.analyzer.core;

/**
 * Generic interface for all inspectors.
 * Inspectors analyze objects and return results that are stored in columns.
 *
 * @param <T> the type of object this inspector can analyze (ProjectFile,
 *            Package, or legacy ProjectFile)
 */
public interface Inspector<T> {

    /**
     * Analyzes the given object and returns a result.
     * 
     * @param objectToAnalyze the object to analyze
     * @return the result of the analysis
     */
    InspectorResult decorate(T objectToAnalyze);

    /**
     * Gets the unique name of this inspector.
     * This is used for configuration and logging.
     * 
     * @return the inspector name
     */
    String getName();

    /**
     * Gets the column name where results from this inspector will be stored.
     * 
     * @return the column name for CSV output
     */
    String getColumnName();

    /**
     * Checks if this inspector supports analyzing the given object type.
     * 
     * @param objectToAnalyze the object to check
     * @return true if this inspector can analyze the object, false otherwise
     */
    boolean supports(T objectToAnalyze);
}
