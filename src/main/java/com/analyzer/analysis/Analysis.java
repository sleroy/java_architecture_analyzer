package com.analyzer.analysis;
import com.analyzer.core.inspector.InspectorDependencies;

import com.analyzer.core.model.Project;

/**
 * Interface for project-level analysis operations.
 * Unlike Inspectors which operate on individual files, Analysis operations
 * examine the entire project structure and relationships between files.
 */
public interface Analysis {

    /**
     * Get the name of this analysis for identification purposes
     */
    String getName();

    /**
     * Get a description of what this analysis does
     */
    String getDescription();

    /**
     * Execute the analysis on the given project.
     * Analysis can examine all project files, their relationships,
     * and store results in the project data.
     * 
     * @param project The project to analyze
     * @return An AnalysisResult indicating success/failure and any data
     */
    AnalysisResult execute(Project project);

    /**
     * Get the priority of this analysis (higher = run earlier)
     * Some analyses may depend on results from other analyses
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Check if this analysis can run on the given project
     * For example, a Java-specific analysis might check for Java files
     */
    default boolean canAnalyze(Project project) {
        return true;
    }

    /**
     * Get the analysis category for grouping purposes
     */
    default String getCategory() {
        return "general";
    }
}
