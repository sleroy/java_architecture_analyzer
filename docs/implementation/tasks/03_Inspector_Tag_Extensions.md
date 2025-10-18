# Task 3: Inspector Tag Extensions

**ID:** Task 3  
**Priority:** P0 (Critical - Foundation)  
**Estimated Effort:** 1-2 hours  
**Prerequisites:** None  
**Deliverables:** Extended InspectorTags with EJB constants  

## Overview

Extend the existing `InspectorTags` class to include comprehensive EJB-specific tags for migration analysis. These tags will be used by inspectors to classify and categorize EJB components, patterns, and migration concerns.

## Technical Requirements

### EJB Tag Categories to Implement

1. **EJB Component Tags** - Component type identification
2. **EJB Interface Tags** - Interface pattern identification  
3. **EJB Persistence Tags** - Persistence pattern classification
4. **EJB Transaction Tags** - Transaction pattern identification
5. **EJB Security Tags** - Security pattern classification
6. **EJB Lookup Tags** - JNDI and service location patterns
7. **EJB Migration Tags** - Migration priority and complexity
8. **EJB Vendor Tags** - Application server specific patterns

### Implementation Specifications

#### Current InspectorTags Structure Review

First, let's understand the current `InspectorTags` structure:

```java
// Current structure (to be extended)
public final class InspectorTags {
    // Existing standard tags
    public static final String SOURCE_FILE = "source_file";
    public static final String BINARY_FILE = "binary_file";
    public static final String JAVA_CLASS = "java_class";
    // ... other existing tags
}
```

#### Extended EJB Tags Implementation

