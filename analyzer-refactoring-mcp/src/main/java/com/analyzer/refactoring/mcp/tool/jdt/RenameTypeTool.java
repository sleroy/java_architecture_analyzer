package com.analyzer.refactoring.mcp.tool.jdt;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Tool for renaming Java types (classes, interfaces, enums, records,
 * annotations).
 * Maps to JDT processor: org.eclipse.jdt.ui.renameTypeProcessor
 */

public class RenameTypeTool extends BaseRefactoringTool {

    private final JdtRefactoringService refactoringService;

    public RenameTypeTool(JdtRefactoringService refactoringService) {
        this.refactoringService = refactoringService;
    }

    @Tool(description = "Rename a Java type (class, interface, enum, record, or annotation) and update all references throughout the project. "
            +
            "Supports updating similar declarations in the type hierarchy. Use this when refactoring type names for better clarity "
            +
            "or to follow naming conventions. All references will be automatically updated if updateReferences is enabled.")
    public String renameType(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") String projectPath,

            @ToolParam(description = "Relative path from project root to the Java source file containing the type to rename") String filePath,

            @ToolParam(description = "Current name of the type to rename (simple name, not fully qualified)") String typeName,

            @ToolParam(description = "New name for the type (simple name, must be a valid Java identifier)") String newName,

            @ToolParam(description = "Whether to update all references to this type throughout the project. Defaults to true.", required = false) @Nullable Boolean updateReferences,

            @ToolParam(description = "Whether to update similar type declarations in the type hierarchy. Defaults to false.", required = false) @Nullable Boolean updateSimilarDeclarations) {
        try {
            var result = refactoringService.renameType(
                    projectPath,
                    filePath,
                    typeName,
                    newName,
                    updateReferences != null ? updateReferences : true,
                    updateSimilarDeclarations != null ? updateSimilarDeclarations : false);
            return toJsonResponse(result);
        } catch (Exception e) {
            logger.error("Error in rename_type tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}";
        }
    }
}
