package com.analyzer.refactoring.mcp.tool.jdt;

import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tool for renaming Java source folders.
 * Maps to JDT processor: org.eclipse.jdt.ui.renameSourceFolderProcessor
 */

public class RenameSourceFolderTool extends BaseRefactoringTool {
    private final JdtRefactoringService refactoringService;
    
    public RenameSourceFolderTool(JdtRefactoringService refactoringService) { 
        this.refactoringService = refactoringService; 
    }
    
    @Tool(description = "Rename a Java source folder (e.g., src/main/java, src/test/java) and automatically update .classpath entries. " +
                        "This updates the project configuration to reflect the new source folder location. " +
                        "Use this when reorganizing project structure or following Maven/Gradle conventions for source folder naming.")
    public String renameSourceFolder(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") 
            String projectPath,
            
            @ToolParam(description = "Relative path from project root to the source folder to rename (e.g., src/main/java)") 
            String folderPath,
            
            @ToolParam(description = "New name for the source folder (path segment, e.g., java-sources)") 
            String newName) {
        try {
            var result = refactoringService.renameSourceFolder(projectPath, folderPath, newName);
            return toJsonResponse(result);
        } catch (Exception e) { 
            logger.error("Error in rename_source_folder tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}"; 
        }
    }
}
