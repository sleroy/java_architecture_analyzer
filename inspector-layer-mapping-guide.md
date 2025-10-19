# Inspector Layer Migration Mapping Guide

## Migration Strategy: From @InspectorDependencies to InspectorLayers

Based on analysis of 58 inspector files, here's the systematic mapping to InspectorLayers constants:

## Layer 1: BASIC - File detection and basic metadata

**InspectorLayers.BASIC** - Fundamental detectors with no dependencies:

1. `FilenameInspector` - ✅ Already correct (no dependencies)
2. `FileExtensionInspector` - ✅ Already correct (no dependencies) 
3. `SourceFileTagInspector` - ✅ Already correct (no dependencies)
4. `IsBinaryJavaClassInspector` - ✅ Already correct (no dependencies)
5. `ClocInspector` - ✅ Already correct (no dependencies)

## Layer 2: JAVA_SOURCE - Java source code analysis

**InspectorLayers.JAVA_SOURCE** - Requires BASIC layer:

1. `JavaSourceFileInspector` - needs SourceFileTagInspector
2. `AbstractSourceFileInspector` - needs SourceFileTagInspector
3. `AbstractJavaParserInspector` - needs JavaSourceFileInspector
4. `AbstractBedrockInspectorAbstract` - needs SourceFileTagInspector
5. `LegacyFrameworkDetector` - needs SourceFileTagInspector
6. `ApplicationServerConfigDetector` - needs SourceFileTagInspector + FileExtensionInspector
7. `EjbDeploymentDescriptorDetector` - needs XML extension
8. `EjbDeploymenDescriptorAnalyzerInspector` - needs XML extension

## Layer 3: JAVA_BINARY - Java bytecode analysis

**InspectorLayers.JAVA_BINARY** - Requires BASIC layer:

1. `AbstractASMInspector` - needs IsBinaryJavaClassInspector
2. `AbstractBinaryClassInspector` - needs IsBinaryJavaClassInspector
3. `ClassFileInspector` - needs IsBinaryJavaClassInspector
4. `BinaryClassFQNInspector` - needs IsBinaryJavaClassInspector
5. `TypeInspectorASMInspector` - needs IsBinaryJavaClassInspector
6. `JavaVersionInspector` - needs IsBinaryJavaClassInspector
7. `MethodCountInspectorAbstractAbstractASMInspector` - requires JAVA_BINARY

## Layer 4: JAVA_CLASSLOADER - Runtime class loading

**InspectorLayers.JAVA_CLASSLOADER** - Requires JAVA_SOURCE AND JAVA_BINARY:

1. `AbstractClassLoaderBasedInspector` - requires multiple Java tags
2. `AbstractProjectFileAnnotationCountInspector` - requires TAG_JAVA_DETECTED
3. `AnnotationCountInspector` - requires TAG_JAVA_DETECTED
4. `InheritanceDepthInspector` - needs IsBinaryJavaClassInspector
5. `TypeUsageInspector` - needs IsBinaryJavaClassInspector
6. `InterfaceNumberInspector` - needs IsBinaryJavaClassInspector
7. `CyclomaticComplexityInspector` - requires RESOURCE_HAS_JAVA_SOURCE

## Layer 5: EJB_DETECTION - EJB component identification

**InspectorLayers.EJB_DETECTION** - Requires JAVA_SOURCE OR JAVA_BINARY:

1. `EntityBeanInspector` - ✅ Already migrated (uses explicit tags)
2. `SessionBeanInspector` - needs JavaSourceFileInspector
3. `MessageDrivenBeanInspector` - needs JavaSourceFileInspector
4. `EjbHomeInterfaceInspector` - needs JavaSourceFileInspector
5. `EjbRemoteInterfaceInspector` - needs JavaSourceFileInspector
6. `EjbJavaFileDetector` - needs JavaSourceFileInspector
7. `IdentifyServletSourceInspector` - needs JavaSourceFileInspector

## Layer 6: EJB_ANALYSIS - Advanced EJB pattern analysis

**InspectorLayers.EJB_ANALYSIS** - Requires EJB_DETECTION:

1. `BusinessDelegatePatternInspector` - needs JavaSourceFileInspector
2. `CmpFieldMappingInspector` - needs IsBinaryJavaClassInspector + EntityBeanInspector
3. `ComplexCmpRelationshipInspector` - needs JavaSourceFileInspector + EntityBeanInspector
4. `CustomDataTransferPatternInspector` - needs JavaSourceFileInspector
5. `StatefulSessionStateInspector` - needs SessionBeanInspector
6. `EjbCreateMethodUsageInspector` - needs IsBinaryJavaClassInspector
7. `JndiLookupInspector` - needs JavaSourceFileInspector
8. `ProgrammaticTransactionUsageInspector` - (need to check)

## Layer 7: FRAMEWORK_INTEGRATION - Framework integration

**InspectorLayers.FRAMEWORK_INTEGRATION** - Requires EJB_ANALYSIS:

1. `JBossEjbConfigurationInspector` - needs EjbDeploymentDescriptorDetector
2. `DatabaseResourceManagementInspector` - needs EjbDeploymentDescriptorDetector
3. `DeclarativeTransactionInspector` - requires EJB_DEPLOYMENT_DESCRIPTOR
4. `JdbcDataAccessPatternInspector` - needs IsBinaryJavaClassInspector

## Graph-related (Utilities - minimal changes needed):

1. `JavaClassGraphInspector` - no dependencies
2. `BinaryJavaClassNodeInspector` - needs IsBinaryJavaClassInspector  
3. `SourceJavaClassNodeInspector` - needs JavaSourceFileInspector + SourceFileTagInspector
4. `CodeQualityInspectorAbstractAbstract` - produces only

## Migration Patterns

### Pattern A: Basic Layer Dependencies
```java
// BEFORE
@InspectorDependencies(needs = { SourceFileTagInspector.class })

// AFTER  
@InspectorDependencies(requires = { InspectorLayers.BASIC })
```

### Pattern B: Java Source Dependencies
```java
// BEFORE
@InspectorDependencies(needs = { JavaSourceFileInspector.class })

// AFTER
@InspectorDependencies(requires = { InspectorLayers.JAVA_SOURCE })
```

### Pattern C: Java Binary Dependencies
```java
// BEFORE
@InspectorDependencies(needs = { IsBinaryJavaClassInspector.class })

// AFTER
@InspectorDependencies(requires = { InspectorLayers.JAVA_BINARY })
```

### Pattern D: ClassLoader Dependencies
```java
// BEFORE
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_DETECTED })

// AFTER
@InspectorDependencies(requires = { InspectorLayers.JAVA_CLASSLOADER })
```

### Pattern E: EJB Chain Dependencies
```java
// BEFORE
@InspectorDependencies(needs = { EntityBeanInspector.class })

// AFTER
@InspectorDependencies(requires = { InspectorLayers.EJB_DETECTION })
```

## Total Migration Count: 58 inspectors
- Layer 1 (BASIC): 5 inspectors - ✅ Already correct
- Layer 2 (JAVA_SOURCE): 8 inspectors
- Layer 3 (JAVA_BINARY): 7 inspectors  
- Layer 4 (JAVA_CLASSLOADER): 7 inspectors
- Layer 5 (EJB_DETECTION): 7 inspectors (1 done)
- Layer 6 (EJB_ANALYSIS): 8 inspectors
- Layer 7 (FRAMEWORK_INTEGRATION): 4 inspectors
- Utilities/Graph: 5 inspectors
