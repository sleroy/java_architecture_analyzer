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
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.*;

/**
 * Inspector I-0207: Complex CMP Relationship Inspector
 * <p>
 * Detects complex Container Managed Relationships (CMR) in EJB 2.x entity beans
 * including bidirectional relationships, cascade operations, and collection
 * mappings.
 * Provides JPA conversion recommendations for modern Spring Boot applications.
 * <p>
 * Phase 2 - Advanced Persistence & Vendor Support
 */
@InspectorDependencies(need = {

        EntityBeanJavaSourceInspector.class
}, produces = {
        ComplexCmpRelationshipJavaSourceInspector.TAGS.TAG_HAS_COMPLEX_CMR,
        ComplexCmpRelationshipJavaSourceInspector.TAGS.TAG_BIDIRECTIONAL_RELATIONSHIP,
        ComplexCmpRelationshipJavaSourceInspector.TAGS.TAG_CASCADE_OPERATIONS,
        ComplexCmpRelationshipJavaSourceInspector.TAGS.TAG_COLLECTION_RELATIONSHIPS,
        ComplexCmpRelationshipJavaSourceInspector.TAGS.TAG_JPA_CONVERSION_COMPLEXITY
})
public class ComplexCmpRelationshipJavaSourceInspector extends AbstractJavaParserInspector {

    private final ClassNodeRepository classNodeRepository;

    @Inject
    public ComplexCmpRelationshipJavaSourceInspector(ResourceResolver resourceResolver,
            ClassNodeRepository classNodeRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        return super.supports(projectFile);
    }

    @Override
    protected void analyzeCompilationUnit(CompilationUnit cu, ProjectFile projectFile,
                                          NodeDecorator projectFileDecorator) {

        ComplexCmrDetector detector = new ComplexCmrDetector();
        cu.accept(detector, null);

        ComplexCmrMetadata metadata = detector.getMetadata();

        classNodeRepository.getOrCreateClassNode(cu).ifPresent(classNode -> {
            classNode.setProjectFileId(projectFile.getId());

            // Always set all tags, regardless of whether relationships were found
            boolean hasComplexRelationships = metadata.hasComplexRelationships();

            // Set tags on ProjectFile for dependency chain
            projectFile.setProperty(TAGS.TAG_HAS_COMPLEX_CMR, hasComplexRelationships);
            projectFile.setProperty(TAGS.TAG_BIDIRECTIONAL_RELATIONSHIP, metadata.hasBidirectionalRelationships());
            projectFile.setProperty(TAGS.TAG_CASCADE_OPERATIONS, metadata.hasCascadeOperations());
            projectFile.setProperty(TAGS.TAG_COLLECTION_RELATIONSHIPS, metadata.hasCollectionRelationships());
            projectFile.setProperty(TAGS.TAG_JPA_CONVERSION_COMPLEXITY, metadata.getJpaConversionComplexity());

            // Set single consolidated property on ClassNode for analysis data (follows
            // guidelines)
            ComplexCmrAnalysisResult analysisResult = new ComplexCmrAnalysisResult(
                    hasComplexRelationships,
                    metadata.hasBidirectionalRelationships(),
                    metadata.hasCascadeOperations(),
                    metadata.hasCollectionRelationships(),
                    metadata.getJpaConversionComplexity(),
                    metadata,
                    hasComplexRelationships ? generateJpaRecommendations(metadata)
                            : List.of("No complex CMP relationships detected"));

            classNode.setProperty(PROPERTIES.COMPLEX_CMR_ANALYSIS, analysisResult);
        });
    }

    private List<String> generateJpaRecommendations(ComplexCmrMetadata metadata) {
        List<String> recommendations = new ArrayList<>();

        for (CmrRelationship relationship : metadata.getRelationships()) {
            if (relationship.isCollection()) {
                if (relationship.isOneToMany()) {
                    recommendations.add("Convert " + relationship.getFieldName() + " to @OneToMany with List<" +
                            relationship.getTargetEntity() + ">");
                } else if (relationship.isManyToMany()) {
                    recommendations.add("Convert " + relationship.getFieldName() + " to @ManyToMany with Set<" +
                            relationship.getTargetEntity() + ">");
                }

                if (relationship.hasCascadeOperations()) {
                    recommendations.add("Add @JoinColumn or @JoinTable for " + relationship.getFieldName() +
                            " with cascade = CascadeType." + relationship.getCascadeType());
                }
            } else {
                // Single-valued relationship
                if (relationship.isOneToOne()) {
                    recommendations.add("Convert " + relationship.getFieldName() + " to @OneToOne");
                } else if (relationship.isManyToOne()) {
                    recommendations.add("Convert " + relationship.getFieldName() + " to @ManyToOne");
                }
            }

            if (relationship.isBidirectional()) {
                recommendations.add("Add mappedBy attribute for bidirectional relationship: " +
                        relationship.getFieldName());
            }
        }

        return recommendations;
    }

