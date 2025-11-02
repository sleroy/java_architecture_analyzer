# JBoss EJB 2.x to Spring Boot Migration
## Appendices and Reference Materials

This document provides supplementary reference materials for the comprehensive JBoss EJB 2.x to Spring Boot migration plan.

**Related Documents:**
- Main Migration Plan: `MIGRATION_PLAN_JBOSS_TO_SPRINGBOOT.md`
- Task Breakdown: `docs/implementation/tasks/complete-ejb2-migration-plan.md`

---

## Table of Contents

- [Appendix E: EJB 2.x Component Matrix](#appendix-e-ejb-2x-component-matrix)
- [Appendix F: JDK Version Migration Matrix](#appendix-f-jdk-version-migration-matrix)
- [Appendix G: Legacy Antipatterns Catalog](#appendix-g-legacy-antipatterns-catalog)
- [Appendix H: Dependency Update Guide](#appendix-h-dependency-update-guide)
- [Appendix I: JDBC vs JPA Decision Matrix](#appendix-i-jdbc-vs-jpa-decision-matrix)

---

## APPENDIX E: EJB 2.x Component Matrix

### Complete EJB 2.x to Spring Boot Component Mapping

| EJB 2.x Component | Key Characteristics | Spring Boot Equivalent | Pattern TAG | Migration Complexity | Typical Effort |
|-------------------|---------------------|------------------------|-------------|---------------------|----------------|
| **Stateless Session Bean** | No conversational state, pooled by container, thread-safe | `@Service` with `@Transactional` | STATELESS_TO_SPRING_SERVICE | LOW | 1-2 hours per bean |
| **Stateful Session Bean** | Maintains conversational state, passivation/activation | `@Component` with `@Scope(SESSION)` | STATEFUL_TO_SPRING_SCOPE | MEDIUM | 2-4 hours per bean |
| **CMP Entity Bean** | Container-managed persistence, abstract methods | POJO + `@Repository` JDBC DAO | CMP_TO_JDBC_DAO | MEDIUM | 3-6 hours per entity |
| **BMP Entity Bean** | Bean-managed persistence, manual JDBC | POJO + `@Repository` JDBC DAO | BMP_TO_JDBC_DAO | MEDIUM | 2-4 hours per entity |
| **Message-Driven Bean** | Async message processing, JMS listener | `@Component` with `@JmsListener` | MDB_TO_SPRING_JMS | MEDIUM | 2-3 hours per MDB |
| **Home Interface** | Factory for creating/finding EJBs | Eliminated (Spring DI) | HOME_INTERFACE_REMOVAL | LOW | 30 min per interface |
| **Remote Interface** | Remote business interface | Eliminated or plain Java interface | REMOTE_INTERFACE_REMOVAL | LOW | 30 min per interface |
| **Local Interface** | Local business interface | Eliminated or plain Java interface | REMOTE_INTERFACE_REMOVAL | LOW | 30 min per interface |
| **Service Endpoint Interface** | Web service endpoint | `@Endpoint` with `@PayloadRoot` | JAXWS_TO_SPRING_WS | MEDIUM | 4-6 hours per service |

### EJB Lifecycle Callback Mapping

| EJB 2.x Lifecycle Method | Purpose | Spring Boot Equivalent |
|-------------------------|---------|------------------------|
| `ejbCreate()` | Entity/Session bean initialization | `@PostConstruct` or constructor |
| `ejbPostCreate()` | Post-creation callback | `@PostConstruct` |
| `ejbRemove()` | Bean removal | `@PreDestroy` |
| `ejbActivate()` | Stateful bean activation | Not needed (HTTP session handles) |
| `ejbPassivate()` | Stateful bean passivation | Not needed (HTTP session handles) |
| `ejbLoad()` | Load entity state from DB | DAO `findById()` method |
| `ejbStore()` | Persist entity state to DB | DAO `update()` method |
| `setSessionContext()` | Inject session context | Constructor injection |
| `setEntityContext()` | Inject entity context | Not needed |
| `setMessageDrivenContext()` | Inject MDB context | Not needed |

### EJB Context Operations Mapping

| EJB Context Operation | Purpose | Spring Boot Equivalent |
|----------------------|---------|------------------------|
| `getCallerPrincipal()` | Get authenticated user | `SecurityContextHolder.getContext().getAuthentication()` |
| `isCallerInRole("role")` | Check user role | `auth.getAuthorities().contains("ROLE_X")` |
| `setRollbackOnly()` | Mark transaction for rollback | `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` |
| `getRollbackOnly()` | Check rollback status | `TransactionAspectSupport.currentTransactionStatus().isRollbackOnly()` |
| `getUserTransaction()` | Get transaction handle | Inject `PlatformTransactionManager` |
| `getEJBHome()` | Get Home interface | Not needed (use Spring DI) |
| `getEJBLocalHome()` | Get LocalHome interface | Not needed (use Spring DI) |

### Transaction Attribute Mapping

| EJB Transaction Attribute | Behavior | Spring `@Transactional` Propagation |
|--------------------------|----------|-------------------------------------|
| `REQUIRED` (default) | Join existing or create new | `Propagation.REQUIRED` (default) |
| `REQUIRES_NEW` | Always create new transaction | `Propagation.REQUIRES_NEW` |
| `MANDATORY` | Must have existing transaction | `Propagation.MANDATORY` |
| `NOT_SUPPORTED` | Suspend current transaction | `Propagation.NOT_SUPPORTED` |
| `SUPPORTS` | Optional transaction | `Propagation.SUPPORTS` |
| `NEVER` | Fail if transaction exists | `Propagation.NEVER` |

---

## APPENDIX F: JDK Version Migration Matrix

### JDK Feature Compatibility Matrix

| Feature / API | JDK 8 | JDK 11 | JDK 17 | JDK 21 | Notes |
|--------------|-------|--------|--------|--------|-------|
| **Lambda Expressions** | ✅ | ✅ | ✅ | ✅ | Available since JDK 8 |
| **Stream API** | ✅ | ✅ | ✅ | ✅ | Available since JDK 8 |
| **Optional** | ✅ | ✅ | ✅ | ✅ | Available since JDK 8 |
| **Date/Time API** | ✅ | ✅ | ✅ | ✅ | java.time package since JDK 8 |
| **HTTP Client (java.net.http)** | ❌ | ✅ | ✅ | ✅ | New in JDK 11 |
| **var (Local Variable Type Inference)** | ❌ | ✅ | ✅ | ✅ | New in JDK 10, refined in 11 |
| **String Methods (isBlank, lines, etc.)** | ❌ | ✅ | ✅ | ✅ | New in JDK 11 |
| **Switch Expressions** | ❌ | ❌ | ✅ | ✅ | New in JDK 14 (standard in 17) |
| **Text Blocks (""")** | ❌ | ❌ | ✅ | ✅ | New in JDK 15 (standard in 17) |
| **Pattern Matching (instanceof)** | ❌ | ❌ | ✅ | ✅ | New in JDK 16 (standard in 17) |
| **Records** | ❌ | ❌ | ✅ | ✅ | New in JDK 16 (standard in 17) |
| **Sealed Classes** | ❌ | ❌ | ✅ | ✅ | New in JDK 17 |
| **Pattern Matching (switch)** | ❌ | ❌ | ❌ | ✅ | New in JDK 21 |
| **Virtual Threads** | ❌ | ❌ | ❌ | ✅ | New in JDK 21 |
| **Sequenced Collections** | ❌ | ❌ | ❌ | ✅ | New in JDK 21 |

### Removed JDK Features

| Removed Feature | Removed In | Replacement |
|----------------|-----------|-------------|
| **Java EE Modules** (JAXB, JAX-WS, etc.) | JDK 11 | Add explicit dependencies or migrate to Jakarta EE |
| **CORBA** | JDK 11 | Use REST or gRPC |
| **Applet API** | JDK 11 | Use web technologies |
| **Nashorn JavaScript Engine** | JDK 11 (deprecated), removed JDK 15 | Use GraalVM or external JS engine |
| **Security Manager** | Deprecated JDK 17, removed JDK 18+ | Use OS-level security, containerization |
| **Thread.stop(), Thread.destroy()** | Deprecated long ago | Use interrupt mechanism |
| **Finalization (finalize())** | Deprecated JDK 9, removed eventually | Use try-with-resources, Cleaner API |

### Spring Boot Version Requirements

| Spring Boot Version | Minimum JDK | Maximum JDK | javax.* or jakarta.* | Notes |
|--------------------|-------------|-------------|---------------------|-------|
| **2.0.x - 2.4.x** | JDK 8 | JDK 15 | javax.* | Legacy support |
| **2.5.x - 2.7.x** | JDK 8 | JDK 19 | javax.* | **Recommended for JDK 8-11 migration** |
| **3.0.x - 3.1.x** | JDK 17 | JDK 21 | jakarta.* | **Major namespace change** |
| **3.2.x+** | JDK 17 | JDK 21+ | jakarta.* | Latest stable |

### Library Compatibility by JDK Version

| Library | JDK 8 Version | JDK 11 Version | JDK 17 Version | JDK 21 Version | Breaking Changes |
|---------|---------------|----------------|----------------|----------------|------------------|
| **Jackson** | 2.13.x | 2.13.x | 2.15.x | 2.16.x | Minor API changes |
| **Log4j2** | 2.17.x | 2.19.x | 2.20.x | 2.21.x | Configuration changes |
| **Hibernate** | 5.4.x | 5.6.x | 6.2.x | 6.4.x | Major changes in 6.x |
| **Commons Lang** | 3.12.0 | 3.12.0 | 3.13.0 | 3.14.0 | Maintained |
| **Guava** | 30.x | 31.x | 32.x | 33.x | Maintained |
| **Apache HttpClient** | 4.5.x | 4.5.x | 5.2.x | 5.3.x | Major API change in 5.x |
| **HikariCP** | 3.4.x | 4.0.x | 5.0.x | 5.1.x | Configuration changes |

### JDK Migration Path Recommendations

**Path 1: Conservative (Recommended for Large Applications)**
```
JDK 8 → JDK 11 → JDK 17 → JDK 21
      (Test)   (Test)    (Test)
     2-3 weeks 2-3 weeks 2-4 weeks
```

**Path 2: Moderate (Medium Applications)**
```
JDK 8 → JDK 11 → JDK 21
      (Test)    (Test)
     2-3 weeks 3-4 weeks
```

**Path 3: Aggressive (Small Applications, Modern Codebase)**
```
JDK 8 → JDK 17 → JDK 21
      (Test)    (Test)
     3-4 weeks 2-3 weeks
```

---

## APPENDIX G: Legacy Antipatterns Catalog

### Common Legacy Java Antipatterns and Solutions

#### 1. Deep Inheritance Hierarchies

**Antipattern:**
```java
BaseEntity → AuditableEntity → BusinessEntity → DomainEntity → Customer
(5 levels deep)
```

**Problems:**
- Tight coupling
- Hard to understand class relationships
- Difficult to modify base classes
- Fragile base class problem

**Detection:**
```bash
# Find deep hierarchies (manual analysis needed)
# Look for chains of "extends" keywords
grep -r "extends.*extends" src/
```

**Refactoring Strategy:**
- Favor composition over inheritance
- Keep inheritance depth ≤3 levels
- Use interfaces for capabilities
- Extract common functionality into composed components

**Estimated Effort:** 2-4 hours per hierarchy

---

#### 2. God Classes

**Antipattern:**
```java
@Service
public class CustomerService {
    // 50+ methods handling:
    // - Customer CRUD
    // - Order management
    // - Payment processing
    // - Notifications
    // - Reporting
    // - Analytics
    // ... (1500+ lines)
}
```

**Problems:**
- Violates Single Responsibility Principle
- Hard to test
- High coupling
- Difficult to maintain

**Detection:**
```bash
# Find large classes (>1000 lines)
find src -name "*.java" -exec wc -l {} + | awk '$1 > 1000' | sort -rn

# Find classes with many methods (>20)
# Use IDE or tools like PMD, SonarQube
```

**Refactoring Strategy:**
- Extract related methods into separate services
- Identify cohesive responsibilities
- Create focused, single-purpose classes
- Use composition to reassemble functionality

**Estimated Effort:** 1-2 days per god class

---

#### 3. Anemic Domain Model

**Antipattern:**
```java
// Domain object with no behavior
public class Order {
    private Long id;
    private List<OrderItem> items;
    private BigDecimal total;
    // Only getters/setters, no business logic
}

// All logic in separate service
@Service
public class OrderService {
    public BigDecimal calculateTotal(Order order) {
        return order.getItems().stream()
            .map(item -> item.getPrice().multiply(item.getQuantity()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

**Problems:**
- Business logic scattered
- Domain objects are just data carriers
- Tight coupling between services and models

**Detection:**
- POJOs with only getters/setters
- Services with complex calculations on POJOs
- Lack of domain methods

**Refactoring Strategy:**
```java
// Rich domain model
public class Order {
    private List<OrderItem> items;
    
    public BigDecimal calculateTotal() {
        return items.stream()
            .map(OrderItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public void addItem(Product product, int quantity) {
        // Validation and business rules here
    }
}
```

**Estimated Effort:** 1-2 hours per domain object

---

#### 4. Manual Singleton Pattern

**Antipattern:**
```java
public class ConfigManager {
    private static ConfigManager instance;
    private Properties config;
    
    private ConfigManager() {
        config = new Properties();
    }
    
    public static ConfigManager getInstance() {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager();
                }
            }
        }
        return instance;
    }
}
```

**Problems:**
- Hard to test (global state)
- Hard to mock
- Tight coupling
- Concurrency issues if not implemented correctly

**Detection:**
```bash
grep -r "private static.*getInstance" src/
grep -r "private.*constructor" src/
```

**Refactoring Strategy:**
```java
// Spring manages lifecycle
@Component
public class ConfigManager {
    private final Properties config;
    
    public ConfigManager() {
        this.config = loadConfig();
    }
    
    // Or better: use @ConfigurationProperties
}
```

**Estimated Effort:** 30 minutes per singleton

---

#### 5. Excessive Checked Exceptions

**Antipattern:**
```java
public Customer findCustomer(Long id) 
    throws CustomerNotFoundException, DatabaseException, ValidationException {
    // Forces all callers to handle multiple exceptions
}
```

**Problems:**
- Forces exception handling at every call site
- Clutters code with try-catch blocks
- Often leads to empty catch blocks
- Violates "fail fast" principle

**Detection:**
```bash
grep -r "throws.*Exception.*Exception" src/
```

**Refactoring Strategy:**
```java
// Use runtime exceptions
public Optional<Customer> findCustomer(Long id) {
    // Let unexpected errors propagate
    // Use Optional for "not found" case
}

// Or with runtime exception
public Customer findCustomerOrThrow(Long id) {
    return repository.findById(id)
        .orElseThrow(() -> new CustomerNotFoundException(id));
}
```

**Estimated Effort:** 30 minutes per method

---

#### 6. Static Utility Hell

**Antipattern:**
```java
public final class StringUtils {
    public static String encrypt(String text) {
        // Complex encryption logic with dependencies
    }
    
    public static String validateAndFormat(String text) {
        // Complex validation with external dependencies
    }
}
```

**Problems:**
- Hard to test
- Hard to mock dependencies
- Hidden dependencies
- Tight coupling

**Detection:**
```bash
grep -r "public static.*Utils" src/
grep -r "public final class.*Util" src/
```

**Refactoring Strategy:**
```java
// For complex logic with dependencies
@Component
public class StringProcessor {
    private final EncryptionService encryptionService;
    
    public StringProcessor(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }
    
    public String encrypt(String text) {
        return encryptionService.encrypt(text);
    }
}

// Keep static only for truly stateless utilities
public final class StringUtils {
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }
}
```

**Estimated Effort:** 1-2 hours per utility class

---

#### 7. Magic Numbers and Strings

**Antipattern:**
```java
if (order.getStatus() == 3) {  // What is 3?
    // Process order
}

if ("ACTIVE".equals(status)) {  // Typo-prone
    // Do something
}
```

**Problems:**
- Hard to understand code
- Error-prone
- Difficult to maintain

**Detection:**
```bash
# Manual code review needed
# Look for numeric literals and string constants
```

**Refactoring Strategy:**
```java
// Use enums
public enum OrderStatus {
    PENDING(1),
    PROCESSING(2),
    COMPLETED(3),
    CANCELLED(4);
    
    private final int code;
    OrderStatus(int code) { this.code = code; }
}

if (order.getStatus() == OrderStatus.COMPLETED) {
    // Clear intent
}
```

**Estimated Effort:** 15 minutes per occurrence

---

#### 8. Premature Optimization

**Antipattern:**
```java
// Overly complex caching for rarely-accessed data
private Map<String, SoftReference<Customer>> cache = 
    new ConcurrentHashMap<>();
    
public Customer getCustomer(String id) {
    // Complex cache management for data accessed once per day
}
```

**Problems:**
- Increased complexity
- More bugs
- Harder to maintain
- Marginal performance benefit

**Detection:**
- Manual code review
- Look for complex caching, pooling without metrics

**Refactoring Strategy:**
```java
// Start simple
@Cacheable("customers")
public Customer getCustomer(String id) {
    return customerRepository.findById(id);
}

// Add complexity only when needed
// Measure first, optimize later
```

**Estimated Effort:** Varies

---

### Antipattern Detection Tools

| Tool | Purpose | Cost |
|------|---------|------|
| **SonarQube** | Code quality & security | Free (Community), Paid (Enterprise) |
| **PMD** | Static code analysis | Free |
| **Checkstyle** | Code style violations | Free |
| **SpotBugs** | Bug patterns | Free |
| **ArchUnit** | Architecture testing | Free |
| **JDepend** | Package dependency analysis | Free |

---

## APPENDIX H: Dependency Update Guide

### Critical Dependencies for Migration

#### Spring Boot Starters

```xml
<!-- Core Spring Boot -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <!-- JDK 8: Use 2.7.18 -->
    <!-- JDK 17+: Use 3.2.x -->
</dependency>

<!-- Web (includes REST, embedded Tomcat) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- JDBC (NO JPA) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>

<!-- Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- Actuator (monitoring) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Testing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

#### Database Drivers

| Database | JDK 8 Driver | JDK 11+ Driver | Notes |
|----------|-------------|----------------|-------|
| **PostgreSQL** | 42.2.27 | 42.5.x | Stable |
| **MySQL** | 8.0.33 | 8.2.x | Check MySQL version |
| **Oracle** | 19.x | 21.x | License considerations |
| **SQL Server** | 9.4.x | 12.x | Microsoft driver |
| **H2 (Testing)** | 1.4.200 | 2.2.x | In-memory database |

```xml
<!-- Example: PostgreSQL -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.5.4</version>
</dependency>
```

#### Logging

```xml
<!-- Spring Boot uses Logback by default -->
<!-- To switch to Log4j2 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-log4j2</artifactId>
</dependency>
```

#### Common Libraries

| Library | Purpose | JDK 8 Version | JDK 11+ Version | Spring Boot Managed? |
|---------|---------|---------------|-----------------|---------------------|
| **Apache Commons Lang** | Utilities | 3.12.0 | 3.14.0 | No |
| **Apache Commons Collections** | Collections | 4.4 | 4.4 | No |
| **Apache Commons IO** | I/O utilities | 2.11.0 | 2.15.1 | No |
| **Guava** | Google utilities | 30.1-jre | 33.0-jre | No |
| **Jackson** | JSON processing | 2.13.x | 2.16.x | Yes (via Spring Boot) |
| **Lombok** | Boilerplate reduction | 1.18.26 | 1.18.30 | No |

### Dependencies to Remove

| Dependency | Reason for Removal | Replacement |
|-----------|-------------------|-------------|
| **jboss-ejb-api_*** | EJB container dependencies | Spring Context |
| **javax.ejb:ejb-api** | EJB API | Spring annotations |
| **javax.servlet:servlet-api** | Container-provided | Spring Boot Embedded |
| **jaxws-api** (if on JDK 11+) | Removed from JDK | Spring WS or explicit JAXB |
| **javax.xml.bind:jaxb-api** (JDK 11+) | Removed from JDK | jakarta.xml.bind or use Spring Boot 3.x |
| **Arquillian dependencies** | EJB testing framework | Spring Boot Test |

### Breaking Changes by Version

#### Spring Boot 2.7 → 3.0

| Change | Impact | Migration Action |
|--------|--------|------------------|
| **javax.* → jakarta.*** | HIGH | Replace all javax imports with jakarta |
| **Minimum JDK 17** | HIGH | Upgrade JDK |
| **Spring Security 6.0** | HIGH | Update security configuration (no WebSecurityConfigurerAdapter) |
| **Actuator changes** | MEDIUM | Update actuator endpoints configuration |
| **Hibernate 6** (if using JPA) | MEDIUM | Review query syntax |

```bash
# Automated namespace replacement
find src -name "*.java" -exec sed -i 's/import javax\.servlet/import jakarta.servlet/g' {} +
find src -name "*.java" -exec sed -i 's/import javax\.persistence/import jakarta.persistence/g' {} +
find src -name "*.java" -exec sed -i 's/import javax\.validation/import jakarta.validation/g' {} +
```

---

## APPENDIX I: JDBC vs JPA Decision Matrix

### When to Use Pure JDBC (This Migration Approach)

✅ **Use JDBC When:**

| Scenario | Reason |
|----------|--------|
| **Legacy SQL queries exist** | Reuse existing, tested SQL without rewriting |
| **Complex SQL queries** | Native SQL easier to write and optimize than JPQL |
| **Performance critical** | Direct control over SQL, no ORM overhead |
| **Reporting/Analytics** | Complex aggregations better in SQL |
| **Database-specific features** | Window functions, CTEs, stored procedures |
| **Small to medium data models** | JDBC overhead manageable |
| **Team expertise in SQL** | Leverage existing SQL skills |
| **Batch operations** | JdbcTemplate batch updates very efficient |

### When to Consider JPA/Hibernate (Future Enhancement)

✅ **Use JPA When:**

| Scenario | Reason |
|----------|--------|
| **CRUD-heavy application** | JPA simplifies CRUD operations |
| **Complex object graphs** | Automatic relationship management |
| **Database portability needed** | Abstract away database differences |
| **Large domain model** | ORM reduces boilerplate |
| **Team prefers OOP** | Work with objects, not SQL |
| **Caching requirements** | Second-level cache built-in |

### Performance Comparison

| Operation | JDBC Performance | JPA Performance | Winner |
|-----------|------------------|-----------------|--------|
| **Simple SELECT** | 100% (baseline) | 95-100% | ~Tie |
| **Complex JOIN** | 100% | 80-90% | JDBC |
| **Batch INSERT** | 100% | 70-85% | JDBC |
| **Bulk UPDATE** | 100% | 60-80% (N+1 risk) | JDBC |
| **CRUD with caching** | N/A | Can be faster with 2nd level cache | JPA |

### Code Comparison

#### Simple CRUD Operations

**JDBC Approach:**
```java
@Repository
public class CustomerDao {
    private final JdbcTemplate jdbcTemplate;
    
    public Optional<Customer> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT id, name, email FROM customer WHERE id = ?",
                new Object[]{id},
                this::mapRow
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Transactional
    public Customer save(Customer customer) {
        if (customer.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO customer (name, email) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, customer.getName());
                ps.setString(2, customer.getEmail());
                return ps;
            }, keyHolder);
            customer.setId(keyHolder.getKey().longValue());
        } else {
            jdbcTemplate.update(
                "UPDATE customer SET name = ?, email = ? WHERE id = ?",
                customer.getName(), customer.getEmail(), customer.getId());
        }
        return customer;
    }
    
    private Customer mapRow(ResultSet rs, int rowNum) throws SQLException {
        Customer customer = new Customer();
        customer.setId(rs.getLong("id"));
        customer.setName(rs.getString("name"));
        customer.setEmail(rs.getString("email"));
        return customer;
    }
}
```

**JPA Approach:**
```java
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    // That's it! CRUD methods provided automatically
}

// Entity
@Entity
@Table(name = "customer")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String email;
}
```

**Analysis:**
- JDBC: ~30 lines for basic DAO
- JPA: ~3 lines for repository interface
- JDBC: Full control, explicit SQL
- JPA: Less code, but magic behavior

#### Complex Query

**JDBC:**
```java
public List<CustomerOrderSummary> getCustomerOrderSummaries() {
    return jdbcTemplate.query(
        """
        SELECT c.id, c.name, c.email,
               COUNT(o.id) as order_count,
               COALESCE(SUM(o.total), 0) as total_spent
        FROM customer c
        LEFT JOIN orders o ON c.id = o.customer_id
        WHERE c.active = true
        GROUP BY c.id, c.name, c.email
