# Task 10: I-0207 Complex CMP Relationship Inspector

**ID:** Task 10  
**Priority:** P1 (High - Phase 2 Start)  
**Estimated Effort:** 6-8 hours  
**Prerequisites:** Tasks 1, 2, 3, 4 complete  
**Deliverables:** Complex CMP relationship detection and JPA conversion recommendations  

## Overview

Implement inspector I-0207 to detect and analyze complex Container Managed Relationships (CMR) in EJB 2.x entity beans. This inspector builds upon the CMP Field Mapping Inspector (I-0206) to handle sophisticated relationship patterns including bidirectional relationships, cascade operations, and complex foreign key mappings essential for JPA migration.

## Technical Requirements

### Detection Capabilities

1. **Complex CMR Pattern Detection**
   - Bidirectional one-to-one relationships with proper back-references
   - One-to-many and many-to-many relationships with Collection/Set interfaces
   - Self-referencing relationships (parent-child hierarchies)
   - Complex composite foreign key relationships

2. **Cascade Operation Analysis**
   - Cascade delete pattern detection in CMR relationships
   - Cascade create and update patterns in business methods
   - Orphan removal requirements identification
   - Transactional cascade boundary analysis

3. **Collection Interface Mapping**
   - EJB Collection/Set/List interface usage in CMR fields
   - Generic type inference for JPA conversion
   - Lazy vs eager loading pattern detection
   - Collection ordering and sorting requirements

4. **Foreign Key Relationship Analysis**
   - Abstract vs concrete CMP field relationships
   - Composite foreign key mapping patterns
   - Join table requirements for many-to-many relationships
   - Cross-entity referential integrity constraints

### Implementation Specifications

#### Inspector Class Structure

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/ComplexCMPRelationshipInspector.java
public class ComplexCMPRelationshipInspector extends AbstractASMInspector 
        implements GraphAwareInspector {
    
    private static final String INSPECTOR_ID = "I-0207";
    private static final String INSPECTOR_NAME = "Complex CMP Relationship Inspector";
    
    private final Set<String> CMR_COLLECTION_INTERFACES = Set.of(
        "java.util.Collection", "java.util.Set", "java.util.List",
        "javax.ejb.EJBLocalObject", "javax.ejb.EJBObject"
    );
    
    private final Map<String, String> JPA_RELATIONSHIP_MAPPINGS = Map.of(
        "ONE_TO_ONE", "@OneToOne",
        "ONE_TO_MANY", "@OneToMany", 
        "MANY_TO_ONE", "@ManyToOne",
        "MANY_TO_MANY", "@ManyToMany"
    );
    
    private GraphRepository graphRepository;
    private Map<String, ComplexCMRMetadata> cmrAnalysisCache;
    private Map<String, RelationshipDescriptor> relationshipDescriptors;
    
    public ComplexCMPRelationshipInspector() {
        super(INSPECTOR_ID, INSPECTOR_NAME);
        this.cmrAnalysisCache = new ConcurrentHashMap<>();
        this.relationshipDescriptors = new ConcurrentHashMap<>();
    }
    
    @Override
    public void setGraphRepository(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }
    
    @Override
    public List<String> getTags() {
        return Arrays.asList(
            EjbMigrationTags.EJB_CMP_ENTITY,
            EjbMigrationTags.EJB_COMPLEX_RELATIONSHIP,
            EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH,
            EjbMigrationTags.PERSISTENCE_LAYER,
            EjbMigrationTags.JPA_CONVERSION_CANDIDATE
        );
    }
    
    @Override
    protected InspectorResult processClass(ProjectFile file, ClassNode classNode) {
        // Only process CMP entity beans with relationships
        if (!isCMPEntityWithRelationships(classNode)) {
            return InspectorResult.empty();
        }
        
        ComplexCMRMetadata metadata = analyzeComplexCMRPatterns(file, classNode);
        List<RelationshipDescriptor> relationships = extractRelationshipDescriptors(file, classNode);
        
        return createInspectorResult(file, classNode, metadata, relationships);
    }
}
```

#### Complex CMR Pattern Analysis

```java
private ComplexCMRMetadata analyzeComplexCMRPatterns(ProjectFile file, ClassNode classNode) {
    ComplexCMRMetadata.Builder builder = ComplexCMRMetadata.builder()
        .entityName(extractEntityName(classNode))
        .className(classNode.name)
        .projectFile(file);
    
    // Analyze relationship methods
    Map<String, CMRMethodPair> relationshipMethods = groupRelationshipMethods(classNode);
    
    for (Map.Entry<String, CMRMethodPair> entry : relationshipMethods.entrySet()) {
        String relationshipName = entry.getKey();
        CMRMethodPair methodPair = entry.getValue();
        
        ComplexCMRInfo relationship = analyzeComplexRelationship(
            relationshipName, methodPair, classNode);
        builder.addComplexRelationship(relationship);
    }
    
    // Analyze cascade patterns
    List<CascadeOperationInfo> cascadeOps = analyzeCascadeOperations(classNode);
    builder.cascadeOperations(cascadeOps);
    
    // Analyze bidirectional patterns
    List<BidirectionalRelationshipInfo> bidirectional = analyzeBidirectionalRelationships(classNode);
    builder.bidirectionalRelationships(bidirectional);
    
    return builder.build();
}

