package com.analyzer.rules.ejb2spring;

import com.analyzer.api.graph.JavaClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Shared utility class for common EJB detection and analysis logic.
 * Used by both EjbBinaryClassInspector (ASM-based) and EjbClassLoaderInspector
 * (Reflection-based)
 * to ensure consistent detection algorithms and property/tag storage.
 * 
 * <p>
 * This class provides:
 * </p>
 * <ul>
 * <li>Shared constants for EJB annotations and interfaces</li>
 * <li>Common field analysis logic for conversational state detection</li>
 * <li>Annotation metadata storage helpers</li>
 * <li>Method analysis pattern detection</li>
 * <li>Spring scope recommendation logic</li>
 * </ul>
 */
public final class EjbAnalysisUtils {

    private static final Logger logger = LoggerFactory.getLogger(EjbAnalysisUtils.class);

    // Prevent instantiation
    private EjbAnalysisUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== SHARED CONSTANTS ====================

    /**
     * EJB 3.x annotation descriptors (ASM format with L and ;)
     */
    public static final Set<String> EJB3_ANNOTATION_DESCRIPTORS = Set.of(
            "Ljavax/ejb/Stateless;",
            "Ljakarta/ejb/Stateless;",
            "Ljavax/ejb/Stateful;",
            "Ljakarta/ejb/Stateful;",
            "Ljavax/persistence/Entity;",
            "Ljakarta/persistence/Entity;",
            "Ljavax/ejb/MessageDriven;",
            "Ljakarta/ejb/MessageDriven;");

    /**
     * EJB 3.x annotation class names (Reflection format)
     */
    public static final Set<String> EJB3_ANNOTATION_NAMES = Set.of(
            "javax.ejb.Stateless",
            "jakarta.ejb.Stateless",
            "javax.ejb.Stateful",
            "jakarta.ejb.Stateful",
            "javax.persistence.Entity",
            "jakarta.persistence.Entity",
            "javax.ejb.MessageDriven",
            "jakarta.ejb.MessageDriven");

    /**
     * CDI scope annotations (Reflection format)
     */
    public static final Set<String> CDI_SCOPE_ANNOTATIONS = Set.of(
            "javax.enterprise.context.ApplicationScoped",
            "jakarta.enterprise.context.ApplicationScoped",
            "javax.enterprise.context.RequestScoped",
            "jakarta.enterprise.context.RequestScoped",
            "javax.enterprise.context.SessionScoped",
            "jakarta.enterprise.context.SessionScoped",
            "javax.enterprise.context.Dependent",
            "jakarta.enterprise.context.Dependent");

    /**
     * CDI scope annotation descriptors (ASM format)
     */
    public static final Set<String> CDI_SCOPE_ANNOTATION_DESCRIPTORS = Set.of(
            "Ljavax/enterprise/context/ApplicationScoped;",
            "Ljakarta/enterprise/context/ApplicationScoped;",
            "Ljavax/enterprise/context/RequestScoped;",
            "Ljakarta/enterprise/context/RequestScoped;",
            "Ljavax/enterprise/context/SessionScoped;",
            "Ljakarta/enterprise/context/SessionScoped;",
            "Ljavax/enterprise/context/Dependent;",
            "Ljakarta/enterprise/context/Dependent;");

    /**
     * EJB 2.x interface names (internal format with /)
     */
    public static final Set<String> EJB2_INTERFACE_DESCRIPTORS = Set.of(
            "javax/ejb/SessionBean",
            "jakarta/ejb/SessionBean",
            "javax/ejb/EntityBean",
            "jakarta/ejb/EntityBean",
            "javax/ejb/MessageDrivenBean",
            "jakarta/ejb/MessageDrivenBean");

    /**
     * EJB 2.x interface names (Reflection format with .)
     */
    public static final Set<String> EJB2_INTERFACE_NAMES = Set.of(
            "javax.ejb.SessionBean",
            "jakarta.ejb.SessionBean",
            "javax.ejb.EntityBean",
            "jakarta.ejb.EntityBean",
            "javax.ejb.MessageDrivenBean",
            "jakarta.ejb.MessageDrivenBean");

    /**
     * EJB standard interface descriptors (internal format)
     */
    public static final Set<String> EJB_STANDARD_INTERFACE_DESCRIPTORS = Set.of(
            "javax/ejb/EJBHome",
            "jakarta/ejb/EJBHome",
            "javax/ejb/EJBObject",
            "jakarta/ejb/EJBObject",
            "javax/ejb/EJBLocalHome",
            "jakarta/ejb/EJBLocalHome",
            "javax/ejb/EJBLocalObject",
            "jakarta/ejb/EJBLocalObject");

    /**
     * EJB standard interface names (Reflection format)
     */
    public static final Set<String> EJB_STANDARD_INTERFACE_NAMES = Set.of(
            "javax.ejb.EJBHome",
            "jakarta.ejb.EJBHome",
            "javax.ejb.EJBObject",
            "jakarta.ejb.EJBObject",
            "javax.ejb.EJBLocalHome",
            "jakarta.ejb.EJBLocalHome",
            "javax.ejb.EJBLocalObject",
            "jakarta.ejb.EJBLocalObject");

