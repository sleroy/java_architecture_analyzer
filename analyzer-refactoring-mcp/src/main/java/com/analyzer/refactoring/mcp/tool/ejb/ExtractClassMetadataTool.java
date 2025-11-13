package com.analyzer.refactoring.mcp.tool.ejb;

import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;

import com.analyzer.refactoring.mcp.service.EjbMigrationService;
import com.analyzer.refactoring.mcp.service.EjbMigrationService.ClassMetadata;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tool for extracting compact metadata from Java classes.
 * 
 * This tool addresses the massive token waste of including full source code in AI prompts.
 * Instead of sending 2000+ tokens of source code, this tool returns only the essential
 * metadata (annotations, fields, methods) in ~200 tokens.
 * 
 * Token Reduction Example:
 * - Without tool: Full source code = 2000-5000 tokens per class
 * - With tool: Compact metadata JSON = 150-300 tokens per class
 * - Savings: 85-95% reduction
 * 
 * Use cases:
 * - AI needs to understand class structure for decision-making
 * - Analyzing dependencies between classes
 * - Identifying anti-patterns without full source
 * - Planning migrations based on class metadata
 */
public class ExtractClassMetadataTool extends BaseRefactoringTool {
    
    private final EjbMigrationService migrationService;
    
    public ExtractClassMetadataTool(EjbMigrationService migrationService) {
        this.migrationService = migrationService;
    }
    
    @Tool(description = "Extract compact metadata from a Java class without returning full source code. " +
                        "Returns essential information in a token-efficient format: annotations, fields, methods, imports. " +
                        "This tool dramatically reduces token usage by returning structured metadata instead of full source code, " +
                        "enabling AI to make decisions with 85-95% fewer tokens. " +
                        "Use this when you need to understand class structure for decision-making without loading full source.")
    public String extractClassMetadataCompact(
            @ToolParam(description = "Absolute path to the Java project root directory") 
            String projectPath,
            
            @ToolParam(description = "Relative path from project root to the Java source file") 
            String filePath,
            
            @ToolParam(description = "Fully qualified name of the class to extract metadata from") 
            String fullyQualifiedName) {
        try {
            logger.info("Tool called: extract_class_metadata_compact for {}", fullyQualifiedName);
            
            ClassMetadata metadata = migrationService.extractClassMetadataCompact(
                projectPath,
                filePath,
                fullyQualifiedName
            );
            
            return toJsonResponse(metadata);
        } catch (Exception e) {
            logger.error("Error in extract_class_metadata_compact tool", e);
            return "{\"success\":false,\"errors\":[\"Error: " + e.getMessage() + "\"]}";
        }
    }
}
