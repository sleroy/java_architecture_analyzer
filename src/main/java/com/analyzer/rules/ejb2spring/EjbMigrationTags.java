package com.analyzer.rules.ejb2spring;

/**
 * EJB-specific tags for migration analysis.
 * This class contains all tags related to EJB components, patterns, and
 * migration scenarios.
 *
 * <p>
 * These tags are used by EJB inspectors in the ejb2spring package to classify
 * and analyze EJB components for Spring migration.
 * </p>
 *
 * <p>
 * Tag naming conventions:
 * <ul>
 * <li>Use dot notation for hierarchical organization (e.g.,
 * "ejb.cmp.fieldMapping")</li>
 * <li>Use camelCase for the constant name</li>
 * <li>Group related tags together with comments</li>
 * </ul>
 * </p>
 */
public final class EjbMigrationTags {

    public static final String EJB_BEAN_DETECTED = "ejb.bean_detected";

    // Prevent instantiation
    private EjbMigrationTags() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // ==================== EJB COMPONENT TYPE TAGS ====================

    /**
     * Tag for EJB session bean components
     */
    public static final String EJB_SESSION_BEAN = "ejb.sessionBean";

    /**
     * Tag for EJB stateless session bean components
     */
    public static final String EJB_STATELESS_SESSION_BEAN = "ejb.stateless.sessionBean";

    /**
     * Tag for EJB stateful session bean components
     */
    public static final String EJB_STATEFUL_SESSION_BEAN = "ejb.stateful.sessionBean";

    /**
     * Tag for EJB entity bean components
     */
    public static final String EJB_ENTITY_BEAN = "ejb.entityBean";

    /**
     * Tag for EJB CMP entity bean components
     */
    public static final String EJB_CMP_ENTITY = "ejb.cmp.entityBean";

    /**
     * Tag for EJB BMP entity bean components
     */
    public static final String EJB_BMP_ENTITY = "ejb.bmp.entityBean";

    /**
     * Tag for EJB message-driven bean components
     */
    public static final String EJB_MESSAGE_DRIVEN_BEAN = "ejb.messageDrivenBean";

    // ==================== EJB INTERFACE TAGS ====================

    /**
     * Tag for EJB home interface components
     */
    public static final String EJB_HOME_INTERFACE = "ejb.homeInterface";

    /**
     * Tag for EJB remote interface components
     */
    public static final String EJB_REMOTE_INTERFACE = "ejb.remoteInterface";

    /**
     * Tag for EJB local interface components
     */
    public static final String EJB_LOCAL_INTERFACE = "ejb.localInterface";

    /**
     * Tag for EJB local home interface components
     */
    public static final String EJB_LOCAL_HOME_INTERFACE = "ejb.localHomeInterface";

    // ==================== CMP AND PERSISTENCE TAGS ====================

    /**
     * Tag for CMP field mapping metadata
     */
    public static final String EJB_CMP_FIELD_MAPPING = "ejb.cmp.fieldMapping";

    /**
     * Tag for CMP field components
     */
    public static final String EJB_CMP_FIELD = "ejb.cmp.field";

    /**
     * Tag for CMR relationship components
     */
    public static final String EJB_CMR_RELATIONSHIP = "ejb.cmr.relationship";

    /**
     * Tag for complex CMR relationship patterns
     */
    public static final String EJB_COMPLEX_RELATIONSHIP = "ejb.cmr.complex";

    /**
     * Tag for bidirectional CMR relationships
     */
    public static final String EJB_BIDIRECTIONAL_RELATIONSHIP = "ejb.cmr.bidirectional";

    /**
     * Tag for cascade operation patterns in CMR
     */
    public static final String EJB_CASCADE_OPERATIONS = "ejb.cmr.cascade";

    /**
     * Tag for primary key class components
     */
    public static final String EJB_PRIMARY_KEY_CLASS = "ejb.primaryKey";

    /**
     * Tag for composite primary key patterns
     */
    public static final String EJB_COMPOSITE_PRIMARY_KEY = "ejb.primaryKey.composite";

    /**
     * Tag for persistence metadata analysis
     */
    public static final String PERSISTENCE_METADATA = "persistence.metadata";

    /**
     * Tag for persistence layer components
     */
    public static final String PERSISTENCE_LAYER = "persistence.layer";

    // ==================== TRANSACTION MANAGEMENT TAGS ====================

