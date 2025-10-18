# Inspectors Catalog (Detection)

> **Goal:** detectors to inventory an EJB2 codebase and prep for Spring Boot migration.

Columns: **ID**, **Inspector**, **Purpose**, **Depends on**, **Technology**

| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0001 | identify_source_root | Locate repo roots to scan | — | binary_with_asm (N/A), java_parsing |
| I-0002 | identify_module | Find Maven/Gradle modules | I-0001 | java_parsing |
| I-0003 | identify_java_file | Index `**/*.java` with package | I-0002 | java_parsing |
| I-0004 | identify_descriptor_file | Index XML: `ejb-jar.xml`, vendor XML, `web.xml`, `application.xml`, CMP maps | I-0002 | java_parsing |
| I-0005 | identify_resources_file | Index `.properties`, `.yml`, `jndi.properties` | I-0002 | java_parsing |

## EJB Session Beans
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0101 | identify_session_bean_impl | Find classes implementing `javax.ejb.SessionBean` or EJB2 lifecycle methods | I-0003 | binary_with_asm (preferred), java_parsing |
| I-0102 | identify_remote_interface | Find interfaces extending `javax.ejb.EJBObject` | I-0003 | binary_with_asm, java_parsing |
| I-0103 | identify_local_interface | Find interfaces extending `javax.ejb.EJBLocalObject` | I-0003 | binary_with_asm, java_parsing |
| I-0104 | identify_home_interface | Find interfaces extending `javax.ejb.EJBHome`/`EJBLocalHome` | I-0003 | binary_with_asm, java_parsing |
| I-0105 | identify_session_context_usage | Detect `SessionContext` usages | I-0101 | binary_with_asm, java_parsing |

## EJB Entity Beans
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0201 | identify_entity_bean_impl | Find `javax.ejb.EntityBean`/lifecycle methods | I-0003 | binary_with_asm, java_parsing |
| I-0202 | identify_primary_key_class | Detect PK classes used by entity beans | I-0201 | binary_with_asm, java_parsing |
| I-0203 | identify_cmp_mapping_file | Detect vendor CMP mapping files | I-0004 | java_parsing |
| I-0204 | identify_cmr_relationship | Resolve CMR relations | I-0201, I-0203, I-0401 | java_parsing |
| I-0205 | identify_direct_jdbc_in_entity | Detect JDBC in entity beans (BMP) | I-0201 | binary_with_asm, java_parsing |

## Message-Driven Beans
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0301 | identify_mdb_impl | Find `MessageDrivenBean` / `onMessage` | I-0003 | binary_with_asm, java_parsing |
| I-0302 | identify_mdb_activation_config | Extract destination/type/ack from descriptors | I-0401/0501 | java_parsing |

## Descriptors (Portable)
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0401 | identify_ejb_jar_descriptor | Parse `META-INF/ejb-jar.xml` | I-0004 | java_parsing |
| I-0402 | identify_assembly_descriptor | Parse `<assembly-descriptor>` | I-0401 | java_parsing |
| I-0403 | identify_method_transaction_attribute | Extract tx per (bean,method) | I-0402, beans | java_parsing |
| I-0404 | identify_method_security_role | Extract roles per (bean,method) | I-0402, beans | java_parsing |

## Descriptors (Vendor)
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0501 | identify_vendor_ejb_descriptor | Detect vendor EJB XML (WebLogic/JBoss/GlassFish/WebSphere) | I-0004 | java_parsing |
| I-0502 | identify_vendor_jndi_bindings | Map per-bean JNDI bindings | I-0501 | java_parsing |

## Web & EAR
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0601 | identify_ear_application_xml | Parse module list | I-0004 | java_parsing |
| I-0602 | identify_web_xml | Parse servlets/refs | I-0004 | java_parsing |
| I-0603 | identify_ejb_ref_in_webxml | Resolve `<ejb-ref>` → beans | I-0602, I-0101 | java_parsing |

## Lookups & Resources
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0701 | identify_jndi_lookup | Find `InitialContext.lookup(...)` | I-0003 | binary_with_asm, java_parsing |
| I-0702 | identify_service_locator_pattern | Detect Service Locator wrapping JNDI | I-0701 | java_parsing |
| I-0703 | identify_datasource_ref | Extract JDBC/JTA resources | I-0401/0602/0501 | java_parsing |
| I-0704 | identify_jms_destination_ref | Extract JMS Queue/Topic refs | I-0401/0602/0501 | java_parsing |
| I-0705 | identify_env_entry | Extract `<env-entry>` | I-0401/0602 | java_parsing |

