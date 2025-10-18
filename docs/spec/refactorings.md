# Refactoring Scripts Catalog

Columns: **Rule**, **Triggers (Inspectors)**, **Dependencies/Data**, **Technology**, **Purpose**, **Implementation details**

| Rule | Triggers (Inspectors) | Dependencies / Required Data | Technology | Purpose | Implementation details |
|---|---|---|---|---|---|
| R-000: javax→jakarta | I-1003 | Files/imports using `javax.*` | OpenRewrite Jakarta recipes or Eclipse Transformer | Boot 3 readiness | Transform imports/packages; run Spring recipes afterward |
| R-001: Build Modernization | I-1001, I-1002 | Legacy deps, Java level, module graph (A-00) | OpenRewrite Maven/Gradle | Replace app-server deps with Spring Boot starters; set Java 17+ | Update POM/Gradle; verify compile |
| R-100: Collapse Home/Remote/Local | I-0101..I-0104 | Bean impl FQN, interfaces, call sites (A-09) | OpenRewrite JavaVisitor | Simplify EJB API to business service | Remove EJB interfaces, create `@Service` or interface + impl, rewrite usages |
| R-110: Add `@Service` & `@Transactional` | I-0101, I-0403 | Per-method tx attrs from manifest (A-07), readOnly hints | OpenRewrite + manifest.json | Map EJB tx semantics to Spring | Annotate class/methods with Propagation; `readOnly=true` for finders |
| R-120: Replace JNDI/Service Locator | I-0701, I-0702, I-0502 | JNDI names, resolved targets (A-06) | OpenRewrite JavaVisitor | Move to DI / bean injection | Introduce constructor-injected fields; remove lookups; generate configs if needed |
| R-130: Security Annotations | I-0404, I-0904, I-0105 | Roles per method (A-07), principal mapping | OpenRewrite | Apply `@PreAuthorize/@RolesAllowed` | Add annotations; generate `SecurityConfig` skeleton |
| R-200: Entity → `@Entity` | I-0201..I-0203 | PK class, table/column map, fields | Codegen (JavaPoet) + OpenRewrite | Create JPA entities | Generate `@Entity`/`@Embeddable`; move fields; mark old bean deprecated |
| R-210: Move Logic to Service/Repo | I-0201, I-0205, I-0803 | JDBC blocks, transactional hotspots, callers (A-09, A-14) | OpenRewrite + manual review + Bedrock assist | Separate persistence/business logic | Extract methods to `@Service`; create `Repository` APIs |
| R-220: Finder → Repository | I-0802, I-0801 | Finder signatures, EJB-QL | Codegen + OpenRewrite | Spring Data repositories | Create `JpaRepository` with derived methods or `@Query`; update call sites |
| R-230: CMR → JPA Associations | I-0204 | Relationship ends/cardinality | OpenRewrite + codegen | Proper JPA mappings | Add `@OneToMany/@ManyToOne/@ManyToMany` with join columns; adjust equals/hashCode |
| R-300: MDB → `@JmsListener` | I-0301, I-0302, I-0704 | Destination, type, ack, concurrency | Codegen + OpenRewrite | Spring messaging | Generate `@EnableJms` config & listener; replace activation-config |
| R-310: Timers → `@Scheduled` | I-0901, I-0902 | Interval/cron semantics | OpenRewrite | Scheduling | Add `@EnableScheduling` + `@Scheduled` with cron/fixedDelay |
| R-400: `<ejb-ref>` → DI/Clients | I-0603 | Bean refs, call graph (A-09) | XML→Java + OpenRewrite | Web tier integration | Inject beans or generate REST/gRPC clients for cross-JVM calls |
| R-410: EAR → Boot Packaging | I-0601, I-1001 | Module list, packaging | Build scripts + OpenRewrite | Produce Boot apps | Flatten EAR; externalize config |
| R-500: Datasource/JTA → Boot DataSource | I-0703, I-0203 | JNDI→JDBC mapping | YAML generator + Boot auto-config | Runtime config | Create `application.yml`; optionally XA via Narayana/Atomikos |
| R-510: Env-Entry → `application.yml` | I-0705, I-0502 | Key/values and scopes | YAML generator + OpenRewrite | Externalize config | Replace property access with `@Value` |
| R-600: Remove EJB Lifecycle | I-0903 | Callback methods list | OpenRewrite | Cleanups | Delete or map to `@PostConstruct`/`InitializingBean` |
| R-610: Retire Service Locator | I-0702, I-0701 | Call sites | OpenRewrite | Cleanup & DI | Inline DI; deprecate then delete class |
| R-620: Programmatic Tx → `@Transactional` | I-0803 | `UserTransaction` use sites, control flow | OpenRewrite + small CFG | Correct tx semantics | Wrap methods with `@Transactional`; remove manual tx |
| R-700: Remote EJB → REST/gRPC | I-0102 (+ usages) | Network boundary, payloads | Codegen (OpenAPI/gRPC) + OpenRewrite | Decouple from RMI | Generate controllers/clients; rewrite calls |

