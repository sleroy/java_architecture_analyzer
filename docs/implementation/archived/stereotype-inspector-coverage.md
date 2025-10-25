# Stereotype Inspector Coverage Analysis

**Generated**: 2025-10-20  
**Status**: 23/34 stereotypes implemented (68%)

## Executive Summary

This document tracks the implementation status of inspectors for the 34 architectural stereotypes defined in `docs/stereotypes.md`. These stereotypes drive EJB2 to Spring Boot migration analysis.

---

## ✅ Fully Implemented (23/34)

| ID | Stereotype | Detection Pattern | Inspector(s) | Tags |
|---|---|---|---|---|
| **CS-010** | SessionBean (Stateless) | `implements javax.ejb.SessionBean` + Stateless | SessionBeanJavaSourceInspector | `ejb.stateless.sessionBean` |
| **CS-011** | SessionBean (Stateful) | Same + Stateful or ejbActivate/Passivate | SessionBeanJavaSourceInspector<br/>StatefulSessionStateInspector | `ejb.stateful.sessionBean`<br/>`ejb.stateful.conversationalState` |
| **CS-012** | EntityBean (CMP) | `implements javax.ejb.EntityBean` + CMP | EntityBeanJavaSourceInspector<br/>CmpFieldMappingJavaBinaryInspector | `ejb.cmp.entityBean`<br/>`ejb.cmp.fieldMapping` |
| **CS-013** | EntityBean (BMP) | Manual JDBC in entity bean | EntityBeanJavaSourceInspector | `ejb.bmp.entityBean` |
| **CS-014** | MessageDrivenBean | `implements MessageDrivenBean` or `onMessage()` | MessageDrivenBeanInspector | `ejb.messageDrivenBean` |
| **CS-015** | Home Interface | `extends javax.ejb.EJBHome/EJBLocalHome` | EjbHomeInterfaceInspector | `ejb.homeInterface`<br/>`ejb.localHomeInterface` |
| **CS-016** | Remote Interface | `extends javax.ejb.EJBObject` | EjbRemoteInterfaceInspector | `ejb.remoteInterface` |
| **CS-019** | ServiceLocator/Factory | JNDI lookups | ServiceLocatorInspector<br/>JndiLookupInspector | `ejb.client.serviceLocator`<br/>`ejb.client.jndiLookup` |
| **CS-020** | Servlet | `extends javax.servlet.http.HttpServlet` | ServletInspector<br/>IdentifyServletSourceInspector | Custom servlet tags |
| **CS-024** | Form Bean/DTO | POJO under dto/form packages | FormBeanDtoInspector<br/>CustomDataTransferPatternJavaSourceInspector | `dataAccess.dto.pattern` |
| **CS-030** | DAO/Repository | Ends with DAO, JDBC calls | DaoRepositoryInspector<br/>JdbcDataAccessPatternInspector | `dataAccess.dao.pattern`<br/>`jdbc.usage.detected` |
| **CS-032** | Transaction Script | Uses `UserTransaction` | TransactionScriptInspector<br/>ProgrammaticTransactionUsageInspector | `ejb.transaction.programmatic` |
| **CS-033** | Utility/Helper | Static methods only | UtilityHelperInspector | Custom utility tags |
| **CS-040** | TimerBean | Uses `javax.ejb.TimerService` | TimerBeanInspector | Custom timer tags |
| **CS-050** | SecurityFacade | `SessionContext.getCallerPrincipal()` | SecurityFacadeInspector | `ejb.security.configuration` |
| **CS-060** | FactoryBean/Provider | Manually provides instances | FactoryBeanProviderInspector | Custom factory tags |
| **CS-061** | Cache/Singleton | Static mutable or DCL | CacheSingletonInspector | `ejb.performance.caching` |
| **CS-062** | Interceptor/AOP | Vendor interceptors | InterceptorAopInspector | Custom interceptor tags |
| **CS-063** | Configuration/Constants | Constant-only class | ConfigurationConstantsInspector | Custom config tags |
| **CS-070** | Mutable Service | Non-final instance fields | MutableServiceInspector | Custom mutability tags |
| **CS-071** | StateHolder | Field write→read across methods | StatefulSessionStateInspector | `ejb.conversationalState` |
| **CS-073** | Synchronized Singleton | Synchronized static mutable | CacheSingletonInspector | (Covered by CS-061) |

