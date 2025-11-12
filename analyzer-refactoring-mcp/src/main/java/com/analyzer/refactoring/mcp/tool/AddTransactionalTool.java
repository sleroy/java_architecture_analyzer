package com.analyzer.refactoring.mcp.tool;

import com.analyzer.refactoring.mcp.service.OpenRewriteService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool for adding @Transactional annotations to methods based on name patterns.
 * Catalog #6 - High Priority Token Optimization Tool
 * 
 * Token Savings: 1500 → 150 tokens (90% reduction)
 * 
 * Maps to OpenRewrite recipe: AddTransactionalByPatternRecipe
 */
@Component
public class AddTransactionalTool extends BaseRefactoringTool {
    
    private final OpenRewriteService openRewriteService;
    
    public AddTransactionalTool(OpenRewriteService openRewriteService) {
        this.openRewriteService = openRewriteService;
    }
    
    @Tool(description = "Add @Transactional annotations to methods matching name patterns. " +
                        "This tool automatically adds @Transactional to methods that perform data modifications " +
                        "(save*, update*, delete*, create*, etc.) and @Transactional(readOnly=true) to query methods " +
                        "(find*, get*, list*, etc.). This eliminates the need for manual annotation of transactional " +
                        "methods and reduces AI token consumption by 90%.")
    public String addTransactionalToMethods(
            @ToolParam(description = "Absolute path to the Java project root directory") 
            String projectPath,
            
            @ToolParam(description = "List of relative paths from project root to Java classes to process") 
            List<String> classes,
            
            @ToolParam(description = "List of method name patterns that should have @Transactional. " +
                                    "Use * as wildcard. Example: ['save*', 'update*', 'delete*', 'create*']") 
            List<String> methodPatterns,
            
            @ToolParam(description = "List of method name patterns that should have @Transactional(readOnly=true). " +
                                    "Example: ['find*', 'get*', 'list*', 'search*']",
                       required = false) 
            List<String> readOnlyPatterns) {
        try {
            logger.info("Adding @Transactional annotations to {} classes", classes.size());
            
            var result = openRewriteService.addTransactionalByPattern(
                projectPath, 
                classes, 
                methodPatterns != null ? methodPatterns : List.of(),
                readOnlyPatterns != null ? readOnlyPatterns : List.of()
            );
            
            return toJsonResponse(result);
            
        } catch (Exception e) {
            logger.error("Error in add_transactional tool", e);
            return toJsonResponse(java.util.Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
