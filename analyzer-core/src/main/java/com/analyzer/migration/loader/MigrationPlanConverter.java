package com.analyzer.migration.loader;

import com.analyzer.core.db.H2GraphStorageRepository;
import com.analyzer.migration.blocks.ai.AiAssistedBlock;
import com.analyzer.migration.blocks.ai.AiPromptBatchBlock;
import com.analyzer.migration.blocks.ai.AiPromptBlock;
import com.analyzer.migration.blocks.analysis.GraphQueryBlock;
import com.analyzer.migration.blocks.automated.CommandBlock;
import com.analyzer.migration.blocks.automated.FileOperationBlock;
import com.analyzer.migration.blocks.automated.GitCommandBlock;
import com.analyzer.migration.blocks.automated.OpenRewriteBlock;
import com.analyzer.migration.blocks.validation.InteractiveValidationBlock;
import com.analyzer.migration.loader.dto.BlockDTO;
import com.analyzer.migration.loader.dto.MigrationPlanDTO;
import com.analyzer.migration.plan.MigrationBlock;
import com.analyzer.migration.plan.MigrationPlan;
import com.analyzer.migration.plan.Phase;
import com.analyzer.migration.plan.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Converts MigrationPlanDTO objects to domain model objects (MigrationPlan).
 * Handles all block type conversions with proper builder patterns.
 */
public class MigrationPlanConverter {

    private static final Logger logger = LoggerFactory.getLogger(MigrationPlanConverter.class);

    private final H2GraphStorageRepository repository;

    /**
     * Creates a new converter with the specified repository for GraphQueryBlocks.
     *
     * @param repository the graph storage repository
     */
    public MigrationPlanConverter(H2GraphStorageRepository repository) {
        this.repository = repository;
    }

    /**
     * Converts a MigrationPlanDTO to a MigrationPlan domain object.
     *
     * @param dto the DTO to convert
     * @return the converted migration plan
     * @throws IllegalArgumentException if conversion fails
     */
    public MigrationPlan convert(MigrationPlanDTO dto) {
        logger.info("Converting migration plan DTO: {}", dto.getPlanRoot().getName());

        MigrationPlanDTO.PlanRootDTO root = dto.getPlanRoot();

        // Convert phases
        List<Phase> phases = root.getPhases().stream()
                .map(this::convertPhase)
                .collect(Collectors.toList());

        // Build the plan
        MigrationPlan.Builder planBuilder = MigrationPlan.builder(root.getName())
                .version(root.getVersion());

        if (root.getDescription() != null) {
            planBuilder.description(root.getDescription());
        }

        // Note: Variables are stored in MigrationContext, not in MigrationPlan
        // They will be added to the context when the plan is executed

        // Add phases
        phases.forEach(planBuilder::addPhase);

        MigrationPlan plan = planBuilder.build();
        logger.info("Successfully converted migration plan with {} phases", phases.size());

        return plan;
    }

    /**
     * Converts a PhaseDTO to a Phase domain object.
     */
    private Phase convertPhase(MigrationPlanDTO.PhaseDTO dto) {
        logger.debug("Converting phase: {}", dto.getId());

        List<Task> tasks = dto.getTasks().stream()
                .map(this::convertTask)
                .collect(Collectors.toList());

        Phase.Builder phaseBuilder = Phase.builder(dto.getName());

        if (dto.getDescription() != null) {
            phaseBuilder.description(dto.getDescription());
        }

        tasks.forEach(phaseBuilder::addTask);

        return phaseBuilder.build();
    }

    /**
     * Converts a TaskDTO to a Task domain object.
     */
    private Task convertTask(MigrationPlanDTO.TaskDTO dto) {
        logger.debug("Converting task: {}", dto.getId());

        List<MigrationBlock> blocks = dto.getBlocks().stream()
                .map(this::convertBlock)
                .collect(Collectors.toList());

        Task.Builder taskBuilder = Task.builder(dto.getId());

        if (dto.getName() != null) {
            taskBuilder.name(dto.getName());
        }

        if (dto.getDescription() != null) {
            taskBuilder.description(dto.getDescription());
        }

        // Add blocks
        blocks.forEach(taskBuilder::addBlock);

        return taskBuilder.build();
    }

    /**
     * Converts a BlockDTO to a MigrationBlock implementation.
     */
    private MigrationBlock convertBlock(BlockDTO dto) {
        logger.debug("Converting block: {} of type {}", dto.getName(), dto.getType());

        switch (dto.getType().toUpperCase()) {
            case "COMMAND":
                return convertCommandBlock(dto);
            case "GIT":
                return convertGitCommandBlock(dto);
            case "FILE_OPERATION":
                return convertFileOperationBlock(dto);
            case "OPENREWRITE":
                return convertOpenRewriteBlock(dto);
            case "GRAPH_QUERY":
                return convertGraphQueryBlock(dto);
            case "AI_PROMPT":
                return convertAiPromptBlock(dto);
            case "AI_PROMPT_BATCH":
                return convertAiPromptBatchBlock(dto);
            case "AI_ASSISTED":
                return convertAiAssistedBlock(dto);
            case "INTERACTIVE_VALIDATION":
                return convertInteractiveValidationBlock(dto);
            default:
                throw new IllegalArgumentException("Unknown block type: " + dto.getType());
        }
    }

