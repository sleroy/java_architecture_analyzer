package com.analyzer.refactoring.mcp.tool.ejb;

import com.analyzer.refactoring.mcp.tool.BaseRefactoringTool;

import com.analyzer.refactoring.mcp.service.EjbMigrationService;
import com.analyzer.refactoring.mcp.service.EjbMigrationService.MigrationResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;

/**
 * MCP tool for migrating stateless EJB beans to Spring @Service components.
 * 
 * This tool dramatically reduces token usage by performing mechanical transformations
 * that would otherwise require large AI prompts. Instead of sending 2000+ tokens
 * describing how to migrate an EJB, the AI can simply call this tool with ~100 tokens.
 * 
 * Token Reduction Example:
 * - Without tool: 2500 tokens/class × 100 classes = 250,000 tokens
 * - With tool: 150 tokens/class × 100 classes = 15,000 tokens
 * - Savings: 94% reduction (235,000 tokens saved)
 */
public class MigrateStatelessEjbTool extends BaseRefactoringTool {
    
    private final EjbMigrationService migrationService;
    
    public MigrateStatelessEjbTool(EjbMigrationService migrationService) {
        this.migrationService = migrationService;
    }
    
    @Tool(description = "Migrate a stateless session bean to a Spring @Service component. " +
                        "Performs the following transformations automatically: " +
                        "Replaces @Stateless with @Service, removes @Local and @Remote interface annotations, " +
                        "converts @EJB field injection to constructor injection, " +
                        "adds @Transactional to transactional methods (save*, update*, delete*, etc.), " +
                        "updates imports (removes javax.ejb.*, adds org.springframework.*). " +
                        "This tool eliminates the need for large AI prompts describing EJB migration steps, " +
                        "reducing token consumption by ~94% for batch migrations.")
    public String migrateStatelessEjbToService(
            @ToolParam(description = "Absolute path to the Java project root directory")
            String projectPath,
            
            @ToolParam(description = "Relative path from project root to the source Java file")
            String sourcePath,
            
            @ToolParam(description = "Fully qualified name of the EJB class to migrate")
            String fullyQualifiedName,
            
            @ToolParam(description = "Relative path where the migrated file should be written")
            String targetPath,
            
            @ToolParam(description = "Whether to add @Transactional annotations to transactional methods. Defaults to true.", required = false)
            @Nullable
            Boolean addTransactional,
            
            @ToolParam(description = "Whether to convert @EJB fields to constructor injection. Defaults to true.", required = false)
            @Nullable
            Boolean convertToConstructorInjection) {
        try {
            logger.info("Tool called: migrate_stateless_ejb_to_service for {}", fullyQualifiedName);
            
            MigrationResult result = migrationService.migrateStatelessEjbToService(
                projectPath,
                sourcePath,
                fullyQualifiedName,
                targetPath,
                addTransactional != null ? addTransactional : true,
                convertToConstructorInjection != null ? convertToConstructorInjection : true
            );
            
            return toJsonResponse(result);
        } catch (Exception e) {
            logger.error("Error in migrate_stateless_ejb_to_service tool", e);
            return "{\"success\":false,\"errors\":[\"Error: " + e.getMessage() + "\"]}";
        }
    }
}
