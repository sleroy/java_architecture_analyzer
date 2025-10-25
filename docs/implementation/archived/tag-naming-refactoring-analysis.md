# Tag Naming Refactoring Analysis

## Current Issues Identified

### 1. Inconsistent Naming Conventions
- **Mixed underscores and camelCase**: `ejb.bean_detected` vs `ejb.sessionBean`
- **Inconsistent prefixes**: Some constants lack `EJB_` prefix (e.g., `STATEFUL_SESSION_STATE`)
- **Inconsistent domain grouping**: Some tags don't start with their domain prefix

### 2. Redundant or Overlapping Tags
- `EJB_BEAN_DETECTED` and `SESSION_BEAN_FOUND` - both indicate EJB detection
- `STATEFUL_SESSION_STATE` and `EJB_STATEFUL_STATE` - both about stateful state
- `TRANSACTION_BOUNDARY` vs `EJB_TRANSACTION_BOUNDARY` vs `JDBC_TRANSACTION_BOUNDARY`

### 3. Inconsistent Hierarchical Depth
- Some: `ejb.sessionBean` (2 levels)
- Others: `ejb.stateless.sessionBean` (3 levels)
- Complex: `ejb.createMethod.complexInitialization` (4 levels)

### 4. Poor Nesting Benefits
Current flat structure doesn't leverage the new property nesting feature effectively.

## Proposed Refactoring Strategy

### Naming Convention Rules

1. **Constant Names**: Always use `SCREAMING_SNAKE_CASE` with meaningful prefixes
2. **Tag Values**: Use dot notation with lowercase + camelCase
3. **Hierarchy Depth**: Maximum 4 levels for reasonable nesting
4. **Consistency**: Same concepts should use same patterns across domains

### Domain Organization

