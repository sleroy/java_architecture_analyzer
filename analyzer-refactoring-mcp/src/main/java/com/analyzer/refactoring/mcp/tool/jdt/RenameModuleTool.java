package com.analyzer.refactoring.mcp.tool.jdt;

import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Tool for renaming Java modules.
 * Maps to JDT processor: org.eclipse.jdt.ui.renameModuleProcessor
 */

public class RenameModuleTool extends BaseRefactoringTool {
    private final JdtRefactoringService refactoringService;
    
    public RenameModuleTool(JdtRefactoringService refactoringService) { 
        this.refactoringService = refactoringService; 
    }
    
    @Tool(description = "Rename a Java module defined in module-info.java and update all references throughout the project and dependent modules. " +
                        "This updates the module declaration and all requires/exports/opens statements that reference this module. " +
                        "Use this when restructuring Java 9+ modular applications or following module naming conventions.")
    public String renameModule(
            @ToolParam(description = "Absolute path to the Java project root directory containing .project file") 
            String projectPath,
            
            @ToolParam(description = "Current name of the module as declared in module-info.java") 
            String moduleName,
            
            @ToolParam(description = "New name for the module (must follow Java module naming conventions, typically reverse domain notation)") 
            String newName,
            
            @ToolParam(description = "Whether to update all references to this module in requires/exports/opens statements. Defaults to true.", required = false) 
            @Nullable 
            Boolean updateReferences) {
        try {
            var result = refactoringService.renameModule(
                projectPath, 
                moduleName, 
                newName, 
                updateReferences != null ? updateReferences : true);
            return toJsonResponse(result);
        } catch (Exception e) { 
            logger.error("Error in rename_module tool", e);
            return "{\"success\":false,\"messages\":[\"Error: " + e.getMessage() + "\"]}"; 
        }
    }
}