private boolean isCMPEntityWithRelationships(ClassNode classNode) {
    // Must be CMP entity bean
    if (!isCMPEntityBean(classNode)) {
        return false;
    }
    
    // Must have CMR accessor methods
    return classNode.methods.stream()
        .anyMatch(this::isCMRAccessorMethod);
}

private Map<String, CMRMethodPair> groupRelationshipMethods(ClassNode classNode) {
    Map<String, CMRMethodPair> relationships = new HashMap<>();
    
    for (MethodNode method : classNode.methods) {
        if (isCMRAccessorMethod(method)) {
            String relationshipName = extractRelationshipName(method.name);
            
            relationships.computeIfAbsent(relationshipName, k -> new CMRMethodPair())
                .addMethod(method);
        }
    }
    
    // Filter to only complete pairs (getter + setter)
    return relationships.entrySet().stream()
        .filter(entry -> entry.getValue().isComplete())
        .collect(Collectors.toMap(
            Map.Entry::getKey, 
            Map.Entry::getValue
        ));
}

private ComplexCMRInfo analyzeComplexRelationship(String relationshipName, 
        CMRMethodPair methodPair, ClassNode classNode) {
    
    MethodNode getter = methodPair.getGetter();
    MethodNode setter = methodPair.getSetter();
    
    String targetEntityType = extractTargetEntityType(getter.desc);
    RelationshipCardinality cardinality = determineComplexCardinality(getter.desc, setter.desc);
    boolean isCollection = isCollectionRelationship(getter.desc);
    
    ComplexCMRInfo.Builder builder = ComplexCMRInfo.builder()
        .relationshipName(relationshipName)
        .targetEntityType(targetEntityType)
        .cardinality(cardinality)
        .isCollection(isCollection)
        .getterMethod(getter.name)
        .setterMethod(setter.name);
    
    // Analyze cascade operations
    CascadeTypeInfo cascadeInfo = analyzeCascadeType(relationshipName, classNode);
    builder.cascadeInfo(cascadeInfo);
    
    // Analyze fetch strategy
    FetchStrategyInfo fetchInfo = analyzeFetchStrategy(relationshipName, getter);
    builder.fetchStrategy(fetchInfo);
    
    // Analyze join configuration
    JoinConfigurationInfo joinInfo = analyzeJoinConfiguration(relationshipName, classNode);
    builder.joinConfiguration(joinInfo);
    
    // Check for bidirectional mapping
    BidirectionalMappingInfo mappingInfo = checkBidirectionalMapping(
        relationshipName, targetEntityType, classNode);
    builder.bidirectionalMapping(mappingInfo);
    
    return builder.build();
}
```

#### Bidirectional Relationship Detection

```java
private List<BidirectionalRelationshipInfo> analyzeBidirectionalRelationships(ClassNode classNode) {
    List<BidirectionalRelationshipInfo> bidirectional = new ArrayList<>();
    
    // Get all CMR relationships in this entity
    Map<String, ComplexCMRInfo> relationships = getEntityRelationships(classNode);
    
    for (ComplexCMRInfo relationship : relationships.values()) {
        BidirectionalRelationshipInfo biInfo = checkBidirectionalRelationship(
            relationship, classNode);
        
        if (biInfo != null) {
            bidirectional.add(biInfo);
        }
    }
    
    return bidirectional;
}

