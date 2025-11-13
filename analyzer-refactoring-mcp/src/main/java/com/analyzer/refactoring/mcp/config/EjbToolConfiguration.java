package com.analyzer.refactoring.mcp.config;

import com.analyzer.refactoring.mcp.service.AnalysisService;
import com.analyzer.refactoring.mcp.service.EjbMigrationService;
import com.analyzer.refactoring.mcp.tool.ejb.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for EJB migration and analysis tools.
 * These tools support migration from EJB to Spring and provide analysis
 * capabilities.
 */
@Configuration
public class EjbToolConfiguration {

    @Bean
    public EjbMigrationService ejbMigrationService() {
        return new EjbMigrationService();
    }

    @Bean
    public ExtractClassMetadataTool extractClassMetadataTool(final EjbMigrationService migrationService) {
        return new ExtractClassMetadataTool(migrationService);
    }

    @Bean
    public MigrateStatelessEjbTool migrateStatelessEjbTool(final EjbMigrationService migrationService) {
        return new MigrateStatelessEjbTool(migrationService);
    }

    @Bean
    public AnalyzeAntiPatternsTool analyzeAntiPatternsTool(final AnalysisService analysisService) {
        return new AnalyzeAntiPatternsTool(analysisService);
    }

    @Bean
    public GetDependencyGraphTool getDependencyGraphTool(final AnalysisService analysisService) {
        return new GetDependencyGraphTool(analysisService);
    }
}
