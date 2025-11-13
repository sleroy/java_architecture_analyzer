package com.analyzer.refactoring.mcp.tool.jdt;

import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Tool for renaming Java packages.
 * Maps to JDT processor: org.eclipse.jdt.ui.renamePackageProcessor
 */
@Component
public class RenamePackageTool extends BaseRefactoringTool {
    private final JdtRefactoringService refactoringService;
    
    public RenamePackageTool(JdtRefactoringService refactoringService) { 
        this.refactoringService = refactoringService; 
    }
    
    @Tool(description = "Rename a Java package and update all references, imports, and folder structure throughout the project. " +
                        "This is a comprehensive refactoring that updates package declarations, imports, and moves files to the new package directory. " +
                        "Use this when reorganizing code structure, following naming conventions, or refactoring package hierarchies.")
    public String renamePackage(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") 
            String projectPath,
            
            @ToolParam(description = "Fully qualified name of the package to rename (e.g., com.example.oldname)") 
            String packageName,
            
            @ToolParam(description = "New fully qualified name for the package (e.g., com.example.newname)") 
            String newName,
            
            @ToolParam(description = "Whether to update all references (imports, fully qualified names) to this package throughout the project. Defaults to true.", required = false) 
            @Nullable 
            Boolean updateReferences) {
        try {
            var result = refactoringService.renamePackage(
                projectPath, 
                packageName, 
                newName, 
                updateReferences != null ? updateReferences : true);
            return toJsonResponse(result);
        } catch (Exception e) { 
            logger.error("Error in rename_package tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}"; 
        }
    }
}