private BidirectionalRelationshipInfo checkBidirectionalRelationship(
        ComplexCMRInfo relationship, ClassNode classNode) {
    
    String targetEntityName = relationship.getTargetEntityType();
    String currentEntityName = extractEntityName(classNode);
    
    // Look for reverse relationship in target entity
    Optional<ProjectFile> targetEntityFile = findEntityBeanFile(targetEntityName);
    if (!targetEntityFile.isPresent()) {
        return null;
    }
    
    Optional<ClassNode> targetClassNode = loadClassNode(targetEntityFile.get());
    if (!targetClassNode.isPresent()) {
        return null;
    }
    
    // Check for reverse CMR relationship pointing back to current entity
    Optional<ComplexCMRInfo> reverseRelationship = findReverseRelationship(
        targetClassNode.get(), currentEntityName);
    
    if (reverseRelationship.isPresent()) {
        return BidirectionalRelationshipInfo.builder()
            .owningEntityName(currentEntityName)
            .owningSideRelationship(relationship.getRelationshipName())
            .targetEntityName(targetEntityName)
            .inverseSideRelationship(reverseRelationship.get().getRelationshipName())
            .relationshipType(determineBidirectionalType(relationship, reverseRelationship.get()))
            .owningEntity(determineOwningEntity(relationship, reverseRelationship.get()))
            .build();
    }
    
    return null;
}

private BidirectionalRelationshipType determineBidirectionalType(
        ComplexCMRInfo relationship1, ComplexCMRInfo relationship2) {
    
    boolean rel1IsCollection = relationship1.isCollection();
    boolean rel2IsCollection = relationship2.isCollection();
    
    if (!rel1IsCollection && !rel2IsCollection) {
        return BidirectionalRelationshipType.ONE_TO_ONE;
    } else if (rel1IsCollection && rel2IsCollection) {
        return BidirectionalRelationshipType.MANY_TO_MANY;
    } else {
        return BidirectionalRelationshipType.ONE_TO_MANY;
    }
}
```

#### Cascade Operation Analysis

```java
private List<CascadeOperationInfo> analyzeCascadeOperations(ClassNode classNode) {
    List<CascadeOperationInfo> cascadeOps = new ArrayList<>();
    
    // Look for cascade delete patterns in ejbRemove method
    Optional<MethodNode> ejbRemove = findEjbRemoveMethod(classNode);
    if (ejbRemove.isPresent()) {
        List<CascadeOperationInfo> deleteCascades = analyzeCascadeDeletes(ejbRemove.get());
        cascadeOps.addAll(deleteCascades);
    }
    
    // Look for cascade persist patterns in ejbCreate method
    Optional<MethodNode> ejbCreate = findEjbCreateMethod(classNode);
    if (ejbCreate.isPresent()) {
        List<CascadeOperationInfo> persistCascades = analyzeCascadeCreates(ejbCreate.get());
        cascadeOps.addAll(persistCascades);
    }
    
    // Look for cascade update patterns in business methods
    List<CascadeOperationInfo> updateCascades = analyzeCascadeUpdates(classNode);
    cascadeOps.addAll(updateCascades);
    
    return cascadeOps;
}

