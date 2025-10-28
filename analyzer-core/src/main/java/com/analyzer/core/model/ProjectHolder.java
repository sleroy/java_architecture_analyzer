package com.analyzer.core.model;

import java.util.Collections;
import java.util.List;

/**
 * Holder for the current Project instance during analysis.
 * Enables PicoContainer dependency injection of Project to inspectors.
 * 
 * <p>
 * The Project is set early in the analysis pipeline by AnalysisEngine,
 * making it available to all inspectors that need access to project-level
 * configuration such as application package filters.
 * </p>
 * 
 * <p>
 * Usage in Inspectors:
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;Inject
 * public MyInspector(ProjectHolder projectHolder) {
 *     this.projectHolder = projectHolder;
 * }
 * 
 * public void inspect(...) {
 *     List<String> packages = projectHolder.getApplicationPackages();
 *     // Use packages for filtering
 * }
 * }
 * </pre>
 */
public class ProjectHolder {

    private Project project;

    /**
     * Sets the current Project instance.
     * This should be called by AnalysisEngine at the start of analysis.
     * 
     * @param project the Project to hold
     */
    public void setProject(Project project) {
        this.project = project;
    }

    /**
     * Gets the current Project instance.
     * 
     * @return the Project
     * @throws IllegalStateException if Project has not been set
     */
    public Project getProject() {
        if (project == null) {
            throw new IllegalStateException("Project not set in ProjectHolder. " +
                    "AnalysisEngine should set the Project before inspectors run.");
        }
        return project;
    }

    /**
     * Checks if a Project has been set.
     * 
     * @return true if Project is available
     */
    public boolean hasProject() {
        return project != null;
    }

    /**
     * Gets the application package prefixes configured for this analysis.
     * Classes whose FQN starts with these prefixes are considered application code
     * (not library/framework code).
     * 
     * @return list of package prefixes, or empty list if none configured
     */
    @SuppressWarnings("unchecked")
    public List<String> getApplicationPackages() {
        if (!hasProject()) {
            return Collections.emptyList();
        }

        Object packages = project.getProjectData("application.packages");
        if (packages instanceof List) {
            return (List<String>) packages;
        }

        return Collections.emptyList();
    }

    /**
     * Clears the Project reference.
     * Used for cleanup after analysis completes.
     */
    public void clear() {
        this.project = null;
    }
}
