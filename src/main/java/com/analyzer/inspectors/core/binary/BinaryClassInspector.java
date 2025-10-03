package com.analyzer.inspectors.core.binary;

import com.analyzer.core.Clazz;
import com.analyzer.core.Inspector;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;

import java.io.IOException;
import java.io.InputStream;

/**
 * Base class for all binary class inspectors.
 * Provides common functionality for analyzing compiled Java class files.
 * Uses ResourceResolver for unified access to class files in various locations.
 */
public abstract class BinaryClassInspector implements Inspector<Clazz> {

    private final ResourceResolver resourceResolver;

    /**
     * Creates a new BinaryClassInspector with the specified ResourceResolver.
     * 
     * @param resourceResolver the resolver for accessing class file resources
     */
    protected BinaryClassInspector(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Override
    public final InspectorResult decorate(Clazz clazz) {
        if (!supports(clazz)) {
            return InspectorResult.notApplicable(getName());
        }

        try {
            ResourceLocation binaryLocation = clazz.getBinaryLocation();
            if (binaryLocation == null) {
                return InspectorResult.notApplicable(getName());
            }

            return analyzeBinaryClass(clazz, binaryLocation);

        } catch (Exception e) {
            return InspectorResult.error(getName(), "Error analyzing binary class: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(Clazz clazz) {
        return clazz != null && clazz.hasBinaryCode();
    }

    /**
     * Analyzes a binary class using the ResourceResolver.
     */
    private InspectorResult analyzeBinaryClass(Clazz clazz, ResourceLocation binaryLocation) throws IOException {
        try (InputStream classStream = resourceResolver.openStream(binaryLocation)) {
            if (classStream == null) {
                return InspectorResult.error(getName(), "Could not open binary class: " + binaryLocation.getUri());
            }
            return analyzeClassFile(clazz, binaryLocation, classStream);
        }
    }

    /**
     * Analyzes the binary class file for the given class.
     * Subclasses must implement this method to provide specific analysis logic.
     * 
     * @param clazz            the class to analyze
     * @param binaryLocation   the location of the binary class file
     * @param classInputStream the input stream to the class file
     * @return the result of the analysis
     * @throws IOException if there's an error reading the class file
     */
    protected abstract InspectorResult analyzeClassFile(Clazz clazz, ResourceLocation binaryLocation,
            InputStream classInputStream) throws IOException;
}