private List<CascadeOperationInfo> analyzeCascadeDeletes(MethodNode ejbRemove) {
    List<CascadeOperationInfo> cascades = new ArrayList<>();
    
    // Analyze method bytecode for relationship deletion patterns
    InsnList instructions = ejbRemove.instructions;
    AbstractInsnNode[] insnArray = instructions.toArray();
    
    for (int i = 0; i < insnArray.length - 2; i++) {
        AbstractInsnNode insn = insnArray[i];
        
        // Look for CMR relationship access followed by deletion
        if (isRelationshipGetterCall(insn)) {
            String relationshipName = extractRelationshipNameFromCall(insn);
            
            // Check if followed by collection iteration or direct removal
            if (isFollowedByDeletionPattern(insnArray, i + 1)) {
                CascadeOperationInfo cascade = CascadeOperationInfo.builder()
                    .relationshipName(relationshipName)
                    .cascadeType(CascadeType.DELETE)
                    .operationType(CascadeOperationType.EXPLICIT)
                    .methodName(ejbRemove.name)
                    .build();
                
                cascades.add(cascade);
            }
        }
    }
    
    return cascades;
}

private boolean isFollowedByDeletionPattern(AbstractInsnNode[] instructions, int startIndex) {
    // Look for patterns like:
    // - Iterator iteration with remove() calls
    // - Collection.clear() calls
    // - Direct ejbRemove() calls on related entities
    
    for (int i = startIndex; i < Math.min(startIndex + 10, instructions.length); i++) {
        AbstractInsnNode insn = instructions[i];
        
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode methodInsn = (MethodInsnNode) insn;
            
            if ("remove".equals(methodInsn.name) || 
                "clear".equals(methodInsn.name) ||
                "ejbRemove".equals(methodInsn.name)) {
                return true;
            }
        }
    }
    
    return false;
}
```

#### Graph Integration for Complex Relationships

```java
private void createComplexRelationshipGraph(ProjectFile file, ClassNode classNode,
        ComplexCMRMetadata metadata) {
    
    String entityNodeId = classNode.name;
    
    // Get or create the CMP entity node
    CMPEntityBeanNode entityNode = (CMPEntityBeanNode) graphRepository
        .getNode(entityNodeId)
        .orElseGet(() -> createCMPEntityNode(classNode));
    
    // Add complex relationship edges
    for (ComplexCMRInfo relationship : metadata.getComplexRelationships()) {
        createComplexCMREdge(entityNode, relationship);
    }
    
    // Add bidirectional relationship edges
    for (BidirectionalRelationshipInfo birel : metadata.getBidirectionalRelationships()) {
        createBidirectionalRelationshipEdge(entityNode, birel);
    }
    
    // Add cascade operation edges
    for (CascadeOperationInfo cascade : metadata.getCascadeOperations()) {
        createCascadeOperationEdge(entityNode, cascade);
    }
    
    graphRepository.addNode(entityNode);
}

