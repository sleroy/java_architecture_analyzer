package com.analyzer.rules.ejb2spring;

import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.inspector.InspectorTargetType;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.dev.inspectors.binary.AbstractBinaryClassNodeInspector;
import com.analyzer.rules.graph.BinaryJavaClassNodeInspectorV2;
import com.analyzer.rules.std.ApplicationPackageTagInspector;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.inject.Inject;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Inspector I-0206: CMP Field Mapping Inspector
 * <p>
 * Detects and analyzes Container Managed Persistence (CMP) field mappings in
 * EJB 2.x entity beans.
 * This inspector is critical for CMP to JPA migration analysis, identifying CMP
 * fields, their
 * database mappings, and container managed relationships.
 * <p>
 * Phase 1 - Foundation Inspector (P0 Critical Priority)
 */
@InspectorDependencies(need = {BinaryJavaClassNodeInspectorV2.class, ApplicationPackageTagInspector.class}, requires = InspectorTags.TAG_JAVA_DETECTED, produces = {
        EjbMigrationTags.TAG_EJB_CMP_FIELD_MAPPING,
        EjbMigrationTags.TAG_EJB_CMP_FIELD,
        EjbMigrationTags.TAG_EJB_CMR_RELATIONSHIP,
        EjbMigrationTags.TAG_EJB_PRIMARY_KEY_CLASS,
        EjbMigrationTags.TAG_JPA_CONVERSION_CANDIDATE
})
public class CmpFieldMappingJavaBinaryInspector extends AbstractBinaryClassNodeInspector {

    private static final Set<String> CMP_METHOD_PREFIXES = Set.of("get", "set");
    private static final Set<String> CMR_COLLECTION_TYPES = Set.of(
            "java/util/Collection", "java/util/Set", "java/util/List", "java/util/Vector");
    private static final Pattern PATTERN = Pattern.compile("\\([^)]+\\)V");

    private final @NotNull Map<String, CmpEntityMetadata> cmpEntityCache;

    @Inject
    public CmpFieldMappingJavaBinaryInspector(final ResourceResolver resourceResolver,
                                              final ClassNodeRepository classNodeRepository, final LocalCache localCache) {
        super(resourceResolver, classNodeRepository, localCache);
        cmpEntityCache = new ConcurrentHashMap<>();
    }

    private static @NotNull List<String> generateJpaRecommendations(final @NotNull CmpEntityMetadata metadata) {
        final List<String> recommendations = new ArrayList<>();

        recommendations.add("Convert CMP entity '" + metadata.getEntityName() + "' to JPA @Entity");

        for (final CmpFieldInfo field : metadata.getCmpFields()) {
            if (field.isPrimaryKey()) {
                recommendations.add("Convert field '" + field.getName() + "' to @Id");
            } else {
                recommendations.add("Convert field '" + field.getName() + "' to @Column");
            }
        }

        for (final CmrRelationshipInfo cmr : metadata.getCmrRelationships()) {
            recommendations.add("Convert relationship '" + cmr.getName() + "' to @" + cmr.getCardinality());
        }

        return recommendations;
    }


