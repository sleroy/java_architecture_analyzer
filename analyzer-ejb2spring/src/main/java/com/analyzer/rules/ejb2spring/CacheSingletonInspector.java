package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects classes that use static mutable fields or Double-Checked Locking
 * (DCL) pattern
 * for singleton/cache implementation. These classes are candidates for Spring
 * Cache or
 * singleton bean conversion.
 *
 * <p>
 * Cache/Singleton patterns typically include:
 * </p>
 * <ul>
 * <li>Classes with static instance fields (singleton pattern)</li>
 * <li>Double-checked locking pattern (synchronized blocks checking a field
 * twice)</li>
 * <li>Classes with "Cache" or "Singleton" in their name</li>
 * <li>Synchronized getInstance() methods</li>
 * </ul>
 *
 * <p>
 * Migration Target: Spring Cache integration or Spring beans with appropriate
 * scope
 * </p>
 *
 * @see <a href=
 *      "https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#cache">Spring
 *      Cache</a>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE,InspectorTags.TAG_APPLICATION_CLASS }, produces = {
        EjbMigrationTags.EJB_CACHING_PATTERN,
        EjbMigrationTags.SPRING_COMPONENT_CONVERSION,
        EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM
})
public class CacheSingletonInspector extends AbstractJavaClassInspector {

    private static final List<String> SINGLETON_CACHE_PATTERNS_IN_NAME = Arrays.asList(
            "Cache", "Singleton", "Registry", "Manager", "Store", "Pool", "Buffer", "Holder");

    @Inject
    public CacheSingletonInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classNodeRepository);
    }

    @Override
    public String getName() {
        return "Cache Singleton Pattern Detector";
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                NodeDecorator<ProjectFile> projectFileDecorator) {

        if (!(type instanceof ClassOrInterfaceDeclaration)) {
            return;
        }

        ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) type;
        CacheSingletonDetector detector = new CacheSingletonDetector();
        type.accept(detector, null);

        if (detector.isSingletonOrCache()) {
            CacheSingletonInfo info = detector.getCacheSingletonInfo();

            // Set tags according to the produces contract
            projectFileDecorator.setProperty(EjbMigrationTags.EJB_CACHING_PATTERN, true);
            projectFileDecorator.setProperty(EjbMigrationTags.SPRING_COMPONENT_CONVERSION, true);
            projectFileDecorator.setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);

            // Set property on class node for detailed analysis
            classNode.setProperty("cache.singleton.analysis", info.toString());

            // Set analysis statistics
            projectFileDecorator.setProperty("cache.singleton.static_fields", info.staticFieldCount);
            projectFileDecorator.setProperty("cache.singleton.has_dcl", info.hasDoubleCheckedLocking);

            // Set target Spring pattern depending on detected pattern
            if (info.hasDoubleCheckedLocking || info.hasSingletonMethods) {
                projectFileDecorator.setProperty("spring.target.singleton", true);
            } else if (info.staticFieldCount > 1) {
                projectFileDecorator.setProperty("spring.target.cache", true);
            }
        }
    }

    /**
     * Visitor that detects Cache/Singleton patterns by analyzing the AST.
     * Looks for static fields, synchronized methods, and double-checked locking.
     */
    private static class CacheSingletonDetector extends VoidVisitorAdapter<Void> {
        private final CacheSingletonInfo info = new CacheSingletonInfo();
        private boolean isSingletonOrCache = false;
        private boolean hasPrivateConstructor = false;

        public boolean isSingletonOrCache() {
            return isSingletonOrCache;
        }

        public CacheSingletonInfo getCacheSingletonInfo() {
            return info;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            super.visit(classDecl, arg);

            // Check if class name indicates a cache/singleton pattern
            String className = classDecl.getNameAsString();
            for (String pattern : SINGLETON_CACHE_PATTERNS_IN_NAME) {
                if (className.contains(pattern)) {
                    info.hasCacheOrSingletonNamePattern = true;
                    break;
                }
            }

            // Check if class has private constructors (singleton pattern)
            hasPrivateConstructor = classDecl.getConstructors().stream()
                    .anyMatch(ctor -> ctor.getModifiers().contains(Modifier.privateModifier()));
            info.hasPrivateConstructor = hasPrivateConstructor;

            // If we have any of the singleton/cache indicators, mark as singleton/cache
            if (info.staticFieldCount > 0 ||
                    info.hasDoubleCheckedLocking ||
                    info.hasSingletonMethods ||
                    (info.hasCacheOrSingletonNamePattern && hasPrivateConstructor)) {
                isSingletonOrCache = true;
            }
        }

        @Override
        public void visit(FieldDeclaration field, Void arg) {
            super.visit(field, arg);

            // Check for static fields (potentially singleton instance or cache storage)
            if (field.isStatic()) {
                for (VariableDeclarator variable : field.getVariables()) {
                    info.staticFieldCount++;
                    info.staticFields.add(variable.getNameAsString());
                }
            }
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);

            String methodName = method.getNameAsString();

            // Check for getInstance method pattern
            if ((methodName.equals("getInstance") || methodName.startsWith("get") && methodName.contains("Instance"))
                    && method.isStatic()) {
                info.hasSingletonMethods = true;
                info.singletonMethods.add(methodName);
            }

            // Check for synchronized getInstance method (simple singleton)
            if (method.getModifiers().contains(Modifier.synchronizedModifier()) &&
                    methodName.equals("getInstance")) {
                info.hasSynchronizedGetInstance = true;
                info.singletonMethods.add("synchronized " + methodName);
            }
        }

        @Override
        public void visit(SynchronizedStmt syncStmt, Void arg) {
            super.visit(syncStmt, arg);

            // Check the synchronized block for double-checked locking pattern
            // This is a simplistic check - may have false positives/negatives
            BlockStmt body = syncStmt.getBody();
            if (body.getStatements().size() > 0 && body.getStatements().get(0) instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) body.getStatements().get(0);
                // If there's an assignment in the if body, might be double-checked locking
                if (ifStmt.getThenStmt().findAll(AssignExpr.class).size() > 0) {
                    info.hasDoubleCheckedLocking = true;
                    info.dclPatterns.add(syncStmt.toString());
                }
            }
        }
    }

    /**
     * Data class to hold Cache/Singleton pattern analysis information
     */
    public static class CacheSingletonInfo {
        public int staticFieldCount = 0;
        public boolean hasDoubleCheckedLocking = false;
        public boolean hasSingletonMethods = false;
        public boolean hasSynchronizedGetInstance = false;
        public boolean hasCacheOrSingletonNamePattern = false;
        public boolean hasPrivateConstructor = false;
        public List<String> staticFields = new ArrayList<>();
        public List<String> singletonMethods = new ArrayList<>();
        public List<String> dclPatterns = new ArrayList<>();

        @Override
        public String toString() {
            return String.format(
                    "CacheSingleton{staticFields=%d, hasDoubleCheckedLocking=%s, " +
                            "hasSingletonMethods=%s, hasSynchronizedGetInstance=%s, " +
                            "hasCacheNamePattern=%s, hasPrivateConstructor=%s, " +
                            "fields=%s, methods=%s}",
                    staticFieldCount,
                    hasDoubleCheckedLocking,
                    hasSingletonMethods,
                    hasSynchronizedGetInstance,
                    hasCacheOrSingletonNamePattern,
                    hasPrivateConstructor,
                    staticFields.toString(),
                    singletonMethods.toString());
        }
    }
}
