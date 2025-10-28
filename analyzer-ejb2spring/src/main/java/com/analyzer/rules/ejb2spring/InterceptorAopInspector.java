package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.Project;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects vendor interceptors and cross-cutting concerns implementations that
 * are
 * candidates for Spring AOP.
 * 
 * <p>
 * Interceptor patterns typically include:
 * </p>
 * <ul>
 * <li>Classes with EJB, CDI, or vendor-specific interceptor annotations</li>
 * <li>Classes with "Interceptor", "Aspect", or "Advice" in their name</li>
 * <li>Classes implementing cross-cutting concerns like logging, security,
 * transactions, etc.</li>
 * <li>Methods with interceptor callback or around-invoke patterns</li>
 * </ul>
 *
 * <p>
 * Migration Target: Spring AOP @Aspect classes
 * </p>
 *
 * @see <a href=
 *      "https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop">Spring
 *      AOP</a>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE,InspectorTags.TAG_APPLICATION_CLASS }, produces = {
        EjbMigrationTags.SPRING_COMPONENT_CONVERSION,
        EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM,
        EjbMigrationTags.CODE_MODERNIZATION
})
public class InterceptorAopInspector extends AbstractJavaClassInspector {

    private static final List<String> INTERCEPTOR_NAME_PATTERNS = Arrays.asList(
            "Interceptor", "Aspect", "Advice", "Handler", "Wrapper", "Filter", "Chain",
            "Security", "Logging", "Audit", "Transaction", "Metrics");

    private static final Set<String> INTERCEPTOR_ANNOTATION_NAMES = new HashSet<>(Arrays.asList(
            // EJB and CDI interceptors
            "Interceptor", "AroundInvoke", "AroundTimeout", "AroundConstruct",
            "PostConstruct", "PreDestroy", "PrePassivate", "PostActivate",
            // JBoss interceptors
            "ClientInterceptor", "ServerInterceptor", "Interceptors",
            // WebLogic interceptors
            "WLInterceptor", "WLInvocationInterceptor",
            // WebSphere interceptors
            "WSInterceptor", "WSCallbackInterceptor",
            // Generic AOP terms
            "Aspect", "Pointcut", "Before", "After", "Around"));

    private static final Set<String> INTERCEPTOR_IMPORT_PATTERNS = new HashSet<>(Arrays.asList(
            "javax.interceptor", "javax.ejb.AroundInvoke", "javax.annotation.PostConstruct",
            "javax.annotation.PreDestroy", "javax.ejb.PostActivate", "javax.ejb.PrePassivate",
            "org.jboss.aspects", "weblogic.ejb.interceptor", "com.ibm.websphere.ejbcontainer.interceptor"));