    // ==================== METHOD PATTERN DETECTION ====================

    /**
     * Checks if a method name follows EJB lifecycle method patterns.
     * 
     * @param methodName The method name to check
     * @return true if this is an EJB lifecycle method
     */
    public static boolean isEjbLifecycleMethod(String methodName) {
        return methodName.startsWith("ejb") ||
                methodName.equals("setSessionContext") ||
                methodName.equals("setEntityContext") ||
                methodName.equals("setMessageDrivenContext") ||
                methodName.equals("onMessage");
    }

    /**
     * Checks if a method name is a create method pattern.
     * 
     * @param methodName The method name to check
     * @return true if this is a create method
     */
    public static boolean isCreateMethod(String methodName) {
        return methodName.startsWith("create");
    }

    /**
     * Checks if a method name is a finder method pattern.
     * 
     * @param methodName The method name to check
     * @return true if this is a finder method
     */
    public static boolean isFinderMethod(String methodName) {
        return methodName.startsWith("find");
    }

    // ==================== FIELD ANALYSIS ====================

    /**
     * Information about a field for conversational state analysis.
     */
    public static class FieldInfo {
        private final String name;
        private final String type;
        private final boolean isStatic;
        private final boolean isFinal;
        private final boolean isCollection;
        private final boolean isPrimitive;
        private final String visibility;

