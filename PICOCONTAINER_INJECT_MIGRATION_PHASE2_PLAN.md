# PicoContainer @Inject Inspector Migration - Phase 2 Plan

## 🎯 **PHASE 1 COMPLETE - ESTABLISHED FOUNDATION** ✅
- **Infrastructure**: InspectorContainerConfig, InspectorRegistry, AnalysisEngine ✅
- **Universal Pattern**: All inspectors get GraphRepository injection ✅  
- **Representative Examples**: 8/37 inspectors migrated across all base class types ✅
- **Test Pattern**: InheritanceDepthInspectorTest updated to use @Inject constructors ✅
- **Container Validation**: PicoContainer successfully instantiates @Inject inspectors ✅

## 📊 **CURRENT MIGRATION STATUS**
- ✅ **Complete**: 8 inspectors (AnnotationCountInspector, TypeInspectorASMInspector, MessageDrivenBeanInspector, JavaVersionInspector, MethodCountInspector, etc.)
- 🔧 **Remaining**: 29 inspectors need @Inject migration  
- 🎯 **Goal**: Complete universal @Inject pattern across all inspectors

## 🔄 **UNIVERSAL @INJECT MIGRATION PATTERN**
```java
// BEFORE (Old Pattern)  
public class ExampleInspector extends AbstractBaseInspector {
    public ExampleInspector(ResourceResolver resolver, JARClassLoaderService service) {
        super(resolver, service);
    }
}

// AFTER (@Inject Pattern - Applied Universally)
import javax.inject.Inject;
import com.analyzer.core.graph.GraphRepository;

public class ExampleInspector extends AbstractBaseInspector {
    private final GraphRepository graphRepository;
    
    @Inject
    public ExampleInspector(ResourceResolver resolver, JARClassLoaderService service, 
                           GraphRepository graphRepository) {
        super(resolver, service);
        this.graphRepository = graphRepository;
    }
}
```

## 📋 **PHASE 2 SYSTEMATIC MIGRATION PLAN**

### **Batch 1: ASM-Based Inspectors (Priority 1)**
**Base Class**: `AbstractASMInspector`
**Pattern**: Add @Inject + GraphRepository to existing ResourceResolver parameter

1. **CyclomaticComplexityInspector** ❌ (JavaParser-based, actually)
2. **BinaryClassFQNInspector** ❌
3. **BinaryJavaClassNodeInspector** ❌  
4. **CmpFieldMappingJavaBinaryInspector** ❌
5. **EjbCreateMethodUsageInspector** ❌
6. **JdbcDataAccessPatternInspector** ❌
7. **ProgrammaticTransactionUsageInspector** ❌

### **Batch 2: ClassLoader-Based Inspectors (Priority 2)**
**Base Class**: `AbstractClassLoaderBasedInspector`  
**Pattern**: Add @Inject + GraphRepository to existing (ResourceResolver, JARClassLoaderService) parameters

1. **InterfaceNumberInspector** ❌
2. **TypeUsageInspector** ❌  
3. **EjbClassLoaderInspector** ❌

### **Batch 3: JavaParser-Based Inspectors (Priority 3)**
**Base Class**: `AbstractJavaParserInspector`
**Pattern**: Add @Inject + GraphRepository to existing ResourceResolver parameter

1. **CyclomaticComplexityInspector** ❌ (Actually JavaParser-based)
2. **SourceJavaClassNodeInspector** ❌
3. **SessionBeanJavaSourceInspector** ❌  
4. **EntityBeanJavaSourceInspector** ❌
5. **BusinessDelegatePatternJavaSourceInspector** ❌
6. **ComplexCmpRelationshipJavaSourceInspector** ❌
7. **CustomDataTransferPatternJavaSourceInspector** ❌
8. **EjbRemoteInterfaceInspector** ❌
9. **EjbHomeInterfaceInspector** ❌
10. **IdentifyServletSourceInspector** ❌

### **Batch 4: Source File-Based Inspectors (Priority 4)**
**Base Class**: `AbstractSourceFileInspector` / `AbstractTextFileInspector`
**Pattern**: Add @Inject + GraphRepository to existing ResourceResolver parameter

1. **ClocInspector** ❌
2. **EjbDeploymenDescriptorAnalyzerInspector** ❌
3. **JBossEjbConfigurationInspector** ❌  
4. **DatabaseResourceManagementInspector** ❌
5. **JndiLookupInspector** ❌

### **Batch 5: Project File-Based Inspectors (Priority 5)**
**Base Class**: `AbstractProjectFileInspector`
**Pattern**: Add @Inject + GraphRepository (no existing constructor parameters)

1. **JavaImportGraphInspector** ❌

### **Batch 6: Specialized Inspectors (Priority 6)**
**Base Class**: Various specialized base classes
**Pattern**: Custom migration per base class

1. **AbstractJavaAnnotationCountInspector** ❌
2. **CodeQualityInspectorAbstractAbstract** ❌

## 🎯 **MIGRATION METHODOLOGY: Baby Steps™**

### **Per Inspector Migration Steps:**
1. ✅ **Read Inspector File**: Understand current constructor pattern
2. ✅ **Add Imports**: `javax.inject.Inject` + `GraphRepository`  
3. ✅ **Add @Inject Annotation**: To constructor
4. ✅ **Add GraphRepository Parameter**: As final constructor parameter
5. ✅ **Add GraphRepository Field**: `private final GraphRepository graphRepository;`
6. ✅ **Update Field Assignment**: Store GraphRepository in constructor
7. ✅ **Verify Compilation**: Ensure changes compile successfully
8. ✅ **Update Corresponding Test**: If test exists, update to use new constructor
9. ✅ **Document Pattern**: Confirm pattern consistency

### **Per Batch Workflow:**
1. ✅ **Batch Assessment**: Analyze all inspectors in batch
2. ✅ **Representative Migration**: Start with 1-2 examples  
3. ✅ **Pattern Validation**: Confirm compilation and instantiation
4. ✅ **Systematic Application**: Apply pattern to remaining batch inspectors
5. ✅ **Batch Verification**: Compile and test entire batch
6. ✅ **Progress Documentation**: Update this plan with completed items

## 🚀 **SUCCESS CRITERIA**
- ✅ All 29 remaining inspectors have @Inject annotation
- ✅ All inspectors have GraphRepository injection  
- ✅ Universal architecture pattern achieved
- ✅ Compilation successful for entire codebase
- ✅ PicoContainer can instantiate all inspectors
- ✅ Test infrastructure updated for new constructor patterns
- ✅ Clean, maintainable dependency injection architecture

## 📈 **PROGRESS TRACKING**
- **Phase 1**: 8/37 inspectors ✅ **COMPLETE**
- **Phase 2**: 0/29 remaining inspectors ⏳ **IN PROGRESS**
- **Overall**: 8/37 inspectors (22% complete)

**Next Action**: Begin Batch 1 with BinaryClassFQNInspector migration
