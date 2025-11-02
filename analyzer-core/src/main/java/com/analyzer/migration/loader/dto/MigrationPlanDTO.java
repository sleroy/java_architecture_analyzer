package com.analyzer.migration.loader.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root Data Transfer Object for migration plan YAML structure.
 * Represents the complete migration plan with all nested components.
 */
public class MigrationPlanDTO {

    @JsonProperty("migration-plan")
    @NotNull(message = "migration-plan root is required")
    @Valid
    private PlanRootDTO planRoot;

    public MigrationPlanDTO() {
    }

    public PlanRootDTO getPlanRoot() {
        return planRoot;
    }

    public void setPlanRoot(PlanRootDTO planRoot) {
        this.planRoot = planRoot;
    }

    /**
     * Root plan structure containing metadata and phases.
     */
    public static class PlanRootDTO {

        @JsonProperty("name")
        @NotBlank(message = "Plan name is required")
        private String name;

        @JsonProperty("version")
        @NotBlank(message = "Plan version is required")
        private String version;

        @JsonProperty("description")
        private String description;

        @JsonProperty("includes")
        private List<String> includes = new ArrayList<>();

        @JsonProperty("metadata")
        @Valid
        private MetadataDTO metadata;

        @JsonProperty("variables")
        private Map<String, String> variables = new HashMap<>();

        @JsonProperty("phases")
        @Valid
        private List<PhaseDTO> phases = new ArrayList<>();

        public PlanRootDTO() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public MetadataDTO getMetadata() {
            return metadata;
        }

        public void setMetadata(MetadataDTO metadata) {
            this.metadata = metadata;
        }

        public Map<String, String> getVariables() {
            return variables;
        }

        public void setVariables(Map<String, String> variables) {
            this.variables = variables;
        }

        public List<PhaseDTO> getPhases() {
            return phases;
        }

        public void setPhases(List<PhaseDTO> phases) {
            this.phases = phases;
        }

        public List<String> getIncludes() {
            return includes;
        }

        public void setIncludes(List<String> includes) {
            this.includes = includes;
        }
    }

    /**
     * Metadata information for the migration plan.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetadataDTO {

        @JsonProperty("author")
        private String author;

        @JsonProperty("created-date")
        private String createdDate;

        @JsonProperty("last-modified")
        private String lastModified;

        @JsonProperty("source-platform")
        private String sourcePlatform;

        @JsonProperty("target-platform")
        private String targetPlatform;

        @JsonProperty("tags")
        private List<String> tags = new ArrayList<>();

        public MetadataDTO() {
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getCreatedDate() {
            return createdDate;
        }

        public void setCreatedDate(String createdDate) {
            this.createdDate = createdDate;
        }

        public String getLastModified() {
            return lastModified;
        }

        public void setLastModified(String lastModified) {
            this.lastModified = lastModified;
        }

        public String getSourcePlatform() {
            return sourcePlatform;
        }

        public void setSourcePlatform(String sourcePlatform) {
            this.sourcePlatform = sourcePlatform;
        }

        public String getTargetPlatform() {
            return targetPlatform;
        }

        public void setTargetPlatform(String targetPlatform) {
            this.targetPlatform = targetPlatform;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    /**
     * Phase definition containing tasks.
     */
    public static class PhaseDTO {

        @JsonProperty("id")
        @NotBlank(message = "Phase ID is required")
        private String id;

        @JsonProperty("name")
        @NotBlank(message = "Phase name is required")
        private String name;

        @JsonProperty("description")
        private String description;

        @JsonProperty("tasks")
        @NotEmpty(message = "At least one task is required per phase")
        @Valid
        private List<TaskDTO> tasks = new ArrayList<>();

        public PhaseDTO() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

        public List<TaskDTO> getTasks() {
            return tasks;
        }

        public void setTasks(List<TaskDTO> tasks) {
            this.tasks = tasks;
        }
    }

    /**
     * Task definition containing blocks.
     */
    public static class TaskDTO {

        @JsonProperty("id")
        @NotBlank(message = "Task ID is required")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        @JsonProperty("type")
        private String type;

        @JsonProperty("pattern-tag")
        private String patternTag;

        @JsonProperty("depends-on")
        private List<String> dependsOn = new ArrayList<>();

        @JsonProperty("blocks")
        @NotEmpty(message = "At least one block is required per task")
        @Valid
        private List<BlockDTO> blocks = new ArrayList<>();

        public TaskDTO() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<String> getDependsOn() {
            return dependsOn;
        }

        public void setDependsOn(List<String> dependsOn) {
            this.dependsOn = dependsOn;
        }

        public String getPatternTag() {
            return patternTag;
        }

        public void setPatternTag(String patternTag) {
            this.patternTag = patternTag;
        }

        public List<BlockDTO> getBlocks() {
            return blocks;
        }

        public void setBlocks(List<BlockDTO> blocks) {
            this.blocks = blocks;
        }
    }
}
