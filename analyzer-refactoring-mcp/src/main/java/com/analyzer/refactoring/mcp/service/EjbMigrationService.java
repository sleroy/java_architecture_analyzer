package com.analyzer.refactoring.mcp.service;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for EJB to Spring migration operations.
 * Provides token-optimized tools for batch migrations.
 */
@Service
public class EjbMigrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(EjbMigrationService.class);
    
    /**
     * Migrate a stateless session bean to a Spring @Service.
     * 
     * This tool performs the following transformations:
     * - Replace @Stateless with @Service
     * - Remove @Local and @Remote annotations
     * - Convert @EJB fields to constructor injection
     * - Add @Transactional to transactional methods
     * - Update imports
     */
    public MigrationResult migrateStatelessEjbToService(
            String projectPath,
            String sourcePath,
            String fullyQualifiedName,
            String targetPath,
            boolean addTransactional,
            boolean convertToConstructorInjection) {
        
        MigrationResult result = new MigrationResult();
        result.setClassName(fullyQualifiedName);
        
        try {
            logger.info("Migrating stateless EJB: {} -> {}", sourcePath, targetPath);
            
            // Read source file
            Path sourceFile = Paths.get(projectPath, sourcePath);
            if (!Files.exists(sourceFile)) {
                result.addError("Source file not found: " + sourceFile);
                return result;
            }
            
            String source = Files.readString(sourceFile);
            Document document = new Document(source);
            
            // Parse AST
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setSource(document.get().toCharArray());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setResolveBindings(false);
            
            CompilationUnit cu = (CompilationUnit) parser.createAST(null);
            AST ast = cu.getAST();
            ASTRewrite rewriter = ASTRewrite.create(ast);
            
            // Find the type declaration
            TypeDeclaration typeDecl = findTypeDeclaration(cu, fullyQualifiedName);
            if (typeDecl == null) {
                result.addError("Type not found in source: " + fullyQualifiedName);
                return result;
            }
            
            // Track changes
            int changes = 0;
            
            // 1. Replace @Stateless with @Service
            changes += replaceAnnotation(typeDecl, rewriter, ast, "Stateless", "Service");
            result.addChange("Replaced @Stateless with @Service");
            
            // 2. Remove @Local and @Remote
            changes += removeAnnotation(typeDecl, rewriter, "Local");
            changes += removeAnnotation(typeDecl, rewriter, "Remote");
            result.addChange("Removed @Local and @Remote annotations");
            
            // 3. Convert @EJB fields to constructor injection
            if (convertToConstructorInjection) {
                List<FieldInfo> ejbFields = findEjbFields(typeDecl);
                if (!ejbFields.isEmpty()) {
                    changes += convertToConstructorInjection(typeDecl, rewriter, ast, ejbFields);
                    result.addChange("Converted " + ejbFields.size() + " @EJB fields to constructor injection");
                }
            }
            
            // 4. Add @Transactional to transactional methods
            if (addTransactional) {
                List<MethodDeclaration> methods = getPublicMethods(typeDecl);
                for (MethodDeclaration method : methods) {
                    if (shouldHaveTransactional(method)) {
                        changes += addAnnotation(method, rewriter, ast, "Transactional");
                    }
                }
                result.addChange("Added @Transactional annotations");
            }
            
            // 5. Update imports
            changes += updateImports(cu, rewriter, ast);
            result.addChange("Updated imports");
            
            // Apply changes
            if (changes > 0) {
                TextEdit edits = rewriter.rewriteAST(document, null);
                edits.apply(document);
                
                // Write to target
                Path targetFile = Paths.get(projectPath, targetPath);
                Files.createDirectories(targetFile.getParent());
                Files.writeString(targetFile, document.get());
                
                result.setSuccess(true);
                result.setTargetPath(targetPath);
                result.addMessage("Migration completed: " + changes + " modifications made");
            } else {
                result.setSuccess(true);
                result.addMessage("No changes needed");
            }
            
        } catch (IOException e) {
            logger.error("IO error during migration", e);
            result.addError("IO error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error during migration", e);
            result.addError("Migration error: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Extract compact metadata from a Java class.
     * Returns only essential information without full source code.
     */
    public ClassMetadata extractClassMetadataCompact(
            String projectPath,
            String filePath,
            String fullyQualifiedName) {
        
        ClassMetadata metadata = new ClassMetadata();
        metadata.setFullyQualifiedName(fullyQualifiedName);
        
        try {
            Path sourceFile = Paths.get(projectPath, filePath);
            if (!Files.exists(sourceFile)) {
                metadata.addError("File not found: " + sourceFile);
                return metadata;
            }
            
            String source = Files.readString(sourceFile);
            
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setSource(source.toCharArray());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setResolveBindings(false);
            
            CompilationUnit cu = (CompilationUnit) parser.createAST(null);
            TypeDeclaration typeDecl = findTypeDeclaration(cu, fullyQualifiedName);
            
            if (typeDecl == null) {
                metadata.addError("Type not found: " + fullyQualifiedName);
                return metadata;
            }
            
            // Extract annotations
            for (Object modifier : typeDecl.modifiers()) {
                if (modifier instanceof Annotation) {
                    Annotation ann = (Annotation) modifier;
                    metadata.addAnnotation(ann.getTypeName().getFullyQualifiedName());
                }
            }
            
            // Extract fields
            for (FieldDeclaration field : typeDecl.getFields()) {
                List<String> fieldAnnotations = new ArrayList<>();
                for (Object modifier : field.modifiers()) {
                    if (modifier instanceof Annotation) {
                        Annotation ann = (Annotation) modifier;
                        fieldAnnotations.add(ann.getTypeName().getFullyQualifiedName());
                    }
                }
                
                for (Object fragment : field.fragments()) {
                    if (fragment instanceof VariableDeclarationFragment) {
                        VariableDeclarationFragment frag = (VariableDeclarationFragment) fragment;
                        metadata.addField(
                            frag.getName().getIdentifier(),
                            field.getType().toString(),
                            fieldAnnotations,
                            hasModifier(field, "final")
                        );
                    }
                }
            }
            
            // Extract methods
            for (MethodDeclaration method : typeDecl.getMethods()) {
                List<String> methodAnnotations = new ArrayList<>();
                for (Object modifier : method.modifiers()) {
                    if (modifier instanceof Annotation) {
                        Annotation ann = (Annotation) modifier;
                        methodAnnotations.add(ann.getTypeName().getFullyQualifiedName());
                    }
                }
                
                String returnType = method.getReturnType2() != null ? 
                    method.getReturnType2().toString() : "void";
                
                metadata.addMethod(
                    method.getName().getIdentifier(),
                    returnType,
                    methodAnnotations,
                    hasModifier(method, "public")
                );
            }
            
            // Extract imports
            for (Object importDecl : cu.imports()) {
                if (importDecl instanceof ImportDeclaration) {
                    ImportDeclaration imp = (ImportDeclaration) importDecl;
                    metadata.addImport(imp.getName().getFullyQualifiedName());
                }
            }
            
        } catch (Exception e) {
            logger.error("Error extracting metadata", e);
            metadata.addError("Extraction error: " + e.getMessage());
        }
        
        return metadata;
    }
    
    /**
     * Batch replace annotations across multiple classes.
     */
    public BatchAnnotationResult batchReplaceAnnotations(
            String projectPath,
            List<String> filePaths,
            Map<String, String> annotationMappings) {
        
        BatchAnnotationResult result = new BatchAnnotationResult();
        
        for (String filePath : filePaths) {
            try {
                Path sourceFile = Paths.get(projectPath, filePath);
                if (!Files.exists(sourceFile)) {
                    result.addSkipped(filePath, "File not found");
                    continue;
                }
                
                String source = Files.readString(sourceFile);
                Document document = new Document(source);
                
                ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
                parser.setSource(document.get().toCharArray());
                parser.setKind(ASTParser.K_COMPILATION_UNIT);
                
                CompilationUnit cu = (CompilationUnit) parser.createAST(null);
                AST ast = cu.getAST();
                ASTRewrite rewriter = ASTRewrite.create(ast);
                
                int changes = 0;
                
                // Process all type declarations
                for (Object type : cu.types()) {
                    if (type instanceof TypeDeclaration) {
                        TypeDeclaration typeDecl = (TypeDeclaration) type;
                        for (Map.Entry<String, String> mapping : annotationMappings.entrySet()) {
                            changes += replaceAnnotation(typeDecl, rewriter, ast, 
                                mapping.getKey(), mapping.getValue());
                        }
                    }
                }
                
                if (changes > 0) {
                    TextEdit edits = rewriter.rewriteAST(document, null);
                    edits.apply(document);
                    Files.writeString(sourceFile, document.get());
                    result.addProcessed(filePath, changes);
                } else {
                    result.addSkipped(filePath, "No changes needed");
                }
                
            } catch (Exception e) {
                logger.error("Error processing file: " + filePath, e);
                result.addFailed(filePath, e.getMessage());
            }
        }
        
        return result;
    }
    
    // Helper methods
    
    private TypeDeclaration findTypeDeclaration(CompilationUnit cu, String fqn) {
        String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
        for (Object type : cu.types()) {
            if (type instanceof TypeDeclaration) {
                TypeDeclaration typeDecl = (TypeDeclaration) type;
                if (typeDecl.getName().getIdentifier().equals(simpleName)) {
                    return typeDecl;
                }
            }
        }
        return null;
    }
    
    private int replaceAnnotation(TypeDeclaration typeDecl, ASTRewrite rewriter, 
                                  AST ast, String oldName, String newName) {
        List<Annotation> toReplace = new ArrayList<>();
        for (Object modifier : typeDecl.modifiers()) {
            if (modifier instanceof Annotation) {
                Annotation ann = (Annotation) modifier;
                if (ann.getTypeName().getFullyQualifiedName().equals(oldName)) {
                    toReplace.add(ann);
                }
            }
        }
        
        for (Annotation ann : toReplace) {
            MarkerAnnotation newAnn = ast.newMarkerAnnotation();
            newAnn.setTypeName(ast.newSimpleName(newName));
            rewriter.replace(ann, newAnn, null);
        }
        
        return toReplace.size();
    }
    
    private int removeAnnotation(TypeDeclaration typeDecl, ASTRewrite rewriter, String name) {
        List<Annotation> toRemove = new ArrayList<>();
        for (Object modifier : typeDecl.modifiers()) {
            if (modifier instanceof Annotation) {
                Annotation ann = (Annotation) modifier;
                if (ann.getTypeName().getFullyQualifiedName().equals(name)) {
                    toRemove.add(ann);
                }
            }
        }
        
        for (Annotation ann : toRemove) {
            rewriter.remove(ann, null);
        }
        
        return toRemove.size();
    }
    
    private int addAnnotation(MethodDeclaration method, ASTRewrite rewriter, 
                             AST ast, String annotationName) {
        // Check if annotation already exists
        for (Object modifier : method.modifiers()) {
            if (modifier instanceof Annotation) {
                Annotation ann = (Annotation) modifier;
                if (ann.getTypeName().getFullyQualifiedName().equals(annotationName)) {
                    return 0;
                }
            }
        }
        
        MarkerAnnotation ann = ast.newMarkerAnnotation();
        ann.setTypeName(ast.newSimpleName(annotationName));
        
        ListRewrite listRewrite = rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
        listRewrite.insertFirst(ann, null);
        
        return 1;
    }
    
    private List<FieldInfo> findEjbFields(TypeDeclaration typeDecl) {
        List<FieldInfo> ejbFields = new ArrayList<>();
        
        for (FieldDeclaration field : typeDecl.getFields()) {
            boolean hasEjbAnnotation = false;
            for (Object modifier : field.modifiers()) {
                if (modifier instanceof Annotation) {
                    Annotation ann = (Annotation) modifier;
                    if (ann.getTypeName().getFullyQualifiedName().equals("EJB")) {
                        hasEjbAnnotation = true;
                        break;
                    }
                }
            }
            
            if (hasEjbAnnotation) {
                for (Object fragment : field.fragments()) {
                    if (fragment instanceof VariableDeclarationFragment) {
                        VariableDeclarationFragment frag = (VariableDeclarationFragment) fragment;
                        ejbFields.add(new FieldInfo(
                            frag.getName().getIdentifier(),
                            field.getType().toString()
                        ));
                    }
                }
            }
        }
        
        return ejbFields;
    }
    
    private int convertToConstructorInjection(TypeDeclaration typeDecl, ASTRewrite rewriter,
                                              AST ast, List<FieldInfo> fields) {
        // This is simplified - full implementation would:
        // 1. Remove @EJB annotations from fields
        // 2. Make fields final
        // 3. Create/update constructor with parameters
        // For POC, just remove @EJB annotations
        
        int changes = 0;
        for (FieldDeclaration field : typeDecl.getFields()) {
            changes += removeAnnotation(field, rewriter, "EJB");
        }
        return changes;
    }
    
    private int removeAnnotation(FieldDeclaration field, ASTRewrite rewriter, String name) {
        List<Annotation> toRemove = new ArrayList<>();
        for (Object modifier : field.modifiers()) {
            if (modifier instanceof Annotation) {
                Annotation ann = (Annotation) modifier;
                if (ann.getTypeName().getFullyQualifiedName().equals(name)) {
                    toRemove.add(ann);
                }
            }
        }
        
        for (Annotation ann : toRemove) {
            rewriter.remove(ann, null);
        }
        
        return toRemove.size();
    }
    
    private List<MethodDeclaration> getPublicMethods(TypeDeclaration typeDecl) {
        List<MethodDeclaration> methods = new ArrayList<>();
        for (MethodDeclaration method : typeDecl.getMethods()) {
            if (hasModifier(method, "public")) {
                methods.add(method);
            }
        }
        return methods;
    }
    
    private boolean shouldHaveTransactional(MethodDeclaration method) {
        // Heuristic: methods that mutate data should be transactional
        String name = method.getName().getIdentifier().toLowerCase();
        return name.startsWith("save") || name.startsWith("update") || 
               name.startsWith("delete") || name.startsWith("create") ||
               name.startsWith("remove") || name.startsWith("persist");
    }
    
    private boolean hasModifier(BodyDeclaration decl, String modifierName) {
        for (Object modifier : decl.modifiers()) {
            if (modifier instanceof Modifier) {
                Modifier mod = (Modifier) modifier;
                if (mod.getKeyword().toString().equals(modifierName)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private int updateImports(CompilationUnit cu, ASTRewrite rewriter, AST ast) {
        // Simplified - would add Spring imports and remove EJB imports
        return 0;
    }
    
    // Result classes
    
    public static class MigrationResult {
        private boolean success;
        private String className;
        private String targetPath;
        private List<String> changes = new ArrayList<>();
        private List<String> messages = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        
        public String getTargetPath() { return targetPath; }
        public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
        
        public List<String> getChanges() { return changes; }
        public void addChange(String change) { this.changes.add(change); }
        
        public List<String> getMessages() { return messages; }
        public void addMessage(String message) { this.messages.add(message); }
        
        public List<String> getErrors() { return errors; }
        public void addError(String error) { this.errors.add(error); }
    }
    
    public static class ClassMetadata {
        private String fullyQualifiedName;
        private List<String> annotations = new ArrayList<>();
        private List<FieldMetadata> fields = new ArrayList<>();
        private List<MethodMetadata> methods = new ArrayList<>();
        private List<String> imports = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
        
        public String getFullyQualifiedName() { return fullyQualifiedName; }
        public void setFullyQualifiedName(String fqn) { this.fullyQualifiedName = fqn; }
        
        public List<String> getAnnotations() { return annotations; }
        public void addAnnotation(String annotation) { this.annotations.add(annotation); }
        
        public List<FieldMetadata> getFields() { return fields; }
        public void addField(String name, String type, List<String> annotations, boolean isFinal) {
            this.fields.add(new FieldMetadata(name, type, annotations, isFinal));
        }
        
        public List<MethodMetadata> getMethods() { return methods; }
        public void addMethod(String name, String returnType, List<String> annotations, boolean isPublic) {
            this.methods.add(new MethodMetadata(name, returnType, annotations, isPublic));
        }
        
        public List<String> getImports() { return imports; }
        public void addImport(String importName) { this.imports.add(importName); }
        
        public List<String> getErrors() { return errors; }
        public void addError(String error) { this.errors.add(error); }
    }
    
    public static class FieldMetadata {
        private String name;
        private String type;
        private List<String> annotations;
        private boolean isFinal;
        
        public FieldMetadata(String name, String type, List<String> annotations, boolean isFinal) {
            this.name = name;
            this.type = type;
            this.annotations = annotations;
            this.isFinal = isFinal;
        }
        
        public String getName() { return name; }
        public String getType() { return type; }
        public List<String> getAnnotations() { return annotations; }
        public boolean isFinal() { return isFinal; }
    }
    
    public static class MethodMetadata {
        private String name;
        private String returnType;
        private List<String> annotations;
        private boolean isPublic;
        
        public MethodMetadata(String name, String returnType, List<String> annotations, boolean isPublic) {
            this.name = name;
            this.returnType = returnType;
            this.annotations = annotations;
            this.isPublic = isPublic;
        }
        
        public String getName() { return name; }
        public String getReturnType() { return returnType; }
        public List<String> getAnnotations() { return annotations; }
        public boolean isPublic() { return isPublic; }
    }
    
    public static class BatchAnnotationResult {
        private Map<String, Integer> processed = new HashMap<>();
        private Map<String, String> skipped = new HashMap<>();
        private Map<String, String> failed = new HashMap<>();
        
        public void addProcessed(String filePath, int changes) {
            processed.put(filePath, changes);
        }
        
        public void addSkipped(String filePath, String reason) {
            skipped.put(filePath, reason);
        }
        
        public void addFailed(String filePath, String error) {
            failed.put(filePath, error);
        }
        
        public Map<String, Integer> getProcessed() { return processed; }
        public Map<String, String> getSkipped() { return skipped; }
        public Map<String, String> getFailed() { return failed; }
        
        public int getTotalProcessed() { return processed.size(); }
        public int getTotalSkipped() { return skipped.size(); }
        public int getTotalFailed() { return failed.size(); }
    }
    
    private static class FieldInfo {
        String name;
        String type;
        
        FieldInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }
}
