package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.*;

/**
 * Inspector that detects Utility/Helper classes in Java source code
 * for EJB-to-Spring migration analysis.
 * 
 * <p>
 * The UtilityHelperInspector (CS-033) identifies classes that contain only
 * static methods,
 * which are candidates for conversion to Spring components or kept as static
 * utility classes
 * in a Spring Boot application.
 * </p>
 * 
 * <p>
 * Utility class characteristics detected include:
 * <ul>
 * <li>Classes with only static methods</li>
 * <li>Classes with utility/helper naming patterns</li>
 * <li>Classes with private constructors to prevent instantiation</li>
 * <li>Classes that don't maintain state (no instance fields)</li>
 * </ul>
 * </p>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        UtilityHelperInspector.TAGS.TAG_IS_UTILITY,
        EjbMigrationTags.TAG_SPRING_COMPONENT_CONVERSION,
})
public class UtilityHelperInspector extends AbstractJavaClassInspector {

    @Inject
    public UtilityHelperInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository, LocalCache localCache) {
        super(resourceResolver, classNodeRepository, localCache);
    }

    @Override
    public String getName() {
        return "Utility/Helper Class Detector";
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                NodeDecorator<ProjectFile> projectFileDecorator) {

        if (!(type instanceof ClassOrInterfaceDeclaration classDecl)) {
            return;
        }

        String className = classDecl.getNameAsString();

        // Initial assessment based on class name
        boolean hasUtilityName = className.contains("Util") ||
                className.contains("Helper") ||
                className.contains("Utils") ||
                className.endsWith("s"); // Common pattern for utility classes (e.g. Collections, Arrays)

        // Detailed code analysis
        UtilityClassDetector detector = new UtilityClassDetector();
        classDecl.accept(detector, null);

        // Decision logic - a class is a utility class if it has a utility name AND
        // has utility class characteristics OR if it has all utility characteristics
        // regardless of name
        boolean isUtilityClass = (hasUtilityName && detector.hasUtilityCharacteristics()) ||
                detector.isStrictUtilityClass();

        if (isUtilityClass) {
            UtilityInfo info = detector.getUtilityInfo();
            info.className = className;
            info.hasUtilityName = hasUtilityName;

            // Set tags according to the produces contract
            projectFileDecorator.enableTag(TAGS.TAG_IS_UTILITY);
            projectFileDecorator.enableTag(EjbMigrationTags.TAG_SPRING_COMPONENT_CONVERSION);
            projectFileDecorator.getMetrics().setMaxMetric(EjbMigrationTags.METRIC_MIGRATION_COMPLEXITY, EjbMigrationTags.COMPLEXITY_LOW);

            // Set property on class node for detailed analysis
            classNode.setProperty("utility.analysis", info.toString());

            // Set analysis statistics
            projectFileDecorator.setMetric("utility.static_method_count", info.staticMethodCount);
            projectFileDecorator.setMetric("utility.instance_method_count", info.instanceMethodCount);
            projectFileDecorator.setMetric("utility.static_method_ratio", info.getStaticMethodRatio());

            // Set appropriate Spring Boot migration target
            if (info.hasStateManagement) {
                // If it has state, convert to a managed component
                projectFileDecorator.setProperty("spring.conversion.target", "@Component");
            } else {
                // Otherwise, keep as static utility class
                projectFileDecorator.setProperty("spring.conversion.target", "Static Utility Class");
            }
        }
    }

    /**
     * Visitor that detects utility class characteristics by analyzing
     * method modifiers, constructors, and field declarations.
     */
    private static class UtilityClassDetector extends VoidVisitorAdapter<Void> {
        private final UtilityInfo info = new UtilityInfo();

        public boolean hasUtilityCharacteristics() {
            // A class has utility characteristics if:
            // 1. It has static methods
            // 2. It has a high ratio of static to instance methods
            // 3. It has a private constructor or no constructors
            return info.staticMethodCount > 0 &&
                    (info.getStaticMethodRatio() > 0.7 ||
                            info.hasPrivateConstructor ||
                            (info.constructorCount == 0 && !info.hasStateManagement));
        }

        public boolean isStrictUtilityClass() {
            // A class is strictly a utility class if:
            // 1. ALL methods are static
            // 2. It has no instance fields
            // 3. It has a private constructor or no constructors
            return info.staticMethodCount > 0 &&
                    info.instanceMethodCount == 0 &&
                    !info.hasStateManagement &&
                    (info.hasPrivateConstructor || info.constructorCount == 0);
        }

        public UtilityInfo getUtilityInfo() {
            return info;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            // Don't consider interfaces, abstract classes, or inner classes
            if (classDecl.isInterface() ||
                    classDecl.isAbstract() ||
                    classDecl.isInnerClass()) {
                return;
            }

            super.visit(classDecl, arg);

            // Check for instance fields that would indicate state management
            classDecl.getFields().forEach(field -> {
                if (!field.isStatic()) {
                    info.hasStateManagement = true;
                    info.nonStaticFieldCount++;
                } else {
                    info.staticFieldCount++;
                }
            });
        }

        @Override
        public void visit(ConstructorDeclaration constructor, Void arg) {
            super.visit(constructor, arg);

            info.constructorCount++;

            // Check for private constructor - common in utility classes
            if (constructor.getModifiers().contains(Modifier.privateModifier())) {
                info.hasPrivateConstructor = true;
            }
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);

            // Don't count constructors
            if (method.isConstructorDeclaration()) {
                return;
            }

            // Track static vs. instance methods
            if (method.isStatic()) {
                info.staticMethodCount++;
                info.staticMethods.add(method.getNameAsString());

                // Check for common utility method names
                String methodName = method.getNameAsString().toLowerCase();
                if (methodName.startsWith("create") ||
                        methodName.startsWith("get") ||
                        methodName.startsWith("format") ||
                        methodName.startsWith("convert") ||
                        methodName.startsWith("parse") ||
                        methodName.startsWith("build")) {
                    info.utilityMethodPatternCount++;
                }
            } else {
                info.instanceMethodCount++;
                info.instanceMethods.add(method.getNameAsString());
            }
        }
    }

    /**
     * Data class to hold Utility class analysis information
     */
    public static class UtilityInfo {
        public String className;
        public boolean hasUtilityName = false;
        public boolean hasPrivateConstructor = false;
        public boolean hasStateManagement = false;
        public int staticMethodCount = 0;
        public int instanceMethodCount = 0;
        public int staticFieldCount = 0;
        public int nonStaticFieldCount = 0;
        public int constructorCount = 0;
        public int utilityMethodPatternCount = 0;
        public List<String> staticMethods = new ArrayList<>();
        public List<String> instanceMethods = new ArrayList<>();

        public double getStaticMethodRatio() {
            int total = staticMethodCount + instanceMethodCount;
            return total > 0 ? (double) staticMethodCount / total : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                    "UtilityClass{class=%s, utilityName=%b, privateConstructor=%b, state=%b, " +
                            "staticMethods=%d, instanceMethods=%d, staticMethodRatio=%.2f, " +
                            "staticFields=%d, instanceFields=%d, utilityMethodPatterns=%d}",
                    className,
                    hasUtilityName,
                    hasPrivateConstructor,
                    hasStateManagement,
                    staticMethodCount,
                    instanceMethodCount,
                    getStaticMethodRatio(),
                    staticFieldCount,
                    nonStaticFieldCount,
                    utilityMethodPatternCount);
        }
    }

    public static class TAGS {
        public static final String TAG_IS_UTILITY = "utility_helper_inspector.is_utility";
    }
}