    /**
     * Tag for programmatic transaction usage
     */
    public static final String EJB_PROGRAMMATIC_TRANSACTION = "ejb.transaction.programmatic";

    /**
     * Tag for declarative transaction attributes
     */
    public static final String EJB_DECLARATIVE_TRANSACTION = "ejb.transaction.declarative";

    /**
     * Tag for container-managed transactions
     */
    public static final String EJB_CONTAINER_MANAGED_TRANSACTION = "ejb.transaction.containerManaged";

    /**
     * Tag for bean-managed transactions
     */
    public static final String EJB_BEAN_MANAGED_TRANSACTION = "ejb.transaction.beanManaged";

    /**
     * Tag for transaction boundary analysis
     */
    public static final String TRANSACTION_BOUNDARY = "transaction.boundary";

    /**
     * Tag for XA transaction patterns
     */
    public static final String EJB_XA_TRANSACTION = "ejb.transaction.xa";

    // ==================== STATE MANAGEMENT TAGS ====================

    /**
     * Tag for stateful session bean conversational state
     */
    public static final String EJB_STATEFUL_STATE = "ejb.stateful.conversationalState";

    /**
     * Tag for stateful session bean state analysis
     */
    public static final String STATEFUL_SESSION_STATE = "ejb.stateful.sessionState";

    /**
     * Tag for conversational state patterns
     */
    public static final String CONVERSATIONAL_STATE = "ejb.conversationalState";

    /**
     * Tag for cross-method state dependencies
     */
    public static final String CROSS_METHOD_DEPENDENCY = "ejb.crossMethodDependency";

    /**
     * Tag for stateful session bean state management patterns
     */
    public static final String EJB_STATE_MANAGEMENT = "ejb.stateful.stateManagement";

    /**
     * Tag for session synchronization patterns
     */
    public static final String EJB_SESSION_SYNCHRONIZATION = "ejb.stateful.sessionSynchronization";

    // ==================== CLIENT PATTERN TAGS ====================

    /**
     * Tag for EJB create method patterns
     */
    public static final String EJB_CREATE_METHOD = "ejb.createMethod";

    /**
     * Tag for EJB create method usage in client code
     */
    public static final String EJB_CREATE_METHOD_USAGE = "ejb.client.createMethod";

    /**
     * Tag for parameterized create methods
     */
    public static final String EJB_PARAMETERIZED_CREATE = "ejb.createMethod.parameterized";

    /**
     * Tag for complex initialization patterns in create methods
     */
    public static final String EJB_COMPLEX_INITIALIZATION = "ejb.createMethod.complexInitialization";

    /**
     * Tag for EJB client code patterns
     */
    public static final String EJB_CLIENT_CODE = "ejb.client.code";

    /**
     * Tag for dependency injection candidate patterns
     */
    public static final String EJB_DEPENDENCY_INJECTION_CANDIDATE = "ejb.client.dependencyInjectionCandidate";

    /**
     * Tag for business delegate pattern
     */
    public static final String EJB_BUSINESS_DELEGATE = "ejb.client.businessDelegate";

    /**
     * Tag for service locator pattern
     */
    public static final String EJB_SERVICE_LOCATOR = "ejb.client.serviceLocator";

    /**
     * Tag for JNDI lookup patterns
     */
    public static final String EJB_JNDI_LOOKUP = "ejb.client.jndiLookup";

    // ==================== LIFECYCLE MANAGEMENT TAGS ====================

    /**
     * Tag for EJB lifecycle methods (ejbCreate, ejbRemove, etc.)
     */
    public static final String EJB_LIFECYCLE_METHODS = "ejb.lifecycle.methods";

    /**
     * Tag for entity bean lifecycle patterns
     */
    public static final String EJB_ENTITY_LIFECYCLE = "ejb.entity.lifecycle";

    /**
     * Tag for session bean lifecycle patterns
     */
    public static final String EJB_SESSION_LIFECYCLE = "ejb.session.lifecycle";

    // ==================== VENDOR-SPECIFIC TAGS ====================

    /**
     * Tag for WebLogic-specific EJB configurations
     */
    public static final String VENDOR_SPECIFIC_WEBLOGIC = "ejb.vendor.weblogic";

    /**
     * Tag for WebSphere-specific EJB configurations
     */
    public static final String VENDOR_SPECIFIC_WEBSPHERE = "ejb.vendor.websphere";

