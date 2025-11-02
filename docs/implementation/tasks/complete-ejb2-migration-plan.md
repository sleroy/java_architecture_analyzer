# Task: Complete EJB 2.x to Spring Boot Migration Plan

**Status:** IN PROGRESS  
**Priority:** HIGH  
**Created:** 2025-11-01  
**File:** MIGRATION_PLAN_JBOSS_TO_SPRINGBOOT.md

## Context

The migration plan document was started but truncated at approximately 8,000 lines. It currently covers:
- ✅ Phase 0: Pre-migration assessment
- ✅ Phase 1: Spring Boot initialization (Pure JDBC, NO JPA)
- ✅ Phase 2: Database connectivity (JDBC wrappers → Spring JdbcTemplate)
- 🔄 Phase 2B: Entity Bean migration (PARTIALLY COMPLETE - stopped mid-TASK-251)

## What's Missing

The document needs to be completed with the following sections:

### 1. Complete TASK-251 (CMP Entity Bean Migration)
**Current State:** Truncated mid-code example in DAO creation
**Needs:** 
- Complete the `CustomerDao` code example
- Add CMR (Container-Managed Relationships) handling patterns
- Add composite primary key handling
- Add finder method migration examples
- Add validation section

### 2. TASK-252-253: BMP Entity Bean Migration
**Pattern TAG:** BMP_ENTITY_BEAN_IDENTIFICATION, BMP_TO_JDBC_DAO

**Content Needed:**
- BMP identification patterns (search commands)
- BMP characteristics (manual JDBC in ejbLoad/ejbStore)
- Migration approach: Refactor existing JDBC to use Spring JdbcTemplate
- Code examples showing ejbLoad → DAO.findById
- Code examples showing ejbStore → DAO.update
- Validation checklist

### 3. Phase 3: Complete Session Bean Migration
**Enhancement Needed:**
- TASK-300: EJB 2 Session Bean identification (already exists, enhance if needed)
- TASK-301: Stateless Session Bean migration (already exists, review)
- TASK-302: Stateful Session Bean migration (already exists, review)
- Add EJBContext handling patterns (getCallerPrincipal, getRollbackOnly, etc.)

### 4. NEW Phase 3B: Message-Driven Bean Migration
**Pattern TAGs:** MDB_IDENTIFICATION, MDB_TO_SPRING_JMS

**Tasks Needed:**
- TASK-350: Identify all MDBs (search for @MessageDriven, MessageDrivenBean interface)
- TASK-351: Migrate MDB to Spring @JmsListener
  - onMessage() → @JmsListener method
  - MessageListener interface → @Component
  - Destination configuration → application.properties
  - Transaction handling with @Transactional
  - Error handling strategies
  - Code examples

### 5. NEW Phase 3C: EJB Interface Removal
**Pattern TAGs:** HOME_INTERFACE_REMOVAL, REMOTE_INTERFACE_REMOVAL, JNDI_LOOKUP_ELIMINATION

**Tasks Needed:**
- TASK-360: Remove Home/LocalHome interfaces
  - Identify all create() methods → constructor or factory
  - Identify all finder methods → repository methods
  - Remove interface files
  
- TASK-361: Remove Remote/Local interfaces
  - Keep business methods in service classes
  - Remove interface inheritance
  - Update all references
  
- TASK-362: Replace JNDI lookups with Spring DI
  - InitialContext.lookup() → @Autowired
  - PortableRemoteObject.narrow() → Direct injection
  - ejb-ref → Spring bean references
  - Code examples for each pattern

### 6. Phases 4-8: Review and Enhance Existing Content
**Already Present but May Need Enhancement:**
- Phase 4: SOAP (JAX-WS to Spring WS) - Review completeness
- Phase 5: REST (JAX-RS to Spring MVC) - Review completeness  
- Phase 6: Configuration migration - Review completeness
- Phase 7: Testing migration - Review completeness
- Phase 8: Packaging and deployment - Review completeness

### 7. NEW Phase 9: JDK 8 → JDK 21 Migration
**Tasks Needed:**
- TASK-900: Library compatibility analysis
  - Common library updates (Log4j, Commons, Hibernate, Jackson, etc.)
  - javax → jakarta namespace migration
  - Deprecated API replacements
  
- TASK-901: JDK API updates
  - SecurityManager removal (JDK 17+)
  - Removed APIs (Applet, CORBA, etc.)
  - New language features adoption (records, pattern matching, etc.)
  
- TASK-902: Spring Boot version upgrade
  - Spring Boot 2.7.x → 3.x migration path
  - Breaking changes
  - Dependency updates

### 8. NEW Phase 10: Legacy Antipattern Refactoring
**Pattern TAGs:** DEEP_INHERITANCE_REFACTORING, SINGLETON_MODERNIZATION, GOD_CLASS_DECOMPOSITION, STATIC_UTILITY_REFACTORING, CHECKED_EXCEPTION_MODERNIZATION

**Tasks Needed:**
- TASK-1000: Deep inheritance tree refactoring
  - Identify inheritance >3 levels deep
  - Refactor to composition
  - Extract interfaces
  
- TASK-1001: Singleton pattern modernization
  - getInstance() → Spring @Bean
  - Static initialization → Spring lifecycle
  - Thread-safe singleton scope
  
