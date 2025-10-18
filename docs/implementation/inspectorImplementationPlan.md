# EJB Inspector Implementation Plan

> **Purpose:** Prioritized implementation phases for the missing EJB2 to Spring Boot migration inspectors, organized by criticality and dependencies.

## Implementation Priority Matrix

### Phase 1: Critical Detection (Weeks 1-2)
**Goal:** Enable basic EJB component identification and transaction analysis  
**Estimated Effort:** 4-6 inspectors, ~2 weeks  
**Success Criteria:** Can identify all EJB types and basic transaction boundaries

| Priority | Inspector ID | Inspector Name | Rationale | Dependencies |
|----------|--------------|----------------|-----------|--------------|
| **P0** | I-0206 | identify_cmp_field_mapping | Critical for R-240 (CMP→JPA), missing from current impl | I-0203, I-0501 |
| **P0** | I-0804 | identify_programmatic_transaction_usage | Critical for R-621 (Programmatic TX), complex pattern detection | I-0803 |
| **P0** | I-0805 | identify_container_managed_transaction_attrs | Critical for R-622 (Method TX), core migration pattern | I-0403, I-0401 |
| **P1** | I-0905 | identify_stateful_bean_conversational_state | Critical for R-850 (State externalization), complex analysis | I-1101, I-1102 |
| **P1** | I-0706 | identify_ejb_create_method_usage | Critical for R-700 (Client migration), affects all client code | I-0104, I-0701 |
| **P2** | I-0709 | identify_business_delegate_pattern | Important for R-703 (Client patterns), affects architecture | I-0701, I-0702 |

### Phase 2: Advanced Patterns (Weeks 3-4)
**Goal:** Handle complex persistence patterns and vendor configurations  
**Estimated Effort:** 6-8 inspectors, ~2 weeks  
**Success Criteria:** Complete CMP analysis and multi-vendor support

| Priority | Inspector ID | Inspector Name | Rationale | Dependencies |
|----------|--------------|----------------|-----------|--------------|
| **P0** | I-0207 | identify_cmp_relationship_cardinality | Critical for R-242 (CMR→JPA), complex relationship analysis | I-0204, I-0203 |
| **P0** | I-0208 | identify_cmp_query_method | Critical for R-243 (Finder→Repository), query migration | I-0801, I-0802 |
| **P1** | I-0211 | identify_bmp_jdbc_patterns | Important for R-250 (BMP→Repository), custom persistence | I-0205, I-0201 |
| **P1** | I-0503 | identify_weblogic_ejb_descriptor | Important for R-520 (WebLogic configs), vendor coverage | I-0004 |
| **P1** | I-0806 | identify_jta_datasource_usage | Important for R-623 (JTA DataSource), transaction integration | I-0703, I-0501 |
| **P2** | I-0212 | identify_data_access_object_pattern | Valuable for R-251 (DAO→Spring Data), architectural patterns | I-0003, I-0205 |
| **P2** | I-0213 | identify_value_object_pattern | Valuable for R-252 (VO→DTO), data transfer optimization | I-0102, I-0103 |
| **P2** | I-0504 | identify_websphere_ejb_descriptor | Valuable for R-521 (WebSphere configs), vendor coverage | I-0004 |

### Phase 3: Modern Migration (Weeks 5-6)
**Goal:** Enable cloud-native and microservice migration patterns  
**Estimated Effort:** 8-10 inspectors, ~2 weeks  
**Success Criteria:** Full modernization analysis capabilities

| Priority | Inspector ID | Inspector Name | Rationale | Dependencies |
|----------|--------------|----------------|-----------|--------------|
| **P0** | I-1005 | identify_javax_to_jakarta_migration_needs | Critical for Boot 3.x, blocking modern migration | I-1003 |
| **P0** | I-1006 | identify_microservice_boundary_candidates | Critical for R-900 (Service boundaries), architectural | I-0101, I-0201, I-0301 |
| **P1** | I-1008 | identify_configuration_externalization_needs | Important for R-902 (Config externalization), cloud readiness | I-0705, I-0501 |
| **P1** | I-1007 | identify_cloud_native_incompatible_patterns | Important for R-901 (Cloud compatibility), containerization | I-1104, I-1103, I-0901 |
| **P1** | I-0209 | identify_cmp_primary_key_composite | Important for R-241 (Composite PK), data modeling | I-0202, I-0201 |
| **P2** | I-0807 | identify_transaction_rollback_patterns | Valuable for R-625 (TX rollback), error handling | I-0105, I-0803 |
| **P2** | I-0708 | identify_ejb_handle_reference_usage | Valuable for R-702 (Handle→Reference), client migration | I-0102, I-0103 |
| **P2** | I-1009 | identify_distributed_transaction_usage | Valuable for R-903 (Distributed TX), complex patterns | I-0806, I-0501 |
| **P3** | I-0505 | identify_glassfish_ejb_descriptor | Nice-to-have for R-522 (GlassFish configs), vendor coverage | I-0004 |
| **P3** | I-0507 | identify_clustering_configuration | Nice-to-have for R-523 (Clustering→Cloud), legacy patterns | I-0501, I-0503, I-0504 |

