package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.Project;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.api.graph.JavaClassNode;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;

/**
 * Inspector that detects Session Bean implementations for EJB-to-Spring
 * migration analysis.
 * Identifies both EJB 2.x style (implements SessionBean) and EJB 3.x style
 * (@Stateless/@Stateful).
 *
 * <p>
 * This inspector automatically inherits all dependencies from
 * AbstractJavaParserInspector,
 * which includes SOURCE_FILE (from AbstractSourceFileInspector) and
 * Java-specific
 * dependencies (JAVA_DETECTED, JAVA_IS_SOURCE, LANGUAGE).
 * </p>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        SessionBeanJavaSourceInspector.TAGS.TAG_IS_SESSION_BEAN })
public class SessionBeanJavaSourceInspector extends AbstractJavaClassInspector {

    @Inject
    public SessionBeanJavaSourceInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classNodeRepository);
    }

    // No need for empty supports() method - @InspectorDependencies handles
    // filtering

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                NodeDecorator<ProjectFile> projectFileDecorator) {
        if (type instanceof ClassOrInterfaceDeclaration classDecl) {
            SessionBeanDetector detector = new SessionBeanDetector();
            classDecl.accept(detector, null);

            if (detector.isSessionBean()) {
                SessionBeanInfo info = detector.getSessionBeanInfo();
                // Honor produces contract: set tag on ProjectFile for dependency chains
                projectFileDecorator.setProperty(TAGS.TAG_IS_SESSION_BEAN, true);
                // Set analysis data as property on ClassNode for export
                classNode.setProperty(TAGS.TAG_IS_SESSION_BEAN, info);
            }
        }
    }

    @Override
    public String getName() {
        return "Session Bean Inspector";
    }

    public static class TAGS {
        public static final String TAG_IS_SESSION_BEAN = "ejb.is_session_bean";
    }

    /**
     * Visitor that detects Session Bean characteristics by analyzing the AST.
     * Detects Session Beans through:
     * 1. implements javax.ejb.SessionBean (EJB 2.x)
     * 2. @Stateless annotation (EJB 3.x)
     * 3. @Stateful annotation (EJB 3.x)
     */
    private static class SessionBeanDetector extends VoidVisitorAdapter<Void> {
        private final SessionBeanInfo sessionBeanInfo = new SessionBeanInfo();
        private boolean isSessionBean = false;

        public boolean isSessionBean() {
            return isSessionBean;
        }

        public SessionBeanInfo getSessionBeanInfo() {
            return sessionBeanInfo;
        }

        // Predefined lists for better readability and performance
        private static final java.util.Set<String> STATELESS_ANNOTATIONS = java.util.Set.of(
                "Stateless", "javax.ejb.Stateless");
        private static final java.util.Set<String> STATEFUL_ANNOTATIONS = java.util.Set.of(
                "Stateful", "javax.ejb.Stateful");
        private static final java.util.Set<String> SESSION_BEAN_INTERFACES = java.util.Set.of(
                "SessionBean", "javax.ejb.SessionBean");

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            if (classDecl.isInterface()) {
                super.visit(classDecl, arg);
                return;
            }

            sessionBeanInfo.className = classDecl.getNameAsString();

            // Check for EJB 3.x annotations
            for (AnnotationExpr annotation : classDecl.getAnnotations()) {
                String annotationName = annotation.getNameAsString();
                if (STATELESS_ANNOTATIONS.contains(annotationName)) {
                    isSessionBean = true;
                    sessionBeanInfo.sessionType = "STATELESS";
                    sessionBeanInfo.ejbVersion = "3.x";
                    sessionBeanInfo.migrationComplexity = "LOW";
                    return;
                } else if (STATEFUL_ANNOTATIONS.contains(annotationName)) {
                    isSessionBean = true;
                    sessionBeanInfo.sessionType = "STATEFUL";
                    sessionBeanInfo.ejbVersion = "3.x";
                    sessionBeanInfo.migrationComplexity = "MEDIUM";
                    return;
                }
            }

            // Check for EJB 2.x SessionBean interface implementation
            if (classDecl.getImplementedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType implementedType : classDecl.getImplementedTypes()) {
                    String typeName = implementedType.getNameAsString();
                    if (SESSION_BEAN_INTERFACES.contains(typeName)) {
                        isSessionBean = true;
                        sessionBeanInfo.sessionType = "UNKNOWN"; // Need to check ejb-jar.xml
                        sessionBeanInfo.ejbVersion = "2.x";
                        sessionBeanInfo.migrationComplexity = "HIGH";
                        return;
                    }
                }
            }

            super.visit(classDecl, arg);
        }
    }

    /**
     * Data class to hold Session Bean analysis information
     */
    public static class SessionBeanInfo {
        public String className;
        public String sessionType; // STATELESS, STATEFUL, UNKNOWN
        public String ejbVersion; // 2.x, 3.x
        public String migrationComplexity; // LOW, MEDIUM, HIGH
    }
}