    @Inject
    public InterceptorAopInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classNodeRepository);
    }

    @Override
    public String getName() {
        return "Interceptor AOP Pattern Detector";
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                NodeDecorator<ProjectFile> projectFileDecorator) {

        if (!(type instanceof ClassOrInterfaceDeclaration)) {
            return;
        }

        ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) type;
        InterceptorDetector detector = new InterceptorDetector();

        // Get the compilation unit to access imports
        CompilationUnit compilationUnit = type.findCompilationUnit().orElse(null);
        if (compilationUnit != null) {
            detector.setCompilationUnit(compilationUnit);
        }

        // Visit the class to detect interceptor patterns
        type.accept(detector, null);

        if (detector.isInterceptor()) {
            InterceptorInfo info = detector.getInterceptorInfo();

            // Set tags according to the produces contract
            projectFileDecorator.setProperty(EjbMigrationTags.SPRING_COMPONENT_CONVERSION, true);
            projectFileDecorator.setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
            projectFileDecorator.setProperty(EjbMigrationTags.CODE_MODERNIZATION, true);

            // Set custom tags for more detailed analysis
            projectFileDecorator.setProperty("aop.interceptor.detected", true);
            projectFileDecorator.setProperty("spring.target.aspect", true);

            if (info.hasAroundInvoke) {
                projectFileDecorator.setProperty("aop.around_advice_candidate", true);
            }

            if (info.hasLifecycleCallbacks) {
                projectFileDecorator.setProperty("aop.lifecycle_advice_candidate", true);
            }

            // Set property on class node for detailed analysis
            classNode.setProperty("interceptor.aop.analysis", info.toString());
        }
    }

    /**
     * Visitor that detects Interceptor/AOP patterns by analyzing the AST.
     */
    private static class InterceptorDetector extends VoidVisitorAdapter<Void> {
        private final InterceptorInfo info = new InterceptorInfo();
        private boolean isInterceptor = false;
        private CompilationUnit compilationUnit = null;

        public boolean isInterceptor() {
            return isInterceptor;
        }

        public InterceptorInfo getInterceptorInfo() {
            return info;
        }

        public void setCompilationUnit(CompilationUnit compilationUnit) {
            this.compilationUnit = compilationUnit;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            super.visit(classDecl, arg);

            // Check if class name indicates an interceptor
            String className = classDecl.getNameAsString();
            for (String pattern : INTERCEPTOR_NAME_PATTERNS) {
                if (className.contains(pattern)) {
                    info.hasInterceptorNamePattern = true;
                    info.interceptorNamePatterns.add(pattern);
                    break;
                }
            }

            // Check class annotations
            for (AnnotationExpr annotation : classDecl.getAnnotations()) {
                String annotationName = annotation.getNameAsString();
                if (INTERCEPTOR_ANNOTATION_NAMES.contains(annotationName)) {
                    info.hasInterceptorAnnotation = true;
                    info.interceptorAnnotations.add(annotationName);
                }
            }

            // Check imports for interceptor-related packages
            if (compilationUnit != null) {
                for (ImportDeclaration importDecl : compilationUnit.getImports()) {
                    String importName = importDecl.getNameAsString();
                    for (String pattern : INTERCEPTOR_IMPORT_PATTERNS) {
                        if (importName.contains(pattern)) {
                            info.hasInterceptorImports = true;
                            info.interceptorImports.add(importName);
                            break;
                        }
                    }
                }
            }

            // Mark as interceptor if any of the indicators are present
            if (info.hasAroundInvoke || info.hasLifecycleCallbacks ||
                    info.hasInterceptorAnnotation || info.hasInterceptorImports ||
                    (info.hasInterceptorNamePattern && info.interceptorMethods.size() > 0)) {
                isInterceptor = true;
            }
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);

            // Check method annotations for interceptor patterns
            for (AnnotationExpr annotation : method.getAnnotations()) {
                String annotationName = annotation.getNameAsString();

                // Check for AroundInvoke (EJB interceptor method)
                if (annotationName.equals("AroundInvoke") || annotationName.contains("AroundInvoke")) {
                    info.hasAroundInvoke = true;
                    info.interceptorMethods.add("AroundInvoke: " + method.getNameAsString());
                }

                // Check for lifecycle callbacks
                if (annotationName.equals("PostConstruct") ||
                        annotationName.equals("PreDestroy") ||
                        annotationName.equals("PostActivate") ||
                        annotationName.equals("PrePassivate") ||
                        annotationName.equals("AroundConstruct")) {
                    info.hasLifecycleCallbacks = true;
                    info.lifecycleCallbacks.add(annotationName + ": " + method.getNameAsString());
                }
            }

            // Check method name for common interceptor patterns
            String methodName = method.getNameAsString();
            if (methodName.contains("invoke") || methodName.contains("intercept") ||
                    methodName.contains("advice") || methodName.contains("around") ||
                    methodName.contains("before") || methodName.contains("after")) {
                info.interceptorMethods.add(methodName);
            }
        }
    }

    /**
     * Data class to hold Interceptor/AOP pattern analysis information
     */
    public static class InterceptorInfo {
        public boolean hasInterceptorAnnotation = false;
        public boolean hasInterceptorNamePattern = false;
        public boolean hasInterceptorImports = false;
        public boolean hasAroundInvoke = false;
        public boolean hasLifecycleCallbacks = false;

        public List<String> interceptorAnnotations = new ArrayList<>();
        public List<String> interceptorNamePatterns = new ArrayList<>();
        public List<String> interceptorImports = new ArrayList<>();
        public List<String> interceptorMethods = new ArrayList<>();
        public List<String> lifecycleCallbacks = new ArrayList<>();

        @Override
        public String toString() {
            return String.format(
                    "InterceptorInfo{hasAnnotation=%s, hasNamePattern=%s, hasImports=%s, " +
                            "hasAroundInvoke=%s, hasLifecycleCallbacks=%s, " +
                            "annotations=%s, methods=%s}",
                    hasInterceptorAnnotation,
                    hasInterceptorNamePattern,
                    hasInterceptorImports,
                    hasAroundInvoke,
                    hasLifecycleCallbacks,
                    interceptorAnnotations.toString(),
                    interceptorMethods.toString());
        }
    }
}