```java
// File: src/main/java/com/analyzer/core/InspectorTags.java (additions)
public final class InspectorTags {
    // ... existing tags remain unchanged ...
    
    // ==========================================
    // EJB COMPONENT TAGS
    // ==========================================
    
    /** Tag for EJB Session Bean components */
    public static final String EJB_SESSION_BEAN = "ejb_session_bean";
    
    /** Tag for EJB Entity Bean components */
    public static final String EJB_ENTITY_BEAN = "ejb_entity_bean";
    
    /** Tag for EJB Message-Driven Bean components */
    public static final String EJB_MESSAGE_DRIVEN_BEAN = "ejb_message_driven_bean";
    
    /** Tag for Stateful Session Beans */
    public static final String EJB_STATEFUL_SESSION = "ejb_stateful_session";
    
    /** Tag for Stateless Session Beans */
    public static final String EJB_STATELESS_SESSION = "ejb_stateless_session";
    
    /** Tag for Container Managed Persistence Entity Beans */
    public static final String EJB_CMP_ENTITY = "ejb_cmp_entity";
    
    /** Tag for Bean Managed Persistence Entity Beans */
    public static final String EJB_BMP_ENTITY = "ejb_bmp_entity";
    
    // ==========================================
    // EJB INTERFACE TAGS
    // ==========================================
    
    /** Tag for EJB Home interfaces */
    public static final String EJB_HOME_INTERFACE = "ejb_home_interface";
    
    /** Tag for EJB Remote interfaces */
    public static final String EJB_REMOTE_INTERFACE = "ejb_remote_interface";
    
    /** Tag for EJB Local interfaces */
    public static final String EJB_LOCAL_INTERFACE = "ejb_local_interface";
    
    /** Tag for EJB Local Home interfaces */
    public static final String EJB_LOCAL_HOME_INTERFACE = "ejb_local_home_interface";
    
    /** Tag for Primary Key classes */
    public static final String EJB_PRIMARY_KEY_CLASS = "ejb_primary_key_class";
    
    // ==========================================
    // EJB PERSISTENCE TAGS
    // ==========================================
    
    /** Tag for CMP field mappings */
    public static final String EJB_CMP_FIELD = "ejb_cmp_field";
    
    /** Tag for CMR relationships */
    public static final String EJB_CMR_RELATIONSHIP = "ejb_cmr_relationship";
    
    /** Tag for EJB-QL queries */
    public static final String EJB_EJBQL_QUERY = "ejb_ejbql_query";
    
    /** Tag for EJB finder methods */
    public static final String EJB_FINDER_METHOD = "ejb_finder_method";
    
    /** Tag for direct JDBC usage in beans */
    public static final String EJB_DIRECT_JDBC = "ejb_direct_jdbc";
    
    /** Tag for EJB select methods */
    public static final String EJB_SELECT_METHOD = "ejb_select_method";
    
    // ==========================================
    // EJB TRANSACTION TAGS
    // ==========================================
    
    /** Tag for programmatic transaction management */
    public static final String EJB_PROGRAMMATIC_TX = "ejb_programmatic_tx";
    
    /** Tag for declarative transaction management */
    public static final String EJB_DECLARATIVE_TX = "ejb_declarative_tx";
    
    /** Tag for transaction attribute: Required */
    public static final String EJB_TX_REQUIRED = "ejb_tx_required";
    
    /** Tag for transaction attribute: RequiresNew */
    public static final String EJB_TX_REQUIRES_NEW = "ejb_tx_requires_new";
    
    /** Tag for transaction attribute: Supports */
    public static final String EJB_TX_SUPPORTS = "ejb_tx_supports";
    
    /** Tag for transaction attribute: NotSupported */
    public static final String EJB_TX_NOT_SUPPORTED = "ejb_tx_not_supported";
    
    /** Tag for transaction attribute: Never */
    public static final String EJB_TX_NEVER = "ejb_tx_never";
    
    /** Tag for transaction attribute: Mandatory */
    public static final String EJB_TX_MANDATORY = "ejb_tx_mandatory";
    
    /** Tag for UserTransaction usage */
    public static final String EJB_USER_TRANSACTION = "ejb_user_transaction";
    
    // ==========================================
    // EJB SECURITY TAGS
    // ==========================================
    
    /** Tag for EJB security roles */
    public static final String EJB_SECURITY_ROLE = "ejb_security_role";
    
    /** Tag for method-level security */
    public static final String EJB_METHOD_SECURITY = "ejb_method_security";
    
    /** Tag for security role references */
    public static final String EJB_ROLE_REFERENCE = "ejb_role_reference";
    
    /** Tag for programmatic security */
    public static final String EJB_PROGRAMMATIC_SECURITY = "ejb_programmatic_security";
    
    /** Tag for run-as security identity */
    public static final String EJB_RUN_AS_IDENTITY = "ejb_run_as_identity";
    
    // ==========================================
    // EJB LOOKUP AND NAMING TAGS
    // ==========================================
    
    /** Tag for JNDI lookups */
    public static final String EJB_JNDI_LOOKUP = "ejb_jndi_lookup";
    
    /** Tag for Service Locator pattern */
    public static final String EJB_SERVICE_LOCATOR = "ejb_service_locator";
    
    /** Tag for EJB references */
    public static final String EJB_REFERENCE = "ejb_reference";
    
    /** Tag for EJB local references */
    public static final String EJB_LOCAL_REFERENCE = "ejb_local_reference";
    
    /** Tag for resource references */
    public static final String EJB_RESOURCE_REFERENCE = "ejb_resource_reference";
    
    /** Tag for environment entries */
    public static final String EJB_ENV_ENTRY = "ejb_env_entry";
    
    // ==========================================
    // EJB LIFECYCLE TAGS
    // ==========================================
    
    /** Tag for EJB create methods */
    public static final String EJB_CREATE_METHOD = "ejb_create_method";
    
    /** Tag for EJB remove methods */
    public static final String EJB_REMOVE_METHOD = "ejb_remove_method";
    
    /** Tag for EJB activate/passivate methods */
    public static final String EJB_ACTIVATION_METHOD = "ejb_activation_method";
    
    /** Tag for EJB load/store methods */
    public static final String EJB_LOAD_STORE_METHOD = "ejb_load_store_method";
    
    /** Tag for EJB timer service usage */
    public static final String EJB_TIMER_SERVICE = "ejb_timer_service";
    
    // ==========================================
    // EJB MESSAGING TAGS
    // ==========================================
    
    /** Tag for JMS message destinations */
    public static final String EJB_JMS_DESTINATION = "ejb_jms_destination";
    
    /** Tag for message-driven bean activation config */
    public static final String EJB_MDB_ACTIVATION_CONFIG = "ejb_mdb_activation_config";
    
    /** Tag for JMS connection factories */
    public static final String EJB_JMS_CONNECTION_FACTORY = "ejb_jms_connection_factory";
    
    // ==========================================
    // EJB VENDOR-SPECIFIC TAGS
    // ==========================================
    
    /** Tag for WebLogic-specific configurations */
    public static final String EJB_WEBLOGIC_CONFIG = "ejb_weblogic_config";
    
    /** Tag for JBoss-specific configurations */
    public static final String EJB_JBOSS_CONFIG = "ejb_jboss_config";
    
    /** Tag for WebSphere-specific configurations */
    public static final String EJB_WEBSPHERE_CONFIG = "ejb_websphere_config";
    
    /** Tag for GlassFish-specific configurations */
    public static final String EJB_GLASSFISH_CONFIG = "ejb_glassfish_config";
    
    // ==========================================
    // EJB MIGRATION PRIORITY TAGS
    // ==========================================
    
    /** Tag for high-priority migration items */
    public static final String EJB_MIGRATION_HIGH_PRIORITY = "ejb_migration_high_priority";
    
    /** Tag for medium-priority migration items */
    public static final String EJB_MIGRATION_MEDIUM_PRIORITY = "ejb_migration_medium_priority";
    
    /** Tag for low-priority migration items */
    public static final String EJB_MIGRATION_LOW_PRIORITY = "ejb_migration_low_priority";
    
    /** Tag for migration-critical items */
    public static final String EJB_MIGRATION_CRITICAL = "ejb_migration_critical";
    
    /** Tag for migration-optional items */
    public static final String EJB_MIGRATION_OPTIONAL = "ejb_migration_optional";
    
    // ==========================================
    // EJB MIGRATION COMPLEXITY TAGS
    // ==========================================
    
    /** Tag for simple migration items */
    public static final String EJB_MIGRATION_SIMPLE = "ejb_migration_simple";
    
    /** Tag for complex migration items */
    public static final String EJB_MIGRATION_COMPLEX = "ejb_migration_complex";
    
    /** Tag for manual migration required */
    public static final String EJB_MIGRATION_MANUAL = "ejb_migration_manual";
    
    /** Tag for automated migration possible */
    public static final String EJB_MIGRATION_AUTOMATED = "ejb_migration_automated";
    
    // ==========================================
    // EJB ANTI-PATTERN TAGS
    // ==========================================
    
    /** Tag for stateful session bean anti-patterns */
    public static final String EJB_ANTI_PATTERN_STATEFUL = "ejb_anti_pattern_stateful";
    
    /** Tag for remote interface anti-patterns */
    public static final String EJB_ANTI_PATTERN_REMOTE = "ejb_anti_pattern_remote";
    
    /** Tag for entity bean anti-patterns */
    public static final String EJB_ANTI_PATTERN_ENTITY = "ejb_anti_pattern_entity";
    
    /** Tag for transaction anti-patterns */
    public static final String EJB_ANTI_PATTERN_TRANSACTION = "ejb_anti_pattern_transaction";
    
    // ==========================================
    // EJB CONFIGURATION TAGS
    // ==========================================
    
    /** Tag for ejb-jar.xml descriptor */
    public static final String EJB_DEPLOYMENT_DESCRIPTOR = "ejb_deployment_descriptor";
    
    /** Tag for vendor-specific descriptors */
    public static final String EJB_VENDOR_DESCRIPTOR = "ejb_vendor_descriptor";
    
    /** Tag for application.xml descriptor */
    public static final String EJB_APPLICATION_DESCRIPTOR = "ejb_application_descriptor";
    
    /** Tag for web.xml EJB references */
    public static final String EJB_WEB_REFERENCE = "ejb_web_reference";
    
    // ==========================================
    // EJB TAG UTILITY METHODS
    // ==========================================
    
    /**
     * Returns all EJB-related tags as a set.
     * Useful for filtering and categorization.
     */
    public static Set<String> getEJBTags() {
        return Set.of(
            // Component tags
            EJB_SESSION_BEAN, EJB_ENTITY_BEAN, EJB_MESSAGE_DRIVEN_BEAN,
            EJB_STATEFUL_SESSION, EJB_STATELESS_SESSION,
            EJB_CMP_ENTITY, EJB_BMP_ENTITY,
            
            // Interface tags
            EJB_HOME_INTERFACE, EJB_REMOTE_INTERFACE, EJB_LOCAL_INTERFACE,
            EJB_LOCAL_HOME_INTERFACE, EJB_PRIMARY_KEY_CLASS,
            
            // Persistence tags
            EJB_CMP_FIELD, EJB_CMR_RELATIONSHIP, EJB_EJBQL_QUERY,
            EJB_FINDER_METHOD, EJB_DIRECT_JDBC, EJB_SELECT_METHOD,
            
            // Transaction tags
            EJB_PROGRAMMATIC_TX, EJB_DECLARATIVE_TX,
            EJB_TX_REQUIRED, EJB_TX_REQUIRES_NEW, EJB_TX_SUPPORTS,
            EJB_TX_NOT_SUPPORTED, EJB_TX_NEVER, EJB_TX_MANDATORY,
            EJB_USER_TRANSACTION,
            
            // Security tags
            EJB_SECURITY_ROLE, EJB_METHOD_SECURITY, EJB_ROLE_REFERENCE,
            EJB_PROGRAMMATIC_SECURITY, EJB_RUN_AS_IDENTITY,
            
            // Lookup tags
            EJB_JNDI_LOOKUP, EJB_SERVICE_LOCATOR, EJB_REFERENCE,
            EJB_LOCAL_REFERENCE, EJB_RESOURCE_REFERENCE, EJB_ENV_ENTRY,
            
            // Lifecycle tags
            EJB_CREATE_METHOD, EJB_REMOVE_METHOD, EJB_ACTIVATION_METHOD,
            EJB_LOAD_STORE_METHOD, EJB_TIMER_SERVICE,
            
            // Messaging tags
            EJB_JMS_DESTINATION, EJB_MDB_ACTIVATION_CONFIG, EJB_JMS_CONNECTION_FACTORY,
            
            // Vendor tags
            EJB_WEBLOGIC_CONFIG, EJB_JBOSS_CONFIG, EJB_WEBSPHERE_CONFIG, EJB_GLASSFISH_CONFIG,
            
            // Migration tags
            EJB_MIGRATION_HIGH_PRIORITY, EJB_MIGRATION_MEDIUM_PRIORITY, EJB_MIGRATION_LOW_PRIORITY,
            EJB_MIGRATION_CRITICAL, EJB_MIGRATION_OPTIONAL,
            EJB_MIGRATION_SIMPLE, EJB_MIGRATION_COMPLEX,
            EJB_MIGRATION_MANUAL, EJB_MIGRATION_AUTOMATED,
            
            // Anti-pattern tags
            EJB_ANTI_PATTERN_STATEFUL, EJB_ANTI_PATTERN_REMOTE,
            EJB_ANTI_PATTERN_ENTITY, EJB_ANTI_PATTERN_TRANSACTION,
            
            // Configuration tags
            EJB_DEPLOYMENT_DESCRIPTOR, EJB_VENDOR_DESCRIPTOR,
            EJB_APPLICATION_DESCRIPTOR, EJB_WEB_REFERENCE
        );
    }
    
    /**
     * Returns EJB component type tags.
     */
    public static Set<String> getEJBComponentTags() {
        return Set.of(
            EJB_SESSION_BEAN, EJB_ENTITY_BEAN, EJB_MESSAGE_DRIVEN_BEAN,
            EJB_STATEFUL_SESSION, EJB_STATELESS_SESSION,
            EJB_CMP_ENTITY, EJB_BMP_ENTITY
        );
    }
    
    /**
     * Returns EJB migration priority tags.
     */
    public static Set<String> getEJBMigrationPriorityTags() {
        return Set.of(
            EJB_MIGRATION_HIGH_PRIORITY, EJB_MIGRATION_MEDIUM_PRIORITY,
            EJB_MIGRATION_LOW_PRIORITY, EJB_MIGRATION_CRITICAL, EJB_MIGRATION_OPTIONAL
        );
    }
    
    /**
     * Returns EJB transaction-related tags.
     */
    public static Set<String> getEJBTransactionTags() {
        return Set.of(
            EJB_PROGRAMMATIC_TX, EJB_DECLARATIVE_TX,
            EJB_TX_REQUIRED, EJB_TX_REQUIRES_NEW, EJB_TX_SUPPORTS,
            EJB_TX_NOT_SUPPORTED, EJB_TX_NEVER, EJB_TX_MANDATORY,
            EJB_USER_TRANSACTION
        );
    }
    
    // Private constructor to prevent instantiation
    private InspectorTags() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
```