## Mutability-aware Refactors
| Rule | Triggers (Inspectors) | Dependencies/Data | Technology | Purpose | Implementation details |
|---|---|---|---|---|---|
| R-800: Externalize Conversational State | I-1101, I-1102 | Writer→Reader field map; session behavior | Codegen + OpenRewrite | Replace stateful patterns | Create `StateHolder` (DB/Redis) or `@SessionScope` bean; update method signatures/calls |
| R-810: Make Services Stateless/Thread-safe | I-1100, I-1106 | Non-final field list; sync blocks | OpenRewrite | Safe singletons | Move state to locals/holder; ensure constructor DI; remove unsafe sync |
| R-820: Replace Non-Thread-Safe Utils | I-1105 | Util use sites | OpenRewrite | Concurrency safety | Swap to `java.time` APIs; make formatters `static final` |
| R-830: Retire Static Mutable Singletons | I-1104, I-1108 | Static fields & uses | OpenRewrite | Remove global state | Replace with Spring-managed beans or proper caches |
| R-840: ThreadLocal to Scoped Bean | I-1103 | ThreadLocal fields & set/remove | OpenRewrite | Prevent leaks | Replace with scoped bean/proxy; ensure proper cleanup |

## CMP-specific Refactorings
| Rule | Triggers (Inspectors) | Dependencies/Data | Technology | Purpose | Implementation details |
|---|---|---|---|---|---|
| R-240: CMP Field → JPA Field | I-0206, I-0201 | Field-to-column mappings, data types | Codegen + OpenRewrite | Convert CMP fields to JPA annotations | Generate `@Column`, `@Basic`, `@Temporal` annotations; update getters/setters |
| R-241: Composite PK → JPA Embeddable | I-0209, I-0202 | PK class structure, field mappings | Codegen + OpenRewrite | Convert composite keys | Create `@Embeddable` PK class; add `@EmbeddedId` to entity |
| R-242: CMR → JPA Association Advanced | I-0207, I-0204 | Relationship cardinality, foreign keys | Codegen + OpenRewrite | Handle complex CMR relationships | Generate `@JoinTable`, `@JoinColumn`, bidirectional mappings |
| R-243: CMP Finder → Repository Query | I-0208, I-0801 | EJB-QL queries, finder signatures | Codegen + OpenRewrite | Convert complex finders | Create `@Query` annotations, derived query methods |
| R-244: Cascade Operations → JPA Cascade | I-0210, I-0204 | Cascade rules, relationship metadata | OpenRewrite | Map EJB cascade to JPA | Add `cascade` attributes to `@OneToMany/@ManyToOne` |

