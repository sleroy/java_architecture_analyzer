package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.graph.ProjectFileRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.inspectors.core.binary.AbstractASMClassInspector;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

/**
 * Class-centric EJB binary inspector - Phase 3 migration.
 * 
 * <p>
 * Detects all types of EJB beans using ASM bytecode analysis without requiring
 * class loading.
 * This inspector analyzes .class files to identify EJB components, making it
 * ideal for JAR
 * scanning and dependency analysis.
 * </p>
 * 
 * <p>
 * <strong>Detects:</strong>
 * </p>
 * <ul>
 * <li>EJB 3.x annotation-based beans
 * (@Stateless, @Stateful, @Entity, @MessageDriven)</li>
 * <li>EJB 2.x interface-based beans (SessionBean, EntityBean,
 * MessageDrivenBean)</li>
 * <li>EJB standard interfaces (EJBHome, EJBObject, EJBLocalHome,
 * EJBLocalObject)</li>
 * <li>Both javax.ejb and jakarta.ejb namespaces</li>
 * </ul>
 * 
 * <p>
 * <strong>Architecture:</strong>
 * </p>
 * <ul>
 * <li>Extends AbstractASMClassInspector (class-centric architecture)</li>
 * <li>Receives JavaClassNode directly instead of creating it</li>
 * <li>Writes all analysis results to JavaClassNode properties</li>
 * <li>Uses NodeDecorator for type-safe property access</li>
 * <li>Simplified constructor with standard injection pattern</li>
 * </ul>
 * 
 * <p>
 * <strong>Migration Tags Applied:</strong>
 * </p>
 * <ul>
 * <li>Spring conversion recommendations (Service, Component, Repository)</li>
 * <li>JPA entity conversion for entity beans</li>
 * <li>Complexity ratings (Low, Medium, High) for migration effort
 * estimation</li>
 * </ul>
 * 
 * @since Phase 3 - Systematic Inspector Migration (Replaced with class-centric
 *        architecture)
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
public class EjbBinaryClassInspector extends AbstractASMClassInspector {

    private static final Logger logger = LoggerFactory.getLogger(EjbBinaryClassInspector.class);

    @Inject
    public EjbBinaryClassInspector(ProjectFileRepository projectFileRepository,
            ResourceResolver resourceResolver) {
        super(projectFileRepository, resourceResolver);
    }

    @Override
    public String getName() {
        return "EJB Binary Class Inspector (Class-Centric ASM)";
    }

    @Override
    protected ASMClassNodeVisitor createClassVisitor(JavaClassNode classNode,
            NodeDecorator<JavaClassNode> decorator) {
        return new EjbBytecodeVisitor(classNode, decorator);
    }

    /**
     * ASM ClassVisitor that performs comprehensive EJB detection via bytecode
     * analysis.
     * Analyzes annotations, interfaces, and method patterns to identify all EJB
     * components.
     * 
     * <p>
     * This visitor maintains the complete detection logic from the original
     * inspector while
     * adapting to the class-centric architecture by writing all results to
     * JavaClassNode
     * properties through the NodeDecorator.
     * </p>
     */
    private static class EjbBytecodeVisitor extends ASMClassNodeVisitor {

        private final Set<String> ejbAnnotations = new HashSet<>();
        private final Set<String> implementedInterfaces = new HashSet<>();
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

        protected EjbBytecodeVisitor(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator) {
            super(classNode, decorator);
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

        /**
         * Performs comprehensive EJB component analysis and writes results to
         * JavaClassNode.
         * Analyzes EJB 3.x annotations, EJB 2.x interfaces, and EJB standard
         * interfaces.
         */
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

            // Write overall detection result to JavaClassNode
            if (isEjbComponent) {
                setProperty("ejb.binary.detected", true);
                setProperty("ejb.component.type", determineComponentType());
                logger.info("EJB component detected: {}", className);
            }
        }

        /**
         * Analyzes EJB 3.x annotation-based components.
         * Detects @Stateless, @Stateful, @Entity, and @MessageDriven beans.
         * 
         * @return true if any EJB 3.x annotation was found
         */
        private boolean analyzeEjb3Annotations() {
            boolean found = false;

            for (String annotation : ejbAnnotations) {
                switch (annotation) {
                    case "Ljavax/ejb/Stateless;":
                    case "Ljakarta/ejb/Stateless;":
                        setProperty("ejb.stateless.detected", true);
                        setProperty("ejb.session.bean.type", "stateless");
                        setProperty("ejb.version", "3.x");
                        setProperty("ejb.annotation.type", annotation);
                        setProperty("ejb.migration.spring.recommendation", "Spring @Service");
                        setProperty("ejb.migration.complexity", "LOW");
                        enableTag(EjbMigrationTags.EJB_STATELESS_SESSION_BEAN);
                        enableTag(EjbMigrationTags.EJB_SESSION_BEAN);
                        enableTag(EjbMigrationTags.SPRING_SERVICE_CONVERSION);
                        enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_LOW);
                        logger.debug("Detected Stateless Session Bean: {}", className);
                        found = true;
                        break;

                    case "Ljavax/ejb/Stateful;":
                    case "Ljakarta/ejb/Stateful;":
                        setProperty("ejb.stateful.detected", true);
                        setProperty("ejb.session.bean.type", "stateful");
                        setProperty("ejb.version", "3.x");
                        setProperty("ejb.annotation.type", annotation);
                        setProperty("ejb.migration.spring.recommendation", "Spring @Component with @Scope");
                        setProperty("ejb.migration.complexity", "MEDIUM");
                        enableTag(EjbMigrationTags.EJB_STATEFUL_SESSION_BEAN);
                        enableTag(EjbMigrationTags.EJB_SESSION_BEAN);
                        enableTag(EjbMigrationTags.SPRING_COMPONENT_CONVERSION);
                        enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM);
                        logger.debug("Detected Stateful Session Bean: {}", className);
                        found = true;
                        break;

                    case "Ljavax/persistence/Entity;":
                    case "Ljakarta/persistence/Entity;":
                        setProperty("ejb.entity.detected", true);
                        setProperty("ejb.entity.type", "JPA");
                        setProperty("ejb.version", "3.x");
                        setProperty("ejb.annotation.type", annotation);
                        setProperty("ejb.migration.spring.recommendation", "JPA @Entity (already compatible)");
                        setProperty("ejb.migration.complexity", "LOW");
                        enableTag(EjbMigrationTags.EJB_ENTITY_BEAN);
                        enableTag(EjbMigrationTags.EJB_CMP_ENTITY);
                        enableTag(EjbMigrationTags.JPA_ENTITY_CONVERSION);
                        enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_LOW);
                        logger.debug("Detected JPA Entity: {}", className);
                        found = true;
                        break;

                    case "Ljavax/ejb/MessageDriven;":
                    case "Ljakarta/ejb/MessageDriven;":
                        setProperty("ejb.messagedriven.detected", true);
                        setProperty("ejb.version", "3.x");
                        setProperty("ejb.annotation.type", annotation);
                        setProperty("ejb.migration.spring.recommendation", "Spring @Component with JMS listener");
                        setProperty("ejb.migration.complexity", "MEDIUM");
                        enableTag(EjbMigrationTags.EJB_MESSAGE_DRIVEN_BEAN);
                        enableTag(EjbMigrationTags.SPRING_COMPONENT_CONVERSION);
                        enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM);
                        logger.debug("Detected Message-Driven Bean: {}", className);
                        found = true;
                        break;
                }
            }

            return found;
        }

        /**
         * Analyzes EJB 2.x interface-based components.
         * Detects SessionBean, EntityBean, and MessageDrivenBean implementations.
         * 
         * @return true if any EJB 2.x interface was found
         */
        private boolean analyzeEjb2Interfaces() {
            boolean found = false;

            for (String iface : implementedInterfaces) {
                switch (iface) {
                    case "javax/ejb/SessionBean":
                    case "jakarta/ejb/SessionBean":
                        setProperty("ejb.session.bean.detected", true);
                        setProperty("ejb.version", "2.x");
                        setProperty("ejb.interface.type", iface);
                        setProperty("ejb.migration.spring.recommendation", "Spring @Service");
                        setProperty("ejb.migration.complexity", "HIGH");
                        setProperty("ejb.migration.notes", "EJB 2.x requires callback method migration");
                        enableTag(EjbMigrationTags.EJB_SESSION_BEAN);
                        enableTag(EjbMigrationTags.SPRING_SERVICE_CONVERSION);
                        enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH);
                        logger.debug("Detected EJB 2.x Session Bean: {}", className);
                        found = true;
                        break;

                    case "javax/ejb/EntityBean":
                    case "jakarta/ejb/EntityBean":
                        setProperty("ejb.entity.bean.detected", true);
                        setProperty("ejb.entity.type", "BMP");
                        setProperty("ejb.version", "2.x");
                        setProperty("ejb.interface.type", iface);
                        setProperty("ejb.migration.spring.recommendation", "JPA Repository Pattern");
                        setProperty("ejb.migration.complexity", "HIGH");
                        setProperty("ejb.migration.notes", "BMP requires manual data access migration");
                        enableTag(EjbMigrationTags.EJB_ENTITY_BEAN);
                        enableTag(EjbMigrationTags.EJB_BMP_ENTITY);
                        enableTag(EjbMigrationTags.JPA_REPOSITORY_CONVERSION);
                        enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH);
                        logger.debug("Detected EJB 2.x Entity Bean: {}", className);
                        found = true;
                        break;

                    case "javax/ejb/MessageDrivenBean":
                    case "jakarta/ejb/MessageDrivenBean":
                        setProperty("ejb.messagedriven.bean.detected", true);
                        setProperty("ejb.version", "2.x");
                        setProperty("ejb.interface.type", iface);
                        setProperty("ejb.migration.spring.recommendation", "Spring JMS Listener");
                        setProperty("ejb.migration.complexity", "HIGH");
                        setProperty("ejb.migration.notes", "EJB 2.x MDB requires JMS configuration migration");
                        enableTag(EjbMigrationTags.EJB_MESSAGE_DRIVEN_BEAN);
                        enableTag(EjbMigrationTags.SPRING_COMPONENT_CONVERSION);
                        enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH);
                        logger.debug("Detected EJB 2.x Message-Driven Bean: {}", className);
                        found = true;
                        break;
                }
            }

            return found;
        }

        /**
         * Analyzes EJB standard interfaces.
         * Detects EJBHome, EJBObject, EJBLocalHome, and EJBLocalObject implementations.
         * 
         * @return true if any EJB standard interface was found
         */
        private boolean analyzeEjbStandardInterfaces() {
            if (!isInterface) {
                return false; // Only interfaces can be EJB standard interfaces
            }

            boolean found = false;

            for (String iface : implementedInterfaces) {
                switch (iface) {
                    case "javax/ejb/EJBHome":
                    case "jakarta/ejb/EJBHome":
                        setProperty("ejb.home.interface.detected", true);
                        setProperty("ejb.interface.type", "EJBHome");
                        setProperty("ejb.migration.spring.recommendation", "Remove (not needed in Spring)");
                        setProperty("ejb.migration.complexity", "HIGH");
                        setProperty("ejb.migration.notes", "Home interfaces are EJB-specific, remove in Spring");
                        enableTag(EjbMigrationTags.EJB_HOME_INTERFACE);
                        enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH);
                        logger.debug("Detected EJB Home Interface: {}", className);
                        found = true;
                        break;

                    case "javax/ejb/EJBObject":
                    case "jakarta/ejb/EJBObject":
                        setProperty("ejb.remote.interface.detected", true);
                        setProperty("ejb.interface.type", "EJBObject");
                        setProperty("ejb.migration.spring.recommendation", "Convert to Spring @Service interface");
                        setProperty("ejb.migration.complexity", "MEDIUM");
                        setProperty("ejb.migration.notes", "Business methods can be retained");
                        enableTag(EjbMigrationTags.EJB_REMOTE_INTERFACE);
                        enableTag(EjbMigrationTags.SPRING_SERVICE_CONVERSION);
                        enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM);
                        logger.debug("Detected EJB Remote Interface: {}", className);
                        found = true;
                        break;

                    case "javax/ejb/EJBLocalHome":
                    case "jakarta/ejb/EJBLocalHome":
                        setProperty("ejb.localhome.interface.detected", true);
                        setProperty("ejb.interface.type", "EJBLocalHome");
                        setProperty("ejb.migration.spring.recommendation", "Remove (not needed in Spring)");
                        setProperty("ejb.migration.complexity", "HIGH");
                        setProperty("ejb.migration.notes", "Local home interfaces are EJB-specific, remove in Spring");
                        enableTag(EjbMigrationTags.EJB_LOCAL_HOME_INTERFACE);
                        enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH);
                        logger.debug("Detected EJB Local Home Interface: {}", className);
                        found = true;
                        break;

                    case "javax/ejb/EJBLocalObject":
                    case "jakarta/ejb/EJBLocalObject":
                        setProperty("ejb.local.interface.detected", true);
                        setProperty("ejb.interface.type", "EJBLocalObject");
                        setProperty("ejb.migration.spring.recommendation", "Convert to Spring @Service interface");
                        setProperty("ejb.migration.complexity", "MEDIUM");
                        setProperty("ejb.migration.notes", "Business methods can be retained");
                        enableTag(EjbMigrationTags.EJB_LOCAL_INTERFACE);
                        enableTag(EjbMigrationTags.SPRING_SERVICE_CONVERSION);
                        enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM);
                        logger.debug("Detected EJB Local Interface: {}", className);
                        found = true;
                        break;
                }
            }

            // Check if this interface extends EJB standard interfaces directly
            if (superClassName != null && EJB_STANDARD_INTERFACES.contains(superClassName)) {
                analyzeEjbInterfaceInheritance(superClassName);
                found = true;
            }

            return found;
        }

        /**
         * Analyzes interfaces that extend EJB standard interfaces.
         * 
         * @param superInterface The parent EJB interface
         */
        private void analyzeEjbInterfaceInheritance(String superInterface) {
            setProperty("ejb.interface.inheritance.detected", true);
            setProperty("ejb.interface.parent", superInterface);

            switch (superInterface) {
                case "javax/ejb/EJBHome":
                case "jakarta/ejb/EJBHome":
                    setProperty("ejb.home.interface.inheritance", "Extends EJB Home Interface");
                    enableTag(EjbMigrationTags.EJB_HOME_INTERFACE);
                    enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH);
                    break;

                case "javax/ejb/EJBObject":
                case "jakarta/ejb/EJBObject":
                    setProperty("ejb.remote.interface.inheritance", "Extends EJB Remote Interface");
                    enableTag(EjbMigrationTags.EJB_REMOTE_INTERFACE);
                    enableTag(EjbMigrationTags.SPRING_SERVICE_CONVERSION);
                    enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM);
                    break;

                case "javax/ejb/EJBLocalHome":
                case "jakarta/ejb/EJBLocalHome":
                    setProperty("ejb.localhome.interface.inheritance", "Extends EJB Local Home Interface");
                    enableTag(EjbMigrationTags.EJB_LOCAL_HOME_INTERFACE);
                    enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH);
                    break;

                case "javax/ejb/EJBLocalObject":
                case "jakarta/ejb/EJBLocalObject":
                    setProperty("ejb.local.interface.inheritance", "Extends EJB Local Interface");
                    enableTag(EjbMigrationTags.EJB_LOCAL_INTERFACE);
                    enableTag(EjbMigrationTags.SPRING_SERVICE_CONVERSION);
                    enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM);
                    break;
            }
        }

        /**
         * Determines the overall EJB component type for summary reporting.
         * 
         * @return A string describing the detected component type
         */
        private String determineComponentType() {
            if (ejbAnnotations.contains("Ljavax/ejb/Stateless;") ||
                    ejbAnnotations.contains("Ljakarta/ejb/Stateless;")) {
                return "Stateless Session Bean (EJB 3.x)";
            }
            if (ejbAnnotations.contains("Ljavax/ejb/Stateful;") ||
                    ejbAnnotations.contains("Ljakarta/ejb/Stateful;")) {
                return "Stateful Session Bean (EJB 3.x)";
            }
            if (ejbAnnotations.contains("Ljavax/persistence/Entity;") ||
                    ejbAnnotations.contains("Ljakarta/persistence/Entity;")) {
                return "JPA Entity (EJB 3.x)";
            }
            if (ejbAnnotations.contains("Ljavax/ejb/MessageDriven;") ||
                    ejbAnnotations.contains("Ljakarta/ejb/MessageDriven;")) {
                return "Message-Driven Bean (EJB 3.x)";
            }

            // Check EJB 2.x interfaces
            for (String iface : implementedInterfaces) {
                if (iface.contains("SessionBean")) {
                    return "Session Bean (EJB 2.x)";
                }
                if (iface.contains("EntityBean")) {
                    return "Entity Bean (EJB 2.x)";
                }
                if (iface.contains("MessageDrivenBean")) {
                    return "Message-Driven Bean (EJB 2.x)";
                }
                if (iface.contains("EJBHome") || iface.contains("EJBLocalHome")) {
                    return "Home Interface";
                }
                if (iface.contains("EJBObject") || iface.contains("EJBLocalObject")) {
                    return "Business Interface";
                }
            }

            return "Unknown EJB Component";
        }
    }
}