    @Override
    public String getName() {
        return "Complex CMP Relationship Inspector (I-0207)";
    }

    public static class TAGS {
        public static final String TAG_HAS_COMPLEX_CMR = "complex_cmp_relationship.has_complex_cmr";
        public static final String TAG_BIDIRECTIONAL_RELATIONSHIP = "complex_cmp_relationship.bidirectional";
        public static final String TAG_CASCADE_OPERATIONS = "complex_cmp_relationship.cascade_operations";
        public static final String TAG_COLLECTION_RELATIONSHIPS = "complex_cmp_relationship.collection_relationships";
        public static final String TAG_JPA_CONVERSION_COMPLEXITY = "complex_cmp_relationship.jpa_conversion_complexity";
    }

    public static class PROPERTIES {
        public static final String COMPLEX_CMR_ANALYSIS = "complex_cmp_relationship.analysis";
    }

    /**
     * Visitor that detects complex CMP relationships by analyzing getter/setter
     * patterns,
     * collection types, and cascade operation patterns.
     */
    private static class ComplexCmrDetector extends VoidVisitorAdapter<Void> {
        private final ComplexCmrMetadata metadata = new ComplexCmrMetadata();
        private String currentClassName;

        // Predefined sets for type checking - better performance than multiple equals()
        private static final Set<String> PRIMITIVE_AND_COMMON_TYPES = Set.of(
                "int", "long", "String", "boolean", "double", "float",
                "Integer", "Long", "Boolean", "Double", "Float", "Date", "BigDecimal");

        private static final Set<String> COMMON_TYPE_PREFIXES = Set.of(
                "java.lang.", "java.util.Date", "java.math.");

        private static final Set<String> COLLECTION_TYPE_KEYWORDS = Set.of(
                "Collection", "Set", "List", "Vector");

        private static final Set<String> BIDIRECTIONAL_HINT_KEYWORDS = Set.of(
                "parent", "child", "owner", "inverse", "opposite");

        public ComplexCmrMetadata getMetadata() {
            return metadata;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            if (classDecl.isInterface()) {
                super.visit(classDecl, arg);
                return;
            }

            currentClassName = classDecl.getNameAsString();
            metadata.setEntityClass(currentClassName);

            // Analyze methods for CMR relationship patterns
            analyzeMethodsForRelationships(classDecl);

            super.visit(classDecl, arg);
        }

        private void analyzeMethodsForRelationships(ClassOrInterfaceDeclaration classDecl) {
            Map<String, MethodDeclaration> getters = new HashMap<>();
            Map<String, MethodDeclaration> setters = new HashMap<>();

            // First pass: collect all getters and setters
            for (MethodDeclaration method : classDecl.getMethods()) {
                String methodName = method.getNameAsString();

                if (methodName.startsWith("get") && method.getParameters().isEmpty()) {
                    String fieldName = extractFieldName(methodName, "get");
                    getters.put(fieldName, method);
                } else if (methodName.startsWith("set") && method.getParameters().size() == 1) {
                    String fieldName = extractFieldName(methodName, "set");
                    setters.put(fieldName, method);
                }
            }

            // Second pass: analyze getter/setter pairs for relationships
            for (String fieldName : getters.keySet()) {
                if (setters.containsKey(fieldName)) {
                    MethodDeclaration getter = getters.get(fieldName);
                    MethodDeclaration setter = setters.get(fieldName);

                    CmrRelationship relationship = analyzeRelationshipPair(fieldName, getter, setter);
                    if (relationship != null) {
                        metadata.addRelationship(relationship);
                    }
                }
            }

            // Third pass: look for cascade operation methods
            analyzeCascadeOperations(classDecl);
        }

