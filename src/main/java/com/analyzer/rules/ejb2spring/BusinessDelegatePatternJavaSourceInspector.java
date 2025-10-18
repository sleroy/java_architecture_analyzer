package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.ResultDecorator;
import com.analyzer.core.graph.GraphAwareInspector;
import com.analyzer.core.graph.GraphRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.source.AbstractJavaParserInspector;
import com.analyzer.resource.ResourceResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Inspector I-0709: Business Delegate Pattern Inspector
 * <p>
 * Detects Business Delegate pattern usage in EJB applications including:
 * - Business Delegate class detection
 * - Service Locator integration analysis
 * - Client usage pattern detection
 * - Anti-pattern assessment and migration complexity
 * <p>
 * Provides Spring Boot conversion recommendations for direct service injection.
 * <p>
 * Phase 1 - Core EJB Migration Analysis
 */
@InspectorDependencies(need = {
        EntityBeanInspector.class
}, produces = {
        EjbMigrationTags.EJB_BUSINESS_DELEGATE,
        EjbMigrationTags.EJB_SERVICE_LOCATOR,
        EjbMigrationTags.EJB_CLIENT_CODE,
        EjbMigrationTags.EJB_JNDI_LOOKUP,
        EjbMigrationTags.EJB_MIGRATION_HIGH_PRIORITY
})
public class BusinessDelegatePatternInspector extends AbstractJavaParserInspector implements GraphAwareInspector {

    private static final String INSPECTOR_ID = "I-0709";
    private static final String INSPECTOR_NAME = "Business Delegate Pattern Inspector";

    private static final Set<String> BUSINESS_DELEGATE_INDICATORS = Set.of(
            "BusinessDelegate", "Delegate", "ServiceDelegate", "EJBDelegate", "RemoteDelegate"
    );

    private static final Set<String> SERVICE_LOCATOR_INDICATORS = Set.of(
            "ServiceLocator", "ServiceFinder", "EJBServiceLocator", "Locator", "Finder"
    );

    private static final Set<String> JNDI_LOOKUP_METHODS = Set.of(
            "lookup", "lookupLink", "rebind", "bind"
    );

    public BusinessDelegatePatternInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    public void setGraphRepository(GraphRepository graphRepository) {
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        if (projectFile == null || !super.supports(projectFile)) {
            return false;
        }

        // Only process Java source files - require both the tag and .java extension
        return projectFile.getBooleanTag(InspectorTags.TAG_JAVA_IS_SOURCE, false) &&
                projectFile.getFilePath().toString().endsWith(".java");
    }

    @Override
    protected void analyzeCompilationUnit(CompilationUnit cu, ProjectFile projectFile,
                                          ResultDecorator resultDecorator) {

        BusinessDelegateDetector detector = new BusinessDelegateDetector();
        cu.accept(detector, null);

        BusinessDelegateMetadata metadata = detector.getMetadata();

        // Set pattern detection tags
        boolean isBusinessDelegate = metadata.isBusinessDelegate();
        boolean isServiceLocator = metadata.isServiceLocator();
        boolean usesBusinessDelegate = metadata.usesBusinessDelegate();
        boolean usesServiceLocator = metadata.usesServiceLocator();
        boolean hasJndiLookup = metadata.hasJndiLookup();

        projectFile.setTag(EjbMigrationTags.EJB_BUSINESS_DELEGATE, isBusinessDelegate);
        projectFile.setTag(EjbMigrationTags.EJB_SERVICE_LOCATOR, isServiceLocator || usesServiceLocator);
        projectFile.setTag(EjbMigrationTags.EJB_CLIENT_CODE, usesBusinessDelegate);
        projectFile.setTag(EjbMigrationTags.EJB_JNDI_LOOKUP, hasJndiLookup);

        if (isBusinessDelegate || isServiceLocator || usesBusinessDelegate || hasJndiLookup) {
            projectFile.setTag(EjbMigrationTags.EJB_MIGRATION_HIGH_PRIORITY, true);

            // Set migration complexity
            String complexity = assessMigrationComplexity(metadata);
            if ("HIGH".equals(complexity)) {
                projectFile.setTag(EjbMigrationTags.EJB_MIGRATION_COMPLEX, true);
            } else if ("MEDIUM".equals(complexity)) {
                projectFile.setTag(EjbMigrationTags.EJB_MIGRATION_MEDIUM, true);
            } else {
                projectFile.setTag(EjbMigrationTags.EJB_MIGRATION_SIMPLE, true);
            }

            // Store detailed metadata
            projectFile.setTag("business_delegate.pattern_type", metadata.getPatternType());
            projectFile.setTag("business_delegate.complexity", complexity);
            projectFile.setTag("business_delegate.delegate_method_count", String.valueOf(metadata.getDelegateMethodCount()));
            projectFile.setTag("business_delegate.jndi_lookup_count", String.valueOf(metadata.getJndiLookupCount()));

            // Generate migration recommendations
            List<String> recommendations = generateMigrationRecommendations(metadata);
            projectFile.setTag("business_delegate.recommendations", String.join("; ", recommendations));
        }
    }

