# Task 4: I-0206 CMP Field Mapping Inspector

**ID:** Task 4  
**Priority:** P0 (Critical - Highest Priority Phase 1 Inspector)  
**Estimated Effort:** 8-12 hours  
**Prerequisites:** Tasks 1, 2, 3 complete  
**Deliverables:** Complete CMP field mapping detection  

## Overview

Implement inspector I-0206 to detect and analyze Container Managed Persistence (CMP) field mappings in EJB 2.x entity beans. This inspector is critical for CMP to JPA migration analysis, identifying CMP fields, their database mappings, and container managed relationships.

## Technical Requirements

### Detection Capabilities

1. **CMP Field Identification**
   - Detect abstract getter/setter methods in CMP entity beans
   - Extract field names from method signatures
   - Identify field types and relationships
   - Map CMP fields to database columns

2. **Database Mapping Analysis**
   - Parse vendor-specific CMP mapping files
   - Extract table and column mappings
   - Identify primary key fields and composite keys
   - Detect column constraints and properties

3. **Container Managed Relationship Detection**
   - Identify CMR relationship fields
   - Determine relationship cardinality (1:1, 1:M, M:M)
   - Extract relationship names and mappings
   - Detect bidirectional relationship pairs

4. **Migration Complexity Assessment**
   - Classify CMP patterns by migration complexity
   - Identify JPA conversion requirements
   - Detect anti-patterns and migration blockers

### Implementation Specifications

#### Inspector Class Structure

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/CMPFieldMappingInspector.java
public class CMPFieldMappingInspector extends AbstractASMInspector 
        implements GraphAwareInspector {
    
    private static final String INSPECTOR_ID = "I-0206";
    private static final String INSPECTOR_NAME = "CMP Field Mapping Inspector";
    
    private final Set<String> CMP_METHOD_PREFIXES = Set.of("get", "set");
    private final Set<String> CMR_COLLECTION_TYPES = Set.of(
        "java.util.Collection", "java.util.Set", "java.util.List"
    );
    
    private GraphRepository graphRepository;
    private Map<String, CMPEntityMetadata> cmpEntityCache;
    private Map<String, CMPMappingDescriptor> mappingDescriptors;
    
    public CMPFieldMappingInspector() {
        super(INSPECTOR_ID, INSPECTOR_NAME);
        this.cmpEntityCache = new ConcurrentHashMap<>();
        this.mappingDescriptors = new ConcurrentHashMap<>();
    }
    
    @Override
    public void setGraphRepository(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }
    
    @Override
    protected InspectorResult processClass(ProjectFile file, ClassNode classNode) {
        // Only process CMP entity beans
        if (!isCMPEntityBean(classNode)) {
            return InspectorResult.empty();
        }
        
        CMPEntityMetadata metadata = analyzeCMPEntity(file, classNode);
        CMPMappingDescriptor mapping = loadMappingDescriptor(file, classNode);
        
        return createInspectorResult(file, classNode, metadata, mapping);
    }
}
```

#### CMP Entity Analysis Logic

```java
private CMPEntityMetadata analyzeCMPEntity(ProjectFile file, ClassNode classNode) {
    CMPEntityMetadata.Builder builder = CMPEntityMetadata.builder()
        .entityName(extractEntityName(classNode))
        .className(classNode.name)
        .projectFile(file);
    
    // Analyze CMP methods
    for (MethodNode method : classNode.methods) {
        if (isCMPAccessorMethod(method)) {
            CMPFieldInfo field = analyzeCMPField(method);
            builder.addCMPField(field);
        } else if (isCMRAccessorMethod(method)) {
            CMRRelationshipInfo relationship = analyzeCMRRelationship(method);
            builder.addCMRRelationship(relationship);
        } else if (isFinderMethod(method)) {
            FinderMethodInfo finder = analyzeFinderMethod(method);
            builder.addFinderMethod(finder);
        } else if (isSelectMethod(method)) {
            SelectMethodInfo select = analyzeSelectMethod(method);
            builder.addSelectMethod(select);
        }
    }
    
    return builder.build();
}

private boolean isCMPEntityBean(ClassNode classNode) {
    // Check if class extends EntityBean interface
    if (!extendsEntityBean(classNode)) {
        return false;
    }
    
    // Check for abstract CMP methods
    return hasAbstractCMPMethods(classNode);
}

