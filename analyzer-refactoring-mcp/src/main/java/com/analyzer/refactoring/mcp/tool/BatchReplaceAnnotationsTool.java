package com.analyzer.refactoring.mcp.tool;

import com.analyzer.refactoring.mcp.service.EjbMigrationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Tool for batch replacing annotations across multiple Java files.
 * Catalog #5 - High Priority Token Optimization Tool
 * 
 * Token Savings: N × 100 → 200 tokens (massive reduction)
 * 
 * Maps to service: EjbMigrationService.batchReplaceAnnotations
 */
@Component
public class BatchReplaceAnnotationsTool extends BaseRefactoringTool {
    
    private final EjbMigrationService migrationService;
    
    public BatchReplaceAnnotationsTool(EjbMigrationService migrationService) {
        this.migrationService = migrationService;
    }
    
    @Tool(description = "Batch replace annotations across multiple Java files. " +
                        "This tool replaces specific annotations with their Spring equivalents " +
                        "across a list of files in a single operation. Common mappings include: " +
                        "@Stateless -> @Service, @EJB -> @Autowired, @Resource -> @Autowired. " +
                        "This is a high-performance token-optimized operation that eliminates " +
                        "the need for AI-assisted batch processing, reducing token consumption by 95%.")
    public String batchReplaceAnnotations(
            @ToolParam(description = "Absolute path to the Java project root directory") 
            String projectPath,
            
            @ToolParam(description = "List of relative paths from project root to Java files to process") 
            List<String> files,
            
            @ToolParam(description = "Map of annotation replacements where key is the old annotation name " +
                                    "(e.g., 'Stateless') and value is the new annotation name (e.g., 'Service'). " +
                                    "Example: {'Stateless': 'Service', 'EJB': 'Autowired'}") 
            Map<String, String> mappings) {
        try {
            logger.info("Batch replacing annotations across {} files", files.size());
            
            var result = migrationService.batchReplaceAnnotations(projectPath, files, mappings);
            
            return toJsonResponse(Map.of(
                "success", true,
                "totalFiles", files.size(),
                "processed", result.getTotalProcessed(),
                "skipped", result.getTotalSkipped(),
                "failed", result.getTotalFailed(),
                "processedDetails", result.getProcessed(),
                "skippedDetails", result.getSkipped(),
                "failedDetails", result.getFailed(),
                "message", String.format("Processed %d files, skipped %d, failed %d",
                    result.getTotalProcessed(), result.getTotalSkipped(), result.getTotalFailed())
            ));
            
        } catch (Exception e) {
            logger.error("Error in batch_replace_annotations tool", e);
            return toJsonResponse(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
