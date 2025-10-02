package com.analyzer.inspectors.packages;

import com.analyzer.core.Inspector;
import com.analyzer.core.InspectorResult;
import com.analyzer.core.Package;
import com.analyzer.resource.ResourceResolver;

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

    @Override
    public final InspectorResult decorate(Package packageToAnalyze) {
        if (!supports(packageToAnalyze)) {
            return InspectorResult.notApplicable(getName());
        }

        try {
            return analyzePackage(packageToAnalyze);
        } catch (Exception e) {
            return InspectorResult.error(getName(), "Error analyzing package: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(Package packageToAnalyze) {
        return packageToAnalyze != null;
    }

    /**
     * Analyzes the given package.
     * Subclasses must implement this method to provide specific analysis logic.
     * 
     * @param packageToAnalyze the package to analyze
     * @return the result of the analysis
     */
    protected abstract InspectorResult analyzePackage(Package packageToAnalyze);
}
