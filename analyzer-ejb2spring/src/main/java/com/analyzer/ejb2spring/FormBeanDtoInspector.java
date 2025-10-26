package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Inspector that detects Form Beans and DTOs in Java source code for
 * EJB-to-Spring migration analysis.
 * 
 * <p>
 * The FormBeanDtoInspector (CS-024) identifies POJOs that follow the Data
 * Transfer Object pattern
 * or act as form beans in legacy web applications. These classes are typically
 * found in
 * "dto", "form", "bean", "vo", or "model" packages and are candidates for
 * conversion to
 * modern Java records or Lombok @Data classes in Spring Boot applications.
 * </p>
 * 
 * <p>
 * DTO detection is based on:
 * <ul>
 * <li>Package name patterns (dto, form, bean, model)</li>
 * <li>Class naming patterns (*DTO, *Form, *Bean)</li>
 * <li>Structural characteristics (getters/setters, serializable)</li>
 * </ul>
 * </p>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE }, produces = {
        FormBeanDtoInspector.TAGS.TAG_IS_DTO,
        EjbMigrationTags.MIGRATION_COMPLEXITY_LOW
})
public class FormBeanDtoInspector extends AbstractJavaClassInspector {

    @Inject
    public FormBeanDtoInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classNodeRepository);
    }

    @Override
    public String getName() {
        return "Form Bean/DTO Detector";
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                NodeDecorator projectFileDecorator) {

        // Only analyze non-abstract classes
        if (!(type instanceof ClassOrInterfaceDeclaration) ||
                ((ClassOrInterfaceDeclaration) type).isInterface() ||
                ((ClassOrInterfaceDeclaration) type).isAbstract()) {
            return;
        }

        ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) type;
        String packageName = (String) projectFile.getProperty("packageName");
        String className = classDecl.getNameAsString();

        // Initial assessment based on package and class name
        boolean inDtoPackage = false;
        boolean hasDtoName = false;

        // Check package name patterns
        if (packageName != null) {
            String lowerPackage = packageName.toLowerCase();
            inDtoPackage = lowerPackage.contains(".dto") ||
                    lowerPackage.contains(".form") ||
                    lowerPackage.contains(".bean") ||
                    lowerPackage.contains(".vo") ||
                    lowerPackage.contains(".model");
        }

        // Check class name patterns
        if (className != null) {
            String upperClassName = className.toUpperCase();
            hasDtoName = upperClassName.endsWith("DTO") ||
                    upperClassName.endsWith("FORM") ||
                    upperClassName.endsWith("BEAN") ||
                    upperClassName.endsWith("VO");
        }

        // Detailed analysis of class structure
        DtoDetector detector = new DtoDetector();
        classDecl.accept(detector, null);

        // Decision logic
        boolean isDtoByStructure = detector.isDto();
        boolean isDto = (inDtoPackage || hasDtoName) && isDtoByStructure;

        if (isDto) {
            DtoInfo info = detector.getDtoInfo();
            info.packageName = packageName;
            info.className = className;
            info.inDtoPackage = inDtoPackage;
            info.hasDtoNamePattern = hasDtoName;

            // Set tags according to the produces contract
            projectFileDecorator.enableTag(TAGS.TAG_IS_DTO);
            projectFileDecorator.enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_LOW);

            // Set appropriate Spring Boot migration target
            if (info.isMutable) {
                projectFileDecorator.setProperty("spring.conversion.target", "@Data class");
            } else {
                projectFileDecorator.setProperty("spring.conversion.target", "Record");
            }

            // Set property on class node for detailed analysis
            classNode.setProperty("dto.analysis", info.toString());

            // Set analysis statistics
            projectFileDecorator.setProperty("dto.property_count", info.propertyCount);
            projectFileDecorator.setProperty("dto.getter_setter_ratio",
                    info.getterCount > 0 ? (double) info.setterCount / info.getterCount : 0);
            projectFileDecorator.setProperty("dto.serializable", info.isSerializable);
        }
    }

    /**
     * Visitor that detects DTO characteristics by analyzing fields and methods.
     */
    private static class DtoDetector extends VoidVisitorAdapter<Void> {
        private final DtoInfo info = new DtoInfo();
        private boolean hasConstructors = false;
        private int methodCount = 0;
        private final Pattern getterPattern = Pattern.compile("^get[A-Z].*$|^is[A-Z].*$");
        private final Pattern setterPattern = Pattern.compile("^set[A-Z].*$");

        public boolean isDto() {
            // A class is considered a DTO if:
            // 1. It has a balanced ratio of getters to setters (most properties are
            // accessible)
            // 2. The majority of its methods are getters/setters
            // 3. It doesn't have complex business logic methods

            boolean hasGettersSetters = info.getterCount > 0 || info.setterCount > 0;
            double getterSetterRatio = (double) (info.getterCount + info.setterCount) / Math.max(1, methodCount);

            return hasGettersSetters && (getterSetterRatio > 0.7);
        }

        public DtoInfo getDtoInfo() {
            return info;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            // Check if class implements Serializable
            classDecl.getImplementedTypes().forEach(type -> {
                if (type.getNameAsString().equals("Serializable") ||
                        type.getNameAsString().equals("java.io.Serializable")) {
                    info.isSerializable = true;
                }
            });

            super.visit(classDecl, arg);

            // After visiting all members, determine if the class is mutable
            info.isMutable = info.setterCount > 0;
        }

        @Override
        public void visit(FieldDeclaration field, Void arg) {
            super.visit(field, arg);

            if (!field.isStatic()) {
                // Count non-static fields as properties
                field.getVariables().forEach(var -> {
                    info.propertyCount++;
                    info.properties.add(var.getNameAsString());
                });
            }
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);

            String methodName = method.getNameAsString();
            methodCount++;

            // Check for constructor
            if (method.isConstructorDeclaration()) {
                hasConstructors = true;
                return;
            }

            // Check for getter patterns
            if (getterPattern.matcher(methodName).matches() &&
                    method.getParameters().isEmpty() &&
                    !method.getType().isVoidType()) {
                info.getterCount++;
                info.getters.add(methodName);
            }

            // Check for setter patterns
            else if (setterPattern.matcher(methodName).matches() &&
                    method.getParameters().size() == 1 &&
                    (method.getType().isVoidType() ||
                            method.getType().asString().equals(method.getParentNode().get().toString()))) {
                info.setterCount++;
                info.setters.add(methodName);
            }

            // Check for business logic methods
            else if (!method.isPrivate() &&
                    !methodName.equals("equals") &&
                    !methodName.equals("hashCode") &&
                    !methodName.equals("toString")) {
                info.businessMethodCount++;
            }
        }
    }

    /**
     * Data class to hold DTO/Form Bean analysis information
     */
    public static class DtoInfo {
        public String packageName;
        public String className;
        public boolean inDtoPackage = false;
        public boolean hasDtoNamePattern = false;
        public boolean isSerializable = false;
        public boolean isMutable = true;
        public int propertyCount = 0;
        public int getterCount = 0;
        public int setterCount = 0;
        public int businessMethodCount = 0;
        public List<String> properties = new ArrayList<>();
        public List<String> getters = new ArrayList<>();
        public List<String> setters = new ArrayList<>();

        @Override
        public String toString() {
            return String.format(
                    "DTO{package=%s, class=%s, dtoPackage=%b, dtoName=%b, properties=%d, " +
                            "getters=%d, setters=%d, businessMethods=%d, serializable=%b, mutable=%b}",
                    packageName,
                    className,
                    inDtoPackage,
                    hasDtoNamePattern,
                    propertyCount,
                    getterCount,
                    setterCount,
                    businessMethodCount,
                    isSerializable,
                    isMutable);
        }
    }

    public static class TAGS {
        public static final String TAG_IS_DTO = "form_bean_dto_inspector.is_dto";
    }
}