        private CmrRelationship analyzeRelationshipPair(String fieldName, MethodDeclaration getter,
                MethodDeclaration setter) {
            Type returnType = getter.getType();
            String returnTypeName = returnType.toString();

            // Skip primitive types and common Java types
            if (isPrimitiveOrCommonType(returnTypeName)) {
                return null;
            }

            CmrRelationship relationship = new CmrRelationship(fieldName);

            // Determine if it's a collection relationship
            if (isCollectionType(returnTypeName)) {
                relationship.setCollection(true);
                relationship.setCollectionType(extractCollectionType(returnTypeName));
                relationship.setTargetEntity(extractGenericType(returnTypeName));

                // Determine relationship cardinality for collections
                if (returnTypeName.contains("Collection") || returnTypeName.contains("Set")) {
                    // Could be OneToMany or ManyToMany - assume OneToMany for now
                    relationship.setRelationshipType("OneToMany");
                } else if (returnTypeName.contains("List")) {
                    relationship.setRelationshipType("OneToMany");
                }
            } else {
                // Single-valued relationship - only create if it looks like an entity reference
                if (isLikelyEntityType(returnTypeName)) {
                    relationship.setCollection(false);
                    relationship.setTargetEntity(returnTypeName);
                    // Could be OneToOne or ManyToOne - assume ManyToOne for now
                    relationship.setRelationshipType("ManyToOne");
                } else {
                    // Not a likely entity relationship
                    return null;
                }
            }

            // Check for bidirectional relationship hints in method names or comments
            if (hasBidirectionalHints(getter, setter, fieldName)) {
                relationship.setBidirectional(true);
            }

            return relationship;
        }

        private void analyzeCascadeOperations(ClassOrInterfaceDeclaration classDecl) {
            for (MethodDeclaration method : classDecl.getMethods()) {
                String methodName = method.getNameAsString();

                // Look for cascade operation patterns
                if (methodName.equals("ejbRemove")) {
                    // Check if this method has cascade logic
                    if (method.getBody().isPresent() &&
                            method.getBody().get().toString().contains("remove")) {
                        // Mark ALL relationships as having cascade operations
                        for (CmrRelationship relationship : metadata.getRelationships()) {
                            relationship.setCascadeOperations(true);
                            relationship.setCascadeType("REMOVE");
                        }
                    }
                }
            }
        }

        private String extractFieldName(String methodName, String prefix) {
            String fieldName = methodName.substring(prefix.length());
            if (fieldName.length() > 0) {
                return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
            }
            return fieldName;
        }

        private boolean isPrimitiveOrCommonType(String typeName) {
            // Don't treat Collections as common types - they could be relationships
            if (isCollectionType(typeName)) {
                return false;
            }

            // Check exact matches first
            if (PRIMITIVE_AND_COMMON_TYPES.contains(typeName)) {
                return true;
            }

            // Check prefixes
            return COMMON_TYPE_PREFIXES.stream().anyMatch(typeName::startsWith);
        }

        private boolean isLikelyEntityType(String typeName) {
            // Check if this looks like an EJB entity type
            return typeName.endsWith("Bean") || typeName.endsWith("Entity") ||
                    (!isPrimitiveOrCommonType(typeName) && !typeName.startsWith("java."));
        }

        private boolean isCollectionType(String typeName) {
            return COLLECTION_TYPE_KEYWORDS.stream().anyMatch(typeName::contains);
        }

        private String extractCollectionType(String typeName) {
            if (typeName.contains("Set"))
                return "Set";
            if (typeName.contains("List"))
                return "List";
            if (typeName.contains("Collection"))
                return "Collection";
            return "Collection";
        }

        private String extractGenericType(String typeName) {
            int start = typeName.indexOf('<');
            int end = typeName.lastIndexOf('>');
            if (start > 0 && end > start) {
                return typeName.substring(start + 1, end);
            }
            return "Object";
        }

        private boolean hasBidirectionalHints(MethodDeclaration getter, MethodDeclaration setter, String fieldName) {
            // Simple heuristic: if field name contains bidirectional relationship keywords
            String lowerFieldName = fieldName.toLowerCase();
            return BIDIRECTIONAL_HINT_KEYWORDS.stream().anyMatch(lowerFieldName::contains);
        }
    }

    /**
     * Data class to hold Complex CMP Relationship analysis metadata
     */
    public static class ComplexCmrMetadata {
        private final List<CmrRelationship> relationships = new ArrayList<>();
        private String entityClass;

        public void setEntityClass(String entityClass) {
            this.entityClass = entityClass;
        }

        public void addRelationship(CmrRelationship relationship) {
            relationships.add(relationship);
        }

        public List<CmrRelationship> getRelationships() {
            return relationships;
        }

