package com.analyzer.migration.blocks.ai;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.BlockResult;
import com.analyzer.migration.plan.BlockType;
import com.analyzer.migration.plan.MigrationBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates multiple AI prompts iteratively, one per item in a list.
 * Useful for processing lists of files, nodes, or other items that each need
 * individual AI assistance. Delegates to AiPromptBlock instances for actual AI
 * processing.
 */
public class AiPromptBatchBlock implements MigrationBlock {
    private static final Logger logger = LoggerFactory.getLogger(AiPromptBatchBlock.class);

    private final String name;
    private final String itemsVariableName;
    private final String promptTemplate;
    private final String description;
    private final boolean displayFormatted;
    private final int maxPrompts;
    private final ResponseFormat responseFormat;
    private final boolean enableBedrock;

    private AiPromptBatchBlock(Builder builder) {
        this.name = builder.name;
        this.itemsVariableName = builder.itemsVariableName;
        this.promptTemplate = builder.promptTemplate;
        this.description = builder.description;
        this.displayFormatted = builder.displayFormatted;
        this.maxPrompts = builder.maxPrompts;
        this.responseFormat = builder.responseFormat;
        this.enableBedrock = builder.enableBedrock;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BlockResult execute(MigrationContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // Resolve variable name if it contains template syntax (e.g., "${items}")
            String resolvedVariableName = context.resolveVariableName(itemsVariableName);

            // Get the list of items from context
            Object itemsObj = context.getVariable(resolvedVariableName);
            if (itemsObj == null) {
                return BlockResult.failure(
                        "Items variable not found in context",
                        "Variable: " + resolvedVariableName + " (from: " + itemsVariableName + ")");
            }

            List<?> items = convertToList(itemsObj);
            if (items.isEmpty()) {
                return BlockResult.builder()
                        .success(true)
                        .message("No items to process")
                        .warning("Items list was empty")
                        .executionTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // Limit number of prompts if needed
            int itemCount = Math.min(items.size(), maxPrompts > 0 ? maxPrompts : items.size());

            List<String> generatedPrompts = new ArrayList<>();
            List<String> aiResponses = new ArrayList<>();
            List<String> parsedResponses = new ArrayList<>();
            Map<String, Object> aggregatedResults = new HashMap<>();
            int successCount = 0;
            int failureCount = 0;

            logger.info("Processing {} items with AI prompts using delegation to AiPromptBlock", itemCount);

            // Process each item using AiPromptBlock delegation
            for (int i = 0; i < itemCount; i++) {
                Object item = items.get(i);

                try {
                    // Set current item in context for template processing
                    context.setVariable("current_item", item);
                    context.setVariable("item", item);
                    context.setVariable("item_index", item);
                    context.setVariable("current_index", i);
                    context.setVariable("total_items", items.size());

                    // Create individual AiPromptBlock for this item
                    AiPromptBlock promptBlock = AiPromptBlock.builder()
                            .name(name + "_item_" + i)
                            .promptTemplate(promptTemplate)
                            .description(description + " (item " + (i + 1) + "/" + itemCount + ")")
                            .displayFormatted(false) // We'll handle display formatting ourselves
                            .responseFormat(responseFormat)
                            .enableBedrock(enableBedrock)
                            .build();

                    // Execute the individual prompt block
                    BlockResult itemResult = promptBlock.execute(context);

                    if (itemResult.isSuccess()) {
                        successCount++;

                        // Collect results
                        String prompt = (String) itemResult.getOutputVariables().get("prompt");
                        String aiResponse = (String) itemResult.getOutputVariables().get("ai_response");
                        String parsedResponse = (String) itemResult.getOutputVariables().get("ai_response_parsed");

                        generatedPrompts.add(prompt);
                        if (aiResponse != null) {
                            aiResponses.add(aiResponse);
                        }
                        if (parsedResponse != null) {
                            parsedResponses.add(parsedResponse);
                        }

                        // Display if requested
                        if (displayFormatted) {
                            String formattedOutput = formatPromptForDisplay(prompt, i + 1, itemCount);
                            System.out.println("\n" + formattedOutput);

                            if (aiResponse != null) {
                                System.out.println("\nAI RESPONSE " + (i + 1) + "/" + itemCount + ":");
                                System.out.println("=".repeat(80));
                                System.out.println(parsedResponse != null ? parsedResponse : aiResponse);
                                System.out.println("=".repeat(80));
                            }
                        }
                    } else {
                        failureCount++;
                        logger.warn("Failed to process item {}: {}", i, itemResult.getErrorDetails());
                        generatedPrompts.add(null); // Maintain index alignment
                    }
                } catch (Exception e) {
                    failureCount++;
                    logger.error("Error processing item {}: {}", i, e.getMessage(), e);
                    generatedPrompts.add(null); // Maintain index alignment
                }
            }

            // Clean up temporary variables
            context.removeVariable("current_item");
            context.removeVariable("current_index");
            context.removeVariable("total_items");

            long executionTime = System.currentTimeMillis() - startTime;

            BlockResult.Builder resultBuilder = BlockResult.builder()
                    .success(successCount > 0)
                    .message(String.format("Processed %d AI prompts (%d successful, %d failed)",
                            itemCount, successCount, failureCount))
                    .outputVariable("prompts", generatedPrompts)
                    .outputVariable("prompt_count", itemCount)
                    .outputVariable("success_count", successCount)
                    .outputVariable("failure_count", failureCount)
                    .executionTimeMs(executionTime);

            // Add AI responses if available
            if (!aiResponses.isEmpty()) {
                resultBuilder.outputVariable("ai_responses", aiResponses);
            }
            if (!parsedResponses.isEmpty()) {
                resultBuilder.outputVariable("ai_responses_parsed", parsedResponses);
            }

            if (items.size() > itemCount) {
                resultBuilder.warning(String.format(
                        "Limited to %d prompts out of %d items",
                        itemCount, items.size()));
            }

            if (failureCount > 0) {
                resultBuilder.warning(String.format(
                        "%d out of %d items failed processing",
                        failureCount, itemCount));
            }

            return resultBuilder.build();

        } catch (Exception e) {
            return BlockResult.failure(
                    "Failed to generate AI prompt batch",
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
     * Formats a prompt for display with numbering.
     */
    private String formatPromptForDisplay(String prompt, int current, int total) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("=".repeat(80)).append("\n");
        formatted.append(String.format("AI PROMPT %d/%d: %s", current, total, name)).append("\n");
        if (description != null && !description.isEmpty()) {
            formatted.append("Description: ").append(description).append("\n");
        }
        formatted.append("=".repeat(80)).append("\n");
        formatted.append(prompt).append("\n");
        formatted.append("=".repeat(80)).append("\n");
        return formatted.toString();
    }

    @Override
    public BlockType getType() {
        return BlockType.AI_PROMPT_BATCH;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toMarkdownDescription() {
        StringBuilder md = new StringBuilder();
        md.append("**").append(name).append("** (AI Prompt Batch)\n");
        if (description != null && !description.isEmpty()) {
            md.append("- Description: ").append(description).append("\n");
        }
        md.append("- Items Variable: `").append(itemsVariableName).append("`\n");
        if (maxPrompts > 0) {
            md.append("- Max Prompts: ").append(maxPrompts).append("\n");
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
        if (itemsVariableName == null || itemsVariableName.trim().isEmpty()) {
            logger.error("Items variable name is required");
            return false;
        }
        return true;
    }

    @Override
    public String[] getRequiredVariables() {
        return new String[] { itemsVariableName };
    }

    public static class Builder {
        private String name;
        private String itemsVariableName;
        private String promptTemplate;
        private String description;
        private boolean displayFormatted = true;
        private int maxPrompts = -1; // No limit by default
        private ResponseFormat responseFormat = ResponseFormat.TEXT;
        private boolean enableBedrock = true;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder itemsVariableName(String itemsVariableName) {
            this.itemsVariableName = itemsVariableName;
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

        public Builder displayFormatted(boolean displayFormatted) {
            this.displayFormatted = displayFormatted;
            return this;
        }

        public Builder maxPrompts(int maxPrompts) {
            this.maxPrompts = maxPrompts;
            return this;
        }

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder enableBedrock(boolean enableBedrock) {
            this.enableBedrock = enableBedrock;
            return this;
        }

        public AiPromptBatchBlock build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (itemsVariableName == null || itemsVariableName.isEmpty()) {
                throw new IllegalStateException("Items variable name is required");
            }
            if (promptTemplate == null || promptTemplate.isEmpty()) {
                throw new IllegalStateException("Prompt template is required");
            }
            return new AiPromptBatchBlock(this);
        }
    }
}