## Implementation Tasks

### Step 1: Extend InspectorTags Class (45-60 minutes)
1. Open `src/main/java/com/analyzer/core/InspectorTags.java`
2. Add all EJB-specific tag constants as shown above
3. Add utility methods for tag categorization
4. Ensure proper documentation for each tag

### Step 2: Update Tag Validation (15-30 minutes)
1. Update any tag validation logic to include new EJB tags
2. Add tests for tag categorization methods
3. Ensure tag naming consistency follows project conventions

### Step 3: Integration Testing (15-30 minutes)
1. Create unit tests for new utility methods
2. Test tag categorization functionality
3. Validate tag constant values are unique

## File Structure

```
src/main/java/com/analyzer/core/
└── InspectorTags.java                  # Extended with EJB tags

src/test/java/com/analyzer/core/
└── InspectorTagsTest.java              # Tests for new tag functionality
```

## Testing Requirements

### Unit Tests
- Test that all EJB tags are unique
- Test utility method functionality
- Test tag categorization methods
- Validate tag naming conventions

### Integration Tests
- Test that inspectors can use new tags
- Test tag filtering and categorization in analysis results

## Success Criteria

- [ ] All 70+ EJB tags implemented and documented
- [ ] Tag utility methods provide proper categorization
- [ ] No duplicate tag values exist
- [ ] All new functionality is unit tested
- [ ] Integration with existing tag system is seamless

