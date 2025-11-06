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
 * Inspector that detects utility class antipattern.
 * Identifies classes with all static methods and private constructor.
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        EjbMigrationTags.TAG_ANTIPATTERN_UTILITY_CLASS })
public class UtilityClassInspector extends AbstractJavaClassInspector {

    @Inject
    public UtilityClassInspector(ResourceResolver resourceResolver,
            ClassNodeRepository classNodeRepository,
            LocalCache localCache) {
        super(resourceResolver, classNodeRepository, localCache);
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
            NodeDecorator<ProjectFile> projectFileDecorator) {
        if (type instanceof ClassOrInterfaceDeclaration classDecl && !classDecl.isInterface()) {
            UtilityClassDetector detector = new UtilityClassDetector();
            classDecl.accept(detector, null);

            if (detector.isUtilityClass()) {
                UtilityClassInfo info = detector.getUtilityClassInfo();

                // Tag ProjectFile and JavaClassNode
                projectFileDecorator.enableTag(EjbMigrationTags.TAG_ANTIPATTERN_UTILITY_CLASS);
                classNode.enableTag(EjbMigrationTags.TAG_ANTIPATTERN_UTILITY_CLASS);

                // Store analysis data
                classNode.setProperty("antipattern.utility.info", info);

                // Calculate refactoring complexity
                double complexity = calculateComplexity(info);
                classNode.setProperty(EjbMigrationTags.TAG_METRIC_MIGRATION_COMPLEXITY, complexity);
            }
        }
    }

    private double calculateComplexity(UtilityClassInfo info) {
        // Base complexity
        double complexity = EjbMigrationTags.COMPLEXITY_LOW;

        // Increase based on method count
        if (info.staticMethodCount > 10) {
            complexity = EjbMigrationTags.COMPLEXITY_MEDIUM;
        }
        if (info.staticMethodCount > 20) {
            complexity = EjbMigrationTags.COMPLEXITY_HIGH;
        }

        return complexity;
    }

    @Override
    public String getName() {
        return "Utility Class Antipattern Inspector";
    }

    /**
     * Visitor that detects utility class characteristics.
     */
    private static class UtilityClassDetector extends VoidVisitorAdapter<Void> {
        private final UtilityClassInfo utilityClassInfo = new UtilityClassInfo();
        private boolean isUtilityClass = false;

        private static final java.util.Set<String> UTILITY_SUFFIXES = java.util.Set.of(
                "Utils", "Util", "Helper", "Helpers", "Tools", "Tool");

        public boolean isUtilityClass() {
            return isUtilityClass;
        }

        public UtilityClassInfo getUtilityClassInfo() {
            return utilityClassInfo;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            utilityClassInfo.className = classDecl.getNameAsString();

            // Check naming pattern
            for (String suffix : UTILITY_SUFFIXES) {
                if (utilityClassInfo.className.endsWith(suffix)) {
                    utilityClassInfo.hasUtilityNaming = true;
                    break;
                }
            }

            int totalMethods = 0;
            int staticMethods = 0;
            int publicMethods = 0;
            boolean hasPrivateConstructor = false;
            boolean hasNonStaticFields = false;

            // Count methods
            for (MethodDeclaration method : classDecl.getMethods()) {
                totalMethods++;
                if (method.isStatic()) {
                    staticMethods++;
                }
                if (method.isPublic()) {
                    publicMethods++;
                }
                utilityClassInfo.methodNames.add(method.getNameAsString());
            }

            // Check constructor
            for (ConstructorDeclaration constructor : classDecl.getConstructors()) {
                if (constructor.isPrivate()) {
                    hasPrivateConstructor = true;
                }
            }

            // Check if has default constructor (none declared means public default)
            boolean hasNoConstructor = classDecl.getConstructors().isEmpty();

            // Check fields
            for (FieldDeclaration field : classDecl.getFields()) {
                if (!field.isStatic()) {
                    hasNonStaticFields = true;
                    break;
                }
            }

            utilityClassInfo.staticMethodCount = staticMethods;
            utilityClassInfo.totalMethodCount = totalMethods;
            utilityClassInfo.publicMethodCount = publicMethods;
            utilityClassInfo.hasPrivateConstructor = hasPrivateConstructor;

            // Determine if this is a utility class
            // Criteria: all methods are static, private constructor (or no constructor with
            // utility naming), and no non-static fields
            boolean allMethodsStatic = totalMethods > 0 && staticMethods == totalMethods;
            boolean constructorOk = hasPrivateConstructor || (hasNoConstructor && utilityClassInfo.hasUtilityNaming);

            isUtilityClass = allMethodsStatic && constructorOk && !hasNonStaticFields && totalMethods >= 3;

            super.visit(classDecl, arg);
        }
    }

    /**
     * Data class to hold utility class analysis information.
     */
    public static class UtilityClassInfo {
        public String className;
        public boolean hasUtilityNaming;
        public boolean hasPrivateConstructor;
        public int staticMethodCount;
        public int totalMethodCount;
        public int publicMethodCount;
        public List<String> methodNames = new ArrayList<>();
        public String migrationTarget = "Spring @Component with instance methods";
    }
}
