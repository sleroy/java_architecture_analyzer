package com.analyzer.refactoring.mcp.tool.jdt;

import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Tool for renaming Java fields.
 * Maps to JDT processor: org.eclipse.jdt.ui.renameFieldProcessor
 */

public class RenameFieldTool extends BaseRefactoringTool {
    
    private final JdtRefactoringService refactoringService;
    
    public RenameFieldTool(JdtRefactoringService refactoringService) {
        this.refactoringService = refactoringService;
    }
    
    @Tool(description = "Rename a Java field and update all references throughout the project. " +
                        "Optionally updates associated getter and setter methods to match the new field name. " +
                        "Use this when refactoring field names for better clarity, following naming conventions, or when encapsulation patterns require synchronized getter/setter names.")
    public String renameField(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") 
            String projectPath,
            
            @ToolParam(description = "Relative path from project root to the Java source file containing the field to rename") 
            String filePath,
            
            @ToolParam(description = "Current name of the field to rename (simple name)") 
            String fieldName,
            
            @ToolParam(description = "New name for the field (must be a valid Java identifier)") 
            String newName,
            
            @ToolParam(description = "Whether to update all references to this field throughout the project. Defaults to true.", required = false) 
            @Nullable 
            Boolean updateReferences,
            
            @ToolParam(description = "Whether to rename associated getter and setter methods (e.g., getName/setName). Defaults to false.", required = false) 
            @Nullable 
            Boolean renameGettersSetters) {
        try {
            var result = refactoringService.renameField(
                projectPath, 
                filePath, 
                fieldName, 
                newName, 
                updateReferences != null ? updateReferences : true,
                renameGettersSetters != null ? renameGettersSetters : false);
            return toJsonResponse(result);
        } catch (Exception e) {
            logger.error("Error in rename_field tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}";
        }
    }
}
