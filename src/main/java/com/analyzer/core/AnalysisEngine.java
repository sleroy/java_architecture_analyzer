package com.analyzer.core;

import com.analyzer.resource.ResourceResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core analysis engine responsible for executing inspectors on discovered
 * classes.
 * This class implements the decorator pattern where each inspector adds
 * analysis results
 * to the class objects.
 */
public class AnalysisEngine {

    private final InspectorRegistry inspectorRegistry;
    private final ResourceResolver resourceResolver;

    public AnalysisEngine(InspectorRegistry inspectorRegistry, ResourceResolver resourceResolver) {
        this.inspectorRegistry = inspectorRegistry;
        this.resourceResolver = resourceResolver;
    }

    /**
     * Analyzes all discovered classes by running applicable inspectors on each
     * class.
     * Implements the core workflow described in purpose.md:
     * 
     * For each class:
     * - Iterate through each inspector
     * - Check if inspector supports the class type (source vs binary)
     * - Run inspector.decorate(clazz) to add results
     * - Store N/A for unsupported inspectors
     * 
     * @param discoveredClasses   Map of class name to Clazz objects
     * @param requestedInspectors List of inspector names to use (null = all)
     * @return The same classes map with inspector results added
     */
    public Map<String, Clazz> analyze(Map<String, Clazz> discoveredClasses, List<String> requestedInspectors) {
        // Get inspectors to execute
        List<Inspector> inspectors = getInspectorsToExecute(requestedInspectors);

        System.out.println("Starting analysis with " + inspectors.size() + " inspectors...");

        // Execute inspectors on each class
        int processedCount = 0;
        for (Clazz clazz : discoveredClasses.values()) {
            processedCount++;
            if (processedCount % 10 == 0) {
                System.out.println("Analyzed " + processedCount + "/" + discoveredClasses.size() + " classes");
            }

            analyzeClass(clazz, inspectors);
        }

        System.out.println("Analysis completed for " + discoveredClasses.size() + " classes");
        return discoveredClasses;
    }

    /**
     * Analyzes a single class with all applicable inspectors.
     */
    private void analyzeClass(Clazz clazz, List<Inspector> inspectors) {
        for (Inspector inspector : inspectors) {
            try {
                if (inspector.supports(clazz)) {
                    // Inspector supports this class type - execute it
                    InspectorResult result = inspector.decorate(clazz);

                    // Store the result in the class object
                    if (result.isSuccessful()) {
                        clazz.addInspectorResult(inspector.getName(), result.getValue());
                    } else if (result.isNotApplicable()) {
                        clazz.addInspectorResult(inspector.getName(), "N/A");
                    } else if (result.isError()) {
                        clazz.addInspectorResult(inspector.getName(), "ERROR: " + result.getErrorMessage());
                    }
                } else {
                    // Inspector doesn't support this class type - store N/A
                    clazz.addInspectorResult(inspector.getName(), "N/A");
                }
            } catch (Exception e) {
                // Handle inspector errors gracefully
                System.err.println("Error running inspector '" + inspector.getName() +
                        "' on class '" + clazz.getClassName() + "': " + e.getMessage());
                clazz.addInspectorResult(inspector.getName(), "ERROR: " + e.getMessage());
            }
        }
    }

    /**
     * Gets the list of inspectors to execute based on user request.
     */
    private List<Inspector> getInspectorsToExecute(List<String> requestedInspectors) {
        if (requestedInspectors == null || requestedInspectors.isEmpty()) {
            // Use all available inspectors
            return inspectorRegistry.getAllInspectors();
        } else {
            // Use only requested inspectors
            List<Inspector> inspectors = new ArrayList<>();
            for (String inspectorName : requestedInspectors) {
                Inspector inspector = inspectorRegistry.getInspector(inspectorName);
                if (inspector != null) {
                    inspectors.add(inspector);
                } else {
                    System.err.println("Warning: Inspector '" + inspectorName + "' not found");
                }
            }
            return inspectors;
        }
    }

    /**
     * Gets the list of all inspector names that will be used for CSV column
     * headers.
     */
    public List<String> getInspectorNames(List<String> requestedInspectors) {
        List<Inspector> inspectors = getInspectorsToExecute(requestedInspectors);
        List<String> names = new ArrayList<>();
        for (Inspector inspector : inspectors) {
            names.add(inspector.getName());
        }
        return names;
    }

    /**
     * Gets statistics about the analysis.
     */
    public AnalysisStatistics getStatistics() {
        return new AnalysisStatistics(
                inspectorRegistry.getInspectorCount(),
                inspectorRegistry.getSourceInspectorCount(),
                inspectorRegistry.getBinaryInspectorCount());
    }

    /**
     * Statistics about the analysis execution.
     */
    public static class AnalysisStatistics {
        private final int totalInspectors;
        private final int sourceInspectors;
        private final int binaryInspectors;

        public AnalysisStatistics(int totalInspectors, int sourceInspectors, int binaryInspectors) {
            this.totalInspectors = totalInspectors;
            this.sourceInspectors = sourceInspectors;
            this.binaryInspectors = binaryInspectors;
        }

        public int getTotalInspectors() {
            return totalInspectors;
        }

        public int getSourceInspectors() {
            return sourceInspectors;
        }

        public int getBinaryInspectors() {
            return binaryInspectors;
        }

        @Override
        public String toString() {
            return String.format("Analysis Statistics: %d total inspectors (%d source, %d binary)",
                    totalInspectors, sourceInspectors, binaryInspectors);
        }
    }
}
