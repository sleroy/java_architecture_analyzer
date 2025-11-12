package com.analyzer.refactoring.mcp.tool;

import com.analyzer.refactoring.mcp.service.OpenRewriteService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool for removing EJB interfaces (Home, Remote, Local, LocalHome).
 * Catalog #4 - High Priority Token Optimization Tool
 * 
 * Token Savings: 50 × 100 = 5000 → 200 tokens (96% reduction)
 * 
 * Maps to OpenRewrite recipe: RemoveEjbInterfaceRecipe
 * 
 * Operations:
 * - Batch delete Home/Remote/Local/LocalHome interfaces
 * - Remove references to deleted interfaces
 * - Update imports in dependent classes
 */
@Component
public class RemoveEjbInterfaceTool extends BaseRefactoringTool {
    
    private final OpenRewriteService openRewriteService;
    
    public RemoveEjbInterfaceTool(OpenRewriteService openRewriteService) {
        this.openRewriteService = openRewriteService;
    }
    
    @Tool(description = "Remove EJB interfaces and update references. " +
                        "This tool batch removes EJB interface files (Home, Remote, Local, LocalHome) and " +
                        "automatically updates all references in dependent classes. This is essential for cleaning " +
                        "up EJB artifacts after migration to Spring, where these interfaces are no longer needed. " +
                        "For large projects with 50+ interfaces, this reduces token consumption by 96%.")
    public String removeEjbInterfaces(
            @ToolParam(description = "Absolute path to the Java project root directory") 
            String projectPath,
            
            @ToolParam(description = "List of relative paths from project root to EJB interface files to remove") 
            List<String> interfaceFiles,
            
            @ToolParam(description = "Whether to update references in dependent classes. Defaults to true.",
                       required = false) 
            Boolean updateReferences) {
        try {
            logger.info("Removing {} EJB interfaces", interfaceFiles.size());
            
            boolean doUpdateRefs = updateReferences != null ? updateReferences : true;
            var result = openRewriteService.removeEjbInterfaces(projectPath, interfaceFiles, doUpdateRefs);
            
            return toJsonResponse(result);
            
        } catch (Exception e) {
            logger.error("Error in remove_ejb_interfaces tool", e);
            return toJsonResponse(java.util.Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
