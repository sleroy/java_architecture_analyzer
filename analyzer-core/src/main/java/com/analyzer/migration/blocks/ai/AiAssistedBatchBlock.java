package com.analyzer.migration.blocks.ai;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.BlockType;
import com.analyzer.migration.plan.MigrationBlock;
import com.analyzer.migration.template.TemplateProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes AI_ASSISTED for each node in a list with progress tracking.
 * Similar to AI_PROMPT_BATCH but invokes the configured AI backend (Amazon Q,
 * Gemini, etc.) for each node.
 * 
 * <p>
 * This block is useful for processing lists of graph nodes (from GRAPH_QUERY
 * results)
 * where each node requires AI-assisted migration with the configured AI
 * backend.
 * </p>
 * 
 * <p>
 * <strong>Features:</strong>
 * </p>
 * <ul>
 * <li>Takes input-nodes variable (list of graph nodes from GRAPH_QUERY)</li>
 * <li>Processes each node sequentially with AI_ASSISTED</li>
 * <li>Displays progress bar: "Processing node X of Y: {node_id}"</li>
 * <li>Continues on failure and collects errors</li>
 * <li>Provides full node object and node ID as variables</li>
 * </ul>
 * 
 * <p>
 * <strong>Context Variables:</strong>
 * </p>
 * <ul>
 * <li>{@code current_node} - Full graph node object</li>
 * <li>{@code current_node_id} - Node ID string</li>
 * <li>{@code current_index} - Current iteration index (0-based)</li>
 * <li>{@code total_nodes} - Total number of nodes to process</li>
 * </ul>
 */
public class AiAssistedBatchBlock implements MigrationBlock {
    private static final Logger logger = LoggerFactory.getLogger(AiAssistedBatchBlock.class);

    private final String name;
    private final String inputNodesVariableName;
    private final String promptTemplate;
    private final String description;
    private final String workingDirectoryTemplate;
    private final String progressMessage;
    private final int timeoutSeconds;
    private final int maxNodes;

