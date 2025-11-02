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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inspector that detects JAX-RS REST resource implementations for EJB-to-Spring
 * migration analysis.
 * Identifies REST resources through @Path, @GET, @POST, @PUT, @DELETE
 * annotations.
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = { EjbMigrationTags.REST_JAX_RS })
public class RestServiceInspector extends AbstractJavaClassInspector {

    @Inject
    public RestServiceInspector(ResourceResolver resourceResolver,
            ClassNodeRepository classNodeRepository,
            LocalCache localCache) {
        super(resourceResolver, classNodeRepository, localCache);
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
            NodeDecorator<ProjectFile> projectFileDecorator) {
        if (type instanceof ClassOrInterfaceDeclaration classDecl) {
            RestResourceDetector detector = new RestResourceDetector();
            classDecl.accept(detector, null);

            if (detector.isRestResource()) {
                RestResourceInfo info = detector.getRestResourceInfo();

                // Tag ProjectFile for dependency chains
                projectFileDecorator.enableTag(EjbMigrationTags.REST_JAX_RS);

                // Tag JavaClassNode
                classNode.enableTag(EjbMigrationTags.REST_JAX_RS);
                classNode.enableTag(EjbMigrationTags.REST_RESOURCE_ENDPOINT);

                // Store analysis data as property
                classNode.setProperty("rest.resource.info", info);

                // Store migration complexity metric
                double complexity = calculateMigrationComplexity(info);
                classNode.setProperty(EjbMigrationTags.METRIC_MIGRATION_COMPLEXITY, complexity);
            }
        }
    }

    private double calculateMigrationComplexity(RestResourceInfo info) {
        // Base complexity
        double complexity = EjbMigrationTags.COMPLEXITY_LOW;

        // Increase complexity based on number of endpoints
        if (info.endpointCount > 15) {
            complexity = EjbMigrationTags.COMPLEXITY_HIGH;
        } else if (info.endpointCount > 8) {
            complexity = EjbMigrationTags.COMPLEXITY_MEDIUM;
        }

        // Additional complexity for mixed HTTP methods
        if (info.httpMethods.size() > 3) {
            complexity += 0.5;
        }

        return Math.min(complexity, EjbMigrationTags.COMPLEXITY_HIGH);
    }

    @Override
    public String getName() {
        return "JAX-RS REST Resource Inspector";
    }

    /**
     * Visitor that detects JAX-RS REST resource characteristics.
     */
    private static class RestResourceDetector extends VoidVisitorAdapter<Void> {
        private final RestResourceInfo restResourceInfo = new RestResourceInfo();
        private boolean isRestResource = false;

        private static final java.util.Set<String> PATH_ANNOTATIONS = java.util.Set.of(
                "Path", "javax.ws.rs.Path", "jakarta.ws.rs.Path");

        private static final java.util.Set<String> HTTP_METHOD_ANNOTATIONS = java.util.Set.of(
                "GET", "javax.ws.rs.GET", "jakarta.ws.rs.GET",
                "POST", "javax.ws.rs.POST", "jakarta.ws.rs.POST",
                "PUT", "javax.ws.rs.PUT", "jakarta.ws.rs.PUT",
                "DELETE", "javax.ws.rs.DELETE", "jakarta.ws.rs.DELETE",
                "PATCH", "javax.ws.rs.PATCH", "jakarta.ws.rs.PATCH",
                "HEAD", "javax.ws.rs.HEAD", "jakarta.ws.rs.HEAD",
                "OPTIONS", "javax.ws.rs.OPTIONS", "jakarta.ws.rs.OPTIONS");

        private static final java.util.Set<String> PRODUCES_ANNOTATIONS = java.util.Set.of(
                "Produces", "javax.ws.rs.Produces", "jakarta.ws.rs.Produces");

        private static final java.util.Set<String> CONSUMES_ANNOTATIONS = java.util.Set.of(
                "Consumes", "javax.ws.rs.Consumes", "jakarta.ws.rs.Consumes");