private void createComplexCMREdge(CMPEntityBeanNode sourceNode, ComplexCMRInfo relationship) {
    String targetNodeId = relationship.getTargetEntityType();
    
    ComplexCMRRelationshipEdge edge = new ComplexCMRRelationshipEdge(
        generateEdgeId(sourceNode.getNodeId(), targetNodeId, relationship.getRelationshipName()),
        sourceNode.getNodeId(),
        targetNodeId,
        relationship.getRelationshipName()
    );
    
    // Set relationship properties
    edge.setCardinality(relationship.getCardinality().toString());
    edge.setIsCollection(relationship.isCollection());
    edge.setBidirectional(relationship.getBidirectionalMapping() != null);
    
    // Set cascade information
    if (relationship.getCascadeInfo() != null) {
        edge.setCascadeTypes(relationship.getCascadeInfo().getCascadeTypes());
        edge.setOrphanRemoval(relationship.getCascadeInfo().isOrphanRemoval());
    }
    
    // Set fetch strategy
    if (relationship.getFetchStrategy() != null) {
        edge.setFetchType(relationship.getFetchStrategy().getFetchType().toString());
        edge.setLazyLoading(relationship.getFetchStrategy().isLazyLoading());
    }
    
    // Set join configuration
    if (relationship.getJoinConfiguration() != null) {
        edge.setJoinType(relationship.getJoinConfiguration().getJoinType().toString());
        edge.setJoinColumns(relationship.getJoinConfiguration().getJoinColumns());
    }
    
    graphRepository.addEdge(edge);
}
```

#### JPA Conversion Recommendations

```java
private List<JPAConversionRecommendation> generateJPARecommendations(
        ComplexCMRMetadata metadata) {
    
    List<JPAConversionRecommendation> recommendations = new ArrayList<>();
    
    for (ComplexCMRInfo relationship : metadata.getComplexRelationships()) {
        JPAConversionRecommendation recommendation = createJPARecommendation(relationship);
        recommendations.add(recommendation);
    }
    
    return recommendations;
}

private JPAConversionRecommendation createJPARecommendation(ComplexCMRInfo relationship) {
    JPAConversionRecommendation.Builder builder = JPAConversionRecommendation.builder()
        .relationshipName(relationship.getRelationshipName())
        .originalEjbPattern("EJB CMR " + relationship.getCardinality().toString())
        .targetEntityType(relationship.getTargetEntityType());
    
    // Determine JPA annotation
    String jpaAnnotation = determineJPAAnnotation(relationship);
    builder.jpaAnnotation(jpaAnnotation);
    
    // Generate annotation attributes
    Map<String, String> attributes = generateJPAAttributes(relationship);
    builder.annotationAttributes(attributes);
    
    // Generate field transformation
    String fieldTransformation = generateFieldTransformation(relationship);
    builder.fieldTransformation(fieldTransformation);
    
    // Generate cascade configuration
    if (relationship.getCascadeInfo() != null) {
        String cascadeConfig = generateCascadeConfiguration(relationship.getCascadeInfo());
        builder.cascadeConfiguration(cascadeConfig);
    }
    
    // Generate fetch configuration
    if (relationship.getFetchStrategy() != null) {
        String fetchConfig = generateFetchConfiguration(relationship.getFetchStrategy());
        builder.fetchConfiguration(fetchConfig);
    }
    
    // Generate join configuration
    if (relationship.getJoinConfiguration() != null) {
        String joinConfig = generateJoinConfiguration(relationship.getJoinConfiguration());
        builder.joinConfiguration(joinConfig);
    }
    
    return builder.build();
}

private String determineJPAAnnotation(ComplexCMRInfo relationship) {
    RelationshipCardinality cardinality = relationship.getCardinality();
    
    switch (cardinality) {
        case ONE_TO_ONE:
            return "@OneToOne";
        case ONE_TO_MANY:
            return "@OneToMany";
        case MANY_TO_ONE:
            return "@ManyToOne";
        case MANY_TO_MANY:
            return "@ManyToMany";
        default:
            return "@OneToMany"; // Default fallback
    }
}

