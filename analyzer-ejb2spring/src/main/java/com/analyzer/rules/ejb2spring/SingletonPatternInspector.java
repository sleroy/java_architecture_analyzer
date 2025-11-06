package com.analyzer.rules.ejb2spring;

import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.api.graph.JavaClassNode;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Inspector that detects Singleton pattern implementations for antipattern
 * analysis.
 * Identifies both classic and enum-based singleton patterns.
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        EjbMigrationTags.TAG_ANTIPATTERN_SINGLETON })
public class SingletonPatternInspector extends AbstractJavaClassInspector {

    @Inject
    public SingletonPatternInspector(ResourceResolver resourceResolver,
            ClassNodeRepository classNodeRepository,
            LocalCache localCache) {
        super(resourceResolver, classNodeRepository, localCache);
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
            NodeDecorator<ProjectFile> projectFileDecorator) {
        if (type instanceof ClassOrInterfaceDeclaration classDecl && !classDecl.isInterface()) {
            SingletonDetector detector = new SingletonDetector();
            classDecl.accept(detector, null);

            if (detector.isSingleton()) {
                SingletonInfo info = detector.getSingletonInfo();

                // Tag ProjectFile and JavaClassNode
                projectFileDecorator.enableTag(EjbMigrationTags.TAG_ANTIPATTERN_SINGLETON);
                classNode.enableTag(EjbMigrationTags.TAG_ANTIPATTERN_SINGLETON);

                // Store analysis data
                classNode.setProperty("antipattern.singleton.info", info);

                // Calculate refactoring complexity
                double complexity = calculateComplexity(info);
                classNode.setProperty(EjbMigrationTags.TAG_METRIC_MIGRATION_COMPLEXITY, complexity);
            }
        }
    }

    private double calculateComplexity(SingletonInfo info) {
        // Base complexity for singleton refactoring
        double complexity = EjbMigrationTags.COMPLEXITY_LOW;

        // Increase if not thread-safe
        if (!info.isThreadSafe) {
            complexity = EjbMigrationTags.COMPLEXITY_MEDIUM;
        }

        // Increase if has state (fields)
        if (info.hasState) {
            complexity += 1.0;
        }

        return Math.min(complexity, EjbMigrationTags.COMPLEXITY_HIGH);
    }

    @Override
    public String getName() {
        return "Singleton Pattern Inspector";
    }

    /**
     * Visitor that detects Singleton pattern characteristics.
     */
    private static class SingletonDetector extends VoidVisitorAdapter<Void> {
        private final SingletonInfo singletonInfo = new SingletonInfo();
        private boolean isSingleton = false;

        private static final java.util.Set<String> GETINSTANCE_NAMES = java.util.Set.of(
                "getInstance", "instance", "get", "create");

        public boolean isSingleton() {
            return isSingleton;
        }

        public SingletonInfo getSingletonInfo() {
            return singletonInfo;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            singletonInfo.className = classDecl.getNameAsString();

            boolean hasPrivateConstructor = false;
            boolean hasStaticInstance = false;
            boolean hasGetInstanceMethod = false;

            // Check for private constructor
            for (ConstructorDeclaration constructor : classDecl.getConstructors()) {
                if (constructor.isPrivate()) {
                    hasPrivateConstructor = true;
                    break;
                }
            }

            // Check for static instance field
            for (FieldDeclaration field : classDecl.getFields()) {
                if (field.isStatic() && field.isFinal()) {
                    // Check if field type matches class name (classic singleton)
                    String fieldType = field.getVariables().get(0).getTypeAsString();
                    if (fieldType.equals(classDecl.getNameAsString())) {
                        hasStaticInstance = true;
                        singletonInfo.instanceFieldName = field.getVariables().get(0).getNameAsString();
                    }
                }

                // Check for non-final instance (lazy initialization)
                if (field.isStatic() && !field.isFinal()) {
                    String fieldType = field.getVariables().get(0).getTypeAsString();
                    if (fieldType.equals(classDecl.getNameAsString())) {
                        hasStaticInstance = true;
                        singletonInfo.instanceFieldName = field.getVariables().get(0).getNameAsString();
                        singletonInfo.isLazyInit = true;
                    }
                }
            }

            // Check for getInstance() method
            for (MethodDeclaration method : classDecl.getMethods()) {
                if (method.isStatic() && method.isPublic()) {
                    String methodName = method.getNameAsString();
                    if (GETINSTANCE_NAMES.contains(methodName)) {
                        String returnType = method.getTypeAsString();
                        if (returnType.equals(classDecl.getNameAsString())) {
                            hasGetInstanceMethod = true;
                            singletonInfo.getInstanceMethodName = methodName;

                            // Check if method body contains synchronized (thread-safe)
                            if (method.getBody().isPresent()) {
                                String bodyStr = method.getBody().get().toString();
                                if (bodyStr.contains("synchronized")) {
                                    singletonInfo.isThreadSafe = true;
                                }
                            }
                            break;
                        }
                    }
                }
            }

            // Determine if this is a singleton
            isSingleton = hasPrivateConstructor && (hasStaticInstance || hasGetInstanceMethod);

            if (isSingleton) {
                // Check if singleton has state (non-static fields)
                for (FieldDeclaration field : classDecl.getFields()) {
                    if (!field.isStatic()) {
                        singletonInfo.hasState = true;
                        break;
                    }
                }

                singletonInfo.type = determineType(hasStaticInstance, singletonInfo.isLazyInit);
            }

            super.visit(classDecl, arg);
        }

        private String determineType(boolean hasStaticInstance, boolean isLazy) {
            if (hasStaticInstance && !isLazy) {
                return "EAGER";
            } else if (isLazy) {
                return "LAZY";
            } else {
                return "UNKNOWN";
            }
        }
    }

    /**
     * Data class to hold Singleton pattern analysis information.
     */
    public static class SingletonInfo {
        public String className;
        public String type; // EAGER, LAZY, ENUM
        public boolean isThreadSafe;
        public boolean hasState;
        public boolean isLazyInit;
        public String instanceFieldName;
        public String getInstanceMethodName;
        public String migrationTarget = "Spring @Component with @Scope(\"singleton\")";
    }
}
