package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.detection.JavaSourceFileDetector;
import com.analyzer.inspectors.core.source.AbstractJavaParserInspector;
import com.analyzer.resource.ResourceResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.Set;

@InspectorDependencies(need = {JavaSourceFileDetector.class}, produces = {IdentifyServletSourceInspector.TAGS.TAG_IS_SERVLET})
public class IdentifyServletSourceInspector extends AbstractJavaParserInspector {

    public static class TAGS {
        public static final String TAG_IS_SERVLET = "identify_servlet_source_inspector.is_servlet";
    }

    private static final Set<String> SERVLET_ANNOTATIONS = Set.of(
            "WebServlet", "javax.servlet.annotation.WebServlet"
    );

    private static final Set<String> SERVLET_SUPERCLASSES = Set.of(
            "HttpServlet", "javax.servlet.http.HttpServlet"
    );

    private static final Set<String> SERVLET_INTERFACES = Set.of(
            "Servlet", "javax.servlet.Servlet"
    );

    private final ClassNodeRepository classNodeRepository;

    public IdentifyServletSourceInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
    }

    @Override
    protected void analyzeCompilationUnit(CompilationUnit cu, ProjectFile projectFile, ProjectFileDecorator projectFileDecorator) {
        ServletDetector detector = new ServletDetector();
        cu.accept(detector, null);
        
        boolean isServlet = detector.isServlet();
        
        // Honor produces contract - always set tag on ProjectFile
        projectFileDecorator.setTag(TAGS.TAG_IS_SERVLET, isServlet);
        
        // Also set property on ClassNode for analysis data if available
        classNodeRepository.getOrCreateClassNode(cu).ifPresent(classNode -> {
            classNode.setProjectFileId(projectFile.getId());
            classNode.setProperty(TAGS.TAG_IS_SERVLET, isServlet);
        });
    }

    @Override
    public String getName() {
        return "Identify Servlet";
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
                if (SERVLET_ANNOTATIONS.contains(annotationName)) {
                    isServlet = true;
                    return;
                }
            }

            // Check if extends HttpServlet
            if (classDecl.getExtendedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType extendedType : classDecl.getExtendedTypes()) {
                    String typeName = extendedType.getNameAsString();
                    if (SERVLET_SUPERCLASSES.contains(typeName)) {
                        isServlet = true;
                        return;
                    }
                }
            }

            // Check if implements Servlet interface
            if (classDecl.getImplementedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType implementedType : classDecl.getImplementedTypes()) {
                    String typeName = implementedType.getNameAsString();
                    if (SERVLET_INTERFACES.contains(typeName)) {
                        isServlet = true;
                        return;
                    }
                }
            }

            super.visit(classDecl, arg);
        }
    }

}
