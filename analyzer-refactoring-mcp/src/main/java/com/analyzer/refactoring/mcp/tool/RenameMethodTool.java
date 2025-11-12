package com.analyzer.refactoring.mcp.tool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Tool for renaming Java methods.
 * Maps to JDT processor: org.eclipse.jdt.ui.renameMethodProcessor
 */

public class RenameMethodTool extends BaseRefactoringTool {
    
    private final JdtRefactoringService refactoringService;
    
    public RenameMethodTool(JdtRefactoringService refactoringService) {
        this.refactoringService = refactoringService;
    }
    
    @Tool(description = "Rename a Java method and update all invocations throughout the project. " +
                        "Supports virtual method hierarchies, automatically handling overridden methods in subclasses and implementations. " +
                        "Use this when refactoring method names for better clarity, fixing typos, or following naming conventions.")
    public String renameMethod(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") 
            String projectPath,
            
            @ToolParam(description = "Relative path from project root to the Java source file containing the method to rename") 
            String filePath,
            
            @ToolParam(description = "Current name of the method to rename (simple name, not including parameters)") 
            String methodName,
            
            @ToolParam(description = "New name for the method (must be a valid Java identifier)") 
            String newName,
            
            @ToolParam(description = "Whether to update all references (invocations) to this method throughout the project. Defaults to true.", required = false) 
            @Nullable 
            Boolean updateReferences) {
        try {
            var result = refactoringService.renameMethod(
                projectPath, 
                filePath, 
                methodName, 
                newName, 
                updateReferences != null ? updateReferences : true);
            return toJsonResponse(result);
        } catch (Exception e) {
            logger.error("Error in rename_method tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}";
        }
    }
}
