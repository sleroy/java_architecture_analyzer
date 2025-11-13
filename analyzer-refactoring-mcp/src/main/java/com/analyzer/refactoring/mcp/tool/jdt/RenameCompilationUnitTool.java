package com.analyzer.refactoring.mcp.tool.jdt;

import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Tool for renaming Java compilation units (source files).
 * Maps to JDT processor: org.eclipse.jdt.ui.renameCompilationUnitProcessor
 */

public class RenameCompilationUnitTool extends BaseRefactoringTool {
    private final JdtRefactoringService refactoringService;
    
    public RenameCompilationUnitTool(JdtRefactoringService refactoringService) { 
        this.refactoringService = refactoringService; 
    }
    
    @Tool(description = "Rename a Java source file (.java) and automatically update the public type name within the file to match. " +
                        "Updates all references throughout the project. This maintains Java's requirement that the public type name matches the filename. " +
                        "Use this when renaming source files as part of code organization or refactoring.")
    public String renameCompilationUnit(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") 
            String projectPath,
            
            @ToolParam(description = "Relative path from project root to the Java source file to rename (e.g., src/main/java/com/example/OldName.java)") 
            String filePath,
            
            @ToolParam(description = "New name for the file without .java extension (e.g., NewName). Must be a valid Java identifier.") 
            String newName,
            
            @ToolParam(description = "Whether to update all references to the public type in this file throughout the project. Defaults to true.", required = false) 
            @Nullable 
            Boolean updateReferences) {
        try {
            var result = refactoringService.renameCompilationUnit(
                projectPath, 
                filePath, 
                newName, 
                updateReferences != null ? updateReferences : true);
            return toJsonResponse(result);
        } catch (Exception e) { 
            logger.error("Error in rename_compilation_unit tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}"; 
        }
    }
}
