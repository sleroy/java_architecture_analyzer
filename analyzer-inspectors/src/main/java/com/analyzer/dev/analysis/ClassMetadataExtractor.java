package com.analyzer.dev.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extracts compact metadata from Java source files.
 * 
 * This utility provides token-optimized metadata extraction for AI-assisted
 * migrations.
 * Instead of sending full source code (2000+ tokens) to AI, this extracts only
 * essential
 * metadata (200-300 tokens) in JSON-serializable format.
 * 
 * <p>
 * <b>Token Savings</b>: 85-95% reduction per class
 * </p>
 * 
 * <p>
 * <b>Use Case</b>: Feed AI agents with compact metadata for decision-making
 * without
 * exposing full source code.
 * </p>
 * 
 * @since 1.0.0
 */
public class ClassMetadataExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ClassMetadataExtractor.class);
    private final JavaParser javaParser;

    /**
     * Creates extractor with default JavaParser.
     */
    public ClassMetadataExtractor() {
        this.javaParser = new JavaParser();
    }

    /**
     * Creates extractor with custom JavaParser.
     * 
     * @param javaParser custom parser configuration
     */
    public ClassMetadataExtractor(JavaParser javaParser) {
        this.javaParser = javaParser;
    }

    /**
     * Extracts compact metadata from a Java file.
     * 
     * @param javaFile path to Java source file
     * @return metadata or null if parse fails
     */
    public ClassMetadata extract(Path javaFile) {
        try {
            String content = Files.readString(javaFile);
            return extractFromSource(content, javaFile.toString());
        } catch (IOException e) {
            logger.error("Error reading file: {}", javaFile, e);
            return null;
        }
    }

    /**
     * Extracts metadata from source code string.
     * 
     * @param sourceCode Java source code
     * @param sourceName source identifier for error reporting
     * @return metadata or null if parse fails
     */
    public ClassMetadata extractFromSource(String sourceCode, String sourceName) {
        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(sourceCode);

            if (!parseResult.isSuccessful()) {
                logger.error("Parse errors in {}: {}", sourceName, parseResult.getProblems());
                return null;
            }

            CompilationUnit cu = parseResult.getResult().orElse(null);
            if (cu == null) {
                return null;
            }

            return extractFromCompilationUnit(cu);

        } catch (Exception e) {
            logger.error("Error extracting metadata from {}", sourceName, e);
            return null;
        }
    }

    /**
     * Extracts metadata from parsed CompilationUnit.
     * 
     * @param cu parsed compilation unit
     * @return metadata
     */
    public ClassMetadata extractFromCompilationUnit(CompilationUnit cu) {
        ClassMetadata metadata = new ClassMetadata();

        // Extract package
        cu.getPackageDeclaration().ifPresent(pd -> metadata.setPackageName(pd.getNameAsString()));

        // Extract imports
        metadata.setImports(cu.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .collect(Collectors.toList()));

        // Extract primary type (class/interface/enum)
        cu.getPrimaryType().ifPresent(type -> {
            metadata.setSimpleName(type.getNameAsString());
            metadata.setFullyQualifiedName(
                    metadata.getPackageName() != null
                            ? metadata.getPackageName() + "." + type.getNameAsString()
                            : type.getNameAsString());

            if (type.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration classDecl = type.asClassOrInterfaceDeclaration();
                metadata.setInterface(classDecl.isInterface());
                metadata.setAbstract(classDecl.isAbstract());

                // Extract class annotations
                metadata.setAnnotations(extractAnnotations(classDecl.getAnnotations()));

                // Extract fields
                metadata.setFields(extractFields(classDecl.getFields()));

                // Extract methods
                metadata.setMethods(extractMethods(classDecl.getMethods()));

                // Extract superclass
                classDecl.getExtendedTypes().stream()
                        .findFirst()
                        .ifPresent(ext -> metadata.setSuperClassName(ext.getNameAsString()));

                // Extract interfaces
                metadata.setImplementedInterfaces(
                        classDecl.getImplementedTypes().stream()
                                .map(impl -> impl.getNameAsString())
                                .collect(Collectors.toList()));

            } else if (type.isEnumDeclaration()) {
                EnumDeclaration enumDecl = type.asEnumDeclaration();
                metadata.setEnum(true);
                metadata.setAnnotations(extractAnnotations(enumDecl.getAnnotations()));

                // Extract enum constants
                List<String> constants = enumDecl.getEntries().stream()
                        .map(e -> e.getNameAsString())
                        .collect(Collectors.toList());
                metadata.setEnumConstants(constants);
            }
        });

        return metadata;
    }

    private List<String> extractAnnotations(List<AnnotationExpr> annotations) {
        return annotations.stream()
                .map(ann -> ann.getNameAsString())
                .collect(Collectors.toList());
    }

    private List<FieldMetadata> extractFields(List<FieldDeclaration> fields) {
        List<FieldMetadata> fieldMetadataList = new ArrayList<>();

        for (FieldDeclaration field : fields) {
            for (VariableDeclarator var : field.getVariables()) {
                FieldMetadata fm = new FieldMetadata();
                fm.setName(var.getNameAsString());
                fm.setType(var.getTypeAsString());
                fm.setAnnotations(extractAnnotations(field.getAnnotations()));
                fm.setFinal(field.isFinal());
                fm.setStatic(field.isStatic());
                fm.setPublic(field.isPublic());
                fm.setPrivate(field.isPrivate());
                fm.setProtected(field.isProtected());
                fieldMetadataList.add(fm);
            }
        }

        return fieldMetadataList;
    }

    private List<MethodMetadata> extractMethods(List<MethodDeclaration> methods) {
        return methods.stream()
                .map(method -> {
                    MethodMetadata mm = new MethodMetadata();
                    mm.setName(method.getNameAsString());
                    mm.setReturnType(method.getTypeAsString());
                    mm.setAnnotations(extractAnnotations(method.getAnnotations()));
                    mm.setPublic(method.isPublic());
                    mm.setPrivate(method.isPrivate());
                    mm.setProtected(method.isProtected());
                    mm.setStatic(method.isStatic());
                    mm.setAbstract(method.isAbstract());

                    // Extract parameter types
                    mm.setParameterTypes(method.getParameters().stream()
                            .map(p -> p.getTypeAsString())
                            .collect(Collectors.toList()));

                    return mm;
                })
                .collect(Collectors.toList());
    }

    /**
     * Compact class metadata - JSON serializable.
     */
    public static class ClassMetadata {
        private String packageName;
        private String simpleName;
        private String fullyQualifiedName;
        private boolean isInterface;
        private boolean isAbstract;
        private boolean isEnum;
        private String superClassName;
        private List<String> implementedInterfaces = new ArrayList<>();
        private List<String> annotations = new ArrayList<>();
        private List<FieldMetadata> fields = new ArrayList<>();
        private List<MethodMetadata> methods = new ArrayList<>();
        private List<String> imports = new ArrayList<>();
        private List<String> enumConstants = new ArrayList<>();

        // Getters and setters
        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getSimpleName() {
            return simpleName;
        }

        public void setSimpleName(String simpleName) {
            this.simpleName = simpleName;
        }

        public String getFullyQualifiedName() {
            return fullyQualifiedName;
        }

        public void setFullyQualifiedName(String fullyQualifiedName) {
            this.fullyQualifiedName = fullyQualifiedName;
        }

        public boolean isInterface() {
            return isInterface;
        }

        public void setInterface(boolean isInterface) {
            this.isInterface = isInterface;
        }

        public boolean isAbstract() {
            return isAbstract;
        }

        public void setAbstract(boolean isAbstract) {
            this.isAbstract = isAbstract;
        }

        public boolean isEnum() {
            return isEnum;
        }

        public void setEnum(boolean isEnum) {
            this.isEnum = isEnum;
        }

        public String getSuperClassName() {
            return superClassName;
        }

        public void setSuperClassName(String superClassName) {
            this.superClassName = superClassName;
        }

        public List<String> getImplementedInterfaces() {
            return implementedInterfaces;
        }

        public void setImplementedInterfaces(List<String> implementedInterfaces) {
            this.implementedInterfaces = implementedInterfaces;
        }

        public List<String> getAnnotations() {
            return annotations;
        }

        public void setAnnotations(List<String> annotations) {
            this.annotations = annotations;
        }

        public List<FieldMetadata> getFields() {
            return fields;
        }

        public void setFields(List<FieldMetadata> fields) {
            this.fields = fields;
        }

        public List<MethodMetadata> getMethods() {
            return methods;
        }

        public void setMethods(List<MethodMetadata> methods) {
            this.methods = methods;
        }

        public List<String> getImports() {
            return imports;
        }

        public void setImports(List<String> imports) {
            this.imports = imports;
        }

        public List<String> getEnumConstants() {
            return enumConstants;
        }

        public void setEnumConstants(List<String> enumConstants) {
            this.enumConstants = enumConstants;
        }
    }

    /**
     * Compact field metadata.
     */
    public static class FieldMetadata {
        private String name;
        private String type;
        private List<String> annotations = new ArrayList<>();
        private boolean isFinal;
        private boolean isStatic;
        private boolean isPublic;
        private boolean isPrivate;
        private boolean isProtected;

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<String> getAnnotations() {
            return annotations;
        }

        public void setAnnotations(List<String> annotations) {
            this.annotations = annotations;
        }

        public boolean isFinal() {
            return isFinal;
        }

        public void setFinal(boolean isFinal) {
            this.isFinal = isFinal;
        }

        public boolean isStatic() {
            return isStatic;
        }

        public void setStatic(boolean isStatic) {
            this.isStatic = isStatic;
        }

        public boolean isPublic() {
            return isPublic;
        }

        public void setPublic(boolean isPublic) {
            this.isPublic = isPublic;
        }

        public boolean isPrivate() {
            return isPrivate;
        }

        public void setPrivate(boolean isPrivate) {
            this.isPrivate = isPrivate;
        }

        public boolean isProtected() {
            return isProtected;
        }

        public void setProtected(boolean isProtected) {
            this.isProtected = isProtected;
        }
    }

    /**
     * Compact method metadata.
     */
    public static class MethodMetadata {
        private String name;
        private String returnType;
        private List<String> parameterTypes = new ArrayList<>();
        private List<String> annotations = new ArrayList<>();
        private boolean isPublic;
        private boolean isPrivate;
        private boolean isProtected;
        private boolean isStatic;
        private boolean isAbstract;

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getReturnType() {
            return returnType;
        }

        public void setReturnType(String returnType) {
            this.returnType = returnType;
        }

        public List<String> getParameterTypes() {
            return parameterTypes;
        }

        public void setParameterTypes(List<String> parameterTypes) {
            this.parameterTypes = parameterTypes;
        }

        public List<String> getAnnotations() {
            return annotations;
        }

        public void setAnnotations(List<String> annotations) {
            this.annotations = annotations;
        }

        public boolean isPublic() {
            return isPublic;
        }

        public void setPublic(boolean isPublic) {
            this.isPublic = isPublic;
        }

        public boolean isPrivate() {
            return isPrivate;
        }

        public void setPrivate(boolean isPrivate) {
            this.isPrivate = isPrivate;
        }

        public boolean isProtected() {
            return isProtected;
        }

        public void setProtected(boolean isProtected) {
            this.isProtected = isProtected;
        }

        public boolean isStatic() {
            return isStatic;
        }

        public void setStatic(boolean isStatic) {
            this.isStatic = isStatic;
        }

        public boolean isAbstract() {
            return isAbstract;
        }

        public void setAbstract(boolean isAbstract) {
            this.isAbstract = isAbstract;
        }
    }
}