- TASK-1002: God class decomposition
  - Identify classes >1000 lines or >20 methods
  - Apply Single Responsibility Principle
  - Extract service classes
  
- TASK-1003: Static utility refactoring
  - Static utility classes → @Component beans
  - Enable dependency injection
  - Improve testability
  
- TASK-1004: Checked exception modernization
  - Reduce excessive checked exceptions
  - Use Spring exception hierarchy
  - Exception translation layers

### 9. Enhanced Appendices
**Content Needed:**

**Appendix E: Complete EJB 2.x Component Matrix**
- Table with columns: Component Type | Characteristics | Spring Equivalent | Pattern TAG | Complexity | Notes
- Cover all: Session Beans, Entity Beans, MDBs, Interfaces, etc.

**Appendix F: JDK Version Migration Matrix**
- API/Library | JDK 8 | JDK 11 | JDK 17 | JDK 21 | Spring Boot Alternative

**Appendix G: Legacy Antipatterns Catalog**
- Antipattern | Detection | Refactoring Strategy | Spring Approach | Risk Level

**Appendix H: Dependency Update Guide**
- Library | Old Version | New Version | Breaking Changes | Migration Notes

**Appendix I: JDBC vs JPA Decision Matrix**
- When to stay with JDBC
- When to migrate to JPA
- Hybrid approaches

## Implementation Approach

Since the file is getting very large (10,000+ lines expected), consider:

### Option A: Single Comprehensive File
Continue in MIGRATION_PLAN_JBOSS_TO_SPRINGBOOT.md with all content

**Pros:** 
- Single source of truth
- Easy to search
- Complete reference

**Cons:** 
- Very large file
- May be overwhelming
- Harder to navigate

### Option B: Multi-Part Document Structure
Split into multiple files:
- MIGRATION_PLAN_JBOSS_TO_SPRINGBOOT.md (overview + TOC)
- MIGRATION_PLAN_PART1_INFRASTRUCTURE.md (Phases 0-2)
- MIGRATION_PLAN_PART2_ENTITY_BEANS.md (Phase 2B)
- MIGRATION_PLAN_PART3_SESSION_BEANS.md (Phase 3)
- MIGRATION_PLAN_PART4_MDB_INTERFACES.md (Phase 3B-3C)
- MIGRATION_PLAN_PART5_WEB_SERVICES.md (Phases 4-5)
- MIGRATION_PLAN_PART6_DEPLOYMENT.md (Phases 6-8)
- MIGRATION_PLAN_PART7_JDK_MIGRATION.md (Phase 9)
- MIGRATION_PLAN_PART8_ANTIPATTERNS.md (Phase 10)
- MIGRATION_PLAN_APPENDICES.md (All appendices)

**Pros:**
- Manageable file sizes
- Better organization
- Easier to maintain

**Cons:**
- Multiple files to manage
- Cross-references needed

### Option C: Hybrid Approach (RECOMMENDED)
- Main file: MIGRATION_PLAN_JBOSS_TO_SPRINGBOOT.md with complete Phases 0-5
- Supplementary files for advanced topics:
  - MIGRATION_PLAN_JDK_UPGRADE.md (Phase 9 details)
  - MIGRATION_PLAN_ANTIPATTERNS.md (Phase 10 details)
  - MIGRATION_PLAN_APPENDICES.md (All appendices)

## Completion Checklist

- [ ] Complete TASK-251 (CMP to JDBC DAO)
- [ ] Add TASK-252-253 (BMP migration)
- [ ] Enhance Phase 3 (Session Beans - EJBContext patterns)
- [ ] Add Phase 3B (Message-Driven Beans)
- [ ] Add Phase 3C (EJB Interface Removal)
- [ ] Review Phases 4-8 (SOAP, REST, Config, Test, Deploy)
- [ ] Add Phase 9 (JDK 8→21 migration)
- [ ] Add Phase 10 (Antipattern refactoring)
- [ ] Create Appendix E (EJB Component Matrix)
- [ ] Create Appendix F (JDK Migration Matrix)
- [ ] Create Appendix G (Antipatterns Catalog)
- [ ] Create Appendix H (Dependency Updates)
- [ ] Create Appendix I (JDBC vs JPA Guide)
- [ ] Final validation and consistency check
- [ ] Create table of contents with jump links

## Acceptance Criteria

1. ✅ All EJB 2.x component types covered (Session, Entity, MDB)
2. ✅ All EJB 2.x interfaces covered (Home, Remote, Local, SEI)
3. ✅ Pure JDBC approach (NO JPA) maintained throughout
4. ✅ Pattern TAGs for every migration scenario
5. ✅ Class-by-class migration approach documented
6. ✅ Code examples are application-agnostic (use variables)
7. ✅ Validation checklists for each task
8. ✅ JDK 8→21 migration path documented
9. ✅ Legacy antipattern refactoring patterns included
10. ✅ Complete reference appendices

## Notes

- Current file is at ~8,000 lines and truncated
- Estimated final size: 12,000-15,000 lines (or split into multiple files)
- All patterns must use JDBC (no JPA references)
- Keep examples generic and application-agnostic
- Focus on deterministic, repeatable migration patterns

## Next Steps

1. Decide on document structure (A, B, or C)
2. Continue from current truncation point in TASK-251
3. Complete all missing sections systematically
4. Create appendices
5. Final review and validation
