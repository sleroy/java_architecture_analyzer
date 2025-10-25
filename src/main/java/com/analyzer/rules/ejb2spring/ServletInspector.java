package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.source.AbstractJavaClassInspector;
import com.analyzer.resource.ResourceResolver;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.*;

/**
 * Inspector that detects Servlet implementations in Java source code for
 * EJB-to-Spring migration analysis.
 * 
 * <p>
 * This inspector identifies classes that extend javax.servlet.http.HttpServlet
 * (CS-020)
 * which are candidates for migration to Spring Boot @RestController
 * or @Controller implementations.
 * </p>
 * 
 * <p>
 * Servlet detection is crucial for understanding web endpoints in legacy
 * applications
 * and mapping them to modern Spring MVC patterns.
 * </p>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        ServletInspector.TAGS.TAG_IS_SERVLET,
        EjbMigrationTags.SPRING_COMPONENT_CONVERSION,
        EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM
})
public class ServletInspector extends AbstractJavaClassInspector {

    @Inject
    public ServletInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classNodeRepository);
    }

    @Override
    public String getName() {
        return "Servlet Implementation Detector";
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                NodeDecorator projectFileDecorator) {

        if (type instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) type;
            ServletDetector detector = new ServletDetector();
            classDecl.accept(detector, null);

            if (detector.isServlet()) {
                ServletInfo info = detector.getServletInfo();

                // Set tags according to the produces contract
                projectFileDecorator.setProperty(TAGS.TAG_IS_SERVLET, true);
                projectFileDecorator.setProperty(EjbMigrationTags.SPRING_COMPONENT_CONVERSION, true);
                projectFileDecorator.setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);

                // Set property on class node for detailed analysis
                classNode.setProperty("servlet.analysis", info.toString());

                // Set analysis statistics
                projectFileDecorator.setProperty("servlet.http_methods", info.httpMethodCount);

                // Set Spring Boot migration target based on servlet type
                if (info.isRestServlet) {
                    projectFileDecorator.setProperty("spring.conversion.target", "@RestController");
                } else {
                    projectFileDecorator.setProperty("spring.conversion.target", "@Controller");
                }
            }
        }
    }

    /**
     * Visitor that detects Servlet implementations by analyzing the class hierarchy
     * and method signatures.
     */
    private static class ServletDetector extends VoidVisitorAdapter<Void> {
        private final ServletInfo info = new ServletInfo();
        private boolean isServlet = false;

        // List of servlet parent class names to check
        private static final Set<String> SERVLET_CLASSES = Set.of(
                "HttpServlet", "javax.servlet.http.HttpServlet",
                "GenericServlet", "javax.servlet.GenericServlet");

        // HTTP method names to detect REST-style servlets
        private static final Set<String> HTTP_METHODS = Set.of(
                "doGet", "doPost", "doPut", "doDelete", "doHead",
                "doOptions", "doTrace", "service");

        public boolean isServlet() {
            return isServlet;
        }

        public ServletInfo getServletInfo() {
            return info;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            if (classDecl.isInterface()) {
                super.visit(classDecl, arg);
                return;
            }

            info.className = classDecl.getNameAsString();

            // Check if this class extends a Servlet class
            if (classDecl.getExtendedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType extendedType : classDecl.getExtendedTypes()) {
                    String typeName = extendedType.getNameAsString();
                    if (SERVLET_CLASSES.contains(typeName)) {
                        isServlet = true;
                        info.servletType = typeName;
                        break;
                    }
                }
            }

            super.visit(classDecl, arg);
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);

            // Only analyze further if we've determined this is a servlet
            if (!isServlet) {
                return;
            }

            String methodName = method.getNameAsString();

            // Check for HTTP methods
            if (HTTP_METHODS.contains(methodName)) {
                info.httpMethodCount++;
                info.httpMethods.add(methodName);

                // Simple heuristic - servlets with doGet/doPost but no forwarding are likely
                // REST-style
                if (methodName.equals("doGet") || methodName.equals("doPost")) {
                    // Check if method contains code for writing directly to response
                    String body = method.toString();
                    if ((body.contains("getWriter()") || body.contains("getOutputStream()")) &&
                            !body.contains("RequestDispatcher") &&
                            !body.contains("forward(") &&
                            !body.contains("include(")) {
                        info.isRestServlet = true;
                    }
                }
            }
        }
    }

    /**
     * Data class to hold Servlet implementation analysis information
     */
    public static class ServletInfo {
        public String className;
        public String servletType;
        public int httpMethodCount = 0;
        public List<String> httpMethods = new ArrayList<>();
        public boolean isRestServlet = false;

        @Override
        public String toString() {
            return String.format(
                    "Servlet{className=%s, type=%s, httpMethods=%d, methodList=%s, isRestStyle=%b}",
                    className,
                    servletType,
                    httpMethodCount,
                    httpMethods.toString(),
                    isRestServlet);
        }
    }

    public static class TAGS {
        public static final String TAG_IS_SERVLET = "servlet_inspector.is_servlet";
    }
}