        public boolean isRestResource() {
            return isRestResource;
        }

        public RestResourceInfo getRestResourceInfo() {
            return restResourceInfo;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            if (classDecl.isInterface()) {
                super.visit(classDecl, arg);
                return;
            }

            restResourceInfo.className = classDecl.getNameAsString();

            // Check for @Path annotation on class
            String classPath = null;
            for (AnnotationExpr annotation : classDecl.getAnnotations()) {
                String annotationName = annotation.getNameAsString();
                if (PATH_ANNOTATIONS.contains(annotationName)) {
                    isRestResource = true;
                    classPath = extractPathValue(annotation);
                    restResourceInfo.basePath = classPath;
                    break;
                }
            }

            // Analyze methods for HTTP method annotations
            if (isRestResource) {
                for (MethodDeclaration method : classDecl.getMethods()) {
                    RestEndpoint endpoint = analyzeMethod(method, classPath);
                    if (endpoint != null) {
                        restResourceInfo.endpointCount++;
                        restResourceInfo.endpoints.add(endpoint);
                        restResourceInfo.httpMethods.add(endpoint.httpMethod);
                    }
                }
            }

            super.visit(classDecl, arg);
        }

        private RestEndpoint analyzeMethod(MethodDeclaration method, String classPath) {
            RestEndpoint endpoint = null;
            String methodPath = null;
            String httpMethod = null;
            String produces = null;
            String consumes = null;

            for (AnnotationExpr annotation : method.getAnnotations()) {
                String annotationName = annotation.getNameAsString();

                if (PATH_ANNOTATIONS.contains(annotationName)) {
                    methodPath = extractPathValue(annotation);
                } else if (isHttpMethodAnnotation(annotationName)) {
                    httpMethod = extractHttpMethod(annotationName);
                } else if (PRODUCES_ANNOTATIONS.contains(annotationName)) {
                    produces = extractMediaType(annotation);
                } else if (CONSUMES_ANNOTATIONS.contains(annotationName)) {
                    consumes = extractMediaType(annotation);
                }
            }

            if (httpMethod != null) {
                endpoint = new RestEndpoint();
                endpoint.methodName = method.getNameAsString();
                endpoint.httpMethod = httpMethod;
                endpoint.path = buildFullPath(classPath, methodPath);
                endpoint.produces = produces != null ? produces : "application/json";
                endpoint.consumes = consumes != null ? consumes : "application/json";
            }

            return endpoint;
        }

        private boolean isHttpMethodAnnotation(String annotationName) {
            return HTTP_METHOD_ANNOTATIONS.contains(annotationName);
        }

        private String extractHttpMethod(String annotationName) {
            // Extract just the HTTP method name (GET, POST, etc.)
            if (annotationName.contains(".")) {
                return annotationName.substring(annotationName.lastIndexOf('.') + 1);
            }
            return annotationName;
        }

        private String extractPathValue(AnnotationExpr annotation) {
            // Simple extraction - in real implementation would parse annotation value
            return "/path";
        }

        private String extractMediaType(AnnotationExpr annotation) {
            // Simple extraction - in real implementation would parse annotation value
            return "application/json";
        }

        private String buildFullPath(String classPath, String methodPath) {
            if (classPath == null && methodPath == null)
                return "/";
            if (classPath == null)
                return methodPath;
            if (methodPath == null)
                return classPath;
            return classPath + methodPath;
        }
    }

    /**
     * Data class to hold REST resource analysis information
     */
    public static class RestResourceInfo {
        public String className;
        public String basePath;
        public int endpointCount;
        public List<RestEndpoint> endpoints = new ArrayList<>();
        public java.util.Set<String> httpMethods = new java.util.HashSet<>();
        public String migrationTarget = "Spring @RestController"; // Default Spring target
    }

    /**
     * Data class to hold REST endpoint information
     */
    public static class RestEndpoint {
        public String methodName;
        public String httpMethod;
        public String path;
        public String produces;
        public String consumes;
    }
}