        public FieldInfo(String name, String type, boolean isStatic, boolean isFinal,
                boolean isCollection, boolean isPrimitive, String visibility) {
            this.name = name;
            this.type = type;
            this.isStatic = isStatic;
            this.isFinal = isFinal;
            this.isCollection = isCollection;
            this.isPrimitive = isPrimitive;
            this.visibility = visibility;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public boolean isStatic() {
            return isStatic;
        }

        public boolean isFinal() {
            return isFinal;
        }

        public boolean isCollection() {
            return isCollection;
        }

        public boolean isPrimitive() {
            return isPrimitive;
        }

        public String getVisibility() {
            return visibility;
        }

        /**
         * Checks if this field represents mutable conversational state.
         * 
         * @return true if this is a mutable instance field
         */
        public boolean isConversationalState() {
            return !isStatic && !isFinal;
        }

        /**
         * Converts field info to a map for storage in properties.
         * 
         * @return Map representation of this field
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", name);
            map.put("type", type);
            map.put("visibility", visibility);
            map.put("isCollection", isCollection);
            map.put("isPrimitive", isPrimitive);
            return map;
        }
    }

    /**
     * Analyzes conversational state fields and updates the JavaClassNode
     * accordingly.
     * 
     * @param fields    Collection of fields to analyze
     * @param classNode The JavaClassNode to update with analysis results
     */
    public static void analyzeConversationalState(Collection<FieldInfo> fields, JavaClassNode classNode) {
        Set<Map<String, Object>> stateFields = new HashSet<>();
        int collectionCount = 0;
        int totalStateFields = 0;

        for (FieldInfo field : fields) {
            if (field.isConversationalState()) {
                stateFields.add(field.toMap());
                totalStateFields++;

                if (field.isCollection()) {
                    collectionCount++;
                }
            }
        }

        if (!stateFields.isEmpty()) {
            // Store field information
            classNode.setProperty("ejb.conversational.state.fields", stateFields);

            // Enable appropriate tags
            classNode.enableTag(EjbMigrationTags.TAG_EJB_CONVERSATIONAL_STATE_DETECTED);
            classNode.enableTag(EjbMigrationTags.TAG_EJB_STATEFUL_STATE);

            if (collectionCount > 0) {
                classNode.enableTag(EjbMigrationTags.TAG_EJB_COLLECTION_STATE);
            }

            // Determine complexity based on field count and types
            if (totalStateFields > 5 || collectionCount > 2) {
                classNode.enableTag(EjbMigrationTags.TAG_EJB_COMPLEX_STATE_PATTERN);
                classNode.getMetrics().setMaxMetric(
                        EjbMigrationTags.TAG_METRIC_MIGRATION_COMPLEXITY,
                        EjbMigrationTags.COMPLEXITY_HIGH);
            } else if (totalStateFields > 2 || collectionCount > 0) {
                classNode.getMetrics().setMaxMetric(
                        EjbMigrationTags.TAG_METRIC_MIGRATION_COMPLEXITY,
                        EjbMigrationTags.COMPLEXITY_MEDIUM);
            }

            // Recommend Spring scope
            String scopeRecommendation = recommendSpringScope(totalStateFields, collectionCount);
            classNode.setProperty("ejb.spring.scope.recommendation", scopeRecommendation);
            classNode.enableTag(EjbMigrationTags.TAG_SPRING_SCOPE_MIGRATION);

            logger.debug("Conversational state detected: {} fields ({} collections) -> recommend scope: {}",
                    totalStateFields, collectionCount, scopeRecommendation);
        }
    }

    /**
     * Recommends appropriate Spring scope based on conversational state
     * characteristics.
     * 
     * @param fieldCount      Total number of conversational state fields
     * @param collectionCount Number of collection fields
     * @return Recommended Spring scope (session, request, prototype, singleton)
     */
    private static String recommendSpringScope(int fieldCount, int collectionCount) {
        // Complex state or collections -> session scope
        if (fieldCount > 3 || collectionCount > 0) {
            return "session";
        }
        // Moderate state -> request scope
        else if (fieldCount > 1) {
            return "request";
        }
        // Minimal state -> prototype (new instance per use)
        else if (fieldCount > 0) {
            return "prototype";
        }
        // No state -> singleton (default)
        else {
            return "singleton";
        }
    }

    /**
     * Checks if a type descriptor represents a Collection type.
     * 
     * @param typeDescriptor The type descriptor (ASM format or class name)
     * @return true if this is a collection type
     */
    public static boolean isCollectionType(String typeDescriptor) {
        if (typeDescriptor == null) {
            return false;
        }
        // Handle ASM descriptors (Ljava/util/List;) and class names (java.util.List)
        String normalized = typeDescriptor.replace('/', '.');
        return normalized.contains("java.util.List") ||
                normalized.contains("java.util.Set") ||
                normalized.contains("java.util.Map") ||
                normalized.contains("java.util.Collection") ||
                normalized.contains("java.util.ArrayList") ||
                normalized.contains("java.util.HashSet") ||
                normalized.contains("java.util.HashMap");
    }

    /**
     * Checks if a type descriptor represents a primitive type.
     * 
     * @param typeDescriptor The type descriptor (ASM format)
     * @return true if this is a primitive type
     */
    public static boolean isPrimitiveDescriptor(String typeDescriptor) {
        if (typeDescriptor == null || typeDescriptor.length() != 1) {
            return false;
        }
        char desc = typeDescriptor.charAt(0);
        return desc == 'Z' || desc == 'B' || desc == 'C' || desc == 'S' ||
                desc == 'I' || desc == 'J' || desc == 'F' || desc == 'D';
    }

    /**
     * Checks if a type represents an EJB reference.
     * 
     * @param typeDescriptor The type descriptor
     * @return true if this type is likely an EJB reference
     */
    public static boolean isEjbReference(String typeDescriptor) {
        if (typeDescriptor == null) {
            return false;
        }
        // Check for common EJB type patterns
        String normalized = typeDescriptor.replace('/', '.');
        return normalized.contains(".ejb.") ||
                normalized.endsWith("Bean") ||
                normalized.endsWith("EJB");
    }

    // ==================== ANNOTATION METADATA ====================

    /**
     * Stores annotation metadata in a standardized format.
     * 
     * @param classNode      The node to store metadata in
     * @param annotationType The annotation type descriptor
     * @param parameters     Map of annotation parameters
     */
    public static void storeAnnotationMetadata(JavaClassNode classNode, String annotationType,
            Map<String, Object> parameters) {
        String propertyKey = determineAnnotationMetadataProperty(annotationType);
        if (propertyKey != null) {
            classNode.setProperty(propertyKey, parameters);
        }
    }

    /**
     * Determines the property key for storing annotation metadata.
     * 
     * @param annotationType The annotation type
     * @return Property key name
     */
    private static String determineAnnotationMetadataProperty(String annotationType) {
        if (annotationType.contains("Stateless")) {
            return "ejb.stateless.annotation.metadata";
        } else if (annotationType.contains("Stateful")) {
            return "ejb.stateful.annotation.metadata";
        } else if (annotationType.contains("Entity")) {
            return "ejb.entity.annotation.metadata";
        } else if (annotationType.contains("MessageDriven")) {
            return "ejb.messagedriven.annotation.metadata";
        }
        return null;
    }

    /**
     * Maps CDI scope annotation to equivalent Spring scope.
     * 
     * @param cdiScopeDescriptor The CDI scope annotation descriptor
     * @return Equivalent Spring scope name
     */
    public static String mapCdiScopeToSpring(String cdiScopeDescriptor) {
        if (cdiScopeDescriptor == null) {
            return "singleton";
        }

        if (cdiScopeDescriptor.contains("ApplicationScoped")) {
            return "singleton";
        } else if (cdiScopeDescriptor.contains("RequestScoped")) {
            return "request";
        } else if (cdiScopeDescriptor.contains("SessionScoped")) {
            return "session";
        } else if (cdiScopeDescriptor.contains("Dependent")) {
            return "prototype";
        }

        return "singleton";
    }

    /**
     * Analyzes CDI scope annotations and stores Spring migration guidance.
     * 
     * @param cdiScopeDescriptor The detected CDI scope annotation
     * @param classNode          The JavaClassNode to update
     */
    public static void analyzeCdiScope(String cdiScopeDescriptor, JavaClassNode classNode) {
        String springScope = mapCdiScopeToSpring(cdiScopeDescriptor);
        classNode.setProperty("cdi.scope.detected", cdiScopeDescriptor);
        classNode.setProperty("ejb.spring.scope.recommendation", springScope);
        classNode.enableTag(EjbMigrationTags.TAG_SPRING_SCOPE_MIGRATION);

        logger.debug("CDI scope detected: {} -> Spring scope: {}", cdiScopeDescriptor, springScope);
    }
}
