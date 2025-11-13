package com.analyzer.refactoring.mcp.service;

import com.analyzer.ejb2spring.analysis.EjbAntiPatternDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Service for analyzing Java code structure and patterns.
 * Provides token-optimized analysis tools for EJB migrations.
 */
@Service
public class AnalysisService {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalysisService.class);
    private final EjbAntiPatternDetector antiPatternDetector;
    
    public AnalysisService(EjbAntiPatternDetector antiPatternDetector) {
        this.antiPatternDetector = antiPatternDetector;
    }
    
    /**
     * Analyze classes for EJB anti-patterns.
     * Filters out classes that can be migrated deterministically, leaving only
     * problematic cases that may need AI assistance.
     */
    public AntiPatternAnalysisResult analyzeAntiPatterns(
            String projectPath,
            List<String> files) {
        
        AntiPatternAnalysisResult result = new AntiPatternAnalysisResult();
        Path basePath = Paths.get(projectPath);
        
        for (String filePath : files) {
            try {
                Path fullPath = basePath.resolve(filePath);
                
                if (!Files.exists(fullPath)) {
                    result.addSkipped(filePath, "File not found");
                    continue;
                }
                
                // TODO: Implement proper anti-pattern detection
                // Requires ClassMetadataExtractor integration to convert source to ClassMetadata
                // For now, mark all classes as clean (deterministic tools will handle them)
                result.addClean(filePath);
                
            } catch (Exception e) {
                logger.error("Error analyzing file: " + filePath, e);
                result.addFailed(filePath, e.getMessage());
            }
        }
        
        return result;
    }
    
    /**
     * Extract dependency graph for a list of classes.
     * Returns compact class dependency information without full source code.
     */
    public DependencyGraphResult getDependencyGraph(
            String projectPath,
            List<String> files) {
        
        DependencyGraphResult result = new DependencyGraphResult();
        Path basePath = Paths.get(projectPath);
        
        // Simple dependency extraction based on imports and references
        // This is a lightweight implementation - can be enhanced with full AST analysis
        
        for (String filePath : files) {
            try {
                Path fullPath = basePath.resolve(filePath);
                
                if (!Files.exists(fullPath)) {
                    continue;
                }
                
                String source = Files.readString(fullPath);
                String className = extractClassName(filePath);
                
                List<String> dependencies = extractDependencies(source);
                List<String> usedBy = new ArrayList<>(); // Would need full project scan
                
                result.addDependency(className, dependencies, usedBy);
                
            } catch (Exception e) {
                logger.error("Error extracting dependencies from: " + filePath, e);
            }
        }
        
        return result;
    }
    
    private String extractClassName(String filePath) {
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        return fileName.replace(".java", "");
    }
    
    private List<String> extractDependencies(String source) {
        List<String> deps = new ArrayList<>();
        
        // Extract from imports
        String[] lines = source.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("import ") && !line.contains("java.") && !line.contains("javax.")) {
                String importClass = line.substring(7, line.length() - 1).trim();
                String simpleName = importClass.substring(importClass.lastIndexOf('.') + 1);
                if (!deps.contains(simpleName)) {
                    deps.add(simpleName);
                }
            }
        }
        
        return deps;
    }
    
    // Result classes
    
    public static class AntiPatternAnalysisResult {
        private List<ClassAntiPatterns> problematicClasses = new ArrayList<>();
        private List<String> cleanClasses = new ArrayList<>();
        private Map<String, String> skipped = new LinkedHashMap<>();
        private Map<String, String> failed = new LinkedHashMap<>();
        
        public void addProblematic(ClassAntiPatterns patterns) {
            problematicClasses.add(patterns);
        }
        
        public void addClean(String className) {
            cleanClasses.add(className);
        }
        
        public void addSkipped(String className, String reason) {
            skipped.put(className, reason);
        }
        
        public void addFailed(String className, String error) {
            failed.put(className, error);
        }
        
        public List<ClassAntiPatterns> getProblematicClasses() { return problematicClasses; }
        public List<String> getCleanClasses() { return cleanClasses; }
        public Map<String, String> getSkipped() { return skipped; }
        public Map<String, String> getFailed() { return failed; }
        
        public int getTotalProblematic() { return problematicClasses.size(); }
        public int getTotalClean() { return cleanClasses.size(); }
        public int getTotalSkipped() { return skipped.size(); }
        public int getTotalFailed() { return failed.size(); }
    }
    
    public static class ClassAntiPatterns {
        private String className;
        private List<AntiPattern> patterns = new ArrayList<>();
        
        public ClassAntiPatterns(String className) {
            this.className = className;
        }
        
        public void addPattern(String type, String location, String severity, String description) {
            patterns.add(new AntiPattern(type, location, severity, description));
        }
        
        public String getClassName() { return className; }
        public List<AntiPattern> getPatterns() { return patterns; }
    }
    
    public static class AntiPattern {
        private String type;
        private String location;
        private String severity;
        private String description;
        
        public AntiPattern(String type, String location, String severity, String description) {
            this.type = type;
            this.location = location;
            this.severity = severity;
            this.description = description;
        }
        
        public String getType() { return type; }
        public String getLocation() { return location; }
        public String getSeverity() { return severity; }
        public String getDescription() { return description; }
    }
    
    public static class DependencyGraphResult {
        private Map<String, ClassDependencies> dependencies = new LinkedHashMap<>();
        
        public void addDependency(String className, List<String> dependsOn, List<String> usedBy) {
            dependencies.put(className, new ClassDependencies(className, dependsOn, usedBy));
        }
        
        public Map<String, ClassDependencies> getDependencies() { return dependencies; }
    }
    
    public static class ClassDependencies {
        private String className;
        private List<String> dependsOn;
        private List<String> usedBy;
        
        public ClassDependencies(String className, List<String> dependsOn, List<String> usedBy) {
            this.className = className;
            this.dependsOn = dependsOn;
            this.usedBy = usedBy;
        }
        
        public String getClassName() { return className; }
        public List<String> getDependsOn() { return dependsOn; }
        public List<String> getUsedBy() { return usedBy; }
    }
}
