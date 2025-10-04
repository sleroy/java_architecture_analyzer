package com.rules.ejb2spring;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorResult;
import com.analyzer.inspectors.core.source.JavaParserInspector;
import com.analyzer.resource.ResourceResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class IdentifyServletSourceInspector extends JavaParserInspector {

    public IdentifyServletSourceInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    protected InspectorResult analyzeCompilationUnit(CompilationUnit cu, ProjectFile clazz) {
        ServletDetector detector = new ServletDetector();
        cu.accept(detector, null);

        boolean isServlet = detector.isServlet();
        return new InspectorResult(getColumnName(), isServlet);
    }

    @Override
    public String getName() {
        return "Identify Servlet";
    }

    @Override
    public String getColumnName() {
        return "is_servlet";
    }

    /**
     * Visitor that detects servlet characteristics by analyzing the AST.
     * Detects servlets through:
     * 1. @WebServlet annotation
     * 2. extends HttpServlet
     * 3. implements Servlet interface
     */
    private static class ServletDetector extends VoidVisitorAdapter<Void> {
        private boolean isServlet = false;

        public boolean isServlet() {
            return isServlet;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            // Check for @WebServlet annotation
            for (AnnotationExpr annotation : classDecl.getAnnotations()) {
                String annotationName = annotation.getNameAsString();
                if ("WebServlet".equals(annotationName) ||
                        "javax.servlet.annotation.WebServlet".equals(annotationName)) {
                    isServlet = true;
                    return;
                }
            }

            // Check if extends HttpServlet
            if (classDecl.getExtendedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType extendedType : classDecl.getExtendedTypes()) {
                    String typeName = extendedType.getNameAsString();
                    if ("HttpServlet".equals(typeName) ||
                            "javax.servlet.http.HttpServlet".equals(typeName)) {
                        isServlet = true;
                        return;
                    }
                }
            }

            // Check if implements Servlet interface
            if (classDecl.getImplementedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType implementedType : classDecl.getImplementedTypes()) {
                    String typeName = implementedType.getNameAsString();
                    if ("Servlet".equals(typeName) ||
                            "javax.servlet.Servlet".equals(typeName)) {
                        isServlet = true;
                        return;
                    }
                }
            }

            super.visit(classDecl, arg);
        }
    }

}
