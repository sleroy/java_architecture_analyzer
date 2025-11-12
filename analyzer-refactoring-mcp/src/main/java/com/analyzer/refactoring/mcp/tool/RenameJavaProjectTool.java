package com.analyzer.refactoring.mcp.tool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tool for renaming Java projects.
 * Maps to JDT processor: org.eclipse.jdt.ui.renameJavaProjectProcessor
 */

public class RenameJavaProjectTool extends BaseRefactoringTool {
    private final JdtRefactoringService refactoringService;
    
    public RenameJavaProjectTool(JdtRefactoringService refactoringService) { 
        this.refactoringService = refactoringService; 
    }
    
    @Tool(description = "Rename a Java project by updating the .project file and associated Eclipse metadata. " +
                        "This changes the project's display name in the workspace but does not modify the actual directory name on the filesystem. " +
                        "Use this when renaming projects within an Eclipse-based workspace structure.")
    public String renameJavaProject(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") 
            String projectPath,
            
            @ToolParam(description = "Current name of the project as defined in the .project file") 
            String projectName,
            
            @ToolParam(description = "New name for the project (must be a valid project identifier)") 
            String newName) {
        try {
            var result = refactoringService.renameJavaProject(projectPath, projectName, newName);
            return toJsonResponse(result);
        } catch (Exception e) { 
            logger.error("Error in rename_java_project tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}"; 
        }
    }
}
