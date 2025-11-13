package com.analyzer.refactoring.mcp.config;

import com.analyzer.refactoring.mcp.service.JdtRefactoringService;
import com.analyzer.refactoring.mcp.tool.jdt.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for JDT (Eclipse Java Development Tools) based refactoring
 * tools.
 * These tools provide Eclipse-powered Java refactoring capabilities.
 */
@Configuration
public class JdtToolConfiguration {

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
}
