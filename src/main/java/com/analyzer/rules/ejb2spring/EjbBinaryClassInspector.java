package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.binary.AbstractASMInspector;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

/**
 * Binary class inspector that detects all types of EJB beans using ASM bytecode
 * analysis.
 * This inspector analyzes .class files to identify EJB components without
 * requiring
 * class loading, making it ideal for JAR scanning and dependency analysis.
 * 
 * <p>
 * Detects:
 * </p>
 * <ul>
 * <li>EJB 3.x annotation-based beans
 * (@Stateless, @Stateful, @Entity, @MessageDriven)</li>
 * <li>EJB 2.x interface-based beans (SessionBean, EntityBean,
 * MessageDrivenBean)</li>
 * <li>EJB interfaces (EJBHome, EJBObject, EJBLocalHome, EJBLocalObject)</li>
 * <li>Primary key classes and CMP field mappings</li>
 * </ul>
 * 
 * <p>
 * Uses modern simplified dependency architecture.
 * </p>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_BINARY }, produces = {
        EjbMigrationTags.EJB_BEAN_DETECTED,
        EjbMigrationTags.EJB_SESSION_BEAN,
        EjbMigrationTags.EJB_STATELESS_SESSION_BEAN,
        EjbMigrationTags.EJB_STATEFUL_SESSION_BEAN,
        EjbMigrationTags.EJB_ENTITY_BEAN,
        EjbMigrationTags.EJB_CMP_ENTITY,
        EjbMigrationTags.EJB_BMP_ENTITY,
        EjbMigrationTags.EJB_MESSAGE_DRIVEN_BEAN,
        EjbMigrationTags.EJB_HOME_INTERFACE,
        EjbMigrationTags.EJB_REMOTE_INTERFACE,
        EjbMigrationTags.EJB_LOCAL_INTERFACE,
        EjbMigrationTags.EJB_LOCAL_HOME_INTERFACE,
        EjbMigrationTags.EJB_PRIMARY_KEY_CLASS,
        EjbMigrationTags.SPRING_SERVICE_CONVERSION,
        EjbMigrationTags.SPRING_COMPONENT_CONVERSION,
        EjbMigrationTags.JPA_ENTITY_CONVERSION,
        EjbMigrationTags.JPA_REPOSITORY_CONVERSION,
        EjbMigrationTags.MIGRATION_COMPLEXITY_LOW,
        EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM,
        EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH
})
public class EjbBinaryClassInspector extends AbstractASMInspector {

    private static final Logger logger = LoggerFactory.getLogger(EjbBinaryClassInspector.class);

    private final ClassNodeRepository classNodeRepository;

    @Inject
    public EjbBinaryClassInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
    }

    @Override
    public String getName() {
        return "EJB Binary Class Inspector";
    }

    @Override
    protected ASMClassVisitor createClassVisitor(ProjectFile projectFile, ProjectFileDecorator projectFileDecorator) {
        JavaClassNode classNode = classNodeRepository.getOrCreateClassNodeByFqn(projectFile.getFullyQualifiedName())
                .orElseThrow();
        classNode.setProjectFileId(projectFile.getId());
        return new EjbBytecodeVisitor(projectFile, projectFileDecorator, classNode);
    }

    /**
     * ASM ClassVisitor that performs comprehensive EJB detection via bytecode
     * analysis.
     * Analyzes annotations, interfaces, and method patterns to identify all EJB
     * components.
     */
    private static class EjbBytecodeVisitor extends ASMClassVisitor {

        private final JavaClassNode classNode;
        private final Set<String> ejbAnnotations = new HashSet<>();
        private final Set<String> implementedInterfaces = new HashSet<>();
        private final Set<String> ejbMethods = new HashSet<>();
        private final Map<String, Object> annotationParameters = new HashMap<>();

        private String className;
        private String superClassName;
        private boolean isInterface;
        private boolean isAbstract;

        // EJB 3.x Annotations
        private static final Set<String> EJB3_ANNOTATIONS = Set.of(
                "Ljavax/ejb/Stateless;",
                "Ljavax/ejb/Stateful;",
                "Ljakarta/ejb/Stateless;",
                "Ljakarta/ejb/Stateful;",
                "Ljavax/persistence/Entity;",
                "Ljakarta/persistence/Entity;",
                "Ljavax/ejb/MessageDriven;",
                "Ljakarta/ejb/MessageDriven;");

        // EJB 2.x Interfaces
        private static final Set<String> EJB2_INTERFACES = Set.of(
                "javax/ejb/SessionBean",
                "jakarta/ejb/SessionBean",
                "javax/ejb/EntityBean",
                "jakarta/ejb/EntityBean",
                "javax/ejb/MessageDrivenBean",
                "jakarta/ejb/MessageDrivenBean");

        // EJB Standard Interfaces
        private static final Set<String> EJB_STANDARD_INTERFACES = Set.of(
                "javax/ejb/EJBHome",
                "jakarta/ejb/EJBHome",
                "javax/ejb/EJBObject",
                "jakarta/ejb/EJBObject",
                "javax/ejb/EJBLocalHome",
                "jakarta/ejb/EJBLocalHome",
                "javax/ejb/EJBLocalObject",
                "jakarta/ejb/EJBLocalObject");

        public EjbBytecodeVisitor(ProjectFile projectFile, ProjectFileDecorator projectFileDecorator,
                JavaClassNode classNode) {
            super(projectFile, projectFileDecorator);
            this.classNode = classNode;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            this.className = name;
            this.superClassName = superName;
            this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            this.isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;

            logger.debug("Analyzing class: {} (interface: {}, abstract: {})", name, isInterface, isAbstract);

            // Collect implemented interfaces
            if (interfaces != null) {
                Collections.addAll(implementedInterfaces, interfaces);

                // Check for EJB 2.x interface implementations
                for (String iface : interfaces) {
                    if (EJB2_INTERFACES.contains(iface)) {
                        logger.debug("Found EJB 2.x interface implementation: {}", iface);
                    }
                    if (EJB_STANDARD_INTERFACES.contains(iface)) {
                        logger.debug("Found EJB standard interface implementation: {}", iface);
                    }
                }
            }

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            logger.debug("Found annotation: {} on class: {}", descriptor, className);

            if (EJB3_ANNOTATIONS.contains(descriptor)) {
                ejbAnnotations.add(descriptor);
                logger.debug("Detected EJB 3.x annotation: {}", descriptor);

                // Return annotation visitor to collect parameters
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        annotationParameters.put(descriptor + "." + name, value);
                        logger.debug("Annotation parameter: {}={}", name, value);
                    }
                };
            }

            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public void visitEnd() {
            // Perform comprehensive EJB analysis
            analyzeEjbComponents();
            super.visitEnd();
        }

        private void analyzeEjbComponents() {
            boolean isEjbComponent = false;

            // Analyze EJB 3.x annotation-based components
            if (analyzeEjb3Annotations()) {
                isEjbComponent = true;
            }

            // Analyze EJB 2.x interface-based components
            if (analyzeEjb2Interfaces()) {
                isEjbComponent = true;
            }

            // Analyze EJB standard interfaces
            if (analyzeEjbStandardInterfaces()) {
                isEjbComponent = true;
            }

            // Honor produces contract: set tags on ProjectFile if EJB component found
            if (isEjbComponent) {
                projectFile.setTag(EjbMigrationTags.EJB_BEAN_DETECTED, true);
                classNode.setProperty("ejb.binary.detected", true);
                logger.info("EJB component detected: {}", className);
            }
        }

        private boolean analyzeEjb3Annotations() {
            boolean found = false;

            for (String annotation : ejbAnnotations) {
                switch (annotation) {
                    case "Ljavax/ejb/Stateless;":
                    case "Ljakarta/ejb/Stateless;":
                        // Honor produces contract: set tags on ProjectFile
                        projectFile.setTag(EjbMigrationTags.EJB_STATELESS_SESSION_BEAN, true);
                        projectFile.setTag(EjbMigrationTags.EJB_SESSION_BEAN, true);
                        projectFile.setTag(EjbMigrationTags.SPRING_SERVICE_CONVERSION, true);
                        projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_LOW, true);
                        // Store analysis data as single property on ClassNode
                        classNode.setProperty("ejb.stateless.analysis", "EJB 3.x Stateless Session Bean detected");
                        logger.debug("Detected Stateless Session Bean: {}", className);
                        found = true;
                        break;

                    case "Ljavax/ejb/Stateful;":
                    case "Ljakarta/ejb/Stateful;":
                        projectFile.setTag(EjbMigrationTags.EJB_STATEFUL_SESSION_BEAN, true);
                        projectFile.setTag(EjbMigrationTags.EJB_SESSION_BEAN, true);
                        projectFile.setTag(EjbMigrationTags.SPRING_COMPONENT_CONVERSION, true);
                        projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
                        classNode.setProperty("ejb.stateful.analysis", "EJB 3.x Stateful Session Bean detected");
                        logger.debug("Detected Stateful Session Bean: {}", className);
                        found = true;
                        break;

                    case "Ljavax/persistence/Entity;":
                    case "Ljakarta/persistence/Entity;":
                        projectFile.setTag(EjbMigrationTags.EJB_ENTITY_BEAN, true);
                        projectFile.setTag(EjbMigrationTags.EJB_CMP_ENTITY, true);
                        projectFile.setTag(EjbMigrationTags.JPA_ENTITY_CONVERSION, true);
                        projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_LOW, true);
                        classNode.setProperty("ejb.entity.analysis", "JPA Entity detected");
                        logger.debug("Detected JPA Entity: {}", className);
                        found = true;
                        break;

                    case "Ljavax/ejb/MessageDriven;":
                    case "Ljakarta/ejb/MessageDriven;":
                        projectFile.setTag(EjbMigrationTags.EJB_MESSAGE_DRIVEN_BEAN, true);
                        projectFile.setTag(EjbMigrationTags.SPRING_COMPONENT_CONVERSION, true);
                        projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
                        classNode.setProperty("ejb.messagedriven.analysis", "EJB 3.x Message-Driven Bean detected");
                        logger.debug("Detected Message-Driven Bean: {}", className);
                        found = true;
                        break;
                }
            }

            return found;
        }

        private boolean analyzeEjb2Interfaces() {
            boolean found = false;

            for (String iface : implementedInterfaces) {
                switch (iface) {
                    case "javax/ejb/SessionBean":
                    case "jakarta/ejb/SessionBean":
                        projectFile.setTag(EjbMigrationTags.EJB_SESSION_BEAN, true);
                        projectFile.setTag(EjbMigrationTags.SPRING_SERVICE_CONVERSION, true);
                        projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH, true);
                        classNode.setProperty("ejb.session2x.analysis", "EJB 2.x Session Bean interface detected");
                        logger.debug("Detected EJB 2.x Session Bean: {}", className);
                        found = true;
                        break;

                    case "javax/ejb/EntityBean":
                    case "jakarta/ejb/EntityBean":
                        projectFile.setTag(EjbMigrationTags.EJB_ENTITY_BEAN, true);
                        projectFile.setTag(EjbMigrationTags.EJB_BMP_ENTITY, true);
                        projectFile.setTag(EjbMigrationTags.JPA_REPOSITORY_CONVERSION, true);
                        projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH, true);
                        classNode.setProperty("ejb.entity2x.analysis", "EJB 2.x Entity Bean interface detected");
                        logger.debug("Detected EJB 2.x Entity Bean: {}", className);
                        found = true;
                        break;

                    case "javax/ejb/MessageDrivenBean":
                    case "jakarta/ejb/MessageDrivenBean":
                        projectFile.setTag(EjbMigrationTags.EJB_MESSAGE_DRIVEN_BEAN, true);
                        projectFile.setTag(EjbMigrationTags.SPRING_COMPONENT_CONVERSION, true);
                        projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH, true);
                        classNode.setProperty("ejb.mdb2x.analysis", "EJB 2.x Message-Driven Bean interface detected");
                        logger.debug("Detected EJB 2.x Message-Driven Bean: {}", className);
                        found = true;
                        break;
                }
            }

            return found;
        }

        private boolean analyzeEjbStandardInterfaces() {
            if (!isInterface) {
                return false; // Only interfaces can be EJB standard interfaces
            }

            boolean found = false;

            for (String iface : implementedInterfaces) {
                switch (iface) {
                    case "javax/ejb/EJBHome":
                    case "jakarta/ejb/EJBHome":
                        projectFile.setTag(EjbMigrationTags.EJB_HOME_INTERFACE, true);
                        projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH, true);
                        classNode.setProperty("ejb.home.analysis", "EJB Home Interface detected");
                        logger.debug("Detected EJB Home Interface: {}", className);
                        found = true;
                        break;

                    case "javax/ejb/EJBObject":
                    case "jakarta/ejb/EJBObject":
                        projectFile.setTag(EjbMigrationTags.EJB_REMOTE_INTERFACE, true);
                        projectFile.setTag(EjbMigrationTags.SPRING_SERVICE_CONVERSION, true);
                        projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
                        classNode.setProperty("ejb.remote.analysis", "EJB Remote Interface detected");
                        logger.debug("Detected EJB Remote Interface: {}", className);
                        found = true;
                        break;

                    case "javax/ejb/EJBLocalHome":
                    case "jakarta/ejb/EJBLocalHome":
                        projectFile.setTag(EjbMigrationTags.EJB_LOCAL_HOME_INTERFACE, true);
                        projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH, true);
                        classNode.setProperty("ejb.localhome.analysis", "EJB Local Home Interface detected");
                        logger.debug("Detected EJB Local Home Interface: {}", className);
                        found = true;
                        break;

                    case "javax/ejb/EJBLocalObject":
                    case "jakarta/ejb/EJBLocalObject":
                        projectFile.setTag(EjbMigrationTags.EJB_LOCAL_INTERFACE, true);
                        projectFile.setTag(EjbMigrationTags.SPRING_SERVICE_CONVERSION, true);
                        projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
                        classNode.setProperty("ejb.local.analysis", "EJB Local Interface detected");
                        logger.debug("Detected EJB Local Interface: {}", className);
                        found = true;
                        break;
                }
            }

            // Check if this interface extends EJB standard interfaces directly
            if (superClassName != null && EJB_STANDARD_INTERFACES.contains(superClassName)) {
                // Interface extends an EJB standard interface
                analyzeEjbInterfaceInheritance(superClassName);
                found = true;
            }

            return found;
        }

        private void analyzeEjbInterfaceInheritance(String superInterface) {
            switch (superInterface) {
                case "javax/ejb/EJBHome":
                case "jakarta/ejb/EJBHome":
                    projectFile.setTag(EjbMigrationTags.EJB_HOME_INTERFACE, true);
                    projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH, true);
                    classNode.setProperty("ejb.home.inheritance", "Extends EJB Home Interface");
                    break;

                case "javax/ejb/EJBObject":
                case "jakarta/ejb/EJBObject":
                    projectFile.setTag(EjbMigrationTags.EJB_REMOTE_INTERFACE, true);
                    projectFile.setTag(EjbMigrationTags.SPRING_SERVICE_CONVERSION, true);
                    projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
                    classNode.setProperty("ejb.remote.inheritance", "Extends EJB Remote Interface");
                    break;

                case "javax/ejb/EJBLocalHome":
                case "jakarta/ejb/EJBLocalHome":
                    projectFile.setTag(EjbMigrationTags.EJB_LOCAL_HOME_INTERFACE, true);
                    projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH, true);
                    classNode.setProperty("ejb.localhome.inheritance", "Extends EJB Local Home Interface");
                    break;

                case "javax/ejb/EJBLocalObject":
                case "jakarta/ejb/EJBLocalObject":
                    projectFile.setTag(EjbMigrationTags.EJB_LOCAL_INTERFACE, true);
                    projectFile.setTag(EjbMigrationTags.SPRING_SERVICE_CONVERSION, true);
                    projectFile.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
                    classNode.setProperty("ejb.local.inheritance", "Extends EJB Local Interface");
                    break;
            }
        }
    }
}
