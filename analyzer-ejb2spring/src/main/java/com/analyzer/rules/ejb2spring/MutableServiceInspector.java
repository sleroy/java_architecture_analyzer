package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects service classes with non-final instance fields that could cause
 * thread safety issues.
 * These are candidates for refactoring to improve thread safety in a Spring
 * environment.
 * 
 * <p>
 * Thread-unsafe patterns typically include:
 * </p>
 * <ul>
 * <li>Service classes with non-final instance fields</li>
 * <li>Instance fields modified across multiple methods</li>
 * <li>Mutable state in singleton beans</li>
 * <li>Classes where instance variables are used to cache computation
 * results</li>
 * </ul>
 * 
 * <p>
 * Migration Target: Thread-safe Spring @Service or @Component beans
 * </p>
 * 
 * @see <a href=
 *      "https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#beans-factory-scopes">Spring
 *      Bean Scopes</a>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM,
        EjbMigrationTags.CODE_MODERNIZATION,
        EjbMigrationTags.SPRING_COMPONENT_CONVERSION
})
public class MutableServiceInspector extends AbstractJavaClassInspector {

    private static final List<String> SERVICE_CLASS_NAME_PATTERNS = Arrays.asList(
            "Service", "Manager", "Handler", "Processor", "Controller", "Facade", "Helper",
            "Provider", "Coordinator", "Orchestrator", "Dispatcher", "Mediator");

