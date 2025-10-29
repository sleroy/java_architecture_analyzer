package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects classes that act as Factory or Provider beans, manually creating and
 * providing instances.
 * These classes are candidates for Spring @Bean configuration methods.
 *
 * <p>
 * Factory patterns typically include:
 * </p>
 * <ul>
 * <li>Classes with "Factory" or "Provider" in their name</li>
 * <li>Classes with methods that return new instances of other classes</li>
 * <li>Classes with getInstance() or createXXX() methods that return
 * objects</li>
 * <li>Classes that have a significant number of "new" expressions in return
 * statements</li>
 * </ul>
 *
 * <p>
 * Migration Target: Spring @Bean configuration methods
 * </p>
 *
 * @see <a href=
 *      "https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/Bean.html">Spring @Bean</a>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        EjbMigrationTags.SPRING_CONFIG_CONVERSION,
        EjbMigrationTags.SPRING_COMPONENT_CONVERSION,
        EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM
})
public class FactoryBeanProviderInspector extends AbstractJavaClassInspector {

    private static final List<String> FACTORY_PATTERNS_IN_NAME = Arrays.asList(
            "Factory", "Provider", "Builder", "Creator", "Generator");

    private static final List<String> FACTORY_METHOD_PREFIXES = Arrays.asList(
            "create", "get", "build", "make", "new", "obtain", "provide");

    @Inject
    public FactoryBeanProviderInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classNodeRepository);
    }

    @Override
    public String getName() {
        return "Factory Bean Provider Detector";
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                NodeDecorator<ProjectFile> projectFileDecorator) {

        if (!(type instanceof ClassOrInterfaceDeclaration)) {
            return;
        }

        FactoryBeanDetector detector = new FactoryBeanDetector();
        type.accept(detector, null);

        if (detector.isFactoryBean()) {
            FactoryBeanInfo info = detector.getFactoryBeanInfo();

            // Set tags according to the produces contract
            projectFileDecorator.setProperty(EjbMigrationTags.SPRING_CONFIG_CONVERSION, true);
            projectFileDecorator.setProperty(EjbMigrationTags.SPRING_COMPONENT_CONVERSION, true);
            projectFileDecorator.setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);

            // Set property on class node for detailed analysis
            classNode.setProperty("factory.bean.analysis", info.toString());

            // Set analysis statistics
            projectFileDecorator.setProperty("factory.bean.factory_methods", info.factoryMethodCount);
            projectFileDecorator.setProperty("factory.bean.object_creations", info.objectCreationCount);
        }
    }

    /**
     * Visitor that detects Factory Bean Provider patterns by analyzing the AST.
     * Looks for methods that create and return objects.
     */
    private static class FactoryBeanDetector extends VoidVisitorAdapter<Void> {
        private final FactoryBeanInfo info = new FactoryBeanInfo();
        private boolean isFactoryBean = false;

        public boolean isFactoryBean() {
            return isFactoryBean;
        }

        public FactoryBeanInfo getFactoryBeanInfo() {
            return info;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            super.visit(classDecl, arg);

            // Check if class name indicates a factory pattern
            String className = classDecl.getNameAsString();
            for (String pattern : FACTORY_PATTERNS_IN_NAME) {
                if (className.contains(pattern)) {
                    info.hasFactoryNamePattern = true;
                    isFactoryBean = true;
                    break;
                }
            }

            // If we have factory methods or the class name indicates a factory pattern,
            // mark as factory bean
            if (info.factoryMethodCount > 1 || (info.factoryMethodCount > 0 && info.objectCreationCount > 0)
                    || info.hasFactoryNamePattern) {
                isFactoryBean = true;
            }
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);

            // Skip void methods - factories typically return something
            if (method.getType().isVoidType()) {
                return;
            }

            // Method name indicates factory pattern
            String methodName = method.getNameAsString();
            boolean isFactoryMethod = false;

            // Check if method has a factory name prefix
            for (String prefix : FACTORY_METHOD_PREFIXES) {
                if (methodName.startsWith(prefix) &&
                        (methodName.length() == prefix.length() ||
                                Character.isUpperCase(methodName.charAt(prefix.length())))) {
                    isFactoryMethod = true;
                    break;
                }
            }

            // Static getInstance() type methods
            if (method.isStatic() &&
                    (methodName.equals("getInstance") ||
                            methodName.equals("getSingleton"))) {
                isFactoryMethod = true;
            }

            // Check if method has returns with "new" expressions
            boolean hasObjectCreation = false;
            for (ReturnStmt returnStmt : method.findAll(ReturnStmt.class)) {
                if (returnStmt.getExpression().isPresent()) {
                    List<ObjectCreationExpr> newExpressions = returnStmt.getExpression().get()
                            .findAll(ObjectCreationExpr.class);

                    if (!newExpressions.isEmpty()) {
                        hasObjectCreation = true;
                        info.objectCreationCount += newExpressions.size();
                        info.objectCreations.add(methodName + ": " + newExpressions.size() + " instances");
                        break;
                    }
                }
            }

            if (isFactoryMethod || hasObjectCreation) {
                info.factoryMethodCount++;
                info.factoryMethods.add(methodName);
            }
        }
    }

    /**
     * Data class to hold Factory Bean pattern analysis information
     */
    public static class FactoryBeanInfo {
        public int factoryMethodCount = 0;
        public int objectCreationCount = 0;
        public boolean hasFactoryNamePattern = false;
        public List<String> factoryMethods = new ArrayList<>();
        public List<String> objectCreations = new ArrayList<>();

        @Override
        public String toString() {
            return String.format(
                    "FactoryBean{factoryMethods=%d, objectCreations=%d, hasFactoryName=%s, methods=%s, creations=%s}",
                    factoryMethodCount,
                    objectCreationCount,
                    hasFactoryNamePattern,
                    factoryMethods.toString(),
                    objectCreations.toString());
        }
    }
}
