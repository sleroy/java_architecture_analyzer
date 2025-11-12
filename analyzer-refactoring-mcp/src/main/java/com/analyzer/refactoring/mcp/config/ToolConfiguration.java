package com.analyzer.refactoring.mcp.config;

import com.analyzer.refactoring.mcp.service.EjbMigrationService;
import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import com.analyzer.refactoring.mcp.tool.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class ToolConfiguration {

    @Bean
    public List<ToolCallback> danTools(
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
            MigrateStatelessEjbTool migrateStatelessEjbTool) {
        return Arrays.stream(ToolCallbacks.from(
            extractClassMetadataTool,
            migrateStatelessEjbTool
        /*    renameTypeTool,
            renameMethodTool,
            renameFieldTool,
            renamePackageTool,
            renameCompilationUnitTool,
            renameJavaProjectTool,
            renameEnumConstantTool,
            renameModuleTool,
            renameResourceTool,
            renameSourceFolderTool,
            deleteElementsTool,
            copyElementsTool,
            moveElementsTool,
            moveStaticMembersTool*/
        )).toList();
    }

    @Bean
    public RenameTypeTool renameTypeTool(final JdtRefactoringService refactoringService) {
        return new RenameTypeTool(refactoringService);
    }

    @Bean
    public RenameMethodTool renameMethodTool(final JdtRefactoringService refactoringService) {
        return new RenameMethodTool(refactoringService);
    }

    @Bean
    public RenameFieldTool renameFieldTool(final JdtRefactoringService refactoringService) {
        return new RenameFieldTool(refactoringService);
    }

    @Bean
    public RenamePackageTool renamePackageTool(final JdtRefactoringService refactoringService) {
        return new RenamePackageTool(refactoringService);
    }

    @Bean
    public RenameCompilationUnitTool renameCompilationUnitTool(final JdtRefactoringService refactoringService) {
        return new RenameCompilationUnitTool(refactoringService);
    }

    @Bean
    public RenameJavaProjectTool renameJavaProjectTool(final JdtRefactoringService refactoringService) {
        return new RenameJavaProjectTool(refactoringService);
    }

    @Bean
    public RenameEnumConstantTool renameEnumConstantTool(final JdtRefactoringService refactoringService) {
        return new RenameEnumConstantTool(refactoringService);
    }

    @Bean
    public RenameModuleTool renameModuleTool(final JdtRefactoringService refactoringService) {
        return new RenameModuleTool(refactoringService);
    }

    @Bean
    public RenameResourceTool renameResourceTool(final JdtRefactoringService refactoringService) {
        return new RenameResourceTool(refactoringService);
    }

    @Bean
    public RenameSourceFolderTool renameSourceFolderTool(final JdtRefactoringService refactoringService) {
        return new RenameSourceFolderTool(refactoringService);
    }

    @Bean
    public DeleteElementsTool deleteElementsTool(final JdtRefactoringService refactoringService) {
        return new DeleteElementsTool(refactoringService);
    }

    @Bean
    public CopyElementsTool copyElementsTool(final JdtRefactoringService refactoringService) {
        return new CopyElementsTool(refactoringService);
    }

    @Bean
    public MoveElementsTool moveElementsTool(final JdtRefactoringService refactoringService) {
        return new MoveElementsTool(refactoringService);
    }

    @Bean
    public MoveStaticMembersTool moveStaticMembersTool(final JdtRefactoringService refactoringService) {
        return new MoveStaticMembersTool(refactoringService);
    }

    // EJB Migration Tools
    @Bean
    public ExtractClassMetadataTool extractClassMetadataTool(final EjbMigrationService migrationService) {
        return new ExtractClassMetadataTool(migrationService);
    }

    @Bean
    public MigrateStatelessEjbTool migrateStatelessEjbTool(final EjbMigrationService migrationService) {
        return new MigrateStatelessEjbTool(migrationService);
    }
    
    @Bean
    public EjbMigrationService ejbMigrationService() {
        return new EjbMigrationService();
    }
}