### Supporting Inspectors (Not Direct Stereotypes)
- **EjbBinaryClassInspector** - Binary analysis for EJB components
- **EjbDeploymentDescriptorInspector** - ejb-jar.xml parsing
- **EjbDeploymentDescriptorDetector** - Deployment descriptor detection
- **ApplicationServerConfigDetector** - JBoss/vendor config detection
- **JBossEjbConfigurationInspector** - JBoss-specific EJB config
- **LegacyFrameworkDetector** - Legacy framework detection
- **DatabaseResourceManagementInspector** - Database resource management
- **ComplexCmpRelationshipJavaSourceInspector** - CMR relationship analysis
- **BusinessDelegatePatternJavaSourceInspector** - Business delegate pattern
- **EjbCreateMethodUsageInspector** - EJB create method analysis
- **EjbClassLoaderInspector** - ClassLoader-based EJB inspection

---

## ⚠️ Missing Inspectors (11/34)

### 🔴 High Priority (6)

#### 1. CS-017: Local Interface
**Pattern**: `extends javax.ejb.EJBLocalObject`  
**Purpose**: Local business API  
**Spring Target**: Collapsed to interface  
**Implementation Notes**:
- Similar to EjbRemoteInterfaceInspector
- Detect `javax.ejb.EJBLocalObject` interface extension
- Tag: `ejb.localInterface`
- Lower migration priority than remote interfaces (local already optimized)

#### 2. CS-018: Primary Key Class
**Pattern**: `implements Serializable`, used in ejb-jar.xml  
**Purpose**: PK for EntityBean  
**Spring Target**: `@Embeddable` or ID type  
**Implementation Notes**:
- Detect classes referenced as `<prim-key-class>` in ejb-jar.xml
- Also detect by naming pattern (*PK, *Key) + Serializable
- Composite keys need special handling
- Tag: `ejb.primaryKey`, `ejb.primaryKey.composite`

#### 3. CS-021: Filter/Listener
**Pattern**: `implements javax.servlet.Filter` / Listener interfaces  
**Purpose**: Cross-cutting web concerns  
**Spring Target**: `@Component` + `@WebFilter`/`@EventListener`  
**Implementation Notes**:
- Critical for web layer migration
- Detect: Filter, ServletContextListener, HttpSessionListener, etc.
- Tag: Custom filter/listener tags
- Migration: Most become Spring components or interceptors

#### 4. CS-051: Role-based Service
**Pattern**: `<method-permission>` in XML  
**Purpose**: Role enforcement  
**Spring Target**: `@PreAuthorize/@RolesAllowed`  
**Implementation Notes**:
- Parse ejb-jar.xml for `<method-permission>` elements
- Map EJB roles to Spring Security expressions
- Tag: `ejb.security.roleBasedMethod`
- Complex: may involve multiple deployment descriptors

#### 5. CS-052: ContextHolder/ThreadLocal
**Pattern**: ThreadLocal fields  
**Purpose**: Context leak risk  
**Spring Target**: Scoped bean  
**Implementation Notes**:
- Detect fields of type `ThreadLocal<T>`
- Identify leaked ThreadLocal (not properly cleaned)
- Tag: `threadLocal.usage`, `threadLocal.leak.risk`
- Critical for thread safety in Spring
- **Note**: Overlaps with CS-072

#### 6. CS-072: ThreadLocal User
**Pattern**: ThreadLocal usage  
**Purpose**: Thread leakage  
**Spring Target**: Scoped bean  
**Implementation Notes**:
- **Duplicate of CS-052** - consolidate into single inspector
- Detect both field declarations and method usage
- Check for proper cleanup (remove() calls)

### 🟡 Medium Priority (3)

#### 7. CS-022: JSF/Struts Action
**Pattern**: `extends Action` / `BackingBean`  
**Purpose**: MVC controller  
**Spring Target**: `@Controller`  
**Implementation Notes**:
- Detect Struts Action classes
- Detect JSF backing beans
- Less common in modern EJB2 apps
- Tag: `mvc.struts.action`, `mvc.jsf.backingBean`