### Phase 4: Performance & Optimization (Weeks 7-8)
**Goal:** Identify performance patterns and optimization opportunities  
**Estimated Effort:** 6-8 inspectors, ~2 weeks  
**Success Criteria:** Complete performance analysis and caching migration

| Priority | Inspector ID | Inspector Name | Rationale | Dependencies |
|----------|--------------|----------------|-----------|--------------|
| **P1** | I-1109 | identify_ejb_caching_patterns | Important for R-854 (EJB→Spring Cache), performance critical | I-0101, I-0201 |
| **P1** | I-1110 | identify_database_connection_patterns | Important for R-855 (Connection pooling), resource management | I-0703, I-0205 |
| **P1** | I-0906 | identify_ejb_interceptor_usage | Important for R-851 (Interceptors→AOP), cross-cutting concerns | I-0101, I-0201 |
| **P2** | I-1111 | identify_remote_interface_performance_issues | Valuable for R-856 (Chatty interfaces), performance optimization | I-0102, call graph |
| **P2** | I-0214 | identify_lazy_loading_pattern | Valuable for R-253 (Lazy loading), performance patterns | I-0201, I-0204 |
| **P2** | I-1112 | identify_bulk_operation_patterns | Valuable for R-857 (Bulk operations), batch processing | I-0201, I-0801 |
| **P3** | I-0215 | identify_optimistic_locking | Nice-to-have for R-254 (Optimistic locking), concurrency | I-0201, I-0203 |
| **P3** | I-0907 | identify_entity_bean_lifecycle_methods | Nice-to-have for R-852 (Entity lifecycle), cleanup patterns | I-0201, I-0903 |

## Implementation Strategy

### Week-by-Week Breakdown

#### Week 1: Foundation
- **I-0206** (CMP field mapping) - Core persistence analysis
- **I-0804** (Programmatic transactions) - Transaction boundary detection
- **I-0805** (Container TX attributes) - Declarative transaction mapping

#### Week 2: Client Migration
- **I-0905** (Stateful state) - Conversational state analysis
- **I-0706** (EJB create methods) - Client pattern detection
- **I-0709** (Business delegates) - Client architecture patterns

#### Week 3: Advanced Persistence
- **I-0207** (CMP relationships) - Complex relationship analysis
- **I-0208** (CMP queries) - Query method analysis
- **I-0211** (BMP JDBC) - Custom persistence detection

#### Week 4: Vendor Support
- **I-0503** (WebLogic descriptors) - Primary vendor support
- **I-0806** (JTA datasources) - Enterprise transaction support
- **I-0212** (DAO patterns) - Data access architecture

### Technology Implementation Notes

#### ASM-based Inspectors (High Performance)
```java
// Priority inspectors requiring bytecode analysis
I-0804: Programmatic TX (method-level analysis)
I-0905: Stateful state (field access patterns)  
I-0706: EJB create methods (method call analysis)
I-1109: Caching patterns (field/method analysis)
```

#### JavaParser-based Inspectors (Structural Analysis)
```java
// Priority inspectors requiring source analysis
I-0206: CMP field mapping (annotation/descriptor parsing)
I-0207: CMP relationships (metadata analysis)
I-0208: CMP queries (EJB-QL parsing)  
I-0503: WebLogic descriptors (XML parsing)
```

#### Graph-enhanced Inspectors (Relationship Analysis)
```java
// Priority inspectors requiring graph context
I-1006: Microservice boundaries (component clustering)
I-0709: Business delegates (call pattern analysis)
I-1111: Performance issues (call chain analysis)
```

## Implementation Dependencies

### Core Technology Stack
- **ASM 9.x**: Bytecode analysis for performance-critical inspectors
- **JavaParser 3.x**: Source code structural analysis
- **JAXB/StAX**: XML descriptor parsing (vendor configs)
- **Graph API**: Relationship and dependency analysis

### Infrastructure Requirements
- **InspectorTags**: Extended with EJB-specific tags
- **GraphRepository**: Enhanced with EJB node types  
- **ProjectFile**: Enhanced with EJB metadata
- **ManifestGenerator**: Output for refactoring rules

### Testing Strategy
- **Unit Tests**: Each inspector with isolated test cases
- **Integration Tests**: Phase completion validation
- **Performance Tests**: Large codebase analysis
- **Migration Tests**: End-to-end refactoring validation

## Success Metrics

### Phase 1 Completion
- [ ] 95%+ detection accuracy for basic EJB components
- [ ] Transaction boundary analysis for 90%+ of methods
- [ ] Stateful session bean state flow analysis
- [ ] Client pattern detection coverage

### Phase 2 Completion  
- [ ] Complete CMP field and relationship mapping
- [ ] Multi-vendor deployment descriptor support
- [ ] BMP pattern detection and JDBC analysis
- [ ] Advanced persistence pattern coverage

### Phase 3 Completion
- [ ] Jakarta EE migration readiness assessment
- [ ] Microservice boundary recommendations
- [ ] Cloud-native compatibility analysis
- [ ] Configuration externalization recommendations

### Phase 4 Completion
- [ ] Performance anti-pattern detection
- [ ] Caching strategy migration planning
- [ ] Database optimization recommendations
- [ ] Complete EJB migration analysis capability

This phased approach ensures that critical migration blockers are addressed first, while building toward comprehensive EJB modernization capabilities.