## Transaction Management Refactorings
| Rule | Triggers (Inspectors) | Dependencies/Data | Technology | Purpose | Implementation details |
|---|---|---|---|---|---|
| R-621: Programmatic TX → Declarative | I-0804, I-0803 | Transaction boundaries, rollback patterns | OpenRewrite + CFG analysis | Replace manual transaction code | Wrap in `@Transactional` methods; remove `UserTransaction` code |
| R-622: Method TX Attributes → Spring TX | I-0805, I-0403 | Per-method tx attributes from descriptors | OpenRewrite + manifest.json | Map EJB tx attributes to Spring | Apply `@Transactional(propagation=...)` with correct propagation |
| R-623: JTA DataSource → Boot DataSource | I-0806, I-0703 | XA datasource configs, JNDI mappings | YAML generator + Boot config | Convert to Spring Boot datasources | Create `application.yml` with XA properties; configure Narayana/Atomikos |
| R-624: Nested TX Context → Spring | I-0808, I-0403 | Transaction scope analysis | OpenRewrite | Handle nested transactions | Map to Spring propagation (REQUIRED, REQUIRES_NEW, etc.) |
| R-625: TX Rollback → Spring Exception | I-0807, I-0105 | Rollback call sites, exception patterns | OpenRewrite | Convert rollback patterns | Replace with Spring rollback exceptions or @Transactional(rollbackFor=...) |

## EJB Client Migration Refactorings  
| Rule | Triggers (Inspectors) | Dependencies/Data | Technology | Purpose | Implementation details |
|---|---|---|---|---|---|
| R-700: EJB Create → Constructor/Factory | I-0706, I-0104 | Home interface usage, create patterns | OpenRewrite | Remove home.create() calls | Replace with direct constructor or factory method injection |
| R-701: EJB Remove → Lifecycle Management | I-0707, I-0102 | Remove method usage, lifecycle patterns | OpenRewrite | Handle bean removal | Replace with Spring bean lifecycle or explicit cleanup |
| R-702: EJB Handle → Service Reference | I-0708, I-0102 | Handle/reference usage patterns | OpenRewrite | Remove EJB handles | Replace serializable handles with service references or DTOs |
| R-703: Business Delegate → Direct Injection | I-0709, I-0701 | Delegate patterns, JNDI lookups | OpenRewrite | Simplify client access | Replace business delegates with direct service injection |
| R-704: Passivation/Activation → Scoped Bean | I-0710, I-1101 | Stateful lifecycle methods | OpenRewrite + Spring scope config | Handle stateful lifecycle | Convert to `@SessionScope` or externalize state to Redis/DB |

## Advanced Persistence Refactorings
| Rule | Triggers (Inspectors) | Dependencies/Data | Technology | Purpose | Implementation details |
|---|---|---|---|---|---|
| R-250: BMP → JPA Repository | I-0211, I-0205 | JDBC patterns in entity beans | OpenRewrite + manual review | Extract persistence logic | Create `@Repository` with custom queries; move JDBC to repository layer |
| R-251: DAO → Spring Data | I-0212, I-0003 | DAO patterns, data access code | OpenRewrite + interface generation | Modernize data access | Convert to Spring Data JPA repositories; generate interfaces |
| R-252: Value Object → DTO/Projection | I-0213, I-0102 | Transfer object patterns | Codegen + OpenRewrite | Modernize data transfer | Create DTOs with Jackson annotations; add projection interfaces |
| R-253: Lazy Loading → JPA Lazy | I-0214, I-0201 | Lazy loading patterns, performance | OpenRewrite | Convert to JPA lazy loading | Add `fetch=LAZY` to associations; create DTO projections for views |
| R-254: Optimistic Locking → JPA Version | I-0215, I-0201 | Version fields, concurrency patterns | OpenRewrite | Handle concurrent updates | Add `@Version` fields; configure optimistic locking |

## Vendor-specific Migration Refactorings
| Rule | Triggers (Inspectors) | Dependencies/Data | Technology | Purpose | Implementation details |
|---|---|---|---|---|---|
| R-520: WebLogic Config → Boot Config | I-0503, I-0501 | WebLogic descriptors, JNDI bindings | YAML generator + config migration | Remove WebLogic dependencies | Extract configs to `application.yml`; remove vendor-specific annotations |
| R-521: WebSphere Config → Boot Config | I-0504, I-0501 | IBM WebSphere bindings | YAML generator | Remove WebSphere dependencies | Convert IBM-specific configs to Spring Boot equivalents |
| R-522: GlassFish Config → Boot Config | I-0505, I-0501 | Sun/Oracle descriptors | YAML generator | Remove GlassFish dependencies | Migrate Sun-specific configurations to standard Spring configs |
| R-523: Clustering Config → Cloud Native | I-0507, I-1007 | Clustering configurations | Cloud config templates | Prepare for container deployment | Remove clustering configs; add cloud-native alternatives (load balancers, etc.) |