## Persistence & Queries
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0801 | identify_ejbql_query | Extract EJB-QL queries | I-0401/0501 | java_parsing |
| I-0802 | identify_finder_method | Map `find*` methods to queries | I-0104/0103, I-0201, I-0801 | java_parsing |
| I-0803 | identify_transaction_api_usage | Find `UserTransaction` / `setRollbackOnly` | I-0003 | binary_with_asm, java_parsing |

## Timers, Interceptors, Security
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0901 | identify_ejb_timer_usage | Find `TimerService` usage | I-0101 | binary_with_asm, java_parsing |
| I-0902 | identify_vendor_timer_config | Extract vendor timer config | I-0501 | java_parsing |
| I-0903 | identify_container_callback_methods | List `ejbActivate/Passivate`, `ejbLoad/Store`, etc. | I-0101/0201/0301 | binary_with_asm, java_parsing |
| I-0904 | identify_declared_security_role | Catalog `<security-role>` | I-0402 or I-0602 | java_parsing |

## Build & APIs
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-1001 | identify_build_descriptor | Parse POM/Gradle & Java level | I-0002 | java_parsing |
| I-1002 | identify_legacy_appserver_dependency | Detect JEE/app-server artifacts | I-1001 | java_parsing |
| I-1003 | identify_javax_usage | Find `javax.*` imports | I-0003 | java_parsing, binary_with_asm |
| I-1004 | identify_jakarta_ready | Mark modules free of `javax.*` | I-1003 | java_parsing |

## Container Managed Persistence (CMP) Deep Analysis
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0206 | identify_cmp_field_mapping | Extract field-to-column mappings from CMP descriptors | I-0203, I-0501 | java_parsing |
| I-0207 | identify_cmp_relationship_cardinality | Analyze one-to-many, many-to-many CMR relationships | I-0204, I-0203 | java_parsing |
| I-0208 | identify_cmp_query_method | Parse finder method implementations and EJB-QL | I-0801, I-0802 | java_parsing |
| I-0209 | identify_cmp_primary_key_composite | Handle complex/composite primary key classes | I-0202, I-0201 | java_parsing, binary_with_asm |
| I-0210 | identify_cmp_cascade_operations | Detect cascade delete/update patterns | I-0204, I-0203 | java_parsing |

## Transaction Boundary Analysis
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0804 | identify_programmatic_transaction_usage | Detect `UserTransaction` patterns beyond basic API usage | I-0803 | binary_with_asm, java_parsing |
| I-0805 | identify_container_managed_transaction_attrs | Map method-level transaction attributes from descriptors | I-0403, I-0401 | java_parsing |
| I-0806 | identify_jta_datasource_usage | Identify XA-capable data sources and JTA patterns | I-0703, I-0501 | java_parsing |
| I-0807 | identify_transaction_rollback_patterns | Find `setRollbackOnly()` and rollback handling | I-0105, I-0803 | binary_with_asm, java_parsing |
| I-0808 | identify_nested_transaction_context | Detect nested transaction scopes and boundaries | I-0403, I-0101 | java_parsing |

## EJB Client Code Detection
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0706 | identify_ejb_create_method_usage | Find `home.create()` patterns and parameters | I-0104, I-0701 | binary_with_asm, java_parsing |
| I-0707 | identify_ejb_remove_method_usage | Detect EJB lifecycle management calls | I-0102, I-0103 | binary_with_asm, java_parsing |
| I-0708 | identify_ejb_handle_reference_usage | Find serializable EJB references/handles | I-0102, I-0103 | binary_with_asm, java_parsing |
| I-0709 | identify_business_delegate_pattern | Identify client-side EJB access patterns | I-0701, I-0702 | java_parsing |
| I-0710 | identify_ejb_passivation_activation | Detect stateful bean passivation/activation logic | I-0101, I-1101 | binary_with_asm, java_parsing |

## Advanced Persistence Patterns
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0211 | identify_bmp_jdbc_patterns | Detect Bean Managed Persistence with raw JDBC | I-0205, I-0201 | binary_with_asm, java_parsing |
| I-0212 | identify_data_access_object_pattern | Find DAO implementations and patterns | I-0003, I-0205 | java_parsing |
| I-0213 | identify_value_object_pattern | Identify transfer objects/DTOs for EJBs | I-0102, I-0103 | java_parsing, binary_with_asm |
| I-0214 | identify_lazy_loading_pattern | Detect lazy loading and performance patterns | I-0201, I-0204 | binary_with_asm, java_parsing |
| I-0215 | identify_optimistic_locking | Find version fields and optimistic concurrency | I-0201, I-0203 | java_parsing |

