package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaParserInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Inspector that detects EJB Remote and Local interfaces for EJB-to-Spring
 * migration analysis.
 * Identifies business interfaces that extend EJBObject or EJBLocalObject that
 * need
 * to be converted to simple Spring service interfaces.
 */
@InspectorDependencies(need = {  }, produces = {
        EjbRemoteInterfaceInspector.TAGS.TAG_IS_REMOTE_INTERFACE })
public class EjbRemoteInterfaceInspector extends AbstractJavaParserInspector {

    public static class TAGS {
        public static final String TAG_IS_REMOTE_INTERFACE = "ejb_remote_interface_inspector.is_remote_interface";
    }

    private final ClassNodeRepository classNodeRepository;

    public EjbRemoteInterfaceInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        // Only process Java source files
        return projectFile != null;
    }

    @Override
    protected void analyzeCompilationUnit(CompilationUnit cu, ProjectFile projectFile,
                                          NodeDecorator projectFileDecorator) {
        classNodeRepository.getOrCreateClassNode(cu).ifPresent(classNode -> {
            classNode.setProjectFileId(projectFile.getId());
            EjbRemoteInterfaceDetector detector = new EjbRemoteInterfaceDetector();
            cu.accept(detector, null);

            if (detector.isEjbRemoteInterface()) {
                EjbRemoteInterfaceInfo info = detector.getEjbRemoteInterfaceInfo();
                projectFileDecorator.setProperty(TAGS.TAG_IS_REMOTE_INTERFACE, info);
                return;
            }

            projectFileDecorator.setProperty(TAGS.TAG_IS_REMOTE_INTERFACE, false);
        });
    }

    @Override
    public String getName() {
        return "EJB Remote/Local Interface Detector";
    }

    /**
     * Visitor that detects EJB Remote/Local interface characteristics by analyzing
     * the AST.
     * Detects Remote/Local interfaces through:
     * 1. extends javax.ejb.EJBObject (Remote interface)
     * 2. extends javax.ejb.EJBLocalObject (Local interface)
     */
    private static class EjbRemoteInterfaceDetector extends VoidVisitorAdapter<Void> {
        private boolean isEjbRemoteInterface = false;
        private EjbRemoteInterfaceInfo remoteInterfaceInfo = new EjbRemoteInterfaceInfo();

        public boolean isEjbRemoteInterface() {
            return isEjbRemoteInterface;
        }

        public EjbRemoteInterfaceInfo getEjbRemoteInterfaceInfo() {
            return remoteInterfaceInfo;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            if (!classDecl.isInterface()) {
                super.visit(classDecl, arg);
                return;
            }

            remoteInterfaceInfo.interfaceName = classDecl.getNameAsString();

            // Check for EJB Remote/Local interface inheritance
            if (classDecl.getExtendedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType extendedType : classDecl.getExtendedTypes()) {
                    String typeName = extendedType.getNameAsString();
                    if ("EJBObject".equals(typeName) ||
                            "javax.ejb.EJBObject".equals(typeName)) {
                        isEjbRemoteInterface = true;
                        remoteInterfaceInfo.interfaceType = "REMOTE";
                        remoteInterfaceInfo.migrationAction = "CONVERT_TO_SPRING_INTERFACE";
                        remoteInterfaceInfo.migrationComplexity = "LOW";
                        analyzeMethods(classDecl);
                        return;
                    } else if ("EJBLocalObject".equals(typeName) ||
                            "javax.ejb.EJBLocalObject".equals(typeName)) {
                        isEjbRemoteInterface = true;
                        remoteInterfaceInfo.interfaceType = "LOCAL";
                        remoteInterfaceInfo.migrationAction = "CONVERT_TO_SPRING_INTERFACE";
                        remoteInterfaceInfo.migrationComplexity = "LOW";
                        analyzeMethods(classDecl);
                        return;
                    }
                }
            }

            super.visit(classDecl, arg);
        }

        private void analyzeMethods(ClassOrInterfaceDeclaration interfaceDecl) {
            for (MethodDeclaration method : interfaceDecl.getMethods()) {
                String methodName = method.getNameAsString();
                int parameterCount = method.getParameters().size();
                String returnType = method.getType().toString();

                BusinessMethodInfo methodInfo = new BusinessMethodInfo();
                methodInfo.methodName = methodName;
                methodInfo.parameterCount = parameterCount;
                methodInfo.returnType = returnType;

                // Check if method throws RemoteException (indicator of remote interface)
                boolean throwsRemoteException = method.getThrownExceptions().stream()
                        .anyMatch(ex -> {
                            String exceptionName = ex.toString();
                            return "RemoteException".equals(exceptionName) ||
                                    "java.rmi.RemoteException".equals(exceptionName);
                        });

                if (throwsRemoteException) {
                    remoteInterfaceInfo.hasRemoteExceptions = true;
                    methodInfo.throwsRemoteException = true;
                }

                remoteInterfaceInfo.businessMethods.add(methodInfo);
            }

            // Adjust migration complexity based on analysis
            if (remoteInterfaceInfo.hasRemoteExceptions) {
                remoteInterfaceInfo.migrationComplexity = "MEDIUM"; // Need to remove RemoteException
            }

            if (remoteInterfaceInfo.businessMethods.size() > 15) {
                remoteInterfaceInfo.migrationComplexity = "HIGH";
            }
        }
    }

    /**
     * Data class to hold business method information
     */
    public static class BusinessMethodInfo {
        public String methodName;
        public String returnType;
        public int parameterCount;
        public boolean throwsRemoteException;
    }

    /**
     * Data class to hold EJB Remote/Local Interface analysis information
     */
    public static class EjbRemoteInterfaceInfo {
        public String interfaceName;
        public String interfaceType; // REMOTE, LOCAL
        public String migrationAction; // CONVERT_TO_SPRING_INTERFACE
        public String migrationComplexity; // LOW, MEDIUM, HIGH
        public boolean hasRemoteExceptions;
        public List<BusinessMethodInfo> businessMethods = new ArrayList<>();

    }
}
