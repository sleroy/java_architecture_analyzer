package com.analyzer.refactoring.mcp.tool.openrewrite;

import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;

import com.analyzer.refactoring.mcp.service.OpenRewriteService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool for converting @EJB field injection to constructor injection.
 * Catalog #8 - High Priority Token Optimization Tool
 * 
 * Token Savings: 1500 → 150 tokens (90% reduction)
 * 
 * Maps to OpenRewrite recipe: FieldToConstructorInjectionRecipe
 * 
 * Transformations:
 * - Convert @EJB annotated fields to constructor parameters
 * - Make fields final
 * - Generate/update constructor with all dependencies
 * - Remove field annotations
 */
@Component
public class ConvertToConstructorInjectionTool extends BaseRefactoringTool {
    
    private final OpenRewriteService openRewriteService;
    
    public ConvertToConstructorInjectionTool(OpenRewriteService openRewriteService) {
        this.openRewriteService = openRewriteService;
    }
    
    @Tool(description = "Convert @EJB field injection to constructor injection. " +
                        "This tool automatically refactors classes to use constructor injection instead of field " +
                        "injection, following Spring best practices. It converts @EJB annotated fields to constructor " +
                        "parameters, makes fields final, and generates the appropriate constructor. This is a critical " +
                        "step in EJB to Spring migration that eliminates manual refactoring work and reduces AI token " +
                        "consumption by 90%.")
    public String convertToConstructorInjection(
            @ToolParam(description = "Absolute path to the Java project root directory") 
            String projectPath,
            
            @ToolParam(description = "List of relative paths from project root to Java classes to process") 
            List<String> classes) {
        try {
            logger.info("Converting to constructor injection in {} classes", classes.size());
            
            var result = openRewriteService.convertToConstructorInjection(projectPath, classes);
            
            return toJsonResponse(result);
            
        } catch (Exception e) {
            logger.error("Error in convert_to_constructor_injection tool", e);
            return toJsonResponse(java.util.Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
