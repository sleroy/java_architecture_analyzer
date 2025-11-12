package com.analyzer.refactoring.mcp.tool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool for deleting Java elements and resources.
 * Maps to JDT processor: org.eclipse.jdt.ui.deleteResourcesProcessor
 */

public class DeleteElementsTool extends BaseRefactoringTool {
    private final JdtRefactoringService refactoringService;

    public DeleteElementsTool(JdtRefactoringService refactoringService) {
        this.refactoringService = refactoringService;
    }

    @Tool(description = "Delete Java elements (classes, methods, fields, packages) or resources from the project. " +
                        "This operation is permanent and removes the specified elements from the project structure. " +
                        "Optionally deletes subpackages when deleting a package. " +
                        "Use this with caution when removing obsolete code, unused classes, or deprecated elements.")
    public String deleteElements(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") 
            String projectPath,
            
            @ToolParam(description = "List of relative paths from project root to elements to delete (files, folders, or Java elements)") 
            List<String> paths,
            
            @ToolParam(description = "Whether to delete subpackages when deleting a package. Only applicable when deleting packages. Defaults to false.", required = false) 
            @Nullable 
            Boolean deleteSubpackages) {
        try {
            var result = refactoringService.deleteElements(
                projectPath, 
                paths, 
                deleteSubpackages != null ? deleteSubpackages : false);
            return toJsonResponse(result);
        } catch (Exception e) {
            logger.error("Error in delete_elements tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}";
        }
    }
}