private boolean isCMPAccessorMethod(MethodNode method) {
    // Must be abstract
    if ((method.access & Opcodes.ACC_ABSTRACT) == 0) {
        return false;
    }
    
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

private CMPFieldInfo analyzeCMPField(MethodNode method) {
    String fieldName = extractFieldName(method.name);
    String fieldType = extractFieldType(method.desc);
    boolean isPrimaryKey = isPrimaryKeyField(fieldName);
    
    return CMPFieldInfo.builder()
        .fieldName(fieldName)
        .fieldType(fieldType)
        .methodName(method.name)
        .methodDescriptor(method.desc)
        .isPrimaryKey(isPrimaryKey)
        .isRequired(determineRequiredStatus(fieldName))
        .build();
}
```

#### CMR Relationship Analysis

```java
private boolean isCMRAccessorMethod(MethodNode method) {
    if (!isCMPAccessorMethod(method)) {
        return false;
    }
    
    String returnType = extractReturnType(method.desc);
    
    // Check for collection types (1:M, M:M relationships)
    if (CMR_COLLECTION_TYPES.contains(returnType)) {
        return true;
    }
    
    // Check for entity bean types (1:1, M:1 relationships)
    return isEntityBeanType(returnType);
}

private CMRRelationshipInfo analyzeCMRRelationship(MethodNode method) {
    String relationshipName = extractFieldName(method.name);
    String targetType = extractCMRTargetType(method.desc);
    RelationshipCardinality cardinality = determineCardinality(method.desc);
    
    return CMRRelationshipInfo.builder()
        .relationshipName(relationshipName)
        .targetEntityType(targetType)
        .cardinality(cardinality)
        .methodName(method.name)
        .isCollection(isCollectionType(method.desc))
        .cascadeDelete(determineCascadeDelete(relationshipName))
        .build();
}

private RelationshipCardinality determineCardinality(String methodDescriptor) {
    String returnType = extractReturnType(methodDescriptor);
    
    if (CMR_COLLECTION_TYPES.contains(returnType)) {
        // Collection types indicate *-to-many relationships
        return RelationshipCardinality.ONE_TO_MANY; // Will be refined with mapping data
    } else {
        // Single entity type indicates *-to-one relationships
        return RelationshipCardinality.MANY_TO_ONE; // Will be refined with mapping data
    }
}
```

#### Database Mapping Integration

```java
private CMPMappingDescriptor loadMappingDescriptor(ProjectFile file, ClassNode classNode) {
    String entityName = extractEntityName(classNode);
    
    // Try to find mapping descriptor from various sources
    CMPMappingDescriptor mapping = findEjbJarMapping(file, entityName);
    if (mapping == null) {
        mapping = findVendorSpecificMapping(file, entityName);
    }
    if (mapping == null) {
        mapping = createDefaultMapping(entityName);
    }
    
    return mapping;
}

private CMPMappingDescriptor findEjbJarMapping(ProjectFile file, String entityName) {
    // Look for ejb-jar.xml in the same module
    Optional<ProjectFile> ejbJarXml = findEjbJarXml(file);
    if (ejbJarXml.isPresent()) {
        return parseEjbJarCMPMapping(ejbJarXml.get(), entityName);
    }
    return null;
}

private CMPMappingDescriptor findVendorSpecificMapping(ProjectFile file, String entityName) {
    // Look for vendor-specific mapping files
    // WebLogic: weblogic-cmp-rdbms-jar.xml
    // JBoss: jbosscmp-jdbc.xml  
    // WebSphere: Map.mapxmi files
    
    Optional<ProjectFile> mappingFile = findVendorMappingFile(file);
    if (mappingFile.isPresent()) {
        return parseVendorMapping(mappingFile.get(), entityName);
    }
    return null;
}
```

#### Graph Integration

```java
private void createGraphNodes(ProjectFile file, CMPEntityMetadata metadata, 
        CMPMappingDescriptor mapping) {
    
    // Create CMP Entity Bean node
    CMPEntityBeanNode entityNode = new CMPEntityBeanNode(
        metadata.getClassName(), 
        metadata.getEntityName()
    );
    
    // Add CMP fields to node
    for (CMPFieldInfo field : metadata.getCmpFields()) {
        String columnMapping = mapping != null ? 
            mapping.getColumnMapping(field.getFieldName()) : null;
        entityNode.addCMPField(field.getFieldName(), columnMapping);
    }
    
    // Add CMR relationships to node
    for (CMRRelationshipInfo cmr : metadata.getCmrRelationships()) {
        CMRRelationship relationship = new CMRRelationship(
            cmr.getRelationshipName(),
            cmr.getTargetEntityType(),
            cmr.getCardinality(),
            cmr.isCascadeDelete()
        );
        entityNode.addCMRRelationship(cmr.getRelationshipName(), relationship);
    }
    
    // Add mapping information
    if (mapping != null) {
        entityNode.setTableName(mapping.getTableName());
        entityNode.setAbstractSchemaName(mapping.getAbstractSchemaName());
    }
    
    graphRepository.addNode(entityNode);
    
    // Create edges for CMR relationships
    createCMREdges(entityNode, metadata.getCmrRelationships());
}

private void createCMREdges(CMPEntityBeanNode sourceNode, 
        List<CMRRelationshipInfo> relationships) {
    
    for (CMRRelationshipInfo cmr : relationships) {
        String targetNodeId = cmr.getTargetEntityType();
        
        CMRRelationshipEdge edge = new CMRRelationshipEdge(
            generateEdgeId(sourceNode.getNodeId(), targetNodeId, cmr.getRelationshipName()),
            sourceNode.getNodeId(),
            targetNodeId,
            cmr.getRelationshipName(),
            cmr.getCardinality().toString()
        );
        
        edge.setCascadeDelete(cmr.isCascadeDelete());
        edge.setMappedBy(cmr.getMappedBy());
        
        graphRepository.addEdge(edge);
    }
}
```

#### Inspector Result Creation

```java
private InspectorResult createInspectorResult(ProjectFile file, ClassNode classNode,
        CMPEntityMetadata metadata, CMPMappingDescriptor mapping) {
    
    InspectorResult.Builder builder = InspectorResult.builder()
        .file(file)
        .addTag(InspectorTags.EJB_ENTITY_BEAN)
        .addTag(InspectorTags.EJB_CMP_ENTITY)
        .addTag(InspectorTags.EJB_MIGRATION_HIGH_PRIORITY);
    
    // Add complexity tags based on analysis
    if (metadata.getCmrRelationships().size() > 3) {
        builder.addTag(InspectorTags.EJB_MIGRATION_COMPLEX);
    } else {
        builder.addTag(InspectorTags.EJB_MIGRATION_SIMPLE);
    }
    
    // Add field-specific tags
    for (CMPFieldInfo field : metadata.getCmpFields()) {
        builder.addTag(InspectorTags.EJB_CMP_FIELD);
        if (field.isPrimaryKey()) {
            builder.addTag(InspectorTags.EJB_PRIMARY_KEY_CLASS);
        }
    }
    
    // Add relationship-specific tags
    for (CMRRelationshipInfo cmr : metadata.getCmrRelationships()) {
        builder.addTag(InspectorTags.EJB_CMR_RELATIONSHIP);
    }
    
    // Add migration guidance
    builder.addProperty("entityName", metadata.getEntityName())
           .addProperty("cmpFieldCount", String.valueOf(metadata.getCmpFields().size()))
           .addProperty("cmrRelationshipCount", String.valueOf(metadata.getCmrRelationships().size()))
           .addProperty("tableName", mapping != null ? mapping.getTableName() : "unknown")
           .addProperty("migrationComplexity", assessMigrationComplexity(metadata));
    
    // Create graph nodes
    createGraphNodes(file, metadata, mapping);
    
    return builder.build();
}

private String assessMigrationComplexity(CMPEntityMetadata metadata) {
    int complexityScore = 0;
    
    // Base complexity for CMP entity
    complexityScore += 3;
    
    // Add complexity for each CMP field
    complexityScore += metadata.getCmpFields().size();
    
    // Add higher complexity for CMR relationships
    complexityScore += metadata.getCmrRelationships().size() * 2;
    
    // Add complexity for composite primary keys
    long pkFieldCount = metadata.getCmpFields().stream()
        .mapToLong(field -> field.isPrimaryKey() ? 1 : 0)
        .sum();
    if (pkFieldCount > 1) {
        complexityScore += 3;
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
```

### Data Model Classes

#### CMPEntityMetadata

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/CMPEntityMetadata.java
public class CMPEntityMetadata {
    private final String entityName;
    private final String className;
    private final ProjectFile projectFile;
    private final List<CMPFieldInfo> cmpFields;
    private final List<CMRRelationshipInfo> cmrRelationships;
    private final List<FinderMethodInfo> finderMethods;
    private final List<SelectMethodInfo> selectMethods;
    
    // Builder pattern implementation
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        // Builder implementation
    }
}
```

#### CMPFieldInfo

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/CMPFieldInfo.java
public class CMPFieldInfo {
    private final String fieldName;
    private final String fieldType;
    private final String methodName;
    private final String methodDescriptor;
    private final boolean isPrimaryKey;
    private final boolean isRequired;
    private final String columnMapping;
    
    // Builder pattern implementation
}
```

#### CMRRelationshipInfo

```java
// File: src/main/java/com/analyzer/rules/ejb2spring/model/CMRRelationshipInfo.java
public class CMRRelationshipInfo {
    private final String relationshipName;
    private final String targetEntityType;
    private final RelationshipCardinality cardinality;
    private final String methodName;
    private final boolean isCollection;
    private final boolean cascadeDelete;
    private final String mappedBy;
    
    // Builder pattern implementation
}

public enum RelationshipCardinality {
    ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY
}
```

## Implementation Tasks

### Step 1: Create Base Inspector Structure (2-3 hours)
1. Create `CMPFieldMappingInspector` class extending `AbstractASMInspector`
2. Implement basic CMP entity bean detection logic
3. Set up graph integration interface

### Step 2: Implement CMP Field Analysis (2-3 hours)
1. Create CMP field detection logic
2. Implement field type analysis
3. Add primary key field identification

### Step 3: Implement CMR Relationship Analysis (2-3 hours)
1. Create CMR relationship detection logic
2. Implement cardinality determination
3. Add relationship mapping analysis

### Step 4: Create Data Model Classes (1-2 hours)
1. Implement `CMPEntityMetadata` and related classes
2. Create builder patterns for complex objects
3. Add validation and utility methods

### Step 5: Implement Database Mapping Integration (1-2 hours)
1. Create mapping descriptor parsing logic
2. Implement vendor-specific mapping support
3. Add default mapping generation

### Step 6: Integration and Testing (1-2 hours)
1. Create comprehensive unit tests
2. Test with sample CMP entity beans
3. Validate graph node creation

## File Structure

```
src/main/java/com/analyzer/rules/ejb2spring/
├── CMPFieldMappingInspector.java              # Main inspector implementation
├── model/
│   ├── CMPEntityMetadata.java                 # CMP entity metadata
│   ├── CMPFieldInfo.java                      # CMP field information
│   ├── CMRRelationshipInfo.java               # CMR relationship information
│   ├── CMPMappingDescriptor.java              # Database mapping descriptor
│   ├── FinderMethodInfo.java                  # Finder method information
│   └── SelectMethodInfo.java                  # Select method information
└── util/
    ├── CMPMappingParser.java                  # Mapping file parser utility
    └── CMPNamingConventions.java              # CMP naming convention utilities

src/test/java/com/analyzer/rules/ejb2spring/
├── CMPFieldMappingInspectorTest.java          # Unit tests
├── CMPEntityMetadataTest.java                 # Metadata tests
└── fixtures/
    ├── SampleCMPEntity.java                   # Test CMP entity bean
    ├── ejb-jar-cmp.xml                        # Sample EJB descriptor
    └── weblogic-cmp-rdbms-jar.xml            # Sample vendor mapping
```

## Testing Requirements

### Unit Tests
- Test CMP entity bean detection
- Test CMP field analysis accuracy
- Test CMR relationship detection
- Test database mapping integration
- Test graph node creation

### Integration Tests
- Test with real EJB applications
- Test vendor-specific mapping parsing
- Test migration complexity assessment
- Test graph integration

## Success Criteria

- [ ] Accurate detection of CMP entity beans (≥95% accuracy)
- [ ] Complete CMP field mapping extraction
- [ ] Accurate CMR relationship detection and cardinality determination
- [ ] Proper database mapping integration
- [ ] Comprehensive graph node creation
- [ ] Migration complexity assessment functionality
- [ ] 100% unit test coverage
- [ ] Performance: <500ms for typical CMP entity analysis

## Implementation Prompt

Use this specification to implement the CMP Field Mapping Inspector. Focus on:

1. **Complete CMP Analysis**: Detect all CMP fields, relationships, and database mappings
2. **Accurate Relationship Modeling**: Properly identify CMR cardinality and configuration
3. **Migration-Ready Output**: Provide detailed information needed for JPA conversion
4. **Performance Optimization**: Efficient analysis for large CMP entity hierarchies
5. **Vendor Support**: Handle major application server CMP mapping formats

This inspector is the foundation for CMP to JPA migration analysis and must provide comprehensive, accurate detection of all CMP patterns and configurations.
