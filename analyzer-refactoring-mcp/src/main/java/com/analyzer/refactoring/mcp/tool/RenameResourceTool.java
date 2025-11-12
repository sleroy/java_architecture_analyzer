package com.analyzer.refactoring.mcp.tool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tool for renaming generic resource files and folders.
 * Maps to JDT processor: org.eclipse.jdt.ui.renameResourceProcessor
 */

public class RenameResourceTool extends BaseRefactoringTool {
    private final JdtRefactoringService refactoringService;
    
    public RenameResourceTool(JdtRefactoringService refactoringService) { 
        this.refactoringService = refactoringService; 
    }
    
    @Tool(description = "Rename a generic resource file or folder in the project (non-Java files like XML, properties, text files, or directories). " +
                        "This does not update references to the resource in code - use this for renaming configuration files, documentation, or other non-code assets. " +
                        "Use this when organizing project resources or following naming conventions for resource files.")
    public String renameResource(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") 
            String projectPath,
            
            @ToolParam(description = "Relative path from project root to the resource file or folder to rename") 
            String resourcePath,
            
            @ToolParam(description = "New name for the resource (filename with extension for files, directory name for folders)") 
            String newName) {
        try {
            var result = refactoringService.renameResource(projectPath, resourcePath, newName);
            return toJsonResponse(result);
        } catch (Exception e) { 
            logger.error("Error in rename_resource tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}"; 
        }
    }
}