## Application Server Configuration (Extended)
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0503 | identify_weblogic_ejb_descriptor | Parse `weblogic-ejb-jar.xml` and WebLogic-specific configs | I-0004 | java_parsing |
| I-0504 | identify_websphere_ejb_descriptor | Handle IBM WebSphere EJB bindings and configs | I-0004 | java_parsing |
| I-0505 | identify_glassfish_ejb_descriptor | Parse Sun/Oracle GlassFish descriptor files | I-0004 | java_parsing |
| I-0506 | identify_generic_vendor_binding | Extensible vendor-specific EJB configuration detection | I-0501 | java_parsing |
| I-0507 | identify_clustering_configuration | Detect EJB clustering and load balancing configs | I-0501, I-0503, I-0504 | java_parsing |

## Modern Migration Concerns
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-1005 | identify_javax_to_jakarta_migration_needs | Detect `javax.*` to `jakarta.*` migration requirements | I-1003 | java_parsing, binary_with_asm |
| I-1006 | identify_microservice_boundary_candidates | Identify service decomposition candidates | I-0101, I-0201, I-0301 | java_parsing + graph analysis |
| I-1007 | identify_cloud_native_incompatible_patterns | Find patterns incompatible with containers/cloud | I-1104, I-1103, I-0901 | binary_with_asm, java_parsing |
| I-1008 | identify_configuration_externalization_needs | Detect hardcoded environment values and configs | I-0705, I-0501 | java_parsing, binary_with_asm |
| I-1009 | identify_distributed_transaction_usage | Find distributed transaction patterns (2PC, etc.) | I-0806, I-0501 | java_parsing |

## EJB Lifecycle and Callbacks (Enhanced)
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-0905 | identify_stateful_bean_conversational_state | Analyze stateful session bean state management | I-1101, I-1102 | binary_with_asm, java_parsing |
| I-0906 | identify_ejb_interceptor_usage | Detect EJB interceptor patterns (EJB 3.0+) | I-0101, I-0201 | binary_with_asm, java_parsing |
| I-0907 | identify_entity_bean_lifecycle_methods | Map ejbLoad, ejbStore, ejbCreate patterns | I-0201, I-0903 | binary_with_asm, java_parsing |
| I-0908 | identify_session_bean_pooling_hints | Detect bean pooling configuration and usage | I-0101, I-0501 | java_parsing |

## Performance and Caching Patterns
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-1109 | identify_ejb_caching_patterns | Detect EJB-level caching implementations | I-0101, I-0201 | binary_with_asm, java_parsing |
| I-1110 | identify_database_connection_patterns | Analyze DB connection usage and pooling | I-0703, I-0205 | binary_with_asm, java_parsing |
| I-1111 | identify_remote_interface_performance_issues | Find chatty interfaces and N+1 patterns | I-0102, call graph analysis | java_parsing + graph analysis |
| I-1112 | identify_bulk_operation_patterns | Detect batch processing and bulk operations | I-0201, I-0801 | java_parsing |

## Mutability & Concurrency
| ID | Inspector | Purpose | Depends on | Technology |
|---|---|---|---|---|
| I-1100 | identify_mutable_service_state | Non-final instance fields / collections | I-0101 or candidate services | binary_with_asm (preferred), java_parsing |
| I-1101 | identify_stateful_ejb_candidate | `<session-type>Stateful` | I-0401 | java_parsing |
| I-1102 | identify_cross_method_state_flow | Field write→read across methods | I-0003 | binary_with_asm + java_parsing |
| I-1103 | identify_threadlocal_usage | `ThreadLocal` usage | I-0003 | binary_with_asm, java_parsing |
| I-1104 | identify_static_mutable_singleton | Static non-final mutable fields | I-0003 | binary_with_asm |
| I-1105 | identify_non_threadsafe_util_usage | `SimpleDateFormat`, etc. | I-0003 | binary_with_asm, java_parsing |
| I-1106 | identify_synchronization_patterns | Synchronized methods/blocks | I-0003 | binary_with_asm |
| I-1107 | identify_session_scope_assumptions | Web session state reliance | I-0602 | java_parsing |
| I-1108 | identify_lazy_singletons_and_caches | DCL and ad-hoc caches | I-0003 | binary_with_asm, java_parsing |

> **GenAI with Bedrock** is not used for detection (to keep it deterministic), but can generate detector rules or help triage findings.