    public String assessMigrationComplexity(BusinessDelegateMetadata metadata) {
        int complexityScore = 0;

        if (metadata.isBusinessDelegate()) {
            complexityScore += 1; // Base complexity for Business Delegate anti-pattern
            if (metadata.getDelegateMethodCount() > 5) {
                complexityScore += 2; // Additional complexity for many methods
            } else if (metadata.getDelegateMethodCount() > 2) {
                complexityScore += 1; // Medium complexity for some methods
            }
        }

        if (metadata.isServiceLocator()) {
            complexityScore += 2; // Service Locator complexity
        }

        if (metadata.usesServiceLocator()) {
            complexityScore += 1; // Service Locator usage complexity
        }

        if (metadata.getJndiLookupCount() > 3) {
            complexityScore += 2; // High JNDI complexity
        } else if (metadata.getJndiLookupCount() > 1) {
            complexityScore += 1; // Medium JNDI complexity
        }

        if (complexityScore <= 2) {
            return "LOW";
        } else if (complexityScore <= 5) {
            return "MEDIUM";
        } else {
            return "HIGH";
        }
    }

    private List<String> generateMigrationRecommendations(BusinessDelegateMetadata metadata) {
        List<String> recommendations = new ArrayList<>();

        if (metadata.isBusinessDelegate()) {
            recommendations.add("Replace Business Delegate with direct @Service injection");
            recommendations.add("Convert EJB lookups to Spring dependency injection");
            if (metadata.getDelegateMethodCount() > 5) {
                recommendations.add("Consider splitting large Business Delegate into multiple services");
            }
        }

        if (metadata.isServiceLocator()) {
            recommendations.add("Replace Service Locator with Spring ApplicationContext or @Autowired");
            recommendations.add("Externalize JNDI configurations to @ConfigurationProperties");
        }

        if (metadata.usesBusinessDelegate()) {
            recommendations.add("Refactor client code to use direct service dependencies");
            recommendations.add("Replace new BusinessDelegate() with @Autowired injection");
        }

        if (metadata.hasJndiLookup()) {
            recommendations.add("Replace JNDI lookups with Spring configuration");
            recommendations.add("Convert to @Value or @ConfigurationProperties for external resources");
        }

        return recommendations;
    }

    @Override
    public String getName() {
        return INSPECTOR_NAME + " (" + INSPECTOR_ID + ")";
    }

    /**
     * Visitor that detects Business Delegate and Service Locator patterns
     * by analyzing class names, method patterns, and JNDI usage.
     */
    private static class BusinessDelegateDetector extends VoidVisitorAdapter<Void> {
        private final BusinessDelegateMetadata metadata = new BusinessDelegateMetadata();
        private String currentClassName;

        public BusinessDelegateMetadata getMetadata() {
            return metadata;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            if (classDecl.isInterface()) {
                super.visit(classDecl, arg);
                return;
            }

            currentClassName = classDecl.getNameAsString();

            // Check if this is a Business Delegate class
            if (isBusinessDelegateClass(currentClassName)) {
                metadata.setBusinessDelegate(true);
                metadata.setPatternType("BUSINESS_DELEGATE");
                analyzeBusinessDelegateMethods(classDecl);
            }

            // Check if this is a Service Locator class or extends one
            boolean isServiceLocator = isServiceLocatorClass(currentClassName);
            boolean extendsServiceLocator = classDecl.getExtendedTypes().stream()
                    .anyMatch(extendedType -> isServiceLocatorClass(extendedType.getNameAsString()));

            if (isServiceLocator || extendsServiceLocator) {
                metadata.setServiceLocator(true);
                if (metadata.isBusinessDelegate()) {
                    metadata.setPatternType("COMPOSITE_PATTERN");
                } else {
                    metadata.setPatternType("SERVICE_LOCATOR");
                }
            }

            // Check for Business Delegate usage
            analyzeBusinessDelegateUsage(classDecl);

            // Check for Service Locator usage
            analyzeServiceLocatorUsage(classDecl);

            // Check for JNDI lookup patterns
            analyzeJndiLookups(classDecl);

            super.visit(classDecl, arg);
        }

        private boolean isBusinessDelegateClass(String className) {
            return BUSINESS_DELEGATE_INDICATORS.stream().anyMatch(className::contains);
        }

        private boolean isServiceLocatorClass(String className) {
            return SERVICE_LOCATOR_INDICATORS.stream().anyMatch(className::contains);
        }

        private void analyzeBusinessDelegateMethods(ClassOrInterfaceDeclaration classDecl) {
            int methodCount = 0;
            for (MethodDeclaration method : classDecl.getMethods()) {
                if (!isConstructor(method) && method.isPublic() && !isGetterSetter(method)) {
                    methodCount++;
                }
            }
            metadata.setDelegateMethodCount(methodCount);
        }

