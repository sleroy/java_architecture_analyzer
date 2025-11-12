package com.analyzer.refactoring.mcp.tool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool for moving Java elements and resources.
 * Maps to JDT processor: org.eclipse.jdt.ui.moveResourcesProcessor
 */

public class MoveElementsTool extends BaseRefactoringTool {
    private final JdtRefactoringService refactoringService;
    
    public MoveElementsTool(JdtRefactoringService refactoringService) { 
        this.refactoringService = refactoringService; 
    }
    
    @Tool(description = "Move Java elements (packages, classes, resources) to a new location in the project and update all references. " +
                        "This relocates the specified elements from their current location to the destination, automatically updating imports, " +
                        "package declarations, and references throughout the project. Useful for reorganizing project structure, " +
                        "consolidating related code, or following package naming conventions.")
    public String moveElements(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") 
            String projectPath,
            
            @ToolParam(description = "List of relative paths from project root to elements to move (files, folders, packages, or compilation units)") 
            List<String> sourcePaths,
            
            @ToolParam(description = "Relative path from project root to the destination directory where elements should be moved") 
            String destinationPath,
            
            @ToolParam(description = "Whether to update all references (imports, package declarations) to moved elements throughout the project. Defaults to true.", required = false) 
            @Nullable 
            Boolean updateReferences) {
        try {
            var result = refactoringService.moveElements(
                projectPath, 
                sourcePaths, 
                destinationPath, 
                updateReferences != null ? updateReferences : true);
            return toJsonResponse(result);
        } catch (Exception e) { 
            logger.error("Error in move_elements tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}"; 
        }
    }
}