    @Inject
    public MutableServiceInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classNodeRepository);
    }

    @Override
    public String getName() {
        return "Mutable Service Detector";
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                NodeDecorator<ProjectFile> projectFileDecorator) {

        if (!(type instanceof ClassOrInterfaceDeclaration)) {
            return;
        }

        ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) type;
        MutableServiceDetector detector = new MutableServiceDetector();
        type.accept(detector, null);

        if (detector.isMutableService()) {
            MutableServiceInfo info = detector.getMutableServiceInfo();

            // Set tags according to the produces contract
            projectFileDecorator.setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
            projectFileDecorator.setProperty(EjbMigrationTags.CODE_MODERNIZATION, true);
            projectFileDecorator.setProperty(EjbMigrationTags.SPRING_COMPONENT_CONVERSION, true);

            // Set custom tags for more detailed analysis
            projectFileDecorator.setProperty("mutable.service.detected", true);

            if (info.crossMethodFieldModification) {
                projectFileDecorator.setProperty("thread_safety.cross_method_mutation", true);
            }

            if (info.hasSynchronizedMethods) {
                projectFileDecorator.setProperty("thread_safety.partial_synchronized", true);
            }

            if (info.mutableFieldCount > 3) {
                projectFileDecorator.setProperty("thread_safety.high_mutation", true);
            }

            // Set property on class node for detailed analysis
            classNode.setProperty("mutable.service.analysis", info.toString());

            // Set analysis statistics
            projectFileDecorator.setProperty("mutable.field.count", info.mutableFieldCount);
            projectFileDecorator.setProperty("mutable.methods.count", info.methodsWithFieldMutations.size());
        }
    }

    /**
     * Visitor that detects mutable service classes by analyzing the AST.
     */
    private static class MutableServiceDetector extends VoidVisitorAdapter<Void> {
        private final MutableServiceInfo info = new MutableServiceInfo();
        private boolean isMutableService = false;
        private final Set<String> mutableFields = new HashSet<>();
        private final Map<String, Set<String>> fieldModificationsByMethod = new HashMap<>();
        private boolean isServiceClass = false;

        public boolean isMutableService() {
            return isMutableService;
        }

        public MutableServiceInfo getMutableServiceInfo() {
            return info;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            super.visit(classDecl, arg);

            // Check if class name indicates a service class
            String className = classDecl.getNameAsString();
            for (String pattern : SERVICE_CLASS_NAME_PATTERNS) {
                if (className.contains(pattern)) {
                    isServiceClass = true;
                    info.isServiceByName = true;
                    break;
                }
            }

            // Check class annotations for service indicators
            for (var annotation : classDecl.getAnnotations()) {
                String annotationName = annotation.getNameAsString();
                if (annotationName.contains("Service") || annotationName.contains("Component") ||
                        annotationName.contains("Controller") || annotationName.contains("Bean") ||
                        annotationName.contains("Stateless") || annotationName.contains("Stateful") ||
                        annotationName.contains("Singleton")) {
                    isServiceClass = true;
                    info.hasServiceAnnotation = true;
                    break;
                }
            }

            // Check for cross-method field modifications
            if (fieldModificationsByMethod.size() > 1) {
                // We have fields modified in more than one method, check for overlaps
                Set<String> modifiedFields = new HashSet<>();
                boolean hasCrossMethodModification = false;

                for (Map.Entry<String, Set<String>> entry : fieldModificationsByMethod.entrySet()) {
                    // Check if any field in this method is also in the running set
                    for (String field : entry.getValue()) {
                        if (modifiedFields.contains(field)) {
                            hasCrossMethodModification = true;
                            info.crossMethodModifiedFields.add(field);
                        } else {
                            modifiedFields.add(field);
                        }
                    }
                }

                info.crossMethodFieldModification = hasCrossMethodModification;
            }

            // Analyze if this is a mutable service
            info.mutableFieldCount = mutableFields.size();

            // Mark as mutable service if it's a service class and has mutable fields
            if (isServiceClass && info.mutableFieldCount > 0) {
                isMutableService = true;
            }
        }

        @Override
        public void visit(FieldDeclaration field, Void arg) {
            super.visit(field, arg);

            // Skip static and final fields
            if (field.isStatic() || field.isFinal()) {
                return;
            }

            // Track non-final instance fields
            for (VariableDeclarator var : field.getVariables()) {
                String fieldName = var.getNameAsString();
                mutableFields.add(fieldName);
                info.mutableFields.add(fieldName);

                // Check if the field type is collection or map (potential for more subtle
                // mutability issues)
                String typeName = var.getType().asString();
                if (typeName.contains("List") || typeName.contains("Set") || typeName.contains("Map") ||
                        typeName.contains("Collection") || typeName.contains("Array")) {
                    info.collectionFields.add(fieldName);
                }
            }
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);

            // Track synchronized methods
            if (method.getModifiers().contains(Modifier.synchronizedModifier())) {
                info.hasSynchronizedMethods = true;
                info.synchronizedMethods.add(method.getNameAsString());
            }

            // Get all field assignments in this method
            List<AssignExpr> assignments = method.findAll(AssignExpr.class);
            Set<String> fieldsModifiedInMethod = new HashSet<>();

            for (AssignExpr assignment : assignments) {
                if (assignment.getTarget() instanceof NameExpr) {
                    String name = ((NameExpr) assignment.getTarget()).getNameAsString();
                    if (mutableFields.contains(name)) {
                        fieldsModifiedInMethod.add(name);
                        info.methodsWithFieldMutations.add(method.getNameAsString());
                    }
                }
            }

            // If any fields are modified, add to the method map
            if (!fieldsModifiedInMethod.isEmpty()) {
                fieldModificationsByMethod.put(method.getNameAsString(), fieldsModifiedInMethod);
            }
        }
    }

    /**
     * Data class to hold Mutable Service pattern analysis information
     */
    public static class MutableServiceInfo {
        public int mutableFieldCount = 0;
        public boolean isServiceByName = false;
        public boolean hasServiceAnnotation = false;
        public boolean hasSynchronizedMethods = false;
        public boolean crossMethodFieldModification = false;
        public List<String> mutableFields = new ArrayList<>();
        public List<String> collectionFields = new ArrayList<>();
        public Set<String> crossMethodModifiedFields = new HashSet<>();
        public List<String> methodsWithFieldMutations = new ArrayList<>();
        public List<String> synchronizedMethods = new ArrayList<>();

        @Override
        public String toString() {
            return String.format(
                    "MutableService{mutableFields=%d, isServiceByName=%s, hasServiceAnnotation=%s, " +
                            "hasSynchronizedMethods=%s, crossMethodModification=%s, " +
                            "fields=%s, crossMethodFields=%s, methods=%s}",
                    mutableFieldCount,
                    isServiceByName,
                    hasServiceAnnotation,
                    hasSynchronizedMethods,
                    crossMethodFieldModification,
                    mutableFields.toString(),
                    crossMethodModifiedFields.toString(),
                    methodsWithFieldMutations.toString());
        }
    }
}
