package com.analyzer.refactoring.mcp.tool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool for copying Java elements and resources.
 * Maps to JDT processor: org.eclipse.jdt.ui.copyResourcesProcessor
 */
public class CopyElementsTool extends BaseRefactoringTool {
    private final JdtRefactoringService refactoringService;
    
    public CopyElementsTool(JdtRefactoringService refactoringService) { 
        this.refactoringService = refactoringService; 
    }
    
    @Tool(description = "Copy Java elements (classes, packages, resources) to a new location in the project. " +
                        "Creates duplicates of the specified elements at the destination while preserving the originals. " +
                        "Useful for creating template classes, duplicating packages for new features, or backing up code before major changes. " +
                        "The copied elements will maintain their internal structure but may need manual adjustments for naming conflicts.")
    public String copyElements(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") 
            String projectPath,
            
            @ToolParam(description = "List of relative paths from project root to elements to copy (files, folders, packages, or compilation units)") 
            List<String> sourcePaths,
            
            @ToolParam(description = "Relative path from project root to the destination directory where elements should be copied") 
            String destinationPath) {
        try {
            var result = refactoringService.copyElements(projectPath, sourcePaths, destinationPath);
            return toJsonResponse(result);
        } catch (Exception e) { 
            logger.error("Error in copy_elements tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}"; 
        }
    }
}