    /**
     * Tag for JBoss-specific EJB configurations
     */
    public static final String VENDOR_SPECIFIC_JBOSS = "ejb.vendor.jboss";

    /**
     * Tag for Oracle AS-specific EJB configurations
     */
    public static final String VENDOR_SPECIFIC_ORACLE = "ejb.vendor.oracle";

    // ==================== MIGRATION COMPLEXITY TAGS ====================

    /**
     * Tag for simple migration scenarios
     */
    public static final String MIGRATION_COMPLEXITY_LOW = "migration.complexity.low";

    /**
     * Tag for moderate migration scenarios
     */
    public static final String MIGRATION_COMPLEXITY_MEDIUM = "migration.complexity.medium";

    /**
     * Tag for complex migration scenarios
     */
    public static final String MIGRATION_COMPLEXITY_HIGH = "migration.complexity.high";

    /**
     * Tag for migration blockers or very complex scenarios
     */
    public static final String MIGRATION_COMPLEXITY_CRITICAL = "migration.complexity.critical";

    /**
     * Tag for high-priority migration targets
     */
    public static final String EJB_MIGRATION_HIGH_PRIORITY = "ejb.migration.highPriority";

    /**
     * Tag for medium-priority migration targets
     */
    public static final String EJB_MIGRATION_MEDIUM_PRIORITY = "ejb.migration.mediumPriority";

    /**
     * Tag for simple migration patterns
     */
    public static final String EJB_MIGRATION_SIMPLE = "ejb.migration.simple";

    /**
     * Tag for medium complexity migration patterns
     */
    public static final String EJB_MIGRATION_MEDIUM = "ejb.migration.medium";

    /**
     * Tag for complex migration patterns
     */
    public static final String EJB_MIGRATION_COMPLEX = "ejb.migration.complex";

    // ==================== JPA CONVERSION TAGS ====================

    /**
     * Tag for JPA conversion candidate components
     */
    public static final String JPA_CONVERSION_CANDIDATE = "jpa.conversion.candidate";

    /**
     * Tag for JPA entity conversion targets
     */
    public static final String JPA_ENTITY_CONVERSION = "jpa.conversion.entity";

    /**
     * Tag for JPA repository conversion targets
     */
    public static final String JPA_REPOSITORY_CONVERSION = "jpa.conversion.repository";

    // ==================== SPRING CONVERSION TAGS ====================

    /**
     * Tag for Spring service conversion targets
     */
    public static final String SPRING_SERVICE_CONVERSION = "spring.conversion.service";

    /**
     * Tag for Spring component conversion targets
     */
    public static final String SPRING_COMPONENT_CONVERSION = "spring.conversion.component";

    /**
     * Tag for Spring transaction conversion targets
     */
    public static final String SPRING_TRANSACTION_CONVERSION = "spring.conversion.transaction";

    /**
     * Tag for Spring configuration conversion targets
     */
    public static final String SPRING_CONFIG_CONVERSION = "spring.conversion.configuration";

    /**
     * Tag for Spring scope migration targets
     */
    public static final String SPRING_SCOPE_MIGRATION = "spring.conversion.scope";

    // ==================== REFACTORING TAGS ====================

    /**
     * Tag for refactoring target identification
     */
    public static final String REFACTORING_TARGET = "refactoring.target";

    /**
     * Tag for code modernization opportunities
     */
    public static final String CODE_MODERNIZATION = "refactoring.modernization";

    /**
     * Tag for architecture improvement opportunities
     */
    public static final String ARCHITECTURE_IMPROVEMENT = "refactoring.architecture";

    // ==================== DEPLOYMENT DESCRIPTOR TAGS ====================

    /**
     * Tag for ejb-jar.xml deployment descriptor
     */
    public static final String EJB_DEPLOYMENT_DESCRIPTOR = "ejb.deployment.descriptor";

    /**
     * Tag for vendor-specific deployment descriptors
     */
    public static final String VENDOR_DEPLOYMENT_DESCRIPTOR = "ejb.deployment.vendor";

    /**
     * Tag for EJB security configuration
     */
    public static final String EJB_SECURITY_CONFIG = "ejb.security.configuration";

    // ==================== PERFORMANCE AND OPTIMIZATION TAGS ====================