    /**
     * Converts BlockDTO to CommandBlock.
     */
    private CommandBlock convertCommandBlock(BlockDTO dto) {
        Map<String, Object> props = dto.getProperties();

        CommandBlock.Builder builder = CommandBlock.builder()
                .name(dto.getName())
                .command(getRequiredString(props, "command", dto.getName()));

        if (props.containsKey("working-directory")) {
            builder.workingDirectory(getString(props, "working-directory"));
        }

        if (props.containsKey("timeout-seconds")) {
            builder.timeoutSeconds(getInteger(props, "timeout-seconds", 300));
        }

        // Add enable_if condition if present
        if (props.containsKey("enable_if")) {
            builder.enableIf(getString(props, "enable_if"));
        }

        return builder.build();
    }

    /**
     * Converts BlockDTO to GitCommandBlock.
     */
    private GitCommandBlock convertGitCommandBlock(BlockDTO dto) {
        Map<String, Object> props = dto.getProperties();

        GitCommandBlock.Builder builder = GitCommandBlock.builder()
                .name(dto.getName());

        // Handle args as either String or List<String>
        Object argsObj = props.get("args");
        if (argsObj == null) {
            throw new IllegalArgumentException(
                    "Required property 'args' missing in Git block: " + dto.getName());
        }

        if (argsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> argsList = (List<String>) argsObj;
            builder.args(argsList);
        } else {
            builder.args(argsObj.toString());
        }

        if (props.containsKey("working-directory")) {
            builder.workingDirectory(getString(props, "working-directory"));
        }

        if (props.containsKey("timeout-seconds")) {
            builder.timeoutSeconds(getInteger(props, "timeout-seconds", 300));
        }

        if (props.containsKey("idempotent")) {
            builder.idempotent(getBoolean(props, "idempotent", false));
        }

        if (props.containsKey("capture-output")) {
            builder.captureOutput(getBoolean(props, "capture-output", true));
        }

        return builder.build();
    }

    /**
     * Converts BlockDTO to FileOperationBlock.
     */
    private FileOperationBlock convertFileOperationBlock(BlockDTO dto) {
        Map<String, Object> props = dto.getProperties();

        String operation = getRequiredString(props, "operation", dto.getName());
        FileOperationBlock.FileOperation op = FileOperationBlock.FileOperation.valueOf(operation.toUpperCase());

        FileOperationBlock.Builder builder = FileOperationBlock.builder()
                .name(dto.getName())
                .operation(op);

        // Add operation-specific properties
        switch (op) {
            case CREATE:
            case REPLACE:
                builder.targetPath(getRequiredString(props, "path", dto.getName()));
                if (props.containsKey("content")) {
                    builder.content(getString(props, "content"));
                }
                break;
            case CREATE_MULTIPLE:
                builder.filesVariable(getRequiredString(props, "files", dto.getName()));
                builder.basePath(getRequiredString(props, "base-path", dto.getName()));
                break;
            case COPY:
            case MOVE:
                builder.sourcePath(getRequiredString(props, "source", dto.getName()));
                builder.targetPath(getRequiredString(props, "destination", dto.getName()));
                break;
            case DELETE:
                builder.sourcePath(getRequiredString(props, "path", dto.getName()));
                break;
        }

        return builder.build();
    }

    /**
     * Converts BlockDTO to OpenRewriteBlock.
     */
    private OpenRewriteBlock convertOpenRewriteBlock(BlockDTO dto) {
        Map<String, Object> props = dto.getProperties();

        OpenRewriteBlock.Builder builder = OpenRewriteBlock.builder()
                .name(dto.getName())
                .recipeName(getRequiredString(props, "recipe", dto.getName()));

        // Note: OpenRewriteBlock doesn't support recipe-options in the current
        // implementation
        // Recipe configuration should be done in OpenRewrite configuration files

        return builder.build();
    }

    /**
     * Converts BlockDTO to GraphQueryBlock.
     */
    private GraphQueryBlock convertGraphQueryBlock(BlockDTO dto) {
        Map<String, Object> props = dto.getProperties();

        String queryTypeStr = getRequiredString(props, "query-type", dto.getName());
        GraphQueryBlock.QueryType queryType = GraphQueryBlock.QueryType.valueOf(queryTypeStr.toUpperCase());

        GraphQueryBlock.Builder builder = GraphQueryBlock.builder()
                .name(dto.getName())
                .repository(repository)
                .queryType(queryType);

        if (props.containsKey("node-type")) {
            builder.nodeType(getString(props, "node-type"));
        }

        if (props.containsKey("tags")) {
            Object tagsObj = props.get("tags");
            if (tagsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> tags = (List<String>) tagsObj;
                builder.requiredTags(tags);
            } else if (tagsObj instanceof String) {
                builder.requiredTag((String) tagsObj);
            }
        }

        if (props.containsKey("output-variable")) {
            builder.outputVariable(getString(props, "output-variable"));
        }

        return builder.build();
    }

