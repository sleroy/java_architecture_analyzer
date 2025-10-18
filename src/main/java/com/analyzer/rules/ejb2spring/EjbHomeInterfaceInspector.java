package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.inspector.InspectorDependencies;

import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.detection.JavaSourceFileDetector;
import com.analyzer.inspectors.core.source.AbstractJavaParserInspector;
import com.analyzer.resource.ResourceResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Inspector that detects EJB Home interfaces for EJB-to-Spring migration
 * analysis.
 * Identifies both Local Home (EJBLocalHome) and Remote Home (EJBHome)
 * interfaces
 * that need to be eliminated in Spring Boot migration.
 */
@InspectorDependencies(need = {
        JavaSourceFileDetector.class
}, produces = {
        EjbHomeInterfaceInspector.TAGS.TAG_IS_HOME_INTERFACE
})
public class EjbHomeInterfaceInspector extends AbstractJavaParserInspector {

    public static class TAGS {
        public static final String TAG_IS_HOME_INTERFACE = "ejb_home_interface_inspector.is_home_interface";
    }

    private final ClassNodeRepository classNodeRepository;

    public EjbHomeInterfaceInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
    }

    @Override
    protected void analyzeCompilationUnit(CompilationUnit cu, ProjectFile projectFile,
            ProjectFileDecorator projectFileDecorator) {
        classNodeRepository.getOrCreateClassNode(cu).ifPresent(classNode -> {
            classNode.setProjectFileId(projectFile.getId());
            EjbHomeInterfaceDetector detector = new EjbHomeInterfaceDetector();
            cu.accept(detector, null);

            if (detector.isEjbHomeInterface()) {
                EjbHomeInterfaceInfo info = detector.getEjbHomeInterfaceInfo();
                classNode.setProperty(TAGS.TAG_IS_HOME_INTERFACE, info.toJson());
                return;
            }

            classNode.setProperty(TAGS.TAG_IS_HOME_INTERFACE, false);
        });
    }

    @Override
    public String getName() {
        return "EJB Home Interface Detector";
    }

    /**
     * Visitor that detects EJB Home interface characteristics by analyzing the AST.
     * Detects Home interfaces through:
     * 1. extends javax.ejb.EJBHome (Remote Home)
     * 2. extends javax.ejb.EJBLocalHome (Local Home)
     */
    private static class EjbHomeInterfaceDetector extends VoidVisitorAdapter<Void> {
        private boolean isEjbHomeInterface = false;
        private EjbHomeInterfaceInfo homeInterfaceInfo = new EjbHomeInterfaceInfo();

        public boolean isEjbHomeInterface() {
            return isEjbHomeInterface;
        }

        public EjbHomeInterfaceInfo getEjbHomeInterfaceInfo() {
            return homeInterfaceInfo;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            if (!classDecl.isInterface()) {
                super.visit(classDecl, arg);
                return;
            }

            homeInterfaceInfo.interfaceName = classDecl.getNameAsString();

            // Check for EJB Home interface inheritance
            if (classDecl.getExtendedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType extendedType : classDecl.getExtendedTypes()) {
                    String typeName = extendedType.getNameAsString();
                    if ("EJBHome".equals(typeName) ||
                            "javax.ejb.EJBHome".equals(typeName)) {
                        isEjbHomeInterface = true;
                        homeInterfaceInfo.homeType = "REMOTE";
                        homeInterfaceInfo.migrationAction = "ELIMINATE";
                        homeInterfaceInfo.migrationComplexity = "MEDIUM";
                        analyzeMethods(classDecl);
                        return;
                    } else if ("EJBLocalHome".equals(typeName) ||
                            "javax.ejb.EJBLocalHome".equals(typeName)) {
                        isEjbHomeInterface = true;
                        homeInterfaceInfo.homeType = "LOCAL";
                        homeInterfaceInfo.migrationAction = "ELIMINATE";
                        homeInterfaceInfo.migrationComplexity = "LOW";
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

                if (methodName.startsWith("create")) {
                    homeInterfaceInfo.createMethods.add(methodName + "(" + parameterCount + " params)");
                } else if (methodName.startsWith("find")) {
                    homeInterfaceInfo.finderMethods.add(methodName + "(" + parameterCount + " params)");
                } else if (methodName.startsWith("remove")) {
                    homeInterfaceInfo.removeMethods.add(methodName + "(" + parameterCount + " params)");
                } else {
                    homeInterfaceInfo.businessMethods.add(methodName + "(" + parameterCount + " params)");
                }
            }

            // Determine migration complexity based on method count
            int totalMethods = homeInterfaceInfo.createMethods.size() +
                    homeInterfaceInfo.finderMethods.size() +
                    homeInterfaceInfo.businessMethods.size();
            if (totalMethods > 10) {
                homeInterfaceInfo.migrationComplexity = "HIGH";
            } else if (totalMethods > 5) {
                homeInterfaceInfo.migrationComplexity = "MEDIUM";
            }
        }
    }

    /**
     * Data class to hold EJB Home Interface analysis information
     */
    public static class EjbHomeInterfaceInfo {
        public String interfaceName;
        public String homeType; // REMOTE, LOCAL
        public String migrationAction; // ELIMINATE
        public String migrationComplexity; // LOW, MEDIUM, HIGH
        public List<String> createMethods = new ArrayList<>();
        public List<String> finderMethods = new ArrayList<>();
        public List<String> removeMethods = new ArrayList<>();
        public List<String> businessMethods = new ArrayList<>();

        public String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"interfaceName\":\"").append(interfaceName != null ? interfaceName : "").append("\",");
            json.append("\"homeType\":\"").append(homeType != null ? homeType : "").append("\",");
            json.append("\"migrationAction\":\"").append(migrationAction != null ? migrationAction : "").append("\",");
            json.append("\"migrationComplexity\":\"").append(migrationComplexity != null ? migrationComplexity : "")
                    .append("\",");
            json.append("\"createMethodCount\":").append(createMethods.size()).append(",");
            json.append("\"finderMethodCount\":").append(finderMethods.size()).append(",");
            json.append("\"removeMethodCount\":").append(removeMethods.size()).append(",");
            json.append("\"businessMethodCount\":").append(businessMethods.size());
            json.append("}");
            return json.toString();
        }
    }
}
