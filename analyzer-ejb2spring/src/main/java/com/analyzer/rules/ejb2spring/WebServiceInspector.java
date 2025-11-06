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
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Inspector that detects JAX-WS Web Service implementations for EJB-to-Spring
 * migration analysis.
 * Identifies SOAP web services through @WebService and @WebMethod annotations.
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        EjbMigrationTags.TAG_WEBSERVICE_JAX_WS })
public class WebServiceInspector extends AbstractJavaClassInspector {

    @Inject
    public WebServiceInspector(ResourceResolver resourceResolver,
            ClassNodeRepository classNodeRepository,
            LocalCache localCache) {
        super(resourceResolver, classNodeRepository, localCache);
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
            NodeDecorator<ProjectFile> projectFileDecorator) {
        if (type instanceof ClassOrInterfaceDeclaration classDecl) {
            WebServiceDetector detector = new WebServiceDetector();
            classDecl.accept(detector, null);

            if (detector.isWebService()) {
                WebServiceInfo info = detector.getWebServiceInfo();

                // Tag ProjectFile for dependency chains
                projectFileDecorator.enableTag(EjbMigrationTags.TAG_WEBSERVICE_JAX_WS);

                // Tag JavaClassNode
                classNode.enableTag(EjbMigrationTags.TAG_WEBSERVICE_JAX_WS);
                classNode.enableTag(EjbMigrationTags.TAG_WEBSERVICE_SOAP_ENDPOINT);

                // Store analysis data as property
                classNode.setProperty("webservice.info", info);

                // Store migration complexity metric
                double complexity = calculateMigrationComplexity(info);
                classNode.setProperty(EjbMigrationTags.TAG_METRIC_MIGRATION_COMPLEXITY, complexity);
            }
        }
    }

    private double calculateMigrationComplexity(WebServiceInfo info) {
        // Base complexity
        double complexity = EjbMigrationTags.COMPLEXITY_LOW;

        // Increase complexity based on number of operations
        if (info.operationCount > 10) {
            complexity = EjbMigrationTags.COMPLEXITY_HIGH;
        } else if (info.operationCount > 5) {
            complexity = EjbMigrationTags.COMPLEXITY_MEDIUM;
        }

        // Additional complexity for SEI (Service Endpoint Interface)
        if (info.hasServiceEndpointInterface) {
            complexity += 1.0;
        }

        return Math.min(complexity, EjbMigrationTags.COMPLEXITY_HIGH);
    }

    @Override
    public String getName() {
        return "JAX-WS Web Service Inspector";
    }

    /**
     * Visitor that detects JAX-WS Web Service characteristics.
     */
    private static class WebServiceDetector extends VoidVisitorAdapter<Void> {
        private final WebServiceInfo webServiceInfo = new WebServiceInfo();
        private boolean isWebService = false;

        private static final java.util.Set<String> WEBSERVICE_ANNOTATIONS = java.util.Set.of(
                "WebService", "javax.jws.WebService", "jakarta.jws.WebService");

        private static final java.util.Set<String> WEBMETHOD_ANNOTATIONS = java.util.Set.of(
                "WebMethod", "javax.jws.WebMethod", "jakarta.jws.WebMethod");

        public boolean isWebService() {
            return isWebService;
        }

        public WebServiceInfo getWebServiceInfo() {
            return webServiceInfo;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            webServiceInfo.className = classDecl.getNameAsString();
            webServiceInfo.isInterface = classDecl.isInterface();

            // Check for @WebService annotation
            for (AnnotationExpr annotation : classDecl.getAnnotations()) {
                String annotationName = annotation.getNameAsString();
                if (WEBSERVICE_ANNOTATIONS.contains(annotationName)) {
                    isWebService = true;
                    webServiceInfo.hasWebServiceAnnotation = true;

                    // Check if it's a SEI (Service Endpoint Interface)
                    if (classDecl.isInterface()) {
                        webServiceInfo.hasServiceEndpointInterface = true;
                    }
                    break;
                }
            }

            // Count @WebMethod annotations
            if (isWebService) {
                for (MethodDeclaration method : classDecl.getMethods()) {
                    for (AnnotationExpr annotation : method.getAnnotations()) {
                        String annotationName = annotation.getNameAsString();
                        if (WEBMETHOD_ANNOTATIONS.contains(annotationName)) {
                            webServiceInfo.operationCount++;
                            webServiceInfo.operations.add(method.getNameAsString());
                            break;
                        }
                    }
                }

                // If no @WebMethod found, all public methods are operations
                if (webServiceInfo.operationCount == 0 && !classDecl.isInterface()) {
                    for (MethodDeclaration method : classDecl.getMethods()) {
                        if (method.isPublic()) {
                            webServiceInfo.operationCount++;
                            webServiceInfo.operations.add(method.getNameAsString());
                        }
                    }
                }
            }

            super.visit(classDecl, arg);
        }
    }

    /**
     * Data class to hold Web Service analysis information
     */
    public static class WebServiceInfo {
        public String className;
        public boolean isInterface;
        public boolean hasWebServiceAnnotation;
        public boolean hasServiceEndpointInterface;
        public int operationCount;
        public List<String> operations = new ArrayList<>();
        public String migrationTarget = "Spring @RestController"; // Default Spring target
    }
}
