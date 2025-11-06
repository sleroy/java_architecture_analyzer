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
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Inspector that detects Service Locator patterns in Java source code for
 * EJB-to-Spring migration analysis.
 * 
 * <p>
 * The Service Locator pattern (CS-019) is a common anti-pattern in EJB
 * applications where
 * classes obtain EJB references through JNDI lookups instead of dependency
 * injection.
 * </p>
 * 
 * <p>
 * This inspector identifies:
 * <ul>
 * <li>Classes that centralize JNDI lookup operations</li>
 * <li>Factory methods that return EJB references</li>
 * <li>Patterns where the class is responsible for locating and providing
 * dependencies</li>
 * </ul>
 * </p>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        EjbMigrationTags.TAG_EJB_SERVICE_LOCATOR,
        EjbMigrationTags.TAG_SPRING_COMPONENT_CONVERSION,
})
public class ServiceLocatorInspector extends AbstractJavaClassInspector {

    @Inject
    public ServiceLocatorInspector(ResourceResolver resourceResolver,
            ClassNodeRepository classNodeRepository,
            LocalCache localCache) {
        super(resourceResolver, classNodeRepository, localCache);
    }

    @Override
    public String getName() {
        return "Service Locator Pattern Detector";
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
            NodeDecorator<ProjectFile> projectFileDecorator) {

        ServiceLocatorDetector detector = new ServiceLocatorDetector();
        type.accept(detector, null);

        if (detector.isServiceLocator()) {
            ServiceLocatorInfo info = detector.getServiceLocatorInfo();

            // Set tags according to the produces contract
            projectFileDecorator.enableTag(EjbMigrationTags.TAG_EJB_SERVICE_LOCATOR);
            projectFileDecorator.enableTag(EjbMigrationTags.TAG_SPRING_COMPONENT_CONVERSION);
            projectFileDecorator.getMetrics().setMaxMetric(EjbMigrationTags.TAG_METRIC_MIGRATION_COMPLEXITY, EjbMigrationTags.COMPLEXITY_MEDIUM);

            // Set property on class node for detailed analysis
            classNode.setProperty("service.locator.analysis", info);

            // Set analysis statistics
            projectFileDecorator.setMetric("service.locator.jndi_calls", info.jndiLookupCount);
            projectFileDecorator.setMetric("service.locator.factory_methods", info.factoryMethodCount);
        }
    }

    /**
     * Visitor that detects Service Locator patterns by analyzing the AST.
     * Looks for JNDI lookup calls and factory method patterns.
     */
    private static class ServiceLocatorDetector extends VoidVisitorAdapter<Void> {
        private final ServiceLocatorInfo info = new ServiceLocatorInfo();
        private boolean isServiceLocator = false;

        // JNDI lookup method patterns
        private static final Set<String> JNDI_LOOKUP_METHODS = Set.of(
                "lookup", "lookupLocal", "lookupRemote", "getEjbHome", "getEjbObject",
                "getObject", "getInitialContext", "createInitialContext", "getJndiContext");

        // JNDI context class patterns
        private static final Set<String> JNDI_CONTEXT_CLASSES = Set.of(
                "Context", "InitialContext", "javax.naming.Context",
                "javax.naming.InitialContext", "NamingContext", "JndiContext");

        // Factory method name patterns
        private static final Set<String> FACTORY_METHOD_NAMES = Set.of(
                "get", "create", "getInstance", "lookup", "getService", "getBean", "createService");

        public boolean isServiceLocator() {
            return isServiceLocator;
        }

        public ServiceLocatorInfo getServiceLocatorInfo() {
            return info;
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);

            // Check if method looks like a factory method
            String methodName = method.getNameAsString();

            boolean returnsInterface = method.getType().isClassOrInterfaceType() &&
                    !method.getType().asClassOrInterfaceType().getNameAsString().equals("void");

            boolean hasFactoryName = FACTORY_METHOD_NAMES.stream()
                    .anyMatch(factoryName -> methodName.contains(factoryName));

            if (returnsInterface && hasFactoryName) {
                info.factoryMethodCount++;
                info.factoryMethods.add(methodName);
            }
        }

        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);

            String methodName = methodCall.getNameAsString();

            // Check for JNDI lookup calls
            if (JNDI_LOOKUP_METHODS.contains(methodName)) {
                if (methodCall.getScope().isPresent()) {
                    String scope = methodCall.getScope().get().toString();
                    if (JNDI_CONTEXT_CLASSES.stream().anyMatch(scope::contains)) {
                        info.jndiLookupCount++;
                        info.jndiCalls.add(methodCall.toString());
                    }
                }
            }

            // If we have JNDI calls or factory methods, mark as service locator
            if (info.jndiLookupCount > 0 || info.factoryMethodCount > 1) {
                isServiceLocator = true;
            }
        }
    }

    /**
     * Data class to hold Service Locator pattern analysis information
     */
    public static class ServiceLocatorInfo {
        public int jndiLookupCount = 0;
        public int factoryMethodCount = 0;
        public List<String> jndiCalls = new ArrayList<>();
        public List<String> factoryMethods = new ArrayList<>();

        @Override
        public String toString() {
            return String.format(
                    "ServiceLocator{jndiLookups=%d, factoryMethods=%d, jndiCallsDetected=%s, factoryMethodsDetected=%s}",
                    jndiLookupCount,
                    factoryMethodCount,
                    jndiCalls.toString(),
                    factoryMethods.toString());
        }
    }

    public static class TAGS {
        public static final String TAG_IS_SERVICE_LOCATOR = "service_locator_inspector.is_service_locator";
    }
}
