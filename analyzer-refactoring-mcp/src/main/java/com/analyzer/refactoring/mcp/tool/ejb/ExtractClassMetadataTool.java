package com.analyzer.refactoring.mcp.tool.ejb;

import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.refactoring.mcp.security.PathSecurityValidator;
import com.analyzer.refactoring.mcp.service.GraphDatabaseService;
import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;

import com.analyzer.refactoring.mcp.service.EjbMigrationService;
import com.analyzer.refactoring.mcp.service.EjbMigrationService.ClassMetadata;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool for extracting compact metadata from Java classes.
 * 
 * This tool addresses the massive token waste of including full source code in
 * AI prompts.
 * Instead of sending 2000+ tokens of source code, this tool returns only the
 * essential
 * metadata (annotations, fields, methods) in ~200 tokens.
 * 
 * When a graph database is available, it also includes:
 * - JavaClassNode metadata (dependencies, metrics, stereotypes, tags)
 * - ProjectFile metadata (package info, file-level properties)
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
    private final GraphDatabaseService graphDatabaseService;
    private final PathSecurityValidator pathSecurityValidator;

    public ExtractClassMetadataTool(
            EjbMigrationService migrationService,
            GraphDatabaseService graphDatabaseService,
            PathSecurityValidator pathSecurityValidator) {
        this.migrationService = migrationService;
        this.graphDatabaseService = graphDatabaseService;
        this.pathSecurityValidator = pathSecurityValidator;
    }

    @Tool(description = "PREFERRED TOOL FOR JAVA CLASS ANALYSIS: Extract compact metadata from a Java class for assessment, refactoring preparation, or understanding class structure. "
            +
            "Use this tool when you need to: " +
            "1) Assess a class before refactoring or migration, " +
            "2) Know what methods, fields, and annotations a class has, " +
            "3) Understand class structure without loading full source code, " +
            "4) Prepare for migration or refactoring operations, " +
            "5) Analyze class complexity and dependencies. " +
            "Returns structured metadata (annotations, fields, methods, imports, metrics, tags) in token-efficient format (150-300 tokens vs 2000-5000 for full source). "
            +
            "85-95% token reduction enables analysis of more classes within token limits. " +
            "If graph database is available, also includes dependencies, complexity metrics, and classification tags.")
    public String extractClassMetadataCompact(
            @ToolParam(description = "Absolute path to the Java project root directory") String projectPath,

            @ToolParam(description = "Relative path from project root to the Java source file") String filePath,

            @ToolParam(description = "Fully qualified name of the class to extract metadata from") String fullyQualifiedName) {
        try {
            logger.info("Tool called: extract_class_metadata_compact for {}", fullyQualifiedName);

            // Security validation
            pathSecurityValidator.validateProjectPath(projectPath, filePath);

            // Extract AST metadata
            ClassMetadata astMetadata = migrationService.extractClassMetadataCompact(
                    projectPath,
                    filePath,
                    fullyQualifiedName);

            // Build enhanced response with graph metadata if available
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fullyQualifiedName", fullyQualifiedName);
            response.put("astMetadata", astMetadata);

            // Add graph metadata if database is available
            if (graphDatabaseService.isInitialized()) {
                Map<String, Object> graphMetadata = new HashMap<>();

                // Find JavaClassNode
                Optional<JavaClassNode> classNode = graphDatabaseService.findClassNode(fullyQualifiedName);
                if (classNode.isPresent()) {
                    JavaClassNode node = classNode.get();
                    Map<String, Object> classNodeData = new HashMap<>();
                    classNodeData.put("sourceType", node.getSourceType());
                    classNodeData.put("methodCount", node.getMethodCount());
                    classNodeData.put("fieldCount", node.getFieldCount());
                    classNodeData.put("metrics", node.getMetrics());
                    classNodeData.put("tags", node.getTags());
                    graphMetadata.put("classNode", classNodeData);
                }

                // Find ProjectFile
                Optional<ProjectFile> projectFile = graphDatabaseService.findProjectFile(filePath);
                if (projectFile.isPresent()) {
                    ProjectFile file = projectFile.get();
                    Map<String, Object> fileData = new HashMap<>();
                    fileData.put("fileName", file.getFileName());
                    fileData.put("extension", file.getFileExtension());
                    fileData.put("relativePath", file.getRelativePath());
                    fileData.put("packageName", file.getProperty("packageName"));
                    fileData.put("tags", file.getTags());
                    fileData.put("properties", file.getProperties());
                    graphMetadata.put("projectFile", fileData);
                }

                if (!graphMetadata.isEmpty()) {
                    response.put("graphMetadata", graphMetadata);
                    logger.debug("Added graph metadata for class: {}", fullyQualifiedName);
                } else {
                    response.put("graphMetadata", null);
                    response.put("graphNote", "Class not found in graph database");
                }
            } else {
                response.put("graphMetadata", null);
                response.put("graphNote", "Graph database not available - run analyzer first to enable");
            }

            return toJsonResponse(response);
        } catch (SecurityException e) {
            logger.error("Security violation in extract_class_metadata_compact", e);
            return "{\"success\":false,\"errors\":[\"Security error: " + e.getMessage() + "\"]}";
        } catch (Exception e) {
            logger.error("Error in extract_class_metadata_compact tool", e);
            return "{\"success\":false,\"errors\":[\"Error: " + e.getMessage() + "\"]}";
        }
    }
}