## Modern Migration Refactorings
| Rule | Triggers (Inspectors) | Dependencies/Data | Technology | Purpose | Implementation details |
|---|---|---|---|---|---|
| R-001: javax → jakarta Migration | I-1005, I-1003 | Package imports, API usage | OpenRewrite Jakarta recipes | Boot 3.x compatibility | Transform `javax.*` to `jakarta.*`; update Spring Boot version |
| R-900: Microservice Boundary Extraction | I-1006, I-0101 | Service boundary analysis, call graphs | Manual + architectural guidance | Decompose monolith | Extract bounded contexts; create separate Spring Boot applications |
| R-901: Cloud Native Compatibility | I-1007, I-1104 | Cloud-incompatible patterns | OpenRewrite + cloud templates | Container readiness | Remove static state; externalize configuration; add health checks |
| R-902: Configuration Externalization | I-1008, I-0705 | Hardcoded configurations | YAML generator + Spring Boot | Externalize all config | Move hardcoded values to `application.yml`; add `@ConfigurationProperties` |
| R-903: Distributed TX → Compensating Actions | I-1009, I-0806 | Distributed transaction usage | Saga pattern templates | Remove 2PC dependencies | Implement compensation patterns; use Spring Boot messaging |

## Lifecycle and Performance Refactorings
| Rule | Triggers (Inspectors) | Dependencies/Data | Technology | Purpose | Implementation details |
|---|---|---|---|---|---|
| R-850: Stateful State → External Store | I-0905, I-1102 | Conversational state analysis | Spring Session + Redis/DB | Externalize session state | Move stateful bean state to Redis/database; add Spring Session |
| R-851: EJB Interceptors → Spring AOP | I-0906, I-0101 | Interceptor patterns, advice | OpenRewrite + AOP config | Convert to Spring AOP | Create `@Aspect` classes; replace EJB interceptors with `@Before/@After` advice |
| R-852: Entity Lifecycle → JPA Callbacks | I-0907, I-0201 | ejbLoad/ejbStore patterns | OpenRewrite | Convert entity callbacks | Replace with `@PrePersist/@PostLoad` JPA callbacks |
| R-853: Bean Pooling → Spring Scoping | I-0908, I-0501 | Pooling configurations | Spring scope configuration | Remove custom pooling | Use Spring singleton/prototype scoping; remove pooling configs |
| R-854: EJB Caching → Spring Cache | I-1109, I-0101 | Caching patterns, cache keys | Spring Cache + Redis/Caffeine | Modernize caching | Replace EJB caching with `@Cacheable/@CacheEvict` annotations |
| R-855: Connection Pooling → HikariCP | I-1110, I-0703 | DB connection patterns | HikariCP configuration | Modern connection pooling | Configure HikariCP in Boot; remove custom pooling code |
| R-856: Chatty Interface → Aggregate Operations | I-1111, I-0102 | N+1 patterns, remote calls | Manual refactoring + DTOs | Optimize remote calls | Batch operations; create aggregate DTOs; reduce round trips |
| R-857: Bulk Operations → Spring Batch/JPA | I-1112, I-0801 | Batch processing patterns | Spring Batch + JPA batch | Optimize bulk processing | Use JPA batch operations; consider Spring Batch for large datasets |

### Notes
- **Bedrock GenAI** is recommended to draft OpenRewrite visitors/codegen templates and explain failing diffs/tests, but keep the **deterministic codemods** as the canonical path.
- All rules should read inputs from `/build/jaa/manifest.json` produced by analyses **A-06/07/08/14** where applicable.
- **Graph Node Dependencies**: Many refactorings now require graph analysis to understand call relationships, service boundaries, and data flow patterns.
- **Phased Migration Strategy**: Complex refactorings (R-900 series) may require manual architectural decisions and should be executed with business stakeholder input.
