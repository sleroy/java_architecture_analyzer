package com.analyzer.migration.plan;

/**
 * Types of migration blocks that can be executed.
 * Each type represents a different category of operation in the migration
 * process.
 */
public enum BlockType {
    /**
     * Execute shell commands (e.g., git operations, file system commands)
     */
    COMMAND,

    /**
     * Execute Git commands with idempotent error handling
     */
    GIT,

    /**
     * File operations (create, copy, move, delete files/directories)
     */
    FILE_OPERATION,

    /**
     * Query the graph database by node types, tags, or properties
     */
    GRAPH_QUERY,

    /**
     * Apply OpenRewrite recipes to filtered code files (batch operation)
     */
    OPENREWRITE,

    /**
     * Generate a single AI prompt for Amazon Q or other AI assistants
     */
    AI_PROMPT,

    /**
     * Generate multiple AI prompts iteratively (one per file/node)
     */
    AI_PROMPT_BATCH,

    /**
     * Execute Amazon Q CLI for AI-assisted tasks with conversation logging
     */
    AI_ASSISTED,

    /**
     * Interactive validation checkpoint requiring human confirmation
     */
    INTERACTIVE_VALIDATION
}
