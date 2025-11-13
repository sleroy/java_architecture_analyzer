package com.analyzer.refactoring.mcp.model;

import javax.script.CompiledScript;
import java.time.Instant;

/**
 * Represents a compiled Groovy script that implements an OpenRewrite visitor.
 * This class encapsulates the compiled script along with metadata about the
 * pattern
 * it was generated to match.
 */
public class OpenRewriteVisitorScript {

    private final String patternDescription;
    private final String nodeType;
    private final String projectPath;
    private final CompiledScript compiledScript;
    private final String sourceCode;
    private final Instant createdAt;
    private final int generationAttempts;

    private OpenRewriteVisitorScript(Builder builder) {
        this.patternDescription = builder.patternDescription;
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
     * Builder for OpenRewriteVisitorScript.
     */
    public static class Builder {
        private String patternDescription;
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

        public OpenRewriteVisitorScript build() {
            if (patternDescription == null || nodeType == null || compiledScript == null) {
                throw new IllegalStateException(
                        "patternDescription, nodeType, and compiledScript are required");
            }
            return new OpenRewriteVisitorScript(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "OpenRewriteVisitorScript{" +
                "pattern='" + patternDescription + '\'' +
                ", nodeType='" + nodeType + '\'' +
                ", projectPath='" + projectPath + '\'' +
                ", attempts=" + generationAttempts +
                ", createdAt=" + createdAt +
                '}';
    }
}
