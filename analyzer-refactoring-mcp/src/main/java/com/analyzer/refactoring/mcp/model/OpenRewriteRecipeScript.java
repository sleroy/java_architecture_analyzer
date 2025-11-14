package com.analyzer.refactoring.mcp.model;

import javax.script.CompiledScript;
import java.time.Instant;

/**
 * Represents a compiled OpenRewrite recipe script.
 * Similar to OpenRewriteVisitorScript but for transformations/recipes.
 * 
 * Contains:
 * - Pattern description (what to find)
 * - Transformation description (what to change)
 * - Target node type
 * - Compiled Groovy script
 * - Source code (for debugging)
 * - Generation metadata
 */
public class OpenRewriteRecipeScript {

    private final String patternDescription;
    private final String transformation;
    private final String nodeType;
    private final String projectPath;
    private final CompiledScript compiledScript;
    private final String sourceCode;
    private final Instant createdAt;
    private final int generationAttempts;

    private OpenRewriteRecipeScript(Builder builder) {
        this.patternDescription = builder.patternDescription;
        this.transformation = builder.transformation;
        this.nodeType = builder.nodeType;
        this.projectPath = builder.projectPath;
        this.compiledScript = builder.compiledScript;
        this.sourceCode = builder.sourceCode;
        this.createdAt = builder.createdAt;
        this.generationAttempts = builder.generationAttempts;
    }

    public String getPatternDescription() {
        return patternDescription;
    }

    public String getTransformation() {
        return transformation;
    }

    public String getNodeType() {
        return nodeType;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public CompiledScript getCompiledScript() {
        return compiledScript;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getGenerationAttempts() {
        return generationAttempts;
    }

    /**
     * Builder for OpenRewriteRecipeScript.
     */
    public static class Builder {
        private String patternDescription;
        private String transformation;
        private String nodeType;
        private String projectPath;
        private CompiledScript compiledScript;
        private String sourceCode;
        private Instant createdAt = Instant.now();
        private int generationAttempts = 1;

        public Builder patternDescription(String patternDescription) {
            this.patternDescription = patternDescription;
            return this;
        }

        public Builder transformation(String transformation) {
            this.transformation = transformation;
            return this;
        }

        public Builder nodeType(String nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Builder projectPath(String projectPath) {
            this.projectPath = projectPath;
            return this;
        }

        public Builder compiledScript(CompiledScript compiledScript) {
            this.compiledScript = compiledScript;
            return this;
        }

        public Builder sourceCode(String sourceCode) {
            this.sourceCode = sourceCode;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder generationAttempts(int generationAttempts) {
            this.generationAttempts = generationAttempts;
            return this;
        }

        public OpenRewriteRecipeScript build() {
            if (patternDescription == null || transformation == null ||
                    nodeType == null || compiledScript == null) {
                throw new IllegalStateException(
                        "patternDescription, transformation, nodeType, and compiledScript are required");
            }
            return new OpenRewriteRecipeScript(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "OpenRewriteRecipeScript{" +
                "pattern='" + patternDescription + '\'' +
                ", transformation='" + transformation + '\'' +
                ", nodeType='" + nodeType + '\'' +
                ", projectPath='" + projectPath + '\'' +
                ", attempts=" + generationAttempts +
                ", createdAt=" + createdAt +
                '}';
    }
}
