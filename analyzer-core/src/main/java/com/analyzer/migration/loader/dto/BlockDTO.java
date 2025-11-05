package com.analyzer.migration.loader.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Transfer Object for migration plan blocks.
 * Uses a flexible Map-based approach to support different block types
 * with varying properties.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BlockDTO {

    @JsonProperty("type")
    @NotBlank(message = "Block type is required")
    private String type;

    @JsonProperty("name")
    @NotBlank(message = "Block name is required")
    private String name;

    @JsonProperty("description")
    private String description;

    // Flexible properties map for block-specific data
    // This avoids creating separate DTOs for each block type
    private Map<String, Object> properties = new HashMap<>();

    public BlockDTO() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    // Convenience methods for accessing properties
    @JsonProperty("command")
    public void setCommand(String command) {
        properties.put("command", command);
    }

    @JsonProperty("working-directory")
    public void setWorkingDirectory(String workingDirectory) {
        properties.put("working-directory", workingDirectory);
    }

    @JsonProperty("timeout-seconds")
    public void setTimeoutSeconds(Integer timeoutSeconds) {
        properties.put("timeout-seconds", timeoutSeconds);
    }

    @JsonProperty("query-type")
    public void setQueryType(String queryType) {
        properties.put("query-type", queryType);
    }

    @JsonProperty("tags")
    public void setTags(Object tags) {
        properties.put("tags", tags);
    }

    @JsonProperty("output-variable")
    public void setOutputVariable(String outputVariable) {
        properties.put("output-variable", outputVariable);
    }

    @JsonProperty("operation")
    public void setOperation(String operation) {
        properties.put("operation", operation);
    }

    @JsonProperty("path")
    public void setPath(String path) {
        properties.put("path", path);
    }

    @JsonProperty("content")
    public void setContent(String content) {
        properties.put("content", content);
    }

    @JsonProperty("source")
    public void setSource(String source) {
        properties.put("source", source);
    }

    @JsonProperty("destination")
    public void setDestination(String destination) {
        properties.put("destination", destination);
    }

    @JsonProperty("recipe")
    public void setRecipe(String recipe) {
        properties.put("recipe", recipe);
    }

    @JsonProperty("recipe-options")
    public void setRecipeOptions(Map<String, Object> recipeOptions) {
        properties.put("recipe-options", recipeOptions);
    }

    @JsonProperty("prompt")
    public void setPrompt(String prompt) {
        properties.put("prompt", prompt);
    }

    @JsonProperty("temperature")
    public void setTemperature(Double temperature) {
        properties.put("temperature", temperature);
    }

    @JsonProperty("max-tokens")
    public void setMaxTokens(Integer maxTokens) {
        properties.put("max-tokens", maxTokens);
    }

    @JsonProperty("prompts")
    public void setPrompts(Object prompts) {
        properties.put("prompts", prompts);
    }

    @JsonProperty("batch-size")
    public void setBatchSize(Integer batchSize) {
        properties.put("batch-size", batchSize);
    }

    @JsonProperty("validation-type")
    public void setValidationType(String validationType) {
        properties.put("validation-type", validationType);
    }

    @JsonProperty("message")
    public void setMessage(String message) {
        properties.put("message", message);
    }

    @JsonProperty("checkpoints")
    public void setCheckpoints(Object checkpoints) {
        properties.put("checkpoints", checkpoints);
    }

    @JsonProperty("continue-on-failure")
    public void setContinueOnFailure(Boolean continueOnFailure) {
        properties.put("continue-on-failure", continueOnFailure);
    }

    // Git block properties
    @JsonProperty("args")
    public void setArgs(Object args) {
        properties.put("args", args);
    }

    @JsonProperty("idempotent")
    public void setIdempotent(Boolean idempotent) {
        properties.put("idempotent", idempotent);
    }

    @JsonProperty("capture-output")
    public void setCaptureOutput(Boolean captureOutput) {
        properties.put("capture-output", captureOutput);
    }

    @JsonProperty("enable_if")
    public void setEnableIf(String enableIf) {
        properties.put("enable_if", enableIf);
    }

    // AI_PROMPT_BATCH block properties
    @JsonProperty("items-variable")
    public void setItemsVariable(String itemsVariable) {
        properties.put("items-variable", itemsVariable);
    }

    @JsonProperty("prompt-template")
    public void setPromptTemplate(String promptTemplate) {
        properties.put("prompt-template", promptTemplate);
    }

    @JsonProperty("max-prompts")
    public void setMaxPrompts(Integer maxPrompts) {
        properties.put("max-prompts", maxPrompts);
    }

    @JsonProperty("parallel")
    public void setParallel(Boolean parallel) {
        properties.put("parallel", parallel);
    }

    // FILE_OPERATION CREATE_MULTIPLE block properties
    @JsonProperty("files")
    public void setFiles(String files) {
        properties.put("files", files);
    }

    @JsonProperty("base-path")
    public void setBasePath(String basePath) {
        properties.put("base-path", basePath);
    }

    // OpenRewrite block properties
    @JsonProperty("file-paths")
    public void setFilePaths(Object filePaths) {
        properties.put("file-paths", filePaths);
    }

    @JsonProperty("file-pattern")
    public void setFilePattern(String filePattern) {
        properties.put("file-pattern", filePattern);
    }

    @JsonProperty("pattern")
    public void setPattern(String pattern) {
        properties.put("pattern", pattern);
    }

    // Maven block properties
    @JsonProperty("goals")
    public void setGoals(String goals) {
        properties.put("goals", goals);
    }

    @JsonProperty("java-home")
    public void setJavaHome(String javaHome) {
        properties.put("java-home", javaHome);
    }

    @JsonProperty("maven-home")
    public void setMavenHome(String mavenHome) {
        properties.put("maven-home", mavenHome);
    }

    @JsonProperty("maven-opts")
    public void setMavenOpts(String mavenOpts) {
        properties.put("maven-opts", mavenOpts);
    }

    @JsonProperty("offline")
    public void setOffline(Boolean offline) {
        properties.put("offline", offline);
    }

    @JsonProperty("profiles")
    public void setProfiles(String profiles) {
        properties.put("profiles", profiles);
    }

    // Note: "properties" is also a YAML field for Maven -D flags
    // Using "maven-properties" to avoid confusion with the internal properties map
    @JsonProperty("properties")
    public void setMavenProperties(Map<String, String> mavenProperties) {
        properties.put("properties", mavenProperties);
    }

    // AI_ASSISTED_BATCH block properties
    @JsonProperty("input-nodes")
    public void setInputNodes(String inputNodes) {
        properties.put("input-nodes", inputNodes);
    }

    @JsonProperty("progress-message")
    public void setProgressMessage(String progressMessage) {
        properties.put("progress-message", progressMessage);
    }

    @JsonProperty("max-nodes")
    public void setMaxNodes(Integer maxNodes) {
        properties.put("max-nodes", maxNodes);
    }
}