## Implementation Prompt

Use this specification to extend the `InspectorTags` class with comprehensive EJB support. Focus on:

1. **Complete Coverage**: Include tags for all EJB patterns and migration concerns
2. **Clear Categorization**: Organize tags logically by functional area
3. **Migration-Aware Design**: Include priority and complexity tags for migration planning
4. **Vendor Support**: Include tags for major application server vendors
5. **Extensibility**: Design tag structure to support future EJB patterns

The tags should enable fine-grained classification of EJB components and patterns, supporting both detection and migration analysis phases.

## Tag Usage Examples

```java
// Example inspector usage
@Override
public InspectorResult inspect(ProjectFile file) {
    if (isStatefulSessionBean(file)) {
        return InspectorResult.builder()
            .addTag(InspectorTags.EJB_SESSION_BEAN)
            .addTag(InspectorTags.EJB_STATEFUL_SESSION)
            .addTag(InspectorTags.EJB_MIGRATION_HIGH_PRIORITY)
            .build();
    }
    return InspectorResult.empty();
}

// Example migration analysis
public boolean requiresManualMigration(Set<String> tags) {
    return tags.contains(InspectorTags.EJB_MIGRATION_MANUAL) ||
           tags.contains(InspectorTags.EJB_ANTI_PATTERN_STATEFUL);
}
```

The extended tag system will be essential for all Phase 1 inspectors and provide the foundation for migration complexity analysis.