    /**
     * Tag for EJB pooling strategies
     */
    public static final String EJB_POOLING_STRATEGY = "ejb.performance.pooling";

    /**
     * Tag for EJB caching patterns
     */
    public static final String EJB_CACHING_PATTERN = "ejb.performance.caching";

    /**
     * Tag for lazy loading patterns
     */
    public static final String EJB_LAZY_LOADING = "ejb.performance.lazyLoading";

    /**
     * Tag for batch processing patterns
     */
    public static final String EJB_BATCH_PROCESSING = "ejb.performance.batch";

    // ==================== MESSAGING TAGS ====================

    /**
     * Tag for JMS integration patterns
     */
    public static final String EJB_JMS_INTEGRATION = "ejb.messaging.jms";

    /**
     * Tag for message queue patterns
     */
    public static final String EJB_MESSAGE_QUEUE = "ejb.messaging.queue";

    /**
     * Tag for topic-based messaging patterns
     */
    public static final String EJB_MESSAGE_TOPIC = "ejb.messaging.topic";

    // ==================== JDBC AND DATA ACCESS PATTERNS ====================

    /**
     * Tag for direct JDBC Connection usage patterns
     */
    public static final String JDBC_CONNECTION_USAGE = "jdbc.connection.usage";

    /**
     * Tag for JDBC PreparedStatement usage patterns
     */
    public static final String JDBC_PREPARED_STATEMENT = "jdbc.preparedStatement.usage";

    /**
     * Tag for JDBC ResultSet processing patterns
     */
    public static final String JDBC_RESULT_SET_PROCESSING = "jdbc.resultSet.processing";

    /**
     * Tag for JDBC Statement usage patterns
     */
    public static final String JDBC_STATEMENT_USAGE = "jdbc.statement.usage";

    /**
     * Tag for JDBC CallableStatement usage patterns
     */
    public static final String JDBC_CALLABLE_STATEMENT = "jdbc.callableStatement.usage";

    /**
     * Tag for SQL query patterns in JDBC code
     */
    public static final String JDBC_SQL_QUERY_PATTERN = "jdbc.sql.query";

    /**
     * Tag for parameterized SQL queries
     */
    public static final String JDBC_PARAMETERIZED_QUERY = "jdbc.sql.parameterized";

    /**
     * Tag for JDBC batch processing patterns
     */
    public static final String JDBC_BATCH_PROCESSING = "jdbc.batch.processing";

    /**
     * Tag for data access layer components using JDBC
     */
    public static final String DATA_ACCESS_LAYER = "dataAccess.layer";

    /**
     * Tag for Data Access Object (DAO) pattern implementations
     */
    public static final String DATA_ACCESS_OBJECT_PATTERN = "dataAccess.dao.pattern";

    /**
     * Tag for Data Transfer Object patterns used with JDBC
     */
    public static final String DATA_TRANSFER_OBJECT = "dataAccess.dto.pattern";

    /**
     * Tag for Value Object patterns for data transfer
     */
    public static final String VALUE_OBJECT = "dataAccess.vo.pattern";

    /**
     * Tag for manual object mapping from ResultSet
     */
    public static final String MANUAL_OBJECT_MAPPING = "dataAccess.manual.mapping";

    /**
     * Tag for custom mapping logic implementations
     */
    public static final String MAPPING_CUSTOM_LOGIC = "dataAccess.mapping.custom";

    /**
     * Tag for manual ResultSet to object mapping patterns
     */
    public static final String MAPPING_RESULTSET_MANUAL = "dataAccess.mapping.resultset";

    /**
     * Tag for JDBC usage with DTO patterns
     */
    public static final String JDBC_DTO_USAGE = "jdbc.dto.usage";

    /**
     * Tag for ResultSet to object mapping patterns
     */
    public static final String RESULT_SET_MAPPING = "dataAccess.resultSet.mapping";

    /**
     * Tag for custom row mapper implementations
     */
    public static final String CUSTOM_ROW_MAPPER = "dataAccess.custom.rowMapper";

    // ==================== RESOURCE MANAGEMENT PATTERNS ====================

    /**
     * Tag for database resource management patterns
     */
    public static final String RESOURCE_MANAGEMENT = "resource.management";

    /**
     * Tag for DataSource configuration patterns
     */
    public static final String DATASOURCE_CONFIGURATION = "resource.datasource.configuration";

