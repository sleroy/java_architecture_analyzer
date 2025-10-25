package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.source.AbstractJavaParserInspector;
import com.analyzer.resource.ResourceResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Inspector that detects Message Driven Bean implementations for EJB-to-Spring
 * JMS migration analysis.
 * Identifies both EJB 2.x style (implements MessageDrivenBean) and EJB 3.x
 * style (@MessageDriven).
 * <p>
 * Uses the new annotation-based dependency system - inherits Java parser
 * dependencies
 * and adds resource requirements.
 */
@InspectorDependencies(requires = { EjbMigrationTags.EJB_BEAN_DETECTED }, produces = {
        MessageDrivenBeanInspector.TAGS.TAG_EJB_MESSAGE_DRIVEN_BEAN,
        MessageDrivenBeanInspector.TAGS.TAG_EJB_MDB_DETECTED,
        MessageDrivenBeanInspector.TAGS.TAG_JMS_CONSUMER,
        MessageDrivenBeanInspector.TAGS.TAG_ANNOTATION_MESSAGE_DRIVEN })
public class MessageDrivenBeanInspector extends AbstractJavaParserInspector {

    private final ClassNodeRepository classNodeRepository;

    @Inject
    public MessageDrivenBeanInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
    }

    @Override
    protected void analyzeCompilationUnit(CompilationUnit cu, ProjectFile projectFile,
                                          NodeDecorator projectFileDecorator) {
        classNodeRepository.getOrCreateClassNode(cu).ifPresent(classNode -> {
            classNode.setProjectFileId(projectFile.getId());
            MessageDrivenBeanDetector detector = new MessageDrivenBeanDetector();
            cu.accept(detector, null);

            if (detector.isMessageDrivenBean()) {
                MessageDrivenBeanInfo info = detector.getMessageDrivenBeanInfo();

                // Set tags on ProjectFile (for dependency chains) - honoring produces contract
                projectFileDecorator.setProperty(TAGS.TAG_EJB_MESSAGE_DRIVEN_BEAN, true);
                projectFileDecorator.setProperty(TAGS.TAG_EJB_MDB_DETECTED, true);
                projectFileDecorator.setProperty(TAGS.TAG_JMS_CONSUMER, true);

                if ("3.x".equals(info.ejbVersion)) {
                    projectFileDecorator.setProperty(TAGS.TAG_ANNOTATION_MESSAGE_DRIVEN, true);
                }

                // Set analysis data as single property on ClassNode (consolidated POJO)
                classNode.setProperty("message_driven_bean_analysis", info);
            }
        });
    }

    @Override
    public String getName() {
        return "Message Driven Bean Detector";
    }

    public static class TAGS {
        public static final String TAG_EJB_MESSAGE_DRIVEN_BEAN = "message_driven_bean_inspector.ejb_message_driven_bean";
        public static final String TAG_ANNOTATION_MESSAGE_DRIVEN = "message_driven_bean_inspector.annotation_message_driven";
        public static final String TAG_JMS_CONSUMER = "message_driven_bean_inspector.jms_consumer";
        public static final String TAG_EJB_MDB_DETECTED = "message_driven_bean_inspector.ejb_mdb_detected";
    }

    /**
     * Visitor that detects Message Driven Bean characteristics by analyzing the
     * AST.
     * Detects MDBs through:
     * 1. implements javax.ejb.MessageDrivenBean (EJB 2.x)
     * 2. implements javax.jms.MessageListener (EJB 2.x/3.x)
     * 3. @MessageDriven annotation (EJB 3.x)
     */
    private static class MessageDrivenBeanDetector extends VoidVisitorAdapter<Void> {
        private final MessageDrivenBeanInfo mdbInfo = new MessageDrivenBeanInfo();
        private boolean isMessageDrivenBean = false;

        public boolean isMessageDrivenBean() {
            return isMessageDrivenBean;
        }

        public MessageDrivenBeanInfo getMessageDrivenBeanInfo() {
            return mdbInfo;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            if (classDecl.isInterface()) {
                super.visit(classDecl, arg);
                return;
            }

            mdbInfo.className = classDecl.getNameAsString();

            // Check for EJB 3.x @MessageDriven annotation
            for (AnnotationExpr annotation : classDecl.getAnnotations()) {
                String annotationName = annotation.getNameAsString();
                if ("MessageDriven".equals(annotationName) ||
                        "javax.ejb.MessageDriven".equals(annotationName)) {
                    isMessageDrivenBean = true;
                    mdbInfo.ejbVersion = "3.x";
                    mdbInfo.migrationComplexity = "LOW"; // EJB 3.x is easier to migrate
                    analyzeMessageListenerMethods(classDecl);
                    return;
                }
            }

            // Check for EJB 2.x interface implementations
            boolean implementsMessageDrivenBean = false;
            boolean implementsMessageListener = false;

            if (classDecl.getImplementedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType implementedType : classDecl.getImplementedTypes()) {
                    String typeName = implementedType.getNameAsString();
                    if ("MessageDrivenBean".equals(typeName) ||
                            "javax.ejb.MessageDrivenBean".equals(typeName)) {
                        implementsMessageDrivenBean = true;
                    } else if ("MessageListener".equals(typeName) ||
                            "javax.jms.MessageListener".equals(typeName)) {
                        implementsMessageListener = true;
                        mdbInfo.implementsMessageListener = true;
                    }
                }
            }

            if (implementsMessageDrivenBean || implementsMessageListener) {
                isMessageDrivenBean = true;
                mdbInfo.ejbVersion = "2.x";
                mdbInfo.migrationComplexity = "MEDIUM"; // EJB 2.x requires more work
                analyzeMessageListenerMethods(classDecl);
            }

            super.visit(classDecl, arg);
        }

        private void analyzeMessageListenerMethods(ClassOrInterfaceDeclaration classDecl) {
            for (MethodDeclaration method : classDecl.getMethods()) {
                String methodName = method.getNameAsString();

                if ("onMessage".equals(methodName)) {
                    mdbInfo.hasOnMessageMethod = true;
                    mdbInfo.onMessageParameters = method.getParameters().size();
                } else if (methodName.startsWith("ejb")) {
                    mdbInfo.ejbMethods.add(methodName);
                } else if ("setMessageDrivenContext".equals(methodName)) {
                    mdbInfo.hasMessageDrivenContext = true;
                }
            }
        }
    }

    /**
     * Data class to hold Message Driven Bean analysis information
     */
    public static class MessageDrivenBeanInfo {
        public String className;
        public String ejbVersion; // 2.x, 3.x
        public String migrationComplexity; // LOW, MEDIUM
        public boolean implementsMessageListener;
        public boolean hasOnMessageMethod;
        public boolean hasMessageDrivenContext;
        public int onMessageParameters;
        public List<String> ejbMethods = new ArrayList<>();

    }
}
