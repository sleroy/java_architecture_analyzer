package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.binary.AbstractBinaryClassNodeInspector;
import com.analyzer.rules.graph.BinaryJavaClassNodeInspector;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.inject.Inject;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
@InspectorDependencies(need = { BinaryJavaClassNodeInspector.class }, requires = {
        InspectorTags.TAG_JAVA_DETECTED }, produces = {
                EjbMigrationTags.EJB_CMP_FIELD_MAPPING,
                EjbMigrationTags.EJB_CMP_FIELD,
                EjbMigrationTags.EJB_CMR_RELATIONSHIP,
                EjbMigrationTags.EJB_PRIMARY_KEY_CLASS,
                EjbMigrationTags.MIGRATION_COMPLEXITY_LOW,
                EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM,
                EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH,
                EjbMigrationTags.JPA_CONVERSION_CANDIDATE
        })
public class CmpFieldMappingJavaBinaryInspector extends AbstractBinaryClassNodeInspector {

    private static final Set<String> CMP_METHOD_PREFIXES = Set.of("get", "set");
    private static final Set<String> CMR_COLLECTION_TYPES = Set.of(
            "java/util/Collection", "java/util/Set", "java/util/List", "java/util/Vector");

    private final Map<String, CmpEntityMetadata> cmpEntityCache;