        public boolean hasComplexRelationships() {
            return !relationships.isEmpty();
        }

        public boolean hasBidirectionalRelationships() {
            return relationships.stream().anyMatch(CmrRelationship::isBidirectional);
        }

        public boolean hasCascadeOperations() {
            return relationships.stream().anyMatch(CmrRelationship::hasCascadeOperations);
        }

        public boolean hasCollectionRelationships() {
            return relationships.stream().anyMatch(CmrRelationship::isCollection);
        }

        public String getJpaConversionComplexity() {
            if (relationships.isEmpty())
                return "NONE";

            long complexCount = relationships.stream()
                    .mapToLong(r -> (r.isBidirectional() ? 1 : 0) +
                            (r.hasCascadeOperations() ? 1 : 0) +
                            (r.isCollection() ? 1 : 0))
                    .sum();

            if (complexCount >= 5)
                return "HIGH";
            if (complexCount >= 2)
                return "MEDIUM";
            return "LOW";
        }

    }

    /**
     * Consolidated analysis result for Complex CMP Relationship analysis
     */
    public static class ComplexCmrAnalysisResult {
        private final boolean hasComplexCmr;
        private final boolean hasBidirectionalRelationships;
        private final boolean hasCascadeOperations;
        private final boolean hasCollectionRelationships;
        private final String jpaConversionComplexity;
        private final ComplexCmrMetadata detailedMetadata;
        private final List<String> jpaRecommendations;

        public ComplexCmrAnalysisResult(boolean hasComplexCmr, boolean hasBidirectionalRelationships,
                boolean hasCascadeOperations, boolean hasCollectionRelationships,
                String jpaConversionComplexity, ComplexCmrMetadata detailedMetadata, List<String> jpaRecommendations) {
            this.hasComplexCmr = hasComplexCmr;
            this.hasBidirectionalRelationships = hasBidirectionalRelationships;
            this.hasCascadeOperations = hasCascadeOperations;
            this.hasCollectionRelationships = hasCollectionRelationships;
            this.jpaConversionComplexity = jpaConversionComplexity != null ? jpaConversionComplexity : "NONE";
            this.detailedMetadata = detailedMetadata;
            this.jpaRecommendations = jpaRecommendations != null ? jpaRecommendations : new ArrayList<>();
        }
    }

    /**
     * Data class representing a single CMP relationship
     */
    public static class CmrRelationship {
        private final String fieldName;
        private String targetEntity;
        private String relationshipType; // OneToOne, OneToMany, ManyToOne, ManyToMany
        private boolean isCollection;
        private String collectionType; // Set, List, Collection
        private boolean isBidirectional;
        private boolean hasCascadeOperations;
        private String cascadeType; // PERSIST, REMOVE, MERGE, etc.

        public CmrRelationship(String fieldName) {
            this.fieldName = fieldName;
        }

        // Getters and setters
        public String getFieldName() {
            return fieldName;
        }

        public String getTargetEntity() {
            return targetEntity;
        }

        public void setTargetEntity(String targetEntity) {
            this.targetEntity = targetEntity;
        }

        public String getRelationshipType() {
            return relationshipType;
        }

        public void setRelationshipType(String relationshipType) {
            this.relationshipType = relationshipType;
        }

        public boolean isCollection() {
            return isCollection;
        }

        public void setCollection(boolean collection) {
            isCollection = collection;
        }

        public String getCollectionType() {
            return collectionType;
        }

        public void setCollectionType(String collectionType) {
            this.collectionType = collectionType;
        }

        public boolean isBidirectional() {
            return isBidirectional;
        }

        public void setBidirectional(boolean bidirectional) {
            isBidirectional = bidirectional;
        }

        public boolean hasCascadeOperations() {
            return hasCascadeOperations;
        }

        public void setCascadeOperations(boolean cascadeOperations) {
            hasCascadeOperations = cascadeOperations;
        }

        public String getCascadeType() {
            return cascadeType;
        }

        public void setCascadeType(String cascadeType) {
            this.cascadeType = cascadeType;
        }

        // Convenience methods
        public boolean isOneToOne() {
            return "OneToOne".equals(relationshipType);
        }

        public boolean isOneToMany() {
            return "OneToMany".equals(relationshipType);
        }

        public boolean isManyToOne() {
            return "ManyToOne".equals(relationshipType);
        }

        public boolean isManyToMany() {
            return "ManyToMany".equals(relationshipType);
        }
    }
}