private Map<String, String> generateJPAAttributes(ComplexCMRInfo relationship) {
    Map<String, String> attributes = new HashMap<>();
    
    // Add mappedBy for bidirectional relationships
    if (relationship.getBidirectionalMapping() != null) {
        BidirectionalMappingInfo biMapping = relationship.getBidirectionalMapping();
        if (!biMapping.isOwningEntity()) {
            attributes.put("mappedBy", "\"" + biMapping.getInverseSideRelationship() + "\"");
        }
    }
    
    // Add cascade types
    if (relationship.getCascadeInfo() != null) {
        String cascadeTypes = relationship.getCascadeInfo().getCascadeTypes().stream()
            .map(type -> "CascadeType." + type.toString())
            .collect(Collectors.joining(", "));
        if (!cascadeTypes.isEmpty()) {
            attributes.put("cascade", "{" + cascadeTypes + "}");
        }
        
        if (relationship.getCascadeInfo().isOrphanRemoval()) {
            attributes.put("orphanRemoval", "true");
        }
    }
    
    // Add fetch type
    if (relationship.getFetchStrategy() != null) {
        FetchType fetchType = relationship.getFetchStrategy().getFetchType();
        if (fetchType != FetchType.LAZY) { // Only specify if not default
            attributes.put("fetch", "FetchType." + fetchType.toString());
        }
    }
    
    return attributes;
}
```

### Data Model Classes

#### ComplexCMRMetadata

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/ComplexCMRMetadata.java
public class ComplexCMRMetadata {
    private final String entityName;
    private final String className;
    private final ProjectFile projectFile;
    private final List<ComplexCMRInfo> complexRelationships;
    private final List<BidirectionalRelationshipInfo> bidirectionalRelationships;
    private final List<CascadeOperationInfo> cascadeOperations;
    private final List<JPAConversionRecommendation> jpaRecommendations;
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters and builder implementation
}
```

#### ComplexCMRInfo

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/ComplexCMRInfo.java
public class ComplexCMRInfo {
    private final String relationshipName;
    private final String targetEntityType;
    private final RelationshipCardinality cardinality;
    private final boolean isCollection;
    private final String getterMethod;
    private final String setterMethod;
    private final CascadeTypeInfo cascadeInfo;
    private final FetchStrategyInfo fetchStrategy;
    private final JoinConfigurationInfo joinConfiguration;
    private final BidirectionalMappingInfo bidirectionalMapping;
    
    // Builder pattern implementation
}
```

#### BidirectionalRelationshipInfo

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/BidirectionalRelationshipInfo.java
public class BidirectionalRelationshipInfo {
    private final String owningEntityName;
    private final String owningSideRelationship;
    private final String targetEntityName;
    private final String inverseSideRelationship;
    private final BidirectionalRelationshipType relationshipType;
    private final boolean isOwningEntity;
    
    // Builder pattern implementation
}

public enum BidirectionalRelationshipType {
    ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY
}
```

#### CascadeOperationInfo

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/CascadeOperationInfo.java
public class CascadeOperationInfo {
    private final String relationshipName;
    private final CascadeType cascadeType;
    private final CascadeOperationType operationType;
    private final String methodName;
    private final boolean isOrphanRemoval;
    private final List<CascadeType> cascadeTypes;
    
    // Builder pattern implementation
}

public enum CascadeType {
    PERSIST, MERGE, REMOVE, REFRESH, DETACH, ALL
}

public enum CascadeOperationType {
    EXPLICIT, IMPLICIT, CONTAINER_MANAGED
}
```

## Implementation Tasks

### Step 1: Create EJB-Specific Tags in ejb2spring Package (1 hour)
1. Create `EjbMigrationTags` class in `com.analyzer.rules.ejb2spring` package
2. Move EJB-related tags from `InspectorTags` to `EjbMigrationTags`
3. Add new complex relationship tags
4. Update existing EJB inspectors to use new tag class

### Step 2: Create Base Inspector Structure (1-2 hours)
1. Create `ComplexCMPRelationshipInspector` class extending `AbstractASMInspector`
2. Implement basic complex CMR detection logic
3. Set up graph integration interface

### Step 3: Implement Complex Relationship Analysis (2-3 hours)
1. Create bidirectional relationship detection logic
2. Implement complex cardinality determination
3. Add collection interface analysis

### Step 4: Implement Cascade Operation Detection (1-2 hours)
1. Create cascade delete pattern detection in ejbRemove methods
2. Implement cascade create pattern analysis
3. Add orphan removal detection

### Step 5: Create Data Model Classes (1-2 hours)
1. Implement `ComplexCMRMetadata` and related classes
2. Create builder patterns for complex objects
3. Add validation and utility methods

### Step 6: Implement JPA Conversion Recommendations (1-2 hours)
1. Create JPA annotation mapping logic
2. Implement conversion recommendation generation
3. Add attribute and configuration mapping

### Step 7: Integration and Testing (1 hour)
1. Create comprehensive unit tests
2. Test with complex CMP entity relationship scenarios
3. Validate graph node and edge creation

## File Structure

```
src/main/java/com/analyzer/rules/ejb2spring/
├── ComplexCMPRelationshipInspector.java       # Main inspector implementation
├── EjbMigrationTags.java                      # EJB-specific tags (NEW)
├── model/
│   ├── ComplexCMRMetadata.java                # Complex CMR metadata
│   ├── ComplexCMRInfo.java                    # Complex CMR information
│   ├── BidirectionalRelationshipInfo.java     # Bidirectional relationship info
│   ├── CascadeOperationInfo.java              # Cascade operation information
│   ├── JPAConversionRecommendation.java       # JPA conversion recommendations
│   ├── FetchStrategyInfo.java                 # Fetch strategy information
│   └── JoinConfigurationInfo.java             # Join configuration information
└── util/
    ├── CMRPatternAnalyzer.java                # CMR pattern analysis utilities
    └── JPAConversionHelper.java               # JPA conversion helper utilities

