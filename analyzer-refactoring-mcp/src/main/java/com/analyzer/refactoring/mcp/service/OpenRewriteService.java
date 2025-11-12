package com.analyzer.refactoring.mcp.service;

import com.analyzer.ejb2spring.openrewrite.*;
import org.openrewrite.*;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for executing OpenRewrite recipes on Java source code.
 * Provides high-performance, deterministic code transformations.
 */
@Service
public class OpenRewriteService {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenRewriteService.class);
    
    /**
     * Add @Transactional annotations to methods matching name patterns.
     */
    public RefactoringResult addTransactionalByPattern(
            String projectPath,
            List<String> files,
            List<String> methodPatterns,
            List<String> readOnlyPatterns) {
        
        Recipe recipe = new AddTransactionalByPatternRecipe(methodPatterns, readOnlyPatterns);
        return executeRecipe(projectPath, files, recipe, "AddTransactional");
    }
    
    /**
     * Migrate EJB security annotations to Spring Security annotations.
     */
    public RefactoringResult migrateSecurityAnnotations(
            String projectPath,
            List<String> files) {
        
        Recipe recipe = new MigrateSecurityAnnotationsRecipe();
        return executeRecipe(projectPath, files, recipe, "MigrateSecurityAnnotations");
    }
    
    /**
     * Convert @EJB field injection to constructor injection.
     */
    public RefactoringResult convertToConstructorInjection(
            String projectPath,
            List<String> files) {
        
        Recipe recipe = new FieldToConstructorInjectionRecipe();
        return executeRecipe(projectPath, files, recipe, "ConvertToConstructorInjection");
    }
    
    /**
     * Remove EJB interfaces (Home, Remote, Local, LocalHome).
     */
    public RefactoringResult removeEjbInterfaces(
            String projectPath,
            List<String> interfaceFiles,
            boolean updateReferences) {
        
        Recipe recipe = new RemoveEjbInterfaceRecipe();
        return executeRecipe(projectPath, interfaceFiles, recipe, "RemoveEjbInterfaces");
    }
    
    /**
     * Generic method to execute any OpenRewrite recipe on a list of files.
     */
    private RefactoringResult executeRecipe(
            String projectPath,
            List<String> files,
            Recipe recipe,
            String operationName) {
        
        RefactoringResult result = new RefactoringResult(operationName);
        Path basePath = Paths.get(projectPath);
        
        try {
            // Parse source files
            List<SourceFile> sourceFiles = new ArrayList<>();
            JavaParser javaParser = JavaParser.fromJavaVersion()
                .build();
            
            for (String filePath : files) {
                Path fullPath = basePath.resolve(filePath);
                
                if (!Files.exists(fullPath)) {
                    result.addSkipped(filePath, "File not found");
                    continue;
                }
                
                try {
                    List<Path> paths = Collections.singletonList(fullPath);
                    Iterable<SourceFile> parsed = javaParser.parse(paths, basePath, new InMemoryExecutionContext());
                    
                    for (SourceFile sf : parsed) {
                        sourceFiles.add(sf);
                    }
                } catch (Exception e) {
                    logger.error("Error parsing file: " + filePath, e);
                    result.addFailed(filePath, "Parse error: " + e.getMessage());
                }
            }
            
            if (sourceFiles.isEmpty()) {
                result.setSuccess(true);
                result.addMessage("No files to process");
                return result;
            }
            
            // Execute recipe
            ExecutionContext ctx = new InMemoryExecutionContext();
            List<Result> results = recipe.run(sourceFiles, ctx).getChangeset().getAllResults();
            
            // Process results
            for (Result res : results) {
                SourceFile before = res.getBefore();
                SourceFile after = res.getAfter();
                
                if (after != null && !before.printAll().equals(after.printAll())) {
                    // Write changes back to file
                    Path sourcePath = basePath.resolve(before.getSourcePath());
                    
                    try {
                        Files.writeString(sourcePath, after.printAll());
                        result.addProcessed(before.getSourcePath().toString(), 1);
                    } catch (IOException e) {
                        logger.error("Error writing file: " + sourcePath, e);
                        result.addFailed(before.getSourcePath().toString(), "Write error: " + e.getMessage());
                    }
                } else {
                    result.addSkipped(before.getSourcePath().toString(), "No changes needed");
                }
            }
            
            result.setSuccess(true);
            result.addMessage(String.format("Processed %d files, skipped %d, failed %d",
                result.getProcessed().size(), result.getSkipped().size(), result.getFailed().size()));
            
        } catch (Exception e) {
            logger.error("Error executing recipe: " + operationName, e);
            result.setSuccess(false);
            result.addMessage("Error: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Result class for refactoring operations.
     */
    public static class RefactoringResult {
        private boolean success;
        private String operation;
        private Map<String, Integer> processed = new LinkedHashMap<>();
        private Map<String, String> skipped = new LinkedHashMap<>();
        private Map<String, String> failed = new LinkedHashMap<>();
        private List<String> messages = new ArrayList<>();
        
        public RefactoringResult(String operation) {
            this.operation = operation;
        }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getOperation() { return operation; }
        
        public Map<String, Integer> getProcessed() { return processed; }
        public void addProcessed(String file, int changes) {
            processed.put(file, changes);
        }
        
        public Map<String, String> getSkipped() { return skipped; }
        public void addSkipped(String file, String reason) {
            skipped.put(file, reason);
        }
        
        public Map<String, String> getFailed() { return failed; }
        public void addFailed(String file, String error) {
            failed.put(file, error);
        }
        
        public List<String> getMessages() { return messages; }
        public void addMessage(String message) {
            messages.add(message);
        }
        
        public int getTotalProcessed() { return processed.size(); }
        public int getTotalSkipped() { return skipped.size(); }
        public int getTotalFailed() { return failed.size(); }
    }
}