    @Inject
    public CmpFieldMappingJavaBinaryInspector(ResourceResolver resourceResolver,
            ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classNodeRepository);
        this.cmpEntityCache = new ConcurrentHashMap<>();
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        return super.supports(projectFile);
    }

    @Override
    public void analyzeClassNode(ProjectFile projectFile, JavaClassNode classNode, ResourceLocation binaryLocation,
            InputStream classInputStream, NodeDecorator<ProjectFile> projectFileDecorator) throws java.io.IOException {
        classNode.setProjectFileId(projectFile.getId());

        try {
            ClassReader classReader = new ClassReader(classInputStream);
            CmpFieldMappingVisitor visitor = new CmpFieldMappingVisitor(projectFile, projectFileDecorator, classNode);
            classReader.accept(visitor, 0);
        } catch (Exception e) {
            projectFileDecorator.error("ASM analysis error: " + e.getMessage());
        }
    }

    /**
     * ASM visitor that analyzes CMP field mappings in entity bean classes
     */
    private class CmpFieldMappingVisitor extends ClassVisitor {
        private final ClassNode classNode;
        private CmpEntityMetadata metadata;
        private final JavaClassNode graphNode;
        private final ProjectFile projectFile;
        private final NodeDecorator<ProjectFile> projectFileDecorator;

        public CmpFieldMappingVisitor(ProjectFile projectFile, NodeDecorator<ProjectFile> projectFileDecorator,
                JavaClassNode graphNode) {
            super(Opcodes.ASM9);
            this.classNode = new ClassNode();
            this.graphNode = graphNode;
            this.projectFile = projectFile;
            this.projectFileDecorator = projectFileDecorator;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            classNode.visit(version, access, name, signature, superName, interfaces);

            // Initialize metadata for any supported file (will be used if it's a proper CMP
            // entity)
            metadata = new CmpEntityMetadata(name, extractEntityName(classNode));

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitEnd() {
            // If this is a proper CMP entity bean, do detailed analysis
            if (metadata != null && isCmpEntityBean(classNode, projectFile)) {
                analyzeCmpMethods();
                setAllTagsAndProperties();
            }
            super.visitEnd();
        }

        private void setProperty(String key, Object value) {
            graphNode.setProperty(key, value);
        }

        private void setTag(String tag, Object value) {
            projectFileDecorator.setProperty(tag, value);
        }

        private void setAllTagsAndProperties() {
            // Honor produces contract - set ALL produced tags on ProjectFile for dependency
            // chains
            setTag(EjbMigrationTags.EJB_CMP_FIELD_MAPPING, true);
            setTag(EjbMigrationTags.EJB_CMP_FIELD, true);
            setTag(EjbMigrationTags.EJB_CMR_RELATIONSHIP, true);
            setTag(EjbMigrationTags.EJB_PRIMARY_KEY_CLASS, true);
            setTag(EjbMigrationTags.JPA_CONVERSION_CANDIDATE, true);

            // Set analysis properties on ClassNode for export data
            setProperty(EjbMigrationTags.EJB_CMP_FIELD_MAPPING, true);
            setProperty(EjbMigrationTags.EJB_CMP_ENTITY, true);
            setProperty(EjbMigrationTags.JPA_CONVERSION_CANDIDATE, true);

            // Generate basic recommendations
            List<String> recommendations = List.of("Convert CMP entity to JPA @Entity");
            setProperty("cmp_field_mapping.jpa_recommendations", String.join("; ", recommendations));

            // Perform detailed analysis if we have proper CMP metadata
            setDetailedAnalysisResults();
        }

        private void setDetailedAnalysisResults() {
            // Set field and relationship analysis properties on ClassNode
            if (!metadata.getCmpFields().isEmpty()) {
                setProperty(EjbMigrationTags.EJB_CMP_FIELD, metadata.getCmpFields().size());
            }

            if (!metadata.getCmrRelationships().isEmpty()) {
                setProperty(EjbMigrationTags.EJB_CMR_RELATIONSHIP, metadata.getCmrRelationships().size());
            }

            // Set primary key analysis properties
            boolean hasPrimaryKey = metadata.getCmpFields().stream()
                    .anyMatch(field -> field.getName().toLowerCase().contains("id") ||
                            field.getName().toLowerCase().contains("key"));
            if (hasPrimaryKey) {
                setProperty(EjbMigrationTags.EJB_PRIMARY_KEY_CLASS, true);
            }

            // Assess migration complexity and set appropriate tags on ProjectFile
            String complexity = assessMigrationComplexity(metadata);
            switch (complexity) {
                case "LOW":
                    setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_LOW, true);
                    setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_LOW, true);
                    break;
                case "MEDIUM":
                    setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
                    setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
                    break;
                case "HIGH":
                    setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH, true);
                    setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH, true);
                    break;
            }

            // Store detailed metadata as analysis property
            setProperty("cmp_field_mapping.metadata", metadata);

            // Generate detailed JPA migration recommendations (override basic ones)
            List<String> recommendations = generateJpaRecommendations(metadata);
            setProperty("cmp_field_mapping.jpa_recommendations", String.join("; ", recommendations));

            // Cache metadata for graph creation
            cmpEntityCache.put(classNode.name, metadata);
        }

        private void analyzeCmpMethods() {
            if (classNode.methods != null) {
                Map<String, MethodNode> getters = new HashMap<>();
                Map<String, MethodNode> setters = new HashMap<>();

                // First pass: collect all getters and setters
                for (MethodNode method : classNode.methods) {
                    if ((method.access & Opcodes.ACC_ABSTRACT) != 0) {
                        String methodName = method.name;

                        if (methodName.startsWith("get") && method.desc.startsWith("()")) {
                            String fieldName = extractFieldName(methodName, "get");
                            getters.put(fieldName, method);
                        } else if (methodName.startsWith("set") && method.desc.matches("\\([^)]+\\)V")) {
                            String fieldName = extractFieldName(methodName, "set");
                            setters.put(fieldName, method);
                        }
                    }
                }

                // Second pass: analyze getter/setter pairs for CMP fields and CMR relationships
                for (String fieldName : getters.keySet()) {
                    if (setters.containsKey(fieldName)) {
                        MethodNode getter = getters.get(fieldName);
                        MethodNode setter = setters.get(fieldName);

                        if (isCmrRelationship(getter.desc)) {
                            CmrRelationshipInfo cmr = analyzeCmrRelationship(fieldName, getter);
                            metadata.addCmrRelationship(cmr);
                        } else {
                            CmpFieldInfo field = analyzeCmpField(fieldName, getter);
                            metadata.addCmpField(field);
                        }
                    }
                }
            }
        }

    }

    private boolean isCmpEntityBean(ClassNode classNode, ProjectFile projectFile) {
        // Check if class implements EntityBean interface
        if (!implementsEntityBean(classNode)) {
            return false;
        }

        // Check for abstract CMP methods
        return hasAbstractCmpMethods(classNode);
    }

    private boolean implementsEntityBean(ClassNode classNode) {
        if (classNode.interfaces != null) {
            for (String interfaceName : classNode.interfaces) {
                if ("javax/ejb/EntityBean".equals(interfaceName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAbstractCmpMethods(ClassNode classNode) {
        if (classNode.methods != null) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & Opcodes.ACC_ABSTRACT) != 0 && isCmpAccessorMethod(method)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCmpAccessorMethod(MethodNode method) {
        String methodName = method.name;

        // Check for getter pattern
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return method.desc.startsWith("()"); // No parameters
        }

        // Check for setter pattern
        if (methodName.startsWith("set") && methodName.length() > 3) {
            return method.desc.matches("\\([^)]+\\)V"); // One parameter, void return
        }

        return false;
    }

    private String extractEntityName(ClassNode classNode) {
        String className = classNode.name;
        int lastSlash = className.lastIndexOf('/');
        String simpleName = lastSlash >= 0 ? className.substring(lastSlash + 1) : className;

        // Remove "Bean" suffix if present
        if (simpleName.endsWith("Bean")) {
            return simpleName.substring(0, simpleName.length() - 4);
        }
        return simpleName;
    }

    private String extractFieldName(String methodName, String prefix) {
        String fieldName = methodName.substring(prefix.length());
        if (!fieldName.isEmpty()) {
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }
        return fieldName;
    }

    private boolean isCmrRelationship(String methodDescriptor) {
        // Extract return type from method descriptor
        String returnType = extractReturnType(methodDescriptor);

        // Check for collection types (1:M, M:M relationships)
        for (String collectionType : CMR_COLLECTION_TYPES) {
            if (returnType.contains(collectionType)) {
                return true;
            }
        }

        // Check for entity bean types (assume non-primitive, non-common types are
        // entities)
        return isLikelyEntityType(returnType);
    }

    private String extractReturnType(String methodDescriptor) {
        int parenIndex = methodDescriptor.indexOf(')');
        if (parenIndex >= 0 && parenIndex < methodDescriptor.length() - 1) {
            String returnTypePart = methodDescriptor.substring(parenIndex + 1);
            if (returnTypePart.startsWith("L") && returnTypePart.endsWith(";")) {
                return returnTypePart.substring(1, returnTypePart.length() - 1);
            }
        }
        return "void";
    }

    private boolean isLikelyEntityType(String typeName) {
        // Skip primitive and common Java types
        if (typeName.length() == 1) { // void or primitives
            return false;
        }

        if (typeName.startsWith("java/lang/") || typeName.startsWith("java/util/") ||
                typeName.startsWith("java/math/") || typeName.equals("java/util/Date")) {
            return false;
        }

        // Likely an entity if it ends with Bean or Entity, or is a custom type
        return typeName.endsWith("Bean") || typeName.endsWith("Entity") ||
                (!typeName.startsWith("java/") && !typeName.startsWith("javax/"));
    }

    private CmpFieldInfo analyzeCmpField(String fieldName, MethodNode getter) {
        String fieldType = extractReturnType(getter.desc);
        boolean isPrimaryKey = fieldName.toLowerCase().contains("id") ||
                fieldName.toLowerCase().contains("key");

        return new CmpFieldInfo(fieldName, fieldType, isPrimaryKey);
    }

    private CmrRelationshipInfo analyzeCmrRelationship(String relationshipName, MethodNode getter) {
        String returnType = extractReturnType(getter.desc);
        boolean isCollection = CMR_COLLECTION_TYPES.stream()
                .anyMatch(returnType::contains);

        String targetEntity = isCollection ? extractGenericType() : returnType;
        String cardinality = determineCardinality(isCollection);

        return new CmrRelationshipInfo(relationshipName, targetEntity, cardinality, isCollection);
    }

    private String extractGenericType() {
        // Simplified generic type extraction for ASM descriptors
        // This would need enhancement for full generic support
        return "Object";
    }

    private String determineCardinality(boolean isCollection) {
        if (isCollection) {
            return "OneToMany"; // Default for collections
        } else {
            return "ManyToOne"; // Default for single entities
        }
    }

    private String assessMigrationComplexity(CmpEntityMetadata metadata) {
        int complexityScore = 0;

        // Base complexity for CMP entity
        complexityScore += 3;

        // Add complexity for each CMP field
        complexityScore += metadata.getCmpFields().size();

        // Add higher complexity for CMR relationships
        complexityScore += metadata.getCmrRelationships().size() * 2;

        // Add complexity for primary keys
        long pkFieldCount = metadata.getCmpFields().stream()
                .mapToLong(field -> field.isPrimaryKey() ? 1 : 0)
                .sum();
        if (pkFieldCount > 1) {
            complexityScore += 3; // Composite primary key
        }

        // Classify complexity
        if (complexityScore <= 5) {
            return "LOW";
        } else if (complexityScore <= 10) {
            return "MEDIUM";
        } else {
            return "HIGH";
        }
    }

    private List<String> generateJpaRecommendations(CmpEntityMetadata metadata) {
        List<String> recommendations = new ArrayList<>();

        recommendations.add("Convert CMP entity '" + metadata.getEntityName() + "' to JPA @Entity");

        for (CmpFieldInfo field : metadata.getCmpFields()) {
            if (field.isPrimaryKey()) {
                recommendations.add("Convert field '" + field.getName() + "' to @Id");
            } else {
                recommendations.add("Convert field '" + field.getName() + "' to @Column");
            }
        }

        for (CmrRelationshipInfo cmr : metadata.getCmrRelationships()) {
            if (cmr.isCollection()) {
                recommendations.add("Convert relationship '" + cmr.getName() + "' to @" + cmr.getCardinality());
            } else {
                recommendations.add("Convert relationship '" + cmr.getName() + "' to @" + cmr.getCardinality());
            }
        }

        return recommendations;
    }

    @Override
    public String getName() {
        return "CMP Field Mapping Inspector (I-0206)";
    }

    // Data classes
    public static class CmpEntityMetadata {
        private final String className;
        private final String entityName;
        private final List<CmpFieldInfo> cmpFields = new ArrayList<>();
        private final List<CmrRelationshipInfo> cmrRelationships = new ArrayList<>();

        public CmpEntityMetadata(String className, String entityName) {
            this.className = className;
            this.entityName = entityName;
        }

        public void addCmpField(CmpFieldInfo field) {
            cmpFields.add(field);
        }

        public void addCmrRelationship(CmrRelationshipInfo relationship) {
            cmrRelationships.add(relationship);
        }

        public String getClassName() {
            return className;
        }

        public String getEntityName() {
            return entityName;
        }

        public List<CmpFieldInfo> getCmpFields() {
            return cmpFields;
        }

        public List<CmrRelationshipInfo> getCmrRelationships() {
            return cmrRelationships;
        }

    }

    public static class CmpFieldInfo {
        private final String name;
        private final String type;
        private final boolean isPrimaryKey;

        public CmpFieldInfo(String name, String type, boolean isPrimaryKey) {
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

        public CmrRelationshipInfo(String name, String targetEntity, String cardinality, boolean isCollection) {
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
}
