package com.analyzer.refactoring.mcp.config;


import com.analyzer.refactoring.mcp.tool.ejb.*;
import com.analyzer.refactoring.mcp.tool.jdt.*;
import com.analyzer.refactoring.mcp.tool.openrewrite.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;


/**
 * Configuration that aggregates all MCP tools and provides them as a list.
 * This configuration collects tools from JDT, EJB, and OpenRewrite
 * configurations
 * and makes them available to the MCP server.
 */
@Configuration
public class ToolAggregationConfiguration {

    /**
     * Aggregates all refactoring and analysis tools into a list.
     * 
     * Currently active tools:
     * - ExtractClassMetadataTool: Extract metadata from Java classes
     * - MigrateStatelessEjbTool: Migrate stateless EJB beans to Spring components
     * - SearchJavaPatternTool: Search for Java patterns using OpenRewrite visitors
     * - AnalyzeAntiPatternsTool: Analyze code for anti-patterns
     * - GetDependencyGraphTool: Generate dependency graphs
     * - AddTransactionalTool: Add @Transactional annotations
     * - BatchReplaceAnnotationsTool: Batch replace annotations
     * - ConvertToConstructorInjectionTool: Convert to constructor injection
     * - MigrateSecurityAnnotationsTool: Migrate security annotations
     * - RemoveEjbInterfaceTool: Remove EJB interfaces
     * 
     * Commented out tools (JDT rename/refactoring operations):
     * These are currently disabled but can be enabled by uncommenting them.
     * 
     * @param renameTypeTool                    Tool for renaming types
     * @param renameMethodTool                  Tool for renaming methods
     * @param renameFieldTool                   Tool for renaming fields
     * @param renamePackageTool                 Tool for renaming packages
     * @param renameCompilationUnitTool         Tool for renaming compilation units
     * @param renameJavaProjectTool             Tool for renaming Java projects
     * @param renameEnumConstantTool            Tool for renaming enum constants
     * @param renameModuleTool                  Tool for renaming modules
     * @param renameResourceTool                Tool for renaming resources
     * @param renameSourceFolderTool            Tool for renaming source folders
     * @param deleteElementsTool                Tool for deleting elements
     * @param copyElementsTool                  Tool for copying elements
     * @param moveElementsTool                  Tool for moving elements
     * @param moveStaticMembersTool             Tool for moving static members
     * @param extractClassMetadataTool          Tool for extracting class metadata
     * @param migrateStatelessEjbTool           Tool for migrating stateless EJBs
     * @param searchJavaPatternTool             Tool for searching Java patterns
     * @param analyzeAntiPatternsTool           Tool for analyzing anti-patterns
     * @param getDependencyGraphTool            Tool for getting dependency graphs
     * @param addTransactionalTool              Tool for adding @Transactional
     * @param batchReplaceAnnotationsTool       Tool for batch annotation
     *                                          replacement
     * @param convertToConstructorInjectionTool Tool for converting to constructor
     *                                          injection
     * @param migrateSecurityAnnotationsTool    Tool for migrating security
     *                                          annotations
     * @param removeEjbInterfaceTool            Tool for removing EJB interfaces
     * @return List of all available tools
     */
    @Bean
    public List<ToolCallback> refactoringTools(
            RenameTypeTool renameTypeTool,
            RenameMethodTool renameMethodTool,
            RenameFieldTool renameFieldTool,
            RenamePackageTool renamePackageTool,
            RenameCompilationUnitTool renameCompilationUnitTool,
            RenameJavaProjectTool renameJavaProjectTool,
            RenameEnumConstantTool renameEnumConstantTool,
            RenameModuleTool renameModuleTool,
            RenameResourceTool renameResourceTool,
            RenameSourceFolderTool renameSourceFolderTool,
            DeleteElementsTool deleteElementsTool,
            CopyElementsTool copyElementsTool,
            MoveElementsTool moveElementsTool,
            MoveStaticMembersTool moveStaticMembersTool,
            ExtractClassMetadataTool extractClassMetadataTool,
            MigrateStatelessEjbTool migrateStatelessEjbTool,
            SearchJavaPatternTool searchJavaPatternTool,
            AnalyzeAntiPatternsTool analyzeAntiPatternsTool,
            GetDependencyGraphTool getDependencyGraphTool,
            AddTransactionalTool addTransactionalTool,
            BatchReplaceAnnotationsTool batchReplaceAnnotationsTool,
            ConvertToConstructorInjectionTool convertToConstructorInjectionTool,
            MigrateSecurityAnnotationsTool migrateSecurityAnnotationsTool,
            RemoveEjbInterfaceTool removeEjbInterfaceTool) {
        return Arrays.asList(ToolCallbacks.from(
                // Active EJB and Analysis tools
                extractClassMetadataTool,
                migrateStatelessEjbTool,
                searchJavaPatternTool,
                analyzeAntiPatternsTool,
                getDependencyGraphTool,
                // Active OpenRewrite tools
                addTransactionalTool,
                batchReplaceAnnotationsTool,
                convertToConstructorInjectionTool,
                migrateSecurityAnnotationsTool,
                removeEjbInterfaceTool)
        /*
         * JDT Refactoring tools (currently disabled):
         * Uncomment these to enable Eclipse JDT refactoring capabilities
         * 
         * renameTypeTool,
         * renameMethodTool,
         * renameFieldTool,
         * renamePackageTool,
         * renameCompilationUnitTool,
         * renameJavaProjectTool,
         * renameEnumConstantTool,
         * renameModuleTool,
         * renameResourceTool,
         * renameSourceFolderTool,
         * deleteElementsTool,
         * copyElementsTool,
         * moveElementsTool,
         * moveStaticMembersTool
         */
        );
    }
}