src/test/java/com/analyzer/rules/ejb2spring/
├── ComplexCMPRelationshipInspectorTest.java   # Unit tests
├── ComplexCMRMetadataTest.java                # Metadata tests
└── fixtures/
    ├── ComplexCMPEntity.java                  # Test complex CMP entity bean
    ├── BidirectionalEntity1.java              # Bidirectional relationship test entity
    ├── BidirectionalEntity2.java              # Bidirectional relationship test entity
    └── complex-ejb-jar.xml                    # Sample complex EJB descriptor
```

## Testing Requirements

### Unit Tests
- Test complex CMP relationship detection (bidirectional, cascade, etc.)
- Test collection interface analysis and generic type inference
- Test cascade operation detection in business methods
- Test JPA conversion recommendation generation
- Test graph integration for complex relationships

### Integration Tests
- Test with real complex EJB entity bean applications
- Test bidirectional relationship detection across multiple entities
- Test complex foreign key and join table scenarios
- Test performance with large relationship hierarchies

## Success Criteria

- [ ] Accurate detection of complex CMR patterns (≥95% accuracy)
- [ ] Complete bidirectional relationship analysis and mapping
- [ ] Accurate cascade operation detection and JPA conversion
- [ ] Proper collection interface mapping with generic types
- [ ] Comprehensive graph node and edge creation for complex relationships
- [ ] JPA conversion recommendations with proper annotations and attributes
- [ ] 100% unit test coverage
- [ ] Performance: <750ms for complex entity relationship analysis

## Migration Recommendations Output

The inspector generates detailed JPA conversion recommendations:

```java
// Example output for a complex bidirectional one-to-many relationship
@OneToMany(mappedBy = "order", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, 
           orphanRemoval = true, fetch = FetchType.LAZY)
private Set<OrderLineItem> lineItems = new HashSet<>();

// Example output for a many-to-many relationship with join table
@ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
@JoinTable(name = "student_course",
    joinColumns = @JoinColumn(name = "student_id"),
    inverseJoinColumns = @JoinColumn(name = "course_id"))
private Set<Course> courses = new HashSet<>();
```

## Implementation Priority

This inspector is critical for Phase 2 success as it:
1. **Handles Complex Enterprise Patterns**: Most real-world EJB applications use complex relationships
2. **Enables JPA Migration**: Provides detailed conversion guidance for sophisticated relationship patterns
3. **Supports Graph Analysis**: Creates detailed relationship graphs for architectural analysis
4. **Foundation for Advanced Inspectors**: Enables subsequent Phase 2 inspectors for inheritance and composite keys

Focus on comprehensive relationship pattern detection and accurate JPA conversion recommendations to support enterprise-grade EJB to Spring migration projects.