        private void analyzeBusinessDelegateUsage(ClassOrInterfaceDeclaration classDecl) {
            // Look for field declarations with Business Delegate types
            for (FieldDeclaration field : classDecl.getFields()) {
                String fieldType = field.getElementType().toString();
                if (BUSINESS_DELEGATE_INDICATORS.stream()
                        .anyMatch(fieldType::contains)) {
                    metadata.setUsesBusinessDelegate(true);
                    break;
                }
            }

            // Look for method calls to Business Delegate constructors or methods
            classDecl.findAll(MethodCallExpr.class).forEach(methodCall -> {
                if (methodCall.getScope().isPresent()) {
                    String scopeType = methodCall.getScope().get().toString();
                    if (BUSINESS_DELEGATE_INDICATORS.stream()
                            .anyMatch(scopeType::contains)) {
                        metadata.setUsesBusinessDelegate(true);
                    }
                }
            });
        }

        private void analyzeServiceLocatorUsage(ClassOrInterfaceDeclaration classDecl) {
            // Look for Service Locator field declarations
            for (FieldDeclaration field : classDecl.getFields()) {
                String fieldType = field.getElementType().toString();
                if (SERVICE_LOCATOR_INDICATORS.stream()
                        .anyMatch(fieldType::contains)) {
                    metadata.setUsesServiceLocator(true);
                    break;
                }
            }

            // Look for Service Locator method calls
            classDecl.findAll(MethodCallExpr.class).forEach(methodCall -> {
                if (methodCall.getScope().isPresent()) {
                    String scopeType = methodCall.getScope().get().toString();
                    if (SERVICE_LOCATOR_INDICATORS.stream()
                            .anyMatch(scopeType::contains)) {
                        metadata.setUsesServiceLocator(true);
                    }
                }
            });
        }

        private void analyzeJndiLookups(ClassOrInterfaceDeclaration classDecl) {
            classDecl.findAll(MethodCallExpr.class).forEach(methodCall -> {
                String methodName = methodCall.getNameAsString();
                if (JNDI_LOOKUP_METHODS.contains(methodName)) {
                    metadata.incrementJndiLookupCount();
                    metadata.setHasJndiLookup(true);
                }
            });
        }

        private boolean isConstructor(MethodDeclaration method) {
            return method.getNameAsString().equals(currentClassName);
        }

        private boolean isGetterSetter(MethodDeclaration method) {
            String methodName = method.getNameAsString();
            return (methodName.startsWith("get") && method.getParameters().isEmpty()) ||
                    (methodName.startsWith("set") && method.getParameters().size() == 1) ||
                    (methodName.startsWith("is") && method.getParameters().isEmpty());
        }
    }

    /**
     * Data class to hold Business Delegate pattern analysis metadata
     */
    public static class BusinessDelegateMetadata {
        private boolean isBusinessDelegate = false;
        private boolean isServiceLocator = false;
        private boolean usesBusinessDelegate = false;
        private boolean usesServiceLocator = false;
        private boolean hasJndiLookup = false;
        private int delegateMethodCount = 0;
        private int jndiLookupCount = 0;
        private String patternType;

        // Getters and setters
        public boolean isBusinessDelegate() {
            return isBusinessDelegate;
        }

        public void setBusinessDelegate(boolean isBusinessDelegate) {
            this.isBusinessDelegate = isBusinessDelegate;
        }

        public boolean isServiceLocator() {
            return isServiceLocator;
        }

        public void setServiceLocator(boolean isServiceLocator) {
            this.isServiceLocator = isServiceLocator;
        }

        public boolean usesBusinessDelegate() {
            return usesBusinessDelegate;
        }

        public void setUsesBusinessDelegate(boolean usesBusinessDelegate) {
            this.usesBusinessDelegate = usesBusinessDelegate;
        }

        public boolean usesServiceLocator() {
            return usesServiceLocator;
        }

        public void setUsesServiceLocator(boolean usesServiceLocator) {
            this.usesServiceLocator = usesServiceLocator;
        }

        public boolean hasJndiLookup() {
            return hasJndiLookup;
        }

        public void setHasJndiLookup(boolean hasJndiLookup) {
            this.hasJndiLookup = hasJndiLookup;
        }

        public int getDelegateMethodCount() {
            return delegateMethodCount;
        }

        public void setDelegateMethodCount(int delegateMethodCount) {
            this.delegateMethodCount = delegateMethodCount;
        }

        public int getJndiLookupCount() {
            return jndiLookupCount;
        }

        public void incrementJndiLookupCount() {
            this.jndiLookupCount++;
        }

        public String getPatternType() {
            return patternType;
        }

        public void setPatternType(String patternType) {
            this.patternType = patternType;
        }
    }
}
