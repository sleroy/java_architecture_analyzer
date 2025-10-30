package com.analyzer.core.engine;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.api.inspector.Inspector;
import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/**
 * Generic multi-pass executor for analyzing graph nodes with convergence
 * detection.
 * This class encapsulates the common multi-pass analysis pattern used across
 * different phases.
 *
 * @param <T> the type of GraphNode being analyzed
 */
public class MultiPassExecutor<T extends GraphNode> {

    private static final Logger logger = LoggerFactory.getLogger(MultiPassExecutor.class);

    /**
     * Configuration for multi-pass execution.
     *
     * @param <T> the type of GraphNode being analyzed
     */
    public static class ExecutionConfig<T extends GraphNode> {
        private final String phaseName;
        private final int maxPasses;
        private final ExecutionProfile.ExecutionPhase executionPhase;
        private final Supplier<Collection<T>> itemSupplier;
        private final List<Inspector<T>> inspectors;
        private final ItemAnalyzer<T> itemAnalyzer;

        public ExecutionConfig(String phaseName,
                int maxPasses,
                ExecutionProfile.ExecutionPhase executionPhase,
                Supplier<Collection<T>> itemSupplier,
                List<Inspector<T>> inspectors,
                ItemAnalyzer<T> itemAnalyzer) {
            this.phaseName = phaseName;
            this.maxPasses = maxPasses;
            this.executionPhase = executionPhase;
            this.itemSupplier = itemSupplier;
            this.inspectors = inspectors;
            this.itemAnalyzer = itemAnalyzer;
        }

        public String getPhaseName() {
            return phaseName;
        }

        public int getMaxPasses() {
            return maxPasses;
        }

        public ExecutionProfile.ExecutionPhase getExecutionPhase() {
            return executionPhase;
        }

        public Collection<T> getItems() {
            return itemSupplier.get();
        }

        public List<Inspector<T>> getInspectors() {
            return inspectors;
        }

        public ItemAnalyzer<T> getItemAnalyzer() {
            return itemAnalyzer;
        }
    }

    /**
     * Functional interface for analyzing a single item.
     * Returns the set of inspector names that were triggered for the item.
     *
     * @param <T> the type of GraphNode being analyzed
     */
    @FunctionalInterface
    public interface ItemAnalyzer<T extends GraphNode> {
        /**
         * Analyzes a single item with the given inspectors.
         *
         * @param item             the item to analyze
         * @param inspectors       the inspectors to apply
         * @param passStartTime    the start time of the current pass
         * @param executionProfile the execution profile for tracking
         * @param pass             the current pass number
         * @return set of inspector names that were triggered
         */
        Set<String> analyze(T item,
                List<Inspector<T>> inspectors,
                LocalDateTime passStartTime,
                ExecutionProfile executionProfile,
                int pass);
    }

    /**
     * Result of a multi-pass execution.
     */
    public static class ExecutionResult {
        private final int passesExecuted;
        private final boolean converged;
        private final int totalItemsProcessed;
        private final ExecutionProfile executionProfile;

        public ExecutionResult(int passesExecuted,
                boolean converged,
                int totalItemsProcessed,
                ExecutionProfile executionProfile) {
            this.passesExecuted = passesExecuted;
            this.converged = converged;
            this.totalItemsProcessed = totalItemsProcessed;
            this.executionProfile = executionProfile;
        }

        public int getPassesExecuted() {
            return passesExecuted;
        }

        public boolean isConverged() {
            return converged;
        }

        public int getTotalItemsProcessed() {
            return totalItemsProcessed;
        }

        public ExecutionProfile getExecutionProfile() {
            return executionProfile;
        }
    }

    /**
     * Executes multi-pass analysis with convergence detection.
     *
     * @param config the execution configuration
     * @return the execution result
     */
    public ExecutionResult execute(ExecutionConfig<T> config) {
        Collection<T> items = config.getItems();
        List<Inspector<T>> inspectors = config.getInspectors();

        if (inspectors.isEmpty()) {
            logger.info("{}: No inspectors found, skipping", config.getPhaseName());
            return new ExecutionResult(0, true, 0, null);
        }

        if (items.isEmpty()) {
            logger.info("{}: No items to analyze, skipping", config.getPhaseName());
            return new ExecutionResult(0, true, 0, null);
        }

        logger.info("{}: Executing {} inspectors on {} items (max passes: {})",
                config.getPhaseName(), inspectors.size(), items.size(), config.getMaxPasses());

        // Initialize execution profile - track all inspectors
        List<String> inspectorNames = inspectors.stream()
                .map(Inspector::getName)
                .toList();
        ExecutionProfile executionProfile = new ExecutionProfile(inspectorNames);

        int pass = 1;
        boolean hasChanges = true;
        int totalProcessed = 0;

        while (hasChanges && pass <= config.getMaxPasses()) {
            logger.info("=== {} Pass {} of {} ===", config.getPhaseName(), pass, config.getMaxPasses());

            LocalDateTime passStartTime = LocalDateTime.now();
            int itemsProcessed = 0;
            int itemsSkipped = 0;
            Set<String> triggeredInspectors = new HashSet<>();

            try (ProgressBar pb = new ProgressBar(config.getPhaseName() + " Pass " + pass, items.size())) {
                for (T item : items) {
                    Set<String> itemInspectors = config.getItemAnalyzer().analyze(
                            item, inspectors, passStartTime, executionProfile, pass);

                    if (!itemInspectors.isEmpty()) {
                        itemsProcessed++;
                        triggeredInspectors.addAll(itemInspectors);
                    } else {
                        itemsSkipped++;
                    }
                    pb.step();
                }
            }

            logger.info("{} Pass {} completed: {} items processed, {} items skipped (up-to-date)",
                    config.getPhaseName(), pass, itemsProcessed, itemsSkipped);

            // Print triggered inspectors for this pass
            if (!triggeredInspectors.isEmpty()) {
                logger.info("{} Pass {} triggered inspectors: [{}]", config.getPhaseName(), pass,
                        String.join(", ", triggeredInspectors.stream().sorted().toList()));
                inspectors = inspectors.stream().filter(i -> !triggeredInspectors.contains(i.getName())).toList();
            } else {
                logger.info("{} Pass {} triggered inspectors: [none]", config.getPhaseName(), pass);
            }

            totalProcessed += itemsProcessed;

            // Check for convergence - if no items were processed, we've converged
            hasChanges = itemsProcessed > 0;

            if (!hasChanges) {
                logger.info("{} convergence achieved after {} passes - no more items need processing",
                        config.getPhaseName(), pass);
                break;
            }

            pass++;
        }

        boolean converged = !hasChanges;
        int actualPasses = converged ? pass : pass - 1;

        if (!converged) {
            logger.warn("{} reached maximum passes ({}) without full convergence",
                    config.getPhaseName(), config.getMaxPasses());
        } else {
            logger.info("{} multi-pass analysis completed successfully in {} passes",
                    config.getPhaseName(), actualPasses);
        }

        // Generate and log execution profile report
        executionProfile.setAnalysisMetrics(actualPasses, items.size());
        executionProfile.markAnalysisComplete();
        logger.info("=== {} Execution Summary ===", config.getPhaseName());
        executionProfile.logReport();
        logger.info("=== End {} Summary ===", config.getPhaseName());

        return new ExecutionResult(actualPasses, converged, totalProcessed, executionProfile);
    }
}