#### 8. CS-034: Legacy ORM Helper
**Pattern**: Vendor CMP APIs  
**Purpose**: Persistence glue  
**Spring Target**: Remove/replace  
**Implementation Notes**:
- Detect proprietary ORM helpers (TopLink, CocoBase, etc.)
- Hard to detect without vendor-specific knowledge
- Tag: `persistence.vendor.helper`

#### 9. CS-042: Listener/Observer
**Pattern**: JMS or async listener (non-MDB)  
**Purpose**: Event consumer  
**Spring Target**: `@JmsListener` / `@EventListener`  
**Implementation Notes**:
- Detect custom listener interfaces (not MDB)
- Observer pattern implementations
- Tag: `event.listener`, `event.observer.pattern`

### 🟢 Low Priority (2)

#### 10. CS-023: Tag/JSP Helper
**Pattern**: `extends TagSupport`  
**Purpose**: View helper  
**Spring Target**: Removed / Thymeleaf fragment  
**Implementation Notes**:
- JSP custom tags
- Rare in EJB2 business logic
- Tag: `web.jsp.customTag`

#### 11. CS-064: Legacy Test Stub
**Pattern**: Test-only class  
**Purpose**: No migration  
**Spring Target**: Spring Boot Test  
**Implementation Notes**:
- Detect test stubs/mocks
- Low priority - tests rewritten anyway
- Tag: `test.stub`, `test.mock`

---

## 📋 Special Cases

### CS-031: JPA Entity
**Pattern**: `@Entity` already  
**Status**: Likely covered by standard annotation inspectors  
**Action**: Verify with existing AnnotationCountInspector  
**No custom inspector needed** - standard JPA annotations sufficient

### CS-041: Job/Batch Bean
**Pattern**: Invoked by timer/scheduler  
**Status**: Likely covered by TimerBeanInspector (CS-040)  
**Action**: Verify TimerBeanInspector detects both timer and scheduled jobs  
**May need enhancement** rather than new inspector

### CS-072 vs CS-052
**Duplicate**: Both detect ThreadLocal usage  
**Recommendation**: Consolidate into single comprehensive ThreadLocal inspector  
**Implementation**: Detect fields, usage, and cleanup patterns

---

## 📊 Implementation Priority Matrix

```
Priority    | Count | Stereotypes
------------|-------|------------------
Critical    | 6     | CS-017, CS-018, CS-021, CS-051, CS-052, CS-072
Important   | 3     | CS-022, CS-034, CS-042
Nice-to-Have| 2     | CS-023, CS-064
Verify      | 2     | CS-031, CS-041
```

---

## 🎯 Recommended Next Steps

1. **Immediate**: Implement CS-052/CS-072 (ThreadLocal) - Single inspector
2. **Week 1**: Implement CS-017 (Local Interface) - Clone Remote inspector
3. **Week 1**: Implement CS-021 (Filter/Listener) - Web layer critical
4. **Week 2**: Implement CS-018 (Primary Key Class) - Persistence layer
5. **Week 2**: Implement CS-051 (Role-based Service) - Security critical
6. **Week 3**: Verify CS-031 (JPA Entity) and CS-041 (Job/Batch) coverage
7. **Week 4**: Implement medium-priority inspectors as needed

---

## 📝 Notes

- **Coverage**: 68% of defined stereotypes have dedicated inspectors
- **Tag Strategy**: All inspectors follow EjbMigrationTags conventions
- **Testing**: Each new inspector needs test cases with sample code
- **Integration**: All inspectors integrate with InspectorRegistry automatically

---

## 🔗 Related Documents

- [docs/stereotypes.md](../stereotypes.md) - Complete stereotype catalog
- [EjbMigrationTags.java](../../src/main/java/com/analyzer/rules/ejb2spring/EjbMigrationTags.java) - Tag definitions
- [docs/implementation/inspectorImplementationPlan.md](./inspectorImplementationPlan.md) - Inspector development guide
- [memory-bank/progress.md](../../memory-bank/progress.md) - Overall project progress
