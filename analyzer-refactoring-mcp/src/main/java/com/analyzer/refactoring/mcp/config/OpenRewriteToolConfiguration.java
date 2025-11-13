package com.analyzer.refactoring.mcp.config;

import com.analyzer.refactoring.mcp.service.EjbMigrationService;
import com.analyzer.refactoring.mcp.service.GroovyScriptExecutionService;
import com.analyzer.refactoring.mcp.service.GroovyScriptGenerationService;
import com.analyzer.refactoring.mcp.service.OpenRewriteExecutionService;
import com.analyzer.refactoring.mcp.service.OpenRewriteService;
import com.analyzer.refactoring.mcp.service.PatternMatcherAgent;
import com.analyzer.refactoring.mcp.service.VisitorScriptCache;
import com.analyzer.refactoring.mcp.service.VisitorTemplateService;
import com.analyzer.refactoring.mcp.tool.openrewrite.AddTransactionalTool;
import com.analyzer.refactoring.mcp.tool.openrewrite.BatchReplaceAnnotationsTool;
import com.analyzer.refactoring.mcp.tool.openrewrite.ConvertToConstructorInjectionTool;
import com.analyzer.refactoring.mcp.tool.openrewrite.MigrateSecurityAnnotationsTool;
import com.analyzer.refactoring.mcp.tool.openrewrite.RemoveEjbInterfaceTool;
import com.analyzer.refactoring.mcp.tool.openrewrite.SearchJavaPatternTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenRewrite-based refactoring tools.
 * These tools provide high-performance, deterministic code transformations
 * using OpenRewrite.
 */
@Configuration
public class OpenRewriteToolConfiguration {

    @Bean
    public AddTransactionalTool addTransactionalTool(final OpenRewriteService openRewriteService) {
        return new AddTransactionalTool(openRewriteService);
    }

    @Bean
    public BatchReplaceAnnotationsTool batchReplaceAnnotationsTool(final EjbMigrationService migrationService) {
        return new BatchReplaceAnnotationsTool(migrationService);
    }

    @Bean
    public ConvertToConstructorInjectionTool convertToConstructorInjectionTool(
            final OpenRewriteService openRewriteService) {
        return new ConvertToConstructorInjectionTool(openRewriteService);
    }

    @Bean
    public MigrateSecurityAnnotationsTool migrateSecurityAnnotationsTool(final OpenRewriteService openRewriteService) {
        return new MigrateSecurityAnnotationsTool(openRewriteService);
    }

    @Bean
    public RemoveEjbInterfaceTool removeEjbInterfaceTool(final OpenRewriteService openRewriteService) {
        return new RemoveEjbInterfaceTool(openRewriteService);
    }

    @Bean
    public SearchJavaPatternTool searchJavaPatternTool(
            final VisitorScriptCache scriptCache,
            final GroovyScriptGenerationService scriptGenerator,
            final GroovyScriptExecutionService scriptExecutor,
            final OpenRewriteExecutionService openRewriteExecutor,
            final VisitorTemplateService templateService,
            final PatternMatcherAgent patternMatcher) {
        return new SearchJavaPatternTool(scriptCache, scriptGenerator, scriptExecutor, openRewriteExecutor,
                templateService, patternMatcher);
    }
}
