package com.analyzer.dev.inspectors.packages;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.core.inspector.InspectorResult;
import com.analyzer.core.model.Package;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.api.resource.ResourceResolver;

/**
 * Base class for all package inspectors.
 * Provides common functionality for analyzing Java packages.
 * Uses ResourceResolver for unified access to resources when needed by concrete
 * implementations.
 */
public abstract class PackageInspector implements Inspector<Package> {

    private final ResourceResolver resourceResolver;

    /**
     * Creates a new PackageInspector with the specified ResourceResolver.
     * 
     * @param resourceResolver the resolver for accessing resources if needed by
     *                         concrete implementations
     */
    protected PackageInspector(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    /**
     * Gets the ResourceResolver for concrete implementations that need to access
     * resources.
     * 
     * @return the resource resolver
     */
    protected ResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    public void decorate(Package packageToAnalyze, NodeDecorator<ProjectFile> projectFileDecorator) {

        try {
            analyzePackage(packageToAnalyze, projectFileDecorator);
        } catch (Exception e) {
            projectFileDecorator.error("Error analyzing package: " + e.getMessage());
        }
    }

    public boolean supports(Package packageToAnalyze) {
        return packageToAnalyze != null;
    }

    /**
     * Analyzes the given package.
     * Subclasses must implement this method to provide specific analysis logic.
     *
     * @param packageToAnalyze the package to analyze
     * @param projectFileDecorator
     * @return the result of the analysis
     */
    protected abstract InspectorResult analyzePackage(Package packageToAnalyze, NodeDecorator<ProjectFile> projectFileDecorator);
}