    /**
     * Tag for DataSource lookup patterns
     */
    public static final String DATASOURCE_LOOKUP = "resource.datasource.lookup";

    /**
     * Tag for JNDI DataSource lookup patterns
     */
    public static final String JNDI_DATASOURCE_LOOKUP = "resource.datasource.jndi";

    /**
     * Tag for connection pooling patterns
     */
    public static final String CONNECTION_POOLING = "resource.connectionPool.pattern";

    /**
     * Tag for resource leak detection patterns
     */
    public static final String RESOURCE_LEAK_DETECTION = "resource.leak.detection";

    /**
     * Tag for proper resource cleanup patterns
     */
    public static final String RESOURCE_CLEANUP = "resource.cleanup.pattern";

    /**
     * Tag for try-with-resources usage patterns
     */
    public static final String TRY_WITH_RESOURCES = "resource.tryWithResources";

    /**
     * Tag for resource reference configurations
     */
    public static final String RESOURCE_REFERENCE = "resource.reference.configuration";

    // ==================== JBOSS-SPECIFIC JDBC PATTERNS ====================

    /**
     * Tag for JBoss DataSource configurations
     */
    public static final String JBOSS_DATASOURCE = "jboss.datasource.configuration";

    /**
     * Tag for JBoss connection pool configurations
     */
    public static final String JBOSS_CONNECTION_POOL = "jboss.connectionPool.configuration";

    /**
     * Tag for JBoss JDBC driver configurations
     */
    public static final String JBOSS_JDBC_CONFIG = "jboss.jdbc.configuration";

    /**
     * Tag for JBoss resource reference patterns
     */
    public static final String JBOSS_RESOURCE_REFERENCE = "jboss.resource.reference";

    /**
     * Tag for JBoss deployment descriptor JDBC configurations
     */
    public static final String JBOSS_JDBC_DESCRIPTOR = "jboss.jdbc.descriptor";

    /**
     * Tag for WildFly DataSource configurations
     */
    public static final String WILDFLY_DATASOURCE = "wildfly.datasource.configuration";

    /**
     * Tag for JBoss security domain JDBC configurations
     */
    public static final String JBOSS_SECURITY_DOMAIN_JDBC = "jboss.security.jdbc";

    // ==================== JDBC TRANSACTION MANAGEMENT ====================

    /**
     * Tag for JDBC manual transaction management patterns
     */
    public static final String JDBC_TRANSACTION_MANAGEMENT = "jdbc.transaction.management";

    /**
     * Tag for manual transaction control patterns
     */
    public static final String JDBC_MANUAL_TRANSACTION = "jdbc.transaction.manual";

    /**
     * Tag for JDBC auto-commit configuration patterns
     */
    public static final String JDBC_AUTO_COMMIT = "jdbc.transaction.autoCommit";

    /**
     * Tag for JDBC transaction boundary patterns
     */
    public static final String JDBC_TRANSACTION_BOUNDARY = "jdbc.transaction.boundary";

    /**
     * Tag for JDBC commit operation patterns
     */
    public static final String JDBC_COMMIT_PATTERN = "jdbc.transaction.commit";

    /**
     * Tag for JDBC rollback operation patterns
     */
    public static final String JDBC_ROLLBACK_PATTERN = "jdbc.transaction.rollback";

    /**
     * Tag for JDBC savepoint usage patterns
     */
    public static final String JDBC_SAVEPOINT_USAGE = "jdbc.transaction.savepoint";

    /**
     * Tag for distributed transaction patterns with JDBC
     */
    public static final String JDBC_DISTRIBUTED_TRANSACTION = "jdbc.transaction.distributed";

    // ==================== CONNECTION POOL PERFORMANCE ====================

    /**
     * Tag for connection pool optimization patterns
     */
    public static final String CONNECTION_POOL_OPTIMIZATION = "connectionPool.optimization";

    /**
     * Tag for connection pool performance analysis
     */
    public static final String CONNECTION_POOL_PERFORMANCE = "connectionPool.performance";

    /**
     * Tag for connection leak detection patterns
     */
    public static final String CONNECTION_LEAK_DETECTION = "connectionPool.leak.detection";

    /**
     * Tag for HikariCP migration candidates
     */
    public static final String HIKARICP_MIGRATION = "connectionPool.hikaricp.migration";