    private static boolean implementsEntityBean(final @NotNull ClassNode classNode) {
        if (classNode.interfaces != null) {
            for (final String interfaceName : classNode.interfaces) {
                if ("javax/ejb/EntityBean".equals(interfaceName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAbstractCmpMethods(final @NotNull ClassNode classNode) {
        if (classNode.methods != null) {
            for (final MethodNode method : classNode.methods) {
                if ((method.access & Opcodes.ACC_ABSTRACT) != 0 && isCmpAccessorMethod(method)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCmpAccessorMethod(final @NotNull MethodNode method) {
        final String methodName = method.name;

        // Check for getter pattern
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return method.desc.startsWith("()"); // No parameters
        }

        // Check for setter pattern
        if (methodName.startsWith("set") && methodName.length() > 3) {
            return PATTERN.matcher(method.desc).matches(); // One parameter, void return
        }

        return false;
    }

    private static boolean isCmpEntityBean(final @NotNull ClassNode classNode) {
        // Check if class implements EntityBean interface
        if (!implementsEntityBean(classNode)) {
            return false;
        }

        // Check for abstract CMP methods
        return hasAbstractCmpMethods(classNode);
    }

    private static @NotNull String extractEntityName(final @NotNull ClassNode classNode) {
        @NotNull final String result;
        final String className = classNode.name;
        final int lastSlash = className.lastIndexOf('/');
        final String simpleName = lastSlash >= 0 ? className.substring(lastSlash + 1) : className;

        // Remove "Bean" suffix if present
        if (simpleName.endsWith("Bean")) {
            result = simpleName.substring(0, simpleName.length() - 4);
        } else {
            result = simpleName;
        }
        return result;
    }

    private static @NotNull String extractFieldName(final @NotNull String methodName, final @NotNull String prefix) {
        final String fieldName = methodName.substring(prefix.length());
        if (!fieldName.isEmpty()) {
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }
        return fieldName;
    }

    private static boolean isCmrRelationship(final @NotNull String methodDescriptor) {
        // Extract return type from method descriptor
        final String returnType = extractReturnType(methodDescriptor);

        // Check for collection types (1:M, M:M relationships)
        for (final String collectionType : CMR_COLLECTION_TYPES) {
            if (returnType.contains(collectionType)) {
                return true;
            }
        }

        // Check for entity bean types (assume non-primitive, non-common types are
        // entities)
        return isLikelyEntityType(returnType);
    }

    private static @NotNull String extractReturnType(final @NotNull String methodDescriptor) {
        final int parenIndex = methodDescriptor.indexOf(')');
        if (parenIndex >= 0 && parenIndex < methodDescriptor.length() - 1) {
            final String returnTypePart = methodDescriptor.substring(parenIndex + 1);
            if (returnTypePart.startsWith("L") && returnTypePart.endsWith(";")) {
                return returnTypePart.substring(1, returnTypePart.length() - 1);
            }
        }
        return "void";
    }

    private static boolean isLikelyEntityType(final @NotNull String typeName) {
        // Skip primitive and common Java types
        if (typeName.length() == 1) { // void or primitives
            return false;
        }

        if (typeName.startsWith("java/lang/") || typeName.startsWith("java/util/") ||
                typeName.startsWith("java/math/") || "java/util/Date".equals(typeName)) {
            return false;
        }

        // Likely an entity if it ends with Bean or Entity, or is a custom type
        return hasEntityNameOrPackage(typeName);
    }

    private static boolean hasEntityNameOrPackage(final @NotNull String typeName) {
        return typeName.endsWith("Bean") || typeName.endsWith("Entity") ||
                (!typeName.startsWith("java/") && !typeName.startsWith("javax/"));
    }

    private static @NotNull CmpFieldInfo analyzeCmpField(final @NotNull String fieldName, final @NotNull MethodNode getter) {
        final String fieldType = extractReturnType(getter.desc);
        final boolean isPrimaryKey = fieldName.toLowerCase().contains("id") ||
                fieldName.toLowerCase().contains("key");

        return new CmpFieldInfo(fieldName, fieldType, isPrimaryKey);
    }

    private static @NotNull CmrRelationshipInfo analyzeCmrRelationship(final String relationshipName, final @NotNull MethodNode getter) {
        final String returnType = extractReturnType(getter.desc);
        final boolean isCollection = CMR_COLLECTION_TYPES.stream()
                                                         .anyMatch(returnType::contains);

        final String targetEntity = isCollection ? extractGenericType() : returnType;
        final String cardinality = determineCardinality(isCollection);

        return new CmrRelationshipInfo(relationshipName, targetEntity, cardinality, isCollection);
    }

    private static @NotNull String extractGenericType() {
        // Simplified generic type extraction for ASM descriptors
        // This would need enhancement for full generic support
        return "Object";
    }

    private static @NotNull String determineCardinality(final boolean isCollection) {
        if (isCollection) {
            return "OneToMany"; // Default for collections
        } else {
            return "ManyToOne"; // Default for single entities
        }
    }

    private static @NotNull String assessMigrationComplexity(final @NotNull CmpEntityMetadata metadata) {
        int complexityScore = 0;

        // Base complexity for CMP entity
        complexityScore += 3;

        // Add complexity for each CMP field
        complexityScore += metadata.getCmpFields().size();

        // Add higher complexity for CMR relationships
        complexityScore += metadata.getCmrRelationships().size() << 1;

        // Add complexity for primary keys
        final long pkFieldCount = metadata.getCmpFields().stream()
                                          .mapToLong(field -> (field.isPrimaryKey() ? 1 : 0))
                                          .sum();
        if (pkFieldCount > 1) {
            complexityScore += 3; // Composite primary key
        }

        return classifyComplexity(complexityScore);
    }

    private static @NotNull String classifyComplexity(final int complexityScore) {
        // Classify complexity
        if (complexityScore <= 5) {
            return "LOW";
        } else if (complexityScore <= 10) {
            return "MEDIUM";
        } else {
            return "HIGH";
        }
    }

    @Override
    public void analyzeClassNode(final @NotNull ProjectFile projectFile, final @NotNull JavaClassNode classNode, final ResourceLocation binaryLocation,
                                 final @NotNull InputStream classInputStream, final @NotNull NodeDecorator<ProjectFile> projectFileDecorator) throws java.io.IOException {
        classNode.setProjectFileId(projectFile.getId());

        try {
            final ClassReader classReader = new ClassReader(classInputStream);
            final CmpFieldMappingVisitor visitor = new CmpFieldMappingVisitor(projectFile, projectFileDecorator, classNode);
            classReader.accept(visitor, 0);
        } catch (final Exception e) {
            projectFileDecorator.error("ASM analysis error: " + e.getMessage());
        }
    }

    @Override
    public @NotNull String getName() {
        return "CMP Field Mapping Inspector (I-0206)";
    }

    @Override
    public @NotNull InspectorTargetType getTargetType() {
        return InspectorTargetType.PROJECT_FILE;
    }

    // Data classes
    public static class CmpEntityMetadata {
        private final String className;
        private final String entityName;
        private final List<CmpFieldInfo> cmpFields = new ArrayList<>();
        private final List<CmrRelationshipInfo> cmrRelationships = new ArrayList<>();

        public CmpEntityMetadata(final String className, final String entityName) {
            this.className = className;
            this.entityName = entityName;
        }

        public void addCmpField(final CmpFieldInfo field) {
            cmpFields.add(field);
        }

        public void addCmrRelationship(final CmrRelationshipInfo relationship) {
            cmrRelationships.add(relationship);
        }

        public String getClassName() {
            return className;
        }

        public String getEntityName() {
            return entityName;
        }

        public @NotNull List<CmpFieldInfo> getCmpFields() {
            return cmpFields;
        }

        public @NotNull List<CmrRelationshipInfo> getCmrRelationships() {
            return cmrRelationships;
        }

    }

    public static class CmpFieldInfo {
        private final String name;
        private final String type;
        private final boolean isPrimaryKey;

        public CmpFieldInfo(final String name, final String type, final boolean isPrimaryKey) {
            this.name = name;
            this.type = type;
            this.isPrimaryKey = isPrimaryKey;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public boolean isPrimaryKey() {
            return isPrimaryKey;
        }
    }

    public static class CmrRelationshipInfo {
        private final String name;
        private final String targetEntity;
        private final String cardinality;
        private final boolean isCollection;

        public CmrRelationshipInfo(final String name, final String targetEntity, final String cardinality, final boolean isCollection) {
            this.name = name;
            this.targetEntity = targetEntity;
            this.cardinality = cardinality;
            this.isCollection = isCollection;
        }

        public String getName() {
            return name;
        }

        public String getTargetEntity() {
            return targetEntity;
        }

        public String getCardinality() {
            return cardinality;
        }

        public boolean isCollection() {
            return isCollection;
        }
    }

    /**
     * ASM visitor that analyzes CMP field mappings in entity bean classes
     */
    private class CmpFieldMappingVisitor extends ClassVisitor {
        private final @NotNull ClassNode classNode;
        private final JavaClassNode graphNode;
        private final ProjectFile projectFile;
        private final NodeDecorator<ProjectFile> projectFileDecorator;
        private CmpEntityMetadata metadata;

        public CmpFieldMappingVisitor(final ProjectFile projectFile, final NodeDecorator<ProjectFile> projectFileDecorator,
                                      final JavaClassNode graphNode) {
            super(Opcodes.ASM9);
            classNode = new ClassNode();
            this.graphNode = graphNode;
            this.projectFile = projectFile;
            this.projectFileDecorator = projectFileDecorator;
        }

        @Override
        public void visit(final int version, final int access, final String name, final String signature, final String superName,
                          final String[] interfaces) {
            classNode.visit(version, access, name, signature, superName, interfaces);

            // Initialize metadata for any supported file (will be used if it's a proper CMP
            // entity)
            metadata = new CmpEntityMetadata(name, extractEntityName(classNode));

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitEnd() {
            // If this is a proper CMP entity bean, do detailed analysis
            if (metadata != null && isCmpEntityBean(classNode)) {
                analyzeCmpMethods();
                setAllTagsAndProperties();
            }
            super.visitEnd();
        }

        private void setProperty(final String key, final Object value) {
            graphNode.setProperty(key, value);
        }

        private void setTag(final String tag, final Object value) {
            projectFileDecorator.setProperty(tag, value);
        }

        private void setAllTagsAndProperties() {
            // Honor produces contract - set ALL produced tags on ProjectFile for dependency
            // chains
            setTag(EjbMigrationTags.TAG_EJB_CMP_FIELD_MAPPING, true);
            setTag(EjbMigrationTags.TAG_EJB_CMP_FIELD, true);
            setTag(EjbMigrationTags.TAG_EJB_CMR_RELATIONSHIP, true);
            setTag(EjbMigrationTags.TAG_EJB_PRIMARY_KEY_CLASS, true);
            setTag(EjbMigrationTags.TAG_JPA_CONVERSION_CANDIDATE, true);

            // Set analysis properties on ClassNode for export data
            setProperty(EjbMigrationTags.TAG_EJB_CMP_FIELD_MAPPING, true);
            setProperty(EjbMigrationTags.TAG_EJB_CMP_ENTITY, true);
            setProperty(EjbMigrationTags.TAG_JPA_CONVERSION_CANDIDATE, true);

            // Generate basic recommendations
            final List<String> recommendations = List.of("Convert CMP entity to JPA @Entity");
            setProperty("cmp_field_mapping.jpa_recommendations", String.join("; ", recommendations));

            // Perform detailed analysis if we have proper CMP metadata
            setDetailedAnalysisResults();
        }

        private void setDetailedAnalysisResults() {
            // Set field and relationship analysis properties on ClassNode
            if (!metadata.getCmpFields().isEmpty()) {
                setProperty(EjbMigrationTags.TAG_EJB_CMP_FIELD, metadata.getCmpFields().size());
            }

            if (!metadata.getCmrRelationships().isEmpty()) {
                setProperty(EjbMigrationTags.TAG_EJB_CMR_RELATIONSHIP, metadata.getCmrRelationships().size());
            }

            // Set primary key analysis properties
            final boolean hasPrimaryKey = metadata.getCmpFields().stream()
                                                  .anyMatch(field -> field.getName().toLowerCase().contains("id") ||
                                                          field.getName().toLowerCase().contains("key"));
            if (hasPrimaryKey) {
                setProperty(EjbMigrationTags.TAG_EJB_PRIMARY_KEY_CLASS, true);
            }

            // Assess migration complexity and set appropriate tags on ProjectFile
            final String complexity = assessMigrationComplexity(metadata);
            switch (complexity) {
                case "LOW":
                    break;
                case "MEDIUM":
                    break;
                case "HIGH":
                    break;
            }

            // Store detailed metadata as analysis property
            setProperty("cmp_field_mapping.metadata", metadata);

            // Generate detailed JPA migration recommendations (override basic ones)
            final List<String> recommendations = generateJpaRecommendations(metadata);
            setProperty("cmp_field_mapping.jpa_recommendations", String.join("; ", recommendations));

            // Cache metadata for graph creation
            cmpEntityCache.put(classNode.name, metadata);
        }

        private void analyzeCmpMethods() {
            if (classNode.methods != null) {
                final Map<String, MethodNode> getters = new HashMap<>();
                final Map<String, MethodNode> setters = new HashMap<>();

                // First pass: collect all getters and setters
                for (final MethodNode method : classNode.methods) {
                    if ((method.access & Opcodes.ACC_ABSTRACT) != 0) {
                        final String methodName = method.name;

                        if (methodName.startsWith("get") && method.desc.startsWith("()")) {
                            final String fieldName = extractFieldName(methodName, "get");
                            getters.put(fieldName, method);
                        } else if (methodName.startsWith("set") && method.desc.matches("\\([^)]+\\)V")) {
                            final String fieldName = extractFieldName(methodName, "set");
                            setters.put(fieldName, method);
                        }
                    }
                }

                // Second pass: analyze getter/setter pairs for CMP fields and CMR relationships
                for (final String fieldName : getters.keySet()) {
                    if (setters.containsKey(fieldName)) {
                        final MethodNode getter = getters.get(fieldName);
                        final MethodNode setter = setters.get(fieldName);

                        if (isCmrRelationship(getter.desc)) {
                            final CmrRelationshipInfo cmr = analyzeCmrRelationship(fieldName, getter);
                            metadata.addCmrRelationship(cmr);
                        } else {
                            final CmpFieldInfo field = analyzeCmpField(fieldName, getter);
                            metadata.addCmpField(field);
                        }
                    }
                }
            }
        }

    }
}