```
ejb.
├── type.                  (Component types)
│   ├── session.
│   │   ├── stateless
│   │   ├── stateful
│   │   └── found
│   ├── entity.
│   │   ├── cmp
│   │   └── bmp
│   ├── messageDriven
│   └── interface.
│       ├── home
│       ├── remote
│       ├── local
│       └── localHome
├── persistence.           (CMP/Persistence)
│   ├── cmp.
│   │   ├── field
│   │   ├── fieldMapping
│   │   └── entity
│   ├── cmr.
│   │   ├── relationship
│   │   ├── complex
│   │   ├── bidirectional
│   │   └── cascade
│   ├── primaryKey.
│   │   ├── class
│   │   └── composite
│   └── metadata
├── transaction.           (Transaction management)
│   ├── programmatic
│   ├── declarative
│   ├── containerManaged
│   ├── beanManaged
│   ├── boundary
│   └── xa
├── state.                 (State management - stateful)
│   ├── conversational
│   ├── management
│   ├── sessionState
│   ├── crossMethod
│   └── synchronization
├── client.                (Client patterns)
│   ├── code
│   ├── createMethod.
│   │   ├── usage
│   │   ├── parameterized
│   │   └── complexInit
│   ├── businessDelegate
│   ├── serviceLocator
│   ├── jndiLookup
│   └── diCandidate
├── lifecycle.             (Lifecycle management)
│   ├── methods
│   ├── entity
│   └── session
├── vendor.                (Vendor-specific)
│   ├── weblogic
│   ├── websphere
│   ├── jboss
│   └── oracle
├── deployment.            (Deployment descriptors)
│   ├── descriptor
│   ├── vendor
│   └── security
├── performance.           (Performance patterns)
│   ├── pooling
│   ├── caching
│   ├── lazyLoading
│   └── batch
└── messaging.             (JMS/Messaging)
    ├── jms
    ├── queue
    └── topic

jdbc.
├── usage.
│   ├── connection
│   ├── statement
│   ├── preparedStatement
│   ├── callableStatement
│   └── resultSet
├── sql.
│   ├── query
│   └── parameterized
├── transaction.
│   ├── management
│   ├── manual
│   ├── autoCommit
│   ├── boundary
│   ├── commit
│   ├── rollback
│   ├── savepoint
│   └── distributed
├── batch.
│   └── processing
├── dto.
│   └── usage
└── performance.
    ├── optimization
    ├── statementCaching
    ├── fetchSize
    ├── connectionReuse
    └── bulkOperations

dataAccess.
├── layer
├── dao.
│   └── pattern
├── dto.
│   └── pattern
├── vo.
│   └── pattern
├── mapping.
│   ├── manual
│   ├── custom
│   ├── resultSet
│   └── custom
├── resultSet.
│   └── mapping
└── custom.
    └── rowMapper

resource.
├── management
├── datasource.
│   ├── configuration
│   ├── lookup
│   └── jndi
├── connectionPool.
│   ├── pattern
│   ├── config
│   ├── configuration
│   └── optimization
├── leak.
│   └── detection
├── cleanup.
│   └── pattern
├── tryWithResources
├── reference.
│   ├── configuration
│   ├── environment
│   └── jdbc
└── environment.
    └── reference

connectionPool.
├── optimization
├── performance
├── leak.
│   └── detection
├── hikaricp.
│   └── migration
├── configuration
├── sizing
├── timeout.
│   └── configuration
├── validation
└── config

spring.
├── conversion.
│   ├── service
│   ├── component
│   ├── transaction
│   ├── configuration
│   └── scope
├── jdbc.
│   └── template.
│       └── migration
├── repository.
│   └── pattern
├── datasource.
│   └── configuration
├── transaction.
│   └── annotation
├── boot.
│   ├── autoConfiguration
│   └── migration.
│       └── candidate
├── data.
│   └── jpa.
│       └── migration
└── configuration.
    └── properties

jpa.
├── conversion.
│   ├── candidate
│   ├── entity
│   └── repository

migration.
├── complexity.
│   ├── low
│   ├── medium
│   ├── high
│   └── critical
└── priority.
    ├── high
    ├── medium
    └── simple

jboss.
├── datasource.
│   └── configuration
├── connectionPool.
│   └── configuration
├── jdbc.
│   ├── configuration
│   └── descriptor
├── resource.
│   └── reference
├── security.
│   ├── jdbc
│   └── domain.jdbc
└── configuration

wildfly.
└── datasource.
    └── configuration

vendor.
└── specific.
    └── configuration

security.
├── configuration
└── constraint

refactoring.
├── target
├── modernization
└── architecture

persistence.
├── metadata
└── layer
```

## Example Improvements

### Before:
```java
public static final String EJB_BEAN_DETECTED = "ejb.bean_detected";
public static final String EJB_STATELESS_SESSION_BEAN = "ejb.stateless.sessionBean";
public static final String STATEFUL_SESSION_STATE = "ejb.stateful.sessionState";
public static final String TRANSACTION_BOUNDARY = "transaction.boundary";
```

### After:
```java
public static final String EJB_TYPE_SESSION_STATELESS = "ejb.type.session.stateless";
public static final String EJB_TYPE_SESSION_STATEFUL = "ejb.type.session.stateful";
public static final String EJB_STATE_SESSION = "ejb.state.session";
public static final String EJB_TRANSACTION_BOUNDARY = "ejb.transaction.boundary";
```

## Benefits of Refactoring

1. **Better JSON Nesting**: Tags will nest beautifully in the JSON output
2. **Consistent Naming**: All tags follow same pattern
3. **Easier Queries**: Can query `ejb.type.*` to get all type info
4. **Clearer Semantics**: Hierarchy reflects logical relationships
5. **Reduced Redundancy**: Eliminates overlapping/duplicate tags

## Migration Path

1. Keep old constants marked as `@Deprecated`
2. Add new constants with correct naming
3. Update inspectors to use new constants
4. Add compatibility layer if needed
5. Remove deprecated constants in next major version

## Next Steps

1. Create refactored EjbMigrationTags class
2. Document mapping from old to new tags
3. Update inspector implementations
4. Test with property nesting feature
5. Validate JSON output structure
