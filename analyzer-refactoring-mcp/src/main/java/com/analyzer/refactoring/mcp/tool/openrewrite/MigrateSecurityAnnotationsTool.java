package com.analyzer.refactoring.mcp.tool.openrewrite;

import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;

import com.analyzer.refactoring.mcp.service.OpenRewriteService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool for migrating EJB security annotations to Spring Security annotations.
 * Catalog #7 - High Priority Token Optimization Tool
 * 
 * Token Savings: 1000 → 100 tokens (90% reduction)
 * 
 * Maps to OpenRewrite recipe: MigrateSecurityAnnotationsRecipe
 * 
 * Transformations:
 * - @RolesAllowed("ADMIN") → @PreAuthorize("hasRole('ADMIN')")
 * - @PermitAll → @PreAuthorize("permitAll()")
 * - @DenyAll → @PreAuthorize("denyAll()")
 */
@Component
public class MigrateSecurityAnnotationsTool extends BaseRefactoringTool {
    
    private final OpenRewriteService openRewriteService;
    
    public MigrateSecurityAnnotationsTool(OpenRewriteService openRewriteService) {
        this.openRewriteService = openRewriteService;
    }
    
    @Tool(description = "Migrate EJB security annotations to Spring Security annotations. " +
                        "This tool automatically converts @RolesAllowed, @PermitAll, and @DenyAll annotations " +
                        "to their Spring Security @PreAuthorize equivalents. This eliminates manual conversion " +
                        "work and reduces AI token consumption by 90%.")
    public String migrateSecurityAnnotations(
            @ToolParam(description = "Absolute path to the Java project root directory") 
            String projectPath,
            
            @ToolParam(description = "List of relative paths from project root to Java classes to process") 
            List<String> classes) {
        try {
            logger.info("Migrating security annotations in {} classes", classes.size());
            
            var result = openRewriteService.migrateSecurityAnnotations(projectPath, classes);
            
            return toJsonResponse(result);
            
        } catch (Exception e) {
            logger.error("Error in migrate_security_annotations tool", e);
            return toJsonResponse(java.util.Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