    /**
     * Converts BlockDTO to AiPromptBlock.
     */
    private AiPromptBlock convertAiPromptBlock(BlockDTO dto) {
        Map<String, Object> props = dto.getProperties();

        AiPromptBlock.Builder builder = AiPromptBlock.builder()
                .name(dto.getName())
                .promptTemplate(getRequiredString(props, "prompt", dto.getName()));

        if (props.containsKey("description")) {
            builder.description(getString(props, "description"));
        }

        if (props.containsKey("output-variable")) {
            builder.outputVariable(getString(props, "output-variable"));
        }

        if (props.containsKey("temperature")) {
            builder.temperature(getDouble(props, "temperature", 0.3));
        }

        if (props.containsKey("max-tokens")) {
            builder.maxTokens(getInteger(props, "max-tokens", 2000));
        }

        return builder.build();
    }

    /**
     * Converts BlockDTO to AiPromptBatchBlock.
     */
    private AiPromptBatchBlock convertAiPromptBatchBlock(BlockDTO dto) {
        Map<String, Object> props = dto.getProperties();

        // AiPromptBatchBlock requires itemsVariableName and promptTemplate
        String itemsVar = getRequiredString(props, "items-variable", dto.getName());

        // Try both "prompt-template" (YAML convention) and "prompt" (fallback)
        String promptTemplate = getString(props, "prompt-template");
        if (promptTemplate == null) {
            promptTemplate = getRequiredString(props, "prompt", dto.getName());
        }

        AiPromptBatchBlock.Builder builder = AiPromptBatchBlock.builder()
                .name(dto.getName())
                .itemsVariableName(itemsVar)
                .promptTemplate(promptTemplate);

        if (props.containsKey("description")) {
            builder.description(getString(props, "description"));
        }

        if (props.containsKey("max-prompts")) {
            builder.maxPrompts(getInteger(props, "max-prompts", -1));
        }

        return builder.build();
    }

    /**
     * Converts BlockDTO to AiAssistedBlock.
     */
    private AiAssistedBlock convertAiAssistedBlock(BlockDTO dto) {
        Map<String, Object> props = dto.getProperties();

        AiAssistedBlock.Builder builder = AiAssistedBlock.builder()
                .name(dto.getName())
                .promptTemplate(getRequiredString(props, "prompt", dto.getName()));

        if (props.containsKey("description")) {
            builder.description(getString(props, "description"));
        }

        if (props.containsKey("output-variable")) {
            builder.outputVariable(getString(props, "output-variable"));
        }

        if (props.containsKey("timeout-seconds")) {
            builder.timeoutSeconds(getInteger(props, "timeout-seconds", 300));
        }

        return builder.build();
    }

    /**
     * Converts BlockDTO to InteractiveValidationBlock.
     */
    private InteractiveValidationBlock convertInteractiveValidationBlock(BlockDTO dto) {
        Map<String, Object> props = dto.getProperties();

        String validationTypeStr = getRequiredString(props, "validation-type", dto.getName());
        InteractiveValidationBlock.ValidationType validationType = InteractiveValidationBlock.ValidationType
                .valueOf(validationTypeStr.toUpperCase().replace("-", "_"));

        InteractiveValidationBlock.Builder builder = InteractiveValidationBlock.builder()
                .name(dto.getName())
                .validationType(validationType);

        if (props.containsKey("message")) {
            builder.message(getString(props, "message"));
        }

        if (props.containsKey("validation-params")) {
            Object paramsObj = props.get("validation-params");
            if (paramsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> params = (List<String>) paramsObj;
                builder.validationParams(params);
            }
        }

        if (props.containsKey("required")) {
            builder.required(getBoolean(props, "required", true));
        }

        return builder.build();
    }

    // Helper methods for safe property extraction

    private String getRequiredString(Map<String, Object> props, String key, String blockName) {
        Object value = props.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Required property '" + key + "' missing in block: " + blockName);
        }
        return value.toString();
    }

    private String getString(Map<String, Object> props, String key) {
        Object value = props.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getInteger(Map<String, Object> props, String key, int defaultValue) {
        Object value = props.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private Double getDouble(Map<String, Object> props, String key, double defaultValue) {
        Object value = props.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private Boolean getBoolean(Map<String, Object> props, String key, boolean defaultValue) {
        Object value = props.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