    private AiAssistedBatchBlock(Builder builder) {
        this.name = builder.name;
        this.inputNodesVariableName = builder.inputNodesVariableName;
        this.promptTemplate = builder.promptTemplate;
        this.description = builder.description;
        this.workingDirectoryTemplate = builder.workingDirectoryTemplate;
        this.progressMessage = builder.progressMessage;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.maxNodes = builder.maxNodes;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BlockResult execute(MigrationContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // Resolve variable name if it contains template syntax (e.g.,
            // "${stateless_beans}")
            String resolvedVariableName = context.resolveVariableName(inputNodesVariableName);

            // Get the list of nodes from context
            Object nodesObj = context.getVariable(resolvedVariableName);
            if (nodesObj == null) {
                return BlockResult.failure(
                        "Input nodes variable not found in context",
                        "Variable: " + resolvedVariableName + " (from: " + inputNodesVariableName + ")");
            }

            List<?> nodes = convertToList(nodesObj);
            if (nodes.isEmpty()) {
                return BlockResult.builder()
                        .success(true)
                        .message("No nodes to process")
                        .warning("Nodes list was empty")
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // Limit number of nodes if needed
            int nodeCount = Math.min(nodes.size(), maxNodes > 0 ? maxNodes : nodes.size());

            List<String> processedNodeIds = new ArrayList<>();
            List<String> failedNodeIds = new ArrayList<>();
            Map<String, String> errorMessages = new HashMap<>();
            int successCount = 0;
            int failureCount = 0;

            logger.info("Processing {} nodes with AI_ASSISTED using configured AI backend", nodeCount);
            System.out.println("\n" + "=".repeat(80));
            System.out.println(String.format("AI_ASSISTED_BATCH: %s", name));
            if (description != null && !description.isEmpty()) {
                System.out.println("Description: " + description);
            }
            System.out.println(String.format("Processing %d nodes...", nodeCount));
            System.out.println("=".repeat(80) + "\n");

            // Process each node using AiAssistedBlock delegation
            for (int i = 0; i < nodeCount; i++) {
                Object node = nodes.get(i);
                String nodeId = extractNodeId(node);

                try {
                    // Display progress
                    String progress = String.format("[%d/%d] %s: %s",
                            i + 1, nodeCount,
                            progressMessage != null ? progressMessage : "Processing node",
                            nodeId);
                    System.out.println(progress);
                    logger.info("Processing node {}/{}: {}", i + 1, nodeCount, nodeId);

                    // Set current node variables in context
                    context.setVariable("current_node", node);
                    context.setVariable("current_node_id", nodeId);
                    context.setVariable("current_index", i);
                    context.setVariable("total_nodes", nodeCount);

                    // Resolve working directory template using substituteVariables
                    String workingDirectory = context.substituteVariables(workingDirectoryTemplate);

                    // Create individual AiAssistedBlock for this node
                    AiAssistedBlock assistedBlock = AiAssistedBlock.builder()
                            .name(name + "_node_" + i)
                            .promptTemplate(promptTemplate)
                            .description(description + " (node " + (i + 1) + "/" + nodeCount + ")")
                            .workingDirectoryTemplate(workingDirectory)
                            .timeoutSeconds(timeoutSeconds)
                            .build();

                    // Execute the individual assisted block
                    BlockResult nodeResult = assistedBlock.execute(context);

                    if (nodeResult.isSuccess()) {
                        successCount++;
                        processedNodeIds.add(nodeId);
                        System.out.println("  ✓ Success");
                    } else {
                        failureCount++;
                        failedNodeIds.add(nodeId);
                        errorMessages.put(nodeId, nodeResult.getErrorDetails());
                        System.out.println("  ✗ Failed: " + nodeResult.getErrorDetails());
                        logger.warn("Failed to process node {}: {}", nodeId, nodeResult.getErrorDetails());
                    }
                } catch (Exception e) {
                    failureCount++;
                    failedNodeIds.add(nodeId);
                    errorMessages.put(nodeId, e.getMessage());
                    System.out.println("  ✗ Error: " + e.getMessage());
                    logger.error("Error processing node {}: {}", nodeId, e.getMessage(), e);
                }

                System.out.println(); // Blank line between nodes
            }

            // Clean up temporary variables
            context.removeVariable("current_node");
            context.removeVariable("current_node_id");
            context.removeVariable("current_index");
            context.removeVariable("total_nodes");

            long executionTime = System.currentTimeMillis() - startTime;

            // Display summary
            System.out.println("=".repeat(80));
            System.out.println(String.format("BATCH COMPLETE: %d successful, %d failed out of %d nodes",
                    successCount, failureCount, nodeCount));
            System.out.println("=".repeat(80) + "\n");

            BlockResult.Builder resultBuilder = BlockResult.builder()
                    .success(successCount > 0)
                    .message(String.format("Processed %d nodes with AI backend (%d successful, %d failed)",
                            nodeCount, successCount, failureCount))
                    .outputVariable("processed_node_ids", processedNodeIds)
                    .outputVariable("failed_node_ids", failedNodeIds)
                    .outputVariable("node_count", nodeCount)
                    .outputVariable("success_count", successCount)
                    .outputVariable("failure_count", failureCount)
                    .executionTimeMs(executionTime);

            if (!errorMessages.isEmpty()) {
                resultBuilder.outputVariable("error_messages", errorMessages);
            }

            if (nodes.size() > nodeCount) {
                resultBuilder.warning(String.format(
                        "Limited to %d nodes out of %d total",
                        nodeCount, nodes.size()));
            }

            if (failureCount > 0) {
                resultBuilder.warning(String.format(
                        "%d out of %d nodes failed processing",
                        failureCount, nodeCount));
            }

            if (failureCount == nodeCount) {
                // All nodes failed
                return resultBuilder
                        .success(false)
                        .message("All nodes failed processing")
                        .build();
            }

            return resultBuilder.build();

        } catch (Exception e) {
            logger.error("Failed to execute AI_ASSISTED_BATCH: {}", e.getMessage(), e);
            return BlockResult.failure(
                    "Failed to execute AI_ASSISTED_BATCH",
                    e.getMessage());
        }
    }

    /**
     * Converts various types to a List.
     */
    @SuppressWarnings("unchecked")
    private List<?> convertToList(Object obj) {
        if (obj instanceof List) {
            return (List<?>) obj;
        } else if (obj instanceof Object[]) {
            return List.of((Object[]) obj);
        } else {
            // Single item - wrap in list
            return List.of(obj);
        }
    }

    /**
     * Extracts node ID from a graph node object.
     * Supports both Map-based and object-based node representations.
     */
    private String extractNodeId(Object node) {
        if (node == null) {
            return "null";
        }

        // If it's a Map (common for graph query results)
        if (node instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nodeMap = (Map<String, Object>) node;
            Object id = nodeMap.get("id");
            if (id != null) {
                return id.toString();
            }
            Object nodeId = nodeMap.get("nodeId");
            if (nodeId != null) {
                return nodeId.toString();
            }
        }

        // Try reflection for getId() or getNodeId()
        try {
            var method = node.getClass().getMethod("getId");
            Object id = method.invoke(node);
            if (id != null) {
                return id.toString();
            }
        } catch (Exception ignored) {
        }

        try {
            var method = node.getClass().getMethod("getNodeId");
            Object id = method.invoke(node);
            if (id != null) {
                return id.toString();
            }
        } catch (Exception ignored) {
        }

        // Fallback to toString()
        return node.toString();
    }

    @Override
    public BlockType getType() {
        return BlockType.AI_ASSISTED_BATCH;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toMarkdownDescription() {
        StringBuilder md = new StringBuilder();
        md.append("**").append(name).append("** (AI Assisted Batch)\n");
        if (description != null && !description.isEmpty()) {
            md.append("- Description: ").append(description).append("\n");
        }
        md.append("- Input Nodes: `").append(inputNodesVariableName).append("`\n");
        md.append("- Working Directory: `").append(workingDirectoryTemplate).append("`\n");
        md.append("- Timeout: ").append(timeoutSeconds).append(" seconds\n");
        if (maxNodes > 0) {
            md.append("- Max Nodes: ").append(maxNodes).append("\n");
        }
        if (promptTemplate.length() <= 200) {
            md.append("- Template: `").append(promptTemplate).append("`\n");
        } else {
            md.append("- Template: ").append(promptTemplate.length()).append(" characters\n");
        }
        return md.toString();
    }

    @Override
    public boolean validate() {
        if (promptTemplate == null || promptTemplate.trim().isEmpty()) {
            logger.error("Prompt template is required");
            return false;
        }
        if (inputNodesVariableName == null || inputNodesVariableName.trim().isEmpty()) {
            logger.error("Input nodes variable name is required");
            return false;
        }
        if (workingDirectoryTemplate == null || workingDirectoryTemplate.trim().isEmpty()) {
            logger.error("Working directory template is required");
            return false;
        }
        return true;
    }

    @Override
    public String[] getRequiredVariables() {
        return new String[] { inputNodesVariableName };
    }

    public static class Builder {
        private String name;
        private String inputNodesVariableName;
        private String promptTemplate;
        private String description;
        private String workingDirectoryTemplate;
        private String progressMessage = "Processing node";
        private int timeoutSeconds = 600;
        private int maxNodes = -1; // No limit by default

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder inputNodesVariableName(String inputNodesVariableName) {
            this.inputNodesVariableName = inputNodesVariableName;
            return this;
        }

        public Builder promptTemplate(String promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder workingDirectoryTemplate(String workingDirectoryTemplate) {
            this.workingDirectoryTemplate = workingDirectoryTemplate;
            return this;
        }

        public Builder progressMessage(String progressMessage) {
            this.progressMessage = progressMessage;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder maxNodes(int maxNodes) {
            this.maxNodes = maxNodes;
            return this;
        }

        public AiAssistedBatchBlock build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (inputNodesVariableName == null || inputNodesVariableName.isEmpty()) {
                throw new IllegalStateException("Input nodes variable name is required");
            }
            if (promptTemplate == null || promptTemplate.isEmpty()) {
                throw new IllegalStateException("Prompt template is required");
            }
            if (workingDirectoryTemplate == null || workingDirectoryTemplate.isEmpty()) {
                throw new IllegalStateException("Working directory template is required");
            }
            return new AiAssistedBatchBlock(this);
        }
    }
}
