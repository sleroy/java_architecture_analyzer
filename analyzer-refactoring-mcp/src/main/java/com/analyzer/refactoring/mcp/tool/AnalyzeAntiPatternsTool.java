package com.analyzer.refactoring.mcp.tool;

import com.analyzer.refactoring.mcp.service.AnalysisService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool for analyzing EJB classes for anti-patterns.
 * Catalog #11 - High-Impact Analysis Tool
 * 
 * Token Savings: Filters 80% of classes from AI prompts
 * 
 * This tool identifies classes with migration issues that need special handling,
 * allowing deterministic tools to handle the remaining 80% automatically.
 */
@Component
public class AnalyzeAntiPatternsTool extends BaseRefactoringTool {
    
    private final AnalysisService analysisService;
    
    public AnalyzeAntiPatternsTool(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }
    
    @Tool(description = "Analyze EJB classes for anti-patterns and migration issues. " +
                        "This tool identifies classes with problematic patterns such as mutable state in stateless beans, " +
                        "factory patterns, non-thread-safe code, and improper resource management. By pre-filtering classes, " +
                        "only 20% with actual issues need AI assistance, while 80% can be migrated deterministically. " +
                        "This dramatically reduces token consumption and focuses AI efforts where they're truly needed.")
    public String analyzeAntiPatterns(
            @ToolParam(description = "Absolute path to the Java project root directory") 
            String projectPath,
            
            @ToolParam(description = "List of relative paths from project root to EJB classes to analyze") 
            List<String> classes) {
        try {
            logger.info("Analyzing {} classes for anti-patterns", classes.size());
            
            var result = analysisService.analyzeAntiPatterns(projectPath, classes);
            
            return toJsonResponse(java.util.Map.of(
                "success", true,
                "totalClasses", classes.size(),
                "problematicClasses", result.getTotalProblematic(),
                "cleanClasses", result.getTotalClean(),
                "skipped", result.getTotalSkipped(),
                "failed", result.getTotalFailed(),
                "problems", result.getProblematicClasses(),
                "cleanClassList", result.getCleanClasses(),
                "message", String.format("Found %d problematic classes out of %d analyzed (%.1f%% can be migrated deterministically)",
                    result.getTotalProblematic(), classes.size(), 
                    (result.getTotalClean() * 100.0 / classes.size()))
            ));
            
        } catch (Exception e) {
            logger.error("Error in analyze_anti_patterns tool", e);
            return toJsonResponse(java.util.Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