    /**
     * Tag for connection pool configuration patterns
     */
    public static final String CONNECTION_POOL_CONFIGURATION = "connectionPool.configuration";

    /**
     * Tag for connection pool sizing patterns
     */
    public static final String CONNECTION_POOL_SIZING = "connectionPool.sizing";

    /**
     * Tag for connection timeout configuration patterns
     */
    public static final String CONNECTION_TIMEOUT_CONFIG = "connectionPool.timeout.configuration";

    /**
     * Tag for connection validation patterns
     */
    public static final String CONNECTION_VALIDATION = "connectionPool.validation";

    // ==================== SPRING BOOT JDBC MIGRATION PATTERNS ====================

    /**
     * Tag for Spring JdbcTemplate migration candidates
     */
    public static final String SPRING_JDBC_TEMPLATE = "spring.jdbc.template.migration";

    /**
     * Tag for Spring Repository pattern conversion targets
     */
    public static final String SPRING_REPOSITORY_PATTERN = "spring.repository.pattern";

    /**
     * Tag for Spring DataSource configuration migration
     */
    public static final String SPRING_DATASOURCE_CONFIG = "spring.datasource.configuration";

    /**
     * Tag for Spring @Transactional annotation candidates
     */
    public static final String SPRING_TRANSACTION_ANNOTATION = "spring.transaction.annotation";

    /**
     * Tag for Spring Boot auto-configuration candidates
     */
    public static final String SPRING_BOOT_AUTO_CONFIG = "spring.boot.autoConfiguration";

    /**
     * Tag for Spring Data JPA migration candidates
     */
    public static final String SPRING_DATA_JPA_MIGRATION = "spring.data.jpa.migration";

    /**
     * Tag for Spring configuration properties migration
     */
    public static final String SPRING_CONFIG_PROPERTIES = "spring.configuration.properties";

    /**
     * Tag for Spring Boot migration candidate projects
     */
    public static final String SPRING_BOOT_MIGRATION_CANDIDATE = "spring.boot.migration.candidate";

    // ==================== ADDITIONAL JBOSS-SPECIFIC TAGS ====================

    /**
     * Tag for JBoss configuration files
     */
    public static final String JBOSS_CONFIGURATION = "jboss.configuration";

    /**
     * Tag for vendor-specific configuration files
     */
    public static final String VENDOR_SPECIFIC_CONFIG = "vendor.specific.configuration";

    /**
     * Tag for security configuration elements
     */
    public static final String SECURITY_CONFIGURATION = "security.configuration";

    /**
     * Tag for session bean found indicator
     */
    public static final String SESSION_BEAN_FOUND = "ejb.sessionBean.found";

    /**
     * Tag for JDBC usage detection
     */
    public static final String JDBC_USAGE = "jdbc.usage.detected";

    /**
     * Tag for connection pool configuration
     */
    public static final String CONNECTION_POOL_CONFIG = "connectionPool.config";

    /**
     * Tag for security constraint configuration
     */
    public static final String SECURITY_CONSTRAINT = "security.constraint";

    /**
     * Tag for resource environment reference
     */
    public static final String RESOURCE_ENV_REFERENCE = "resource.environment.reference";

    /**
     * Tag for JDBC resource reference
     */
    public static final String JDBC_RESOURCE_REFERENCE = "jdbc.resource.reference";

    /**
     * Tag for message destination reference
     */
    public static final String MESSAGE_DESTINATION_REFERENCE = "message.destination.reference";

    // ==================== PERFORMANCE AND OPTIMIZATION (JDBC) ====================

    /**
     * Tag for JDBC performance optimization patterns
     */
    public static final String JDBC_PERFORMANCE_OPTIMIZATION = "jdbc.performance.optimization";

    /**
     * Tag for JDBC statement caching patterns
     */
    public static final String JDBC_STATEMENT_CACHING = "jdbc.performance.statementCaching";

    /**
     * Tag for JDBC result set fetch size optimization
     */
    public static final String JDBC_FETCH_SIZE_OPTIMIZATION = "jdbc.performance.fetchSize";

    /**
     * Tag for JDBC connection reuse patterns
     */
    public static final String JDBC_CONNECTION_REUSE = "jdbc.performance.connectionReuse";

    /**
     * Tag for JDBC bulk operation patterns
     */
    public static final String JDBC_BULK_OPERATIONS = "jdbc.performance.bulkOperations";
}
