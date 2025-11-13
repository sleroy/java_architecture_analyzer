package com.analyzer.refactoring.mcp.tool.jdt;

import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Tool for renaming enum constants.
 * Maps to JDT processor: org.eclipse.jdt.ui.renameEnumConstantProcessor
 */

public class RenameEnumConstantTool extends BaseRefactoringTool {
    private final JdtRefactoringService refactoringService;
    
    public RenameEnumConstantTool(JdtRefactoringService refactoringService) { 
        this.refactoringService = refactoringService; 
    }
    
    @Tool(description = "Rename an enum constant and update all references throughout the project. " +
                        "Automatically handles references in switch statements, comparisons, and other usages. " +
                        "Use this when refactoring enum constant names for better clarity or following naming conventions.")
    public String renameEnumConstant(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") 
            String projectPath,
            
            @ToolParam(description = "Relative path from project root to the Java source file containing the enum constant") 
            String filePath,
            
            @ToolParam(description = "Current name of the enum constant to rename") 
            String constantName,
            
            @ToolParam(description = "New name for the enum constant (must follow Java constant naming conventions, typically UPPER_CASE)") 
            String newName,
            
            @ToolParam(description = "Whether to update all references to this enum constant throughout the project. Defaults to true.", required = false) 
            @Nullable 
            Boolean updateReferences) {
        try {
            var result = refactoringService.renameEnumConstant(
                projectPath, 
                filePath, 
                constantName, 
                newName, 
                updateReferences != null ? updateReferences : true);
            return toJsonResponse(result);
        } catch (Exception e) { 
            logger.error("Error in rename_enum_constant tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}"; 
        }
    }
}
