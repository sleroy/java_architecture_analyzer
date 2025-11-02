# JBoss EJB 2.x to Spring Boot Migration Strategy
## Comprehensive Pattern-Based Migration Plan

**Target Applications:** EJB 2.x (All Components) + SOAP + JDK 8 + Custom Framework + JDBC  
**Approach:** Incremental, pattern-based refactoring with class-by-class application  
**Persistence Strategy:** Pure JDBC with Spring JdbcTemplate (NO JPA/Hibernate)

---

## IMPORTANT NOTES

### Scope and Limitations

1. **Database Schema Migration:** This plan does NOT cover database schema migrations using tools like Flyway or Liquibase. Database schema changes should be handled outside the application migration process by your DBA team or existing migration tools.

2. **Pure JDBC Approach:** This plan maintains native JDBC operations and does NOT migrate to JPA/Hibernate. The focus is on:
   - Migrating connection pooling from JBoss to Spring HikariCP
   - Refactoring JDBC wrapper code to use Spring JdbcTemplate
   - Maintaining existing SQL queries and JDBC patterns
   - Converting Entity Beans to POJOs + JDBC DAOs (not JPA entities)

3. **JDK Version:** Initial migration targets JDK 8 with Spring Boot 2.7.x. Phase 9 covers JDK 8→21 migration path.

4. **Future Improvements:** After successful migration, you may consider:
   - Adding Flyway/Liquibase for schema version control
   - Gradually migrating to JPA if business requirements change
   - Upgrading to Spring Boot 3.x with Jakarta EE namespaces

---

## EJB 2.X COMPONENT COVERAGE

This plan covers ALL EJB 2.x component types:

✅ **Session Beans** (Stateless & Stateful) → Spring Services  
✅ **Entity Beans** (CMP & BMP) → POJOs + JDBC DAOs  
✅ **Message-Driven Beans** → Spring @JmsListener  
✅ **EJB Interfaces** (Home, Remote, Local) → Removed with Spring DI  
✅ **Service Endpoint Interfaces** → Spring WS Endpoints

---

## PHASE 0: PRE-MIGRATION ASSESSMENT AND PREPARATION

### SUBTITLE: Environment Setup and Baseline Establishment

#### TASK-000: Project Baseline Documentation
**Purpose:** Establish a clear baseline of the current application state before migration begins

**Requirements:**
- Git repository with clean working directory
- Access to all project modules
- Running JBoss AS environment
- Database access (optional)

**Actions to do:**
1. Create a new branch `migration/springboot-baseline`
2. Document current application functionality:
   - All REST/SOAP endpoints
   - Business features
   - External integrations
3. Create or execute integration tests to establish baseline behavior
4. Optional: Export current database schema using appropriate database tools
5. Document all external integrations (SOAP services, file systems, message queues, etc.)
6. List all custom framework capabilities being used
7. Create a test suite execution report (if tests exist)
8. Document current JVM parameters and JBoss configuration
9. List all environment variables and system properties

**Way to validate it:**
- Git branch created successfully
- Documentation file `BASELINE.md` created with complete inventory
- All endpoints documented with sample requests/responses
- Application runs successfully in current environment
- Integration tests pass (if available)

---

#### TASK-001: Create Migration Branch Structure
**Purpose:** Set up a parallel development structure to enable incremental migration

**Requirements:**
- Completed TASK-000
- Git access

**Actions to do:**
1. Create migration branch `migration/springboot-initial`
2. Create directory structure for new Spring Boot application:
   ```
   ${PROJECT_NAME}-springboot/
   ├── src/
   │   ├── main/
   │   │   ├── java/
   │   │   │   └── ${BASE_PACKAGE}/
   │   │   │       ├── model/      # POJOs (not JPA entities)
   │   │   │       ├── dao/        # JDBC DAOs
   │   │   │       ├── service/    # Business logic
   │   │   │       ├── controller/ # REST controllers
   │   │   │       ├── endpoint/   # SOAP endpoints
   │   │   │       └── config/     # Spring configuration
   │   │   └── resources/
   │   │       ├── application.properties
   │   │       └── logback-spring.xml
   │   └── test/
   │       ├── java/
   │       │   └── ${BASE_PACKAGE}/
   │       └── resources/
   │           └── application-test.properties
   ├── pom.xml
   └── README.md
   ```
3. Add `.gitignore` for Spring Boot:
   ```
   target/
   logs/
   *.log
   .idea/
   *.iml
   .settings/
   .classpath
   .project
   ```
4. Document migration strategy in `MIGRATION_STRATEGY.md`

**Way to validate it:**
- Branch created successfully
- New directory structure exists
- `.gitignore` properly configured
- Can build empty project structure

---

#### TASK-002: Dependency Analysis and Mapping
**Purpose:** Create a complete mapping of JBoss dependencies to Spring Boot equivalents

**Requirements:**
- Access to all POM files
- Spring Boot documentation

**Actions to do:**
1. Create `DEPENDENCY_MAPPING.md` file
2. List all current dependencies from all POM files
3. Map each dependency to Spring Boot equivalent:
   
   **EJB Dependencies:**
   - `jboss-ejb-api_*` → Spring Context (no direct replacement)
   - `ejb3-persistence` → **spring-jdbc** (JDBC only, NO JPA)
   
   **CDI Dependencies:**
   - `cdi-api` → Spring Context
   - `javax.inject` → Spring DI annotations
   
   **Web Service Dependencies:**
   - `jboss-jaxrs-api_*` → `spring-boot-starter-web`
   - `jaxws-api` → `spring-boot-starter-web-services`
   - `jaxb-api` → Keep as is (for Java 8+)
   
   **JDBC Dependencies:**
   - JBoss Datasource → HikariCP (included in Spring Boot)
   - Custom JDBC wrappers → Spring JdbcTemplate
   
   **JMS Dependencies:**
   - `jboss-jms-api_*` → `spring-boot-starter-activemq` or `spring-jms`
   
   **Other Dependencies:**
   - `servlet-api` → `spring-boot-starter-web` (embedded)
   - `validation-api` → `spring-boot-starter-validation`
   - Keep business libraries as-is (Jasper Reports, Apache POI, etc.)

4. Identify dependencies that can be removed (container-provided)
5. Document version compatibility with JDK 8

**Way to validate it:**
- `DEPENDENCY_MAPPING.md` exists with complete mapping
- All dependencies mapped
- No unmapped dependencies remain
- Spring Boot 2.7.x versions identified (JDK 8 compatible)

---

## PHASE 1: SPRING BOOT PROJECT INITIALIZATION

### SUBTITLE: Bootstrap Spring Boot Application Structure

#### TASK-100: Create Spring Boot Parent POM
**Purpose:** Initialize Spring Boot project with proper parent and dependency management

**Requirements:**
- Completed TASK-002
- Maven 3.6+
- Java 8+

**Actions to do:**
1. Create new `pom.xml` in `${PROJECT_NAME}-springboot/`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.18</version>
        <relativePath/>
    </parent>
    
    <groupId>${YOUR_GROUP_ID}</groupId>
    <artifactId>${PROJECT_NAME}-springboot</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    
    <name>${PROJECT_NAME} Spring Boot Application</name>
    
    <properties>
        <java.version>8</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    
    <dependencies>
        <!-- Core Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <!-- JDBC Support (PURE JDBC - NO JPA) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        
        <!-- Transaction Management -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-tx</artifactId>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Way to validate it:**
- `mvn clean compile` executes successfully
- No dependency resolution errors
- Spring Boot version 2.7.18 resolved
- Java 8 compatibility maintained
- NO JPA dependencies present

---

#### TASK-101: Create Spring Boot Main Application Class
**Purpose:** Create the entry point for Spring Boot application

**Requirements:**
- Completed TASK-100
- Spring Boot parent POM building successfully

**Actions to do:**
1. Create package structure: `src/main/java/${BASE_PACKAGE}/`
2. Create main application class:
```java
package ${BASE_PACKAGE};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement  // Required for @Transactional
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```
3. Create `application.properties` in `src/main/resources/`
4. Add basic application properties:
```properties
spring.application.name=${PROJECT_NAME}
server.port=${PORT}

# Disable JPA auto-configuration (we're using pure JDBC)
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
```

**Way to validate it:**
- `mvn spring-boot:run` starts application successfully
- Application accessible at configured port
- Spring Boot banner displays
- NO JPA initialization in logs
- Application shuts down cleanly with Ctrl+C

---

#### TASK-102: Add Actuator for Health Monitoring
**Purpose:** Enable monitoring and health check endpoints

**Requirements:**Jus
- Completed TASK-101
- Application starting successfully

**Actions to do:**
1. Add Spring Boot Actuator dependency to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```
2. Configure actuator in `application.properties`:
```properties
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always
```
3. Build and restart application

**Way to validate it:**
- `mvn clean package` builds successfully
- Application starts
- Health endpoint returns JSON with status "UP"
- Info endpoint returns empty JSON object (expected)

---

## PHASE 2: DATABASE CONNECTIVITY MIGRATION (JDBC)

### SUBTITLE: JBoss DataSource to Spring JDBC

#### TASK-200: [PATTERN: DATASOURCE_CONFIG_IDENTIFICATION] Identify Database Configuration
**Purpose:** Locate and document all database connectivity configuration in the JBoss application

**Pattern TAG:** DATASOURCE_CONFIG_IDENTIFICATION

**Requirements:**
- Identify all datasource configurations in the JBoss application
- A JBoss datasource configuration typically appears in:
  - `*-ds.xml` files in JBoss deployment descriptors
  - `standalone.xml` or `domain.xml` configuration
  - JNDI lookup strings in code like `java:jboss/datasources/`
  - Properties files with database connection details

**Actions to do:**
For each datasource configuration found:

1. Document the JNDI name (e.g., `java:jboss/datasources/MyAppDS`)
2. Extract connection parameters:
   - Driver class name
   - JDBC URL
   - Username/password (or reference to vault/properties)
   - Connection pool settings (min/max pool size, timeout, etc.)
3. Document transaction settings (XA vs non-XA)
4. Document any custom connection properties
5. Search for all code locations that lookup this datasource:
   ```bash
   # Search for JNDI lookups
   grep -r "java:jboss/datasources" src/
   grep -r "@Resource.*DataSource" src/
   grep -r "lookup.*DataSource" src/
   ```
6. Create `DATASOURCE_INVENTORY.md` documenting all findings

**Way to validate:**
- All datasource configurations documented
- All JNDI lookup locations identified
- Connection pool parameters recorded
- No undocumented datasources remain

---

#### TASK-201: Configure Spring JDBC DataSource
**Purpose:** Replace JBoss datasource with Spring Boot HikariCP datasource

**Requirements:**
- Completed TASK-200
- Database driver available
- Database credentials

**Actions to do:**
1. Add database driver dependency to `pom.xml`:
```xml
<dependency>
    <groupId>${DATABASE_DRIVER_GROUP}</groupId>
    <artifactId>${DATABASE_DRIVER_ARTIFACT}</artifactId>
    <version>${DATABASE_DRIVER_VERSION}</version>
</dependency>
```

2. Configure datasource in `application.properties`:
```properties
# Database Configuration
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=${DB_DRIVER_CLASS}

# HikariCP Configuration (matches JBoss pool settings from TASK-200)
spring.datasource.hikari.maximum-pool-size=${MAX_POOL_SIZE}
spring.datasource.hikari.minimum-idle=${MIN_POOL_SIZE}
spring.datasource.hikari.connection-timeout=${CONNECTION_TIMEOUT}
spring.datasource.hikari.idle-timeout=${IDLE_TIMEOUT}
spring.datasource.hikari.max-lifetime=${MAX_LIFETIME}

# Transaction Configuration (JDBC-based, NOT JPA)
spring.datasource.hikari.auto-commit=false
```

3. Create separate profile files:
   - `application-dev.properties` for development
   - `application-test.properties` for testing
   - `application-prod.properties` for production

**Way to validate:**
- Application starts without database errors
- Connection pool initialized (check logs for HikariPool)
- No connection timeout errors
- Health endpoint shows database status
- Can connect to database using configured credentials

---

#### TASK-202: [PATTERN: JDBC_WRAPPER_IDENTIFICATION] Identify Custom JDBC Wrappers
**Purpose:** Locate all custom JDBC wrapper classes and database access patterns

**Pattern TAG:** JDBC_WRAPPER_IDENTIFICATION

**Requirements:**
- Identify custom framework classes that wrap JDBC operations
- Custom JDBC wrappers typically include:
  - Classes with methods like `executeQuery()`, `executeUpdate()`, `executeBatch()`
  - Classes managing `Connection`, `PreparedStatement`, `ResultSet`
  - Classes handling connection pooling or transaction management
  - Utility classes for SQL execution

**Actions to do:**
For each custom JDBC wrapper class identified:

1. Search for common patterns:
   ```bash
   # Find classes that obtain connections
   grep -r "getConnection()" src/
   grep -r "DataSource" src/ | grep -v "import"
   
   # Find classes executing SQL
   grep -r "PreparedStatement" src/
   grep -r "executeQuery\|executeUpdate" src/
   grep -r "ResultSet" src/
   ```

2. For each wrapper class found, document:
   - Class name and package
   - Methods provided (query execution, updates, batch operations, etc.)
   - How it obtains connections (DataSource lookup, connection pooling, etc.)
   - Transaction handling approach
   - Error handling patterns
   - Resource cleanup patterns (try-catch-finally, try-with-resources)

3. Identify all callers of these wrapper classes:
   ```bash
   grep -r "CustomJdbcWrapper" src/
   ```

4. Create `JDBC_WRAPPER_INVENTORY.md` with:
   - List of all wrapper classes
   - Their responsibilities
   - All calling classes
   - Migration priority (based on usage frequency)

**Way to validate:**
- All JDBC wrapper classes documented
- All calling code locations identified
- Usage patterns understood
- Migration approach defined for each wrapper

---

#### TASK-203: [PATTERN: JDBC_WRAPPER_MIGRATION] Migrate JDBC Wrappers to Spring JdbcTemplate
**Purpose:** Replace custom JDBC wrappers with Spring JdbcTemplate while maintaining existing SQL

**Pattern TAG:** JDBC_WRAPPER_MIGRATION

**Requirements:**
- Completed TASK-202
- Identified at least one custom JDBC wrapper class for migration
- Custom wrapper class characteristics:
  - Contains methods for executing SQL queries
  - Manages JDBC connections and resources
  - Handles ResultSet processing

**Actions to do:**
For each custom JDBC wrapper class (process one class at a time):

1. Create new Spring-managed DAO/Repository class:
   ```java
   package ${BASE_PACKAGE}.dao;
   
   import org.springframework.jdbc.core.JdbcTemplate;
   import org.springframework.jdbc.core.RowMapper;
   import org.springframework.stereotype.Repository;
   import org.springframework.transaction.annotation.Transactional;
   
   @Repository
   public class ${ENTITY_NAME}Dao {
       
       private final JdbcTemplate jdbcTemplate;
       
       public ${ENTITY_NAME}Dao(JdbcTemplate jdbcTemplate) {
           this.jdbcTemplate = jdbcTemplate;
       }
       
       // Methods migrated from custom wrapper
   }
   ```

2. For each method in the original wrapper class:
   
   **Query methods returning single object:**
   ```java
   // OLD: Custom wrapper
   public Customer findById(Long id) {
       Connection conn = dataSource.getConnection();
       try {
           PreparedStatement ps = conn.prepareStatement("SELECT * FROM customers WHERE id = ?");
           ps.setLong(1, id);
           ResultSet rs = ps.executeQuery();
           if (rs.next()) {
               return mapCustomer(rs);
           }
       } finally {
           conn.close();
       }
       return null;
   }
   
   // NEW: Spring JdbcTemplate
   public Customer findById(Long id) {
       try {
           return jdbcTemplate.queryForObject(
               "SELECT * FROM customers WHERE id = ?",
               new Object[]{id},
               (rs, rowNum) -> mapCustomer(rs)
           );
       } catch (EmptyResultDataAccessException e) {
           return null;  // No result found
       }
   }
   ```
   
   **Query methods returning list:**
   ```java
   // OLD: Custom wrapper
   public List<Customer> findAll() {
       // ... ResultSet iteration
   }
   
   // NEW: Spring JdbcTemplate
   public List<Customer> findAll() {
       return jdbcTemplate.query(
           "SELECT * FROM customers ORDER BY name",
           (rs, rowNum) -> mapCustomer(rs)
       );
   }
   ```
   
   **Update/Insert methods:**
   ```java
   // OLD: Custom wrapper
   public int update(Customer customer) {
       // ... PreparedStatement execution
   }
   
   // NEW: Spring JdbcTemplate
   @Transactional
   public int update(Customer customer) {
       return jdbcTemplate.update(
           "UPDATE customers SET name = ?, email = ?, phone = ? WHERE id = ?",
           customer.getName(),
           customer.getEmail(),
           customer.getPhone(),
           customer.getId()
       );
   }
   ```
   
   **Batch operations:**
   ```java
   // OLD: Custom wrapper with batch
   public int[] batchInsert(List<Customer> customers) {
       // ... batch PreparedStatement
   }
   
   // NEW: Spring JdbcTemplate
   @Transactional
   public int[] batchInsert(List<Customer> customers) {
       return jdbcTemplate.batchUpdate(
           "INSERT INTO customers (name, email, phone) VALUES (?, ?, ?)",
           customers,
           customers.size(),
           (ps, customer) -> {
               ps.setString(1, customer.getName());
               ps.setString(2, customer.getEmail());
               ps.setString(3, customer.getPhone());
           }
       );
   }
   ```

3. Create RowMapper helper methods:
   ```java
   private Customer mapCustomer(ResultSet rs) throws SQLException {
       Customer customer = new Customer();
       customer.setId(rs.getLong("id"));
       customer.setName(rs.getString("name"));
       customer.setEmail(rs.getString("email"));
       customer.setPhone(rs.getString("phone"));
       return customer;
   }
   ```

4. Update all calling classes to inject the new DAO:
   ```java
   // OLD: 
   private CustomJdbcWrapper wrapper = new CustomJdbcWrapper();
   
   // NEW:
   private final CustomerDao customerDao;
   
   public ServiceClass(CustomerDao customerDao) {
       this.customerDao = customerDao;
   }
   ```

5. Remove old wrapper class once all usages migrated

**Way to validate:**
- Modified DAO classes compile successfully
- All SQL queries preserved (no logic changes)
- Resource management handled by Spring (no connection leaks)
- All calling classes updated
- Unit tests pass with new DAO
- Integration tests show same behavior as before

---

## PHASE 2B: ENTITY BEAN MIGRATION (JDBC-BASED)

### SUBTITLE: Entity Beans to POJOs + JDBC DAOs

#### TASK-250: [PATTERN: CMP_ENTITY_BEAN_IDENTIFICATION] Identify CMP Entity Beans
**Purpose:** Locate all Container-Managed Persistence (CMP) Entity Beans in the application

**Pattern TAG:** CMP_ENTITY_BEAN_IDENTIFICATION

**Requirements:**
- Identify all CMP Entity Bean implementations
- A CMP Entity Bean typically consists of:
  - **Abstract bean class** with abstract getter/setter methods
  - **Home Interface** with create/finder methods
  - **Remote/Local Interface** with business methods
  - **ejb-jar.xml** entries with `<cmp-field>` and `<cmr-field>` declarations
  - Container handles ALL database operations

**Actions to do:**
1. Search for CMP Entity Bean implementations:
   ```bash
   # Find CMP entity bean classes (usually abstract)
   grep -r "abstract.*implements EntityBean" src/
   grep -r "public abstract.*get" src/
   grep -r "public abstract.*set" src/
   
   # Find in deployment descriptors
   grep -r "<entity>" ejb-jar.xml
   grep -r "<cmp-version>2.x</cmp-version>" ejb-jar.xml
   grep -r "<cmp-field>" ejb-jar.xml
   grep -r "<cmr-field>" ejb-jar.xml
   ```

2. For each CMP Entity Bean found, document:
   - Bean name (from ejb-jar.xml)
   - Bean class name
   - Home interface name
   - Remote/Local interface name
   - Primary key class (simple or composite)
   - CMP fields (persistent fields)
   - CMR fields (relationships)
   - Finder methods (custom queries)
   - ejbCreate methods
   - Abstract schema name (for EJB-QL)

3. Document relationships:
   - One-to-many relationships
   - Many-to-one relationships
   - Many-to-many relationships
   - Cascade options

4. Create `CMP_ENTITY_INVENTORY.md`

**Way to validate:**
- All CMP Entity Beans documented
- All CMP fields listed
- All CMR relationships mapped
- All finder methods identified
- Database table mappings understood

---

#### TASK-251: [PATTERN: CMP_TO_JDBC_DAO] Migrate CMP Entity Beans to POJOs + JDBC DAOs
**Purpose:** Convert CMP Entity Beans to Plain Old Java Objects with JDBC DAO layer

**Pattern TAG:** CMP_TO_JDBC_DAO

**Requirements:**
- Completed TASK-250
- Identified at least one CMP Entity Bean
- Understanding of database schema

**Actions to do:**
For each CMP Entity Bean (migrate one at a time):

1. **Create POJO class** (plain data holder, NO JPA annotations):
   ```java
   // OLD: CMP Entity Bean (abstract with container-managed persistence)
   public abstract class CustomerEJB implements EntityBean {
       // Abstract getters/setters - container implements these
       public abstract Long getId();
       public abstract void setId(Long id);
       
       public abstract String getName();
       public abstract void setName(String name);
       
       public abstract String getEmail();
       public abstract void setEmail(String email);
       
       // CMR relationship
       public abstract Collection getOrders();
       public abstract void setOrders(Collection orders);
       
       // ejbCreate method
       public CustomerPK ejbCreate(Long id, String name, String email) {
           setId(id);
           setName(name);
           setEmail(email);
           return null;
       }
       
       public void ejbPostCreate(Long id, String name, String email) {}
       
       // Finder methods declared in Home interface
       // public Customer findByEmail(String email);
   }
   
   // NEW: Simple POJO (NO annotations, pure Java)
   package ${BASE_PACKAGE}.model;
   
   import java.io.Serializable;
   
   public class Customer implements Serializable {
       private static final long serialVersionUID = 1L;
       
       private Long id;
       private String name;
       private String email;
       private String phone;
       
       // Plain constructors
       public Customer() {}
       
       public Customer(Long id, String name, String email) {
           this.id = id;
           this.name = name;
           this.email = email;
       }
       
       // Plain getters and setters (NO annotations)
       public Long getId() { return id; }
       public void setId(Long id) { this.id = id; }
       
       public String getName() { return name; }
       public void setName(String name) { this.name = name; }
       
       public String getEmail() { return email; }
       public void setEmail(String email) { this.email = email; }
       
       public String getPhone() { return phone; }
       public void setPhone(String phone) { this.phone = phone; }
       
       // Optional: toString, equals, hashCode
   }
   ```

2. **Create JDBC DAO for persistence operations**:
   ```java
   package ${BASE_PACKAGE}.dao;
   
   import ${BASE_PACKAGE}.model.Customer;
   import org.springframework.jdbc.core.JdbcTemplate;
   import org.springframework.jdbc.support.GeneratedKeyHolder;
   import org.springframework.jdbc.support.KeyHolder;
   import org.springframework.stereotype.Repository;
   import org.springframework.transaction.annotation.Transactional;
   import org.springframework.dao.EmptyResultDataAccessException;
   
   import java.sql.PreparedStatement;
   import java.sql.ResultSet;
   import java.sql.SQLException;
   import java.sql.Statement;
   import java.util.List;
   import java.util.Optional;
   
   @Repository
   public class CustomerDao {
       
       private final JdbcTemplate jdbcTemplate;
       
       public CustomerDao(JdbcTemplate jdbcTemplate) {
           this.jdbcTemplate = jdbcTemplate;
       }
       
       // Replaces ejbCreate
       @Transactional
       public Customer create(Customer customer) {
           if (customer.getId() == null) {
               // Auto-generated ID
               KeyHolder keyHolder = new GeneratedKeyHolder();
               jdbcTemplate.update(connection -> {
                   PreparedStatement ps = connection.prepareStatement(
                       "INSERT INTO customer (name, email, phone) VALUES (?, ?, ?)",
                       Statement.RETURN_GENERATED_KEYS
                   );
                   ps.setString(1, customer.getName());
                   ps.setString(2, customer.getEmail());
                   ps.setString(3, customer.getPhone());
                   return ps;
               }, keyHolder);
               customer.setId(keyHolder.getKey().longValue());
           } else {
               // Manual ID
               jdbcTemplate.update(
                   "INSERT INTO customer (id, name, email, phone) VALUES (?, ?, ?, ?)",
                   customer.getId(),
                   customer.getName(),
                   customer.getEmail(),
                   customer.getPhone()
               );
           }
           return customer;
       }
       
       // Replaces ejbStore (update)
       @Transactional
       public int update(Customer customer) {
           return jdbcTemplate.update(
               "UPDATE customer SET name = ?, email = ?, phone = ? WHERE id = ?",
               customer.getName(),
               customer.getEmail(),
               customer.getPhone(),
               customer.getId()
           );
       }
       
       // Replaces ejbRemove
       @Transactional
       public int delete(Long id) {
           return jdbcTemplate.update("DELETE FROM customer WHERE id = ?", id);
       }
       
       // Replaces ejbLoad (findByPrimaryKey)
       public Optional<Customer> findById(Long id) {
           try {
               Customer customer = jdbcTemplate.queryForObject(
                   "SELECT id, name, email, phone FROM customer WHERE id = ?",
                   new Object[]{id},
                   this::mapRow
               );
               return Optional.ofNullable(customer);
           } catch (EmptyResultDataAccessException e) {
               return Optional.empty();
           }
       }
       
       // Replaces custom finder methods
       public Optional<Customer> findByEmail(String email) {
           try {
               Customer customer = jdbcTemplate.queryForObject(
                   "SELECT id, name, email, phone FROM customer WHERE email = ?",
                   new Object[]{email},
                   this::mapRow
               );
               return Optional.ofNullable(customer);
           } catch (EmptyResultDataAccessException e) {
               return Optional.empty();
           }
       }
       
       // Replaces ejbFindAll or similar custom finders
       public List<Customer> findAll() {
           return jdbcTemplate.query(
               "SELECT id, name, email, phone FROM customer ORDER BY name",
               this::mapRow
           );
       }
       
       // Handle CMR (Container-Managed Relationships)
       // Example: Customer has many Orders (one-to-many)
       public List<Order> findOrdersByCustomerId(Long customerId) {
           return jdbcTemplate.query(
               "SELECT o.id, o.order_number, o.order_date, o.total_amount " +
               "FROM orders o WHERE o.customer_id = ?",
               new Object[]{customerId},
               (rs, rowNum) -> {
                   Order order = new Order();
                   order.setId(rs.getLong("id"));
                   order.setOrderNumber(rs.getString("order_number"));
                   order.setOrderDate(rs.getDate("order_date"));
                   order.setTotalAmount(rs.getBigDecimal("total_amount"));
                   return order;
               }
           );
       }
       
       // Handle composite primary keys (if entity uses composite key)
       public Optional<Customer> findByCompositeKey(Long id, String code) {
           try {
               Customer customer = jdbcTemplate.queryForObject(
                   "SELECT id, code, name, email, phone FROM customer " +
                   "WHERE id = ? AND code = ?",
                   new Object[]{id, code},
                   this::mapRow
               );
               return Optional.ofNullable(customer);
           } catch (EmptyResultDataAccessException e) {
               return Optional.empty();
           }
       }
       
       // RowMapper helper method
       private Customer mapRow(ResultSet rs, int rowNum) throws SQLException {
           Customer customer = new Customer();
           customer.setId(rs.getLong("id"));
           customer.setName(rs.getString("name"));
           customer.setEmail(rs.getString("email"));
           customer.setPhone(rs.getString("phone"));
           return customer;
       }
   }
   ```

3. **Update service classes** to use the new POJO and DAO:
   ```java
   // OLD: Session Bean using Entity Bean
   @Stateless
   public class CustomerServiceEJB {
       public Customer createCustomer(String name, String email) {
           CustomerHome home = lookup("CustomerEJB");
           return home.create(name, email);
       }
       
       public Customer findCustomer(Long id) {
           CustomerHome home = lookup("CustomerEJB");
           return home.findByPrimaryKey(id);
       }
   }
   
   // NEW: Spring Service using POJO and DAO
   package ${BASE_PACKAGE}.service;
   
   import ${BASE_PACKAGE}.model.Customer;
   import ${BASE_PACKAGE}.dao.CustomerDao;
   import org.springframework.stereotype.Service;
   import org.springframework.transaction.annotation.Transactional;
   
   @Service
   public class CustomerService {
       
       private final CustomerDao customerDao;
       
       public CustomerService(CustomerDao customerDao) {
           this.customerDao = customerDao;
       }
       
       @Transactional
       public Customer createCustomer(String name, String email) {
           Customer customer = new Customer();
           customer.setName(name);
           customer.setEmail(email);
           return customerDao.create(customer);
       }
       
       public Customer findCustomer(Long id) {
           return customerDao.findById(id)
               .orElseThrow(() -> new RuntimeException("Customer not found: " + id));
       }
   }
   ```

4. **Remove old EJB files** once migration is complete:
   - Remove `CustomerEJB.java` (entity bean class)
   - Remove `CustomerHome.java` (home interface)
   - Remove `CustomerRemote.java` or `CustomerLocal.java` (component interfaces)
   - Remove ejb-jar.xml entry for this entity

**Way to validate:**
- [ ] POJO class created with no EJB dependencies
- [ ] DAO class created with all CRUD operations
- [ ] All SQL queries preserved from original CMP configuration
- [ ] CMR relationships handled with explicit SQL JOINs or separate queries
- [ ] Composite key support added if needed
- [ ] Service classes updated to use new DAO
- [ ] Unit tests created for DAO operations
- [ ] Integration tests pass
- [ ] No references to old entity bean remain
- [ ] Application builds successfully
- [ ] Database operations work correctly

---

#### TASK-252: [PATTERN: BMP_ENTITY_BEAN_IDENTIFICATION] Identify BMP Entity Beans
**Purpose:** Locate all Bean-Managed Persistence (BMP) Entity Beans in the application

**Pattern TAG:** BMP_ENTITY_BEAN_IDENTIFICATION

**Requirements:**
- Identify all BMP Entity Bean implementations
- A BMP Entity Bean typically consists of:
  - **Concrete bean class** (not abstract) with manual persistence code
  - Implements EntityBean interface
  - Contains **ejbLoad()** and **ejbStore()** methods with JDBC code
  - Has **Home Interface** with create/finder methods
  - Has **Remote/Local Interface** with business methods
  - Developer manages ALL database operations manually

**Actions to do:**
1. Search for BMP Entity Bean implementations:
   ```bash
   # Find BMP entity bean classes (NOT abstract, implements EntityBean)
   grep -r "implements EntityBean" src/ | grep -v "abstract"
   
   # Find manual JDBC persistence code
   grep -r "ejbLoad()" src/
   grep -r "ejbStore()" src/
   grep -r "getConnection()" src/ | grep EntityBean
   
   # Find in deployment descriptors
   grep -r "<persistence-type>Bean</persistence-type>" ejb-jar.xml
   ```

2. For each BMP Entity Bean found, document:
   - Bean name (from ejb-jar.xml)
   - Bean class name
   - Home interface name
   - Remote/Local interface name
   - Primary key class
   - ejbLoad() implementation (SELECT queries)
   - ejbStore() implementation (UPDATE queries)
   - ejbCreate() implementation (INSERT queries)
   - ejbRemove() implementation (DELETE queries)
   - Custom finder methods (SELECT queries)
   - Connection management approach
   - Transaction handling

3. Document existing SQL patterns:
   - All SQL queries used
   - Parameter binding approach
   - ResultSet processing
   - Connection acquisition/release
   - Error handling

4. Create `BMP_ENTITY_INVENTORY.md`

**Way to validate:**
- All BMP Entity Beans documented
- All manual JDBC code catalogued
- SQL queries extracted and documented
- Connection management patterns understood
- Ready for migration to Spring JDBC DAO

---

#### TASK-253: [PATTERN: BMP_TO_JDBC_DAO] Migrate BMP Entity Beans to Spring JDBC DAOs
**Purpose:** Refactor Bean-Managed Persistence entities to use Spring JdbcTemplate

**Pattern TAG:** BMP_TO_JDBC_DAO

**Requirements:**
- Completed TASK-252
- Identified at least one BMP Entity Bean
- Understanding of existing JDBC code

**Actions to do:**
For each BMP Entity Bean (migrate one at a time):

1. **Create POJO class** (same as CMP migration in TASK-251):
   ```java
   // OLD: BMP Entity Bean with manual JDBC
   public class CustomerBMP implements EntityBean {
       private Long id;
       private String name;
       private String email;
       private String phone;
       
       private EntityContext context;
       
       // Manual JDBC in ejbLoad
       public void ejbLoad() {
           Long id = (Long) context.getPrimaryKey();
           Connection conn = null;
           try {
               InitialContext ctx = new InitialContext();
               DataSource ds = (DataSource) ctx.lookup("java:jboss/datasources/MyDS");
               conn = ds.getConnection();
               
               PreparedStatement ps = conn.prepareStatement(
                   "SELECT name, email, phone FROM customer WHERE id = ?"
               );
               ps.setLong(1, id);
               ResultSet rs = ps.executeQuery();
               
               if (rs.next()) {
                   this.name = rs.getString("name");
                   this.email = rs.getString("email");
                   this.phone = rs.getString("phone");
               }
           } finally {
               if (conn != null) conn.close();
           }
       }
       
       // Manual JDBC in ejbStore
       public void ejbStore() {
           Connection conn = null;
           try {
               InitialContext ctx = new InitialContext();
               DataSource ds = (DataSource) ctx.lookup("java:jboss/datasources/MyDS");
               conn = ds.getConnection();
               
               PreparedStatement ps = conn.prepareStatement(
                   "UPDATE customer SET name = ?, email = ?, phone = ? WHERE id = ?"
               );
               ps.setString(1, this.name);
               ps.setString(2, this.email);
               ps.setString(3, this.phone);
               ps.setLong(4, this.id);
               ps.executeUpdate();
           } finally {
               if (conn != null) conn.close();
           }
       }
       
       // Getters/setters...
   }
   
   // NEW: Simple POJO (same as TASK-251)
   package ${BASE_PACKAGE}.model;
   
   public class Customer implements Serializable {
       private Long id;
       private String name;
       private String email;
       private String phone;
       
       // Constructors, getters, setters
   }
   ```

2. **Create Spring JDBC DAO** refactoring existing JDBC code:
   ```java
   package ${BASE_PACKAGE}.dao;
   
   import ${BASE_PACKAGE}.model.Customer;
   import org.springframework.jdbc.core.JdbcTemplate;
   import org.springframework.jdbc.support.GeneratedKeyHolder;
   import org.springframework.jdbc.support.KeyHolder;
   import org.springframework.stereotype.Repository;
   import org.springframework.transaction.annotation.Transactional;
   import org.springframework.dao.EmptyResultDataAccessException;
   
   @Repository
   public class CustomerDao {
       
       private final JdbcTemplate jdbcTemplate;
       
       public CustomerDao(JdbcTemplate jdbcTemplate) {
           this.jdbcTemplate = jdbcTemplate;
       }
       
       // Replaces ejbLoad() - use existing SQL from BMP
       public Optional<Customer> findById(Long id) {
           try {
               // Reuse EXACT SQL from ejbLoad()
               return Optional.ofNullable(jdbcTemplate.queryForObject(
                   "SELECT name, email, phone FROM customer WHERE id = ?",
                   new Object[]{id},
                   (rs, rowNum) -> {
                       Customer customer = new Customer();
                       customer.setId(id);
                       customer.setName(rs.getString("name"));
                       customer.setEmail(rs.getString("email"));
                       customer.setPhone(rs.getString("phone"));
                       return customer;
                   }
               ));
           } catch (EmptyResultDataAccessException e) {
               return Optional.empty();
           }
       }
       
       // Replaces ejbStore() - use existing SQL from BMP
       @Transactional
       public int update(Customer customer) {
           // Reuse EXACT SQL from ejbStore()
           return jdbcTemplate.update(
               "UPDATE customer SET name = ?, email = ?, phone = ? WHERE id = ?",
               customer.getName(),
               customer.getEmail(),
               customer.getPhone(),
               customer.getId()
           );
       }
       
       // Replaces ejbCreate() - use existing SQL from BMP
       @Transactional
       public Customer create(Customer customer) {
           // Reuse EXACT SQL from ejbCreate()
           if (customer.getId() == null) {
               KeyHolder keyHolder = new GeneratedKeyHolder();
               jdbcTemplate.update(connection -> {
                   PreparedStatement ps = connection.prepareStatement(
                       "INSERT INTO customer (name, email, phone) VALUES (?, ?, ?)",
                       Statement.RETURN_GENERATED_KEYS
                   );
                   ps.setString(1, customer.getName());
                   ps.setString(2, customer.getEmail());
                   ps.setString(3, customer.getPhone());
                   return ps;
               }, keyHolder);
               customer.setId(keyHolder.getKey().longValue());
           } else {
               jdbcTemplate.update(
                   "INSERT INTO customer (id, name, email, phone) VALUES (?, ?, ?, ?)",
                   customer.getId(),
                   customer.getName(),
                   customer.getEmail(),
                   customer.getPhone()
               );
           }
           return customer;
       }
       
       // Replaces ejbRemove() - use existing SQL from BMP
       @Transactional
       public int delete(Long id) {
           // Reuse EXACT SQL from ejbRemove()
           return jdbcTemplate.update("DELETE FROM customer WHERE id = ?", id);
       }
       
       // Replaces custom finder methods - use existing SQL from BMP
       public Optional<Customer> findByEmail(String email) {
           try {
               // Reuse SQL from custom finder in BMP
               return Optional.ofNullable(jdbcTemplate.queryForObject(
                   "SELECT id, name, email, phone FROM customer WHERE email = ?",
                   new Object[]{email},
                   this::mapRow
               ));
           } catch (EmptyResultDataAccessException e) {
               return Optional.empty();
           }
       }
       
       private Customer mapRow(ResultSet rs, int rowNum) throws SQLException {
           Customer customer = new Customer();
           customer.setId(rs.getLong("id"));
           customer.setName(rs.getString("name"));
           customer.setEmail(rs.getString("email"));
           customer.setPhone(rs.getString("phone"));
           return customer;
       }
   }
   ```

3. **Key differences from CMP migration**:
   - BMP already has SQL queries - reuse them exactly
   - No need to reverse-engineer SQL from EJB-QL
   - Focus on refactoring JDBC code to JdbcTemplate patterns
   - Connection management automatically handled by Spring
   - Transaction management via @Transactional

4. **Update service classes** (same as TASK-251)

5. **Remove old EJB files** once migration complete

**Way to validate:**
- [ ] POJO class created
- [ ] DAO class created using existing BMP SQL queries
- [ ] All SQL queries preserved exactly
- [ ] Connection management removed (handled by Spring)
- [ ] Transaction management via @Transactional
- [ ] Service classes updated
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] No references to old BMP bean remain
- [ ] Application builds successfully

---

## PHASE 3: SESSION BEAN MIGRATION

### SUBTITLE: Session Beans to Spring Services

#### TASK-300: [PATTERN: SESSION_BEAN_IDENTIFICATION] Identify Session Beans
**Purpose:** Locate all Session Bean implementations (Stateless and Stateful)

**Pattern TAG:** SESSION_BEAN_IDENTIFICATION

**Requirements:**
- Identify all Session Bean implementations
- Session Beans include:
  - **Stateless Session Beans** - no conversational state
  - **Stateful Session Beans** - maintain conversational state
  - Both implement SessionBean interface or use annotations
  - Have Home/Local/Remote interfaces

**Actions to do:**
1. Search for Session Bean implementations:
   ```bash
   # Find Session Bean classes
   grep -r "implements SessionBean" src/
   grep -r "@Stateless" src/
   grep -r "@Stateful" src/
   
   # Find in deployment descriptors
   grep -r "<session-type>Stateless</session-type>" ejb-jar.xml
   grep -r "<session-type>Stateful</session-type>" ejb-jar.xml
   ```

2. For each Session Bean found, document:
   - Bean name
   - Type (Stateless vs Stateful)
   - Bean class name
   - Home interface
   - Remote/Local interface
   - Business methods
   - Transaction attributes
   - Security roles
   - Injected resources (@Resource, @EJB)
   - Lifecycle methods (ejbCreate, ejbActivate, ejbPassivate, ejbRemove)

3. Document dependencies:
   - Other EJBs used
   - JMS resources
   - DataSource usage
   - External services

4. Create `SESSION_BEAN_INVENTORY.md`

**Way to validate:**
- All Session Beans documented
- Bean types identified (Stateless/Stateful)
- All business methods listed
- Dependencies mapped
- Transaction requirements understood

---

#### TASK-301: [PATTERN: STATELESS_TO_SPRING_SERVICE] Migrate Stateless Session Beans
**Purpose:** Convert Stateless Session Beans to Spring @Service components

**Pattern TAG:** STATELESS_TO_SPRING_SERVICE

**Requirements:**
- Completed TASK-300
- Identified at least one Stateless Session Bean

**Actions to do:**
For each Stateless Session Bean (migrate one at a time):

1. **Create Spring Service class**:
   ```java
   // OLD: Stateless Session Bean
   @Stateless
   @TransactionAttribute(TransactionAttributeType.REQUIRED)
   public class OrderServiceEJB implements OrderService {
       
       @Resource
       private SessionContext sessionContext;
       
       @EJB
       private CustomerServiceLocal customerService;
       
       @Resource(mappedName = "java:jboss/datasources/MyDS")
       private DataSource dataSource;
       
       public Order createOrder(Long customerId, List<OrderItem> items) {
           Customer customer = customerService.findById(customerId);
           // Business logic
           return order;
       }
       
       public void cancelOrder(Long orderId) {
           // Business logic
           sessionContext.setRollbackOnly();
       }
   }
   
   // NEW: Spring Service
   package ${BASE_PACKAGE}.service;
   
   import ${BASE_PACKAGE}.model.Order;
   import ${BASE_PACKAGE}.model.OrderItem;
   import ${BASE_PACKAGE}.dao.OrderDao;
   import org.springframework.stereotype.Service;
   import org.springframework.transaction.annotation.Transactional;
   import org.springframework.transaction.interceptor.TransactionAspectSupport;
   
   @Service
   @Transactional  // Default to REQUIRED propagation
   public class OrderService {
       
       private final CustomerService customerService;
       private final OrderDao orderDao;
       
       // Constructor injection (replaces @EJB)
       public OrderService(CustomerService customerService, OrderDao orderDao) {
           this.customerService = customerService;
           this.orderDao = orderDao;
       }
       
       public Order createOrder(Long customerId, List<OrderItem> items) {
           Customer customer = customerService.findById(customerId);
           // Business logic (same as before)
           return order;
       }
       
       public void cancelOrder(Long orderId) {
           // Business logic
           // Replace sessionContext.setRollbackOnly() with:
           TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
       }
   }
   ```

2. **Handle EJBContext operations**:
   ```java
   // OLD: EJBContext usage
   sessionContext.getCallerPrincipal();  // Get security principal
   sessionContext.isCallerInRole("admin");  // Check role
   sessionContext.setRollbackOnly();  // Mark transaction for rollback
   sessionContext.getRollbackOnly();  // Check rollback status
   
   // NEW: Spring equivalents
   // For security (add spring-boot-starter-security dependency)
   import org.springframework.security.core.Authentication;
   import org.springframework.security.core.context.SecurityContextHolder;
   
   Authentication auth = SecurityContextHolder.getContext().getAuthentication();
   String username = auth.getName();  // Get principal
   boolean isAdmin = auth.getAuthorities().stream()
       .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));  // Check role
   
   // For transaction management
   import org.springframework.transaction.interceptor.TransactionAspectSupport;
   
   TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
   boolean rollbackOnly = TransactionAspectSupport.currentTransactionStatus().isRollbackOnly();
   ```

3. **Handle transaction attributes**:
   ```java
   // OLD: EJB transaction attributes
   @TransactionAttribute(TransactionAttributeType.REQUIRED)  // Default
   @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
   @TransactionAttribute(TransactionAttributeType.MANDATORY)
   @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
   @TransactionAttribute(TransactionAttributeType.NEVER)
   
   // NEW: Spring transaction propagation
   @Transactional(propagation = Propagation.REQUIRED)  // Default
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   @Transactional(propagation = Propagation.MANDATORY)
   @Transactional(propagation = Propagation.NOT_SUPPORTED)
   @Transactional(propagation = Propagation.NEVER)
   ```

4. **Remove old EJB files**:
   - Remove Session Bean implementation class
   - Remove Home interface
   - Remove Remote/Local interface
   - Remove ejb-jar.xml entry

**Way to validate:**
- [ ] Spring @Service created
- [ ] All business methods migrated
- [ ] Dependencies injected via constructor
- [ ] Transaction propagation configured correctly
- [ ] EJBContext operations replaced
- [ ] Security checks migrated (if applicable)
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] No EJB dependencies remain

---

## PHASE 9: JDK VERSION UPGRADE (8 → 21)

**Objective:** Upgrade from JDK 8 to JDK 21 with intermediate stops at JDK 11 and 17

**Complexity:** HIGH  
**Estimated Effort:** 5-10 days  
**Dependencies:** Phases 1-8 (Application fully migrated to Spring Boot)

**Migration Path:** JDK 8 → JDK 11 → JDK 17 → JDK 21

---

#### TASK-900: [PATTERN: LIBRARY_COMPATIBILITY_ANALYSIS] Analyze Library Compatibility

**Purpose:** Identify all dependencies that need updates for JDK compatibility

**Pattern TAG:** LIBRARY_COMPATIBILITY_ANALYSIS

**Requirements:**
- Application running on JDK 8 with Spring Boot 2.7.x
- Target: JDK 21 with Spring Boot 3.x

**Actions to do:**

1. **Analyze current dependencies**:
   ```bash
   # List all dependencies
   mvn dependency:tree > dependencies.txt
   
   # Check for javax.* packages
   grep -r "import javax\." src/
   ```

2. **Document compatibility requirements**:
   
   **JDK 11 Requirements:**
   - Spring Boot 2.1+ (already on 2.7.x ✓)
   - All libraries must support JDK 11
   
   **JDK 17 Requirements:**
   - Spring Boot 2.5+ (already on 2.7.x ✓)
   - Strong encapsulation enforced
   
   **JDK 21 + Spring Boot 3.x Requirements:**
   - **MAJOR CHANGE:** javax.* → jakarta.* namespace
   - Spring Boot 3.0+ requires JDK 17 minimum
   - All dependencies must use Jakarta EE 9+

3. **Create dependency upgrade matrix**:
   ```markdown
   # DEPENDENCY_UPGRADE_MATRIX.md
   
   | Library | Current Version | JDK 11 Version | JDK 17 Version | JDK 21 + SB3 Version | Notes |
   |---------|----------------|----------------|----------------|---------------------|--------|
   | Spring Boot | 2.7.18 | 2.7.18 | 2.7.18 | 3.2.x | Major: jakarta namespace |
   | Jackson | 2.13.x | 2.13.x | 2.13.x | 2.15.x | Minor updates |
   | Log4j2 | 2.17.x | 2.17.x | 2.20.x | 2.21.x | Security fixes |
   | Commons Lang | 3.12.0 | 3.12.0 | 3.12.0 | 3.14.0 | Maintained |
   | javax.* APIs | 1.x | 1.x | 1.x | jakarta.* 3.x | Namespace change |
   ```

4. **Identify removed JDK APIs**:
   - **JDK 11:** Removed CORBA, Java EE modules (java.xml.ws, java.xml.bind)
   - **JDK 17:** Removed Security Manager, Applet API
   - **JDK 21:** Continued deprecations

**Way to validate:**
- [ ] All dependencies documented
- [ ] Compatibility matrix created
- [ ] Removed APIs identified
- [ ] Migration path defined

---

#### TASK-901: [PATTERN: JDK_API_UPDATES] Update JDK APIs and Language Features

**Purpose:** Migrate code to use new JDK APIs and remove deprecated features

**Pattern TAG:** JDK_API_UPDATES

**Requirements:**
- Completed TASK-900
- Understanding of JDK changes

**Actions to do:**

1. **JDK 8 → 11 Migration**:
   
   **Add missing Java EE modules** (temporary):
   ```xml
   <!-- JDK 11 removed Java EE modules - add explicitly if still on Spring Boot 2.x -->
   <dependency>
       <groupId>javax.xml.bind</groupId>
       <artifactId>jaxb-api</artifactId>
       <version>2.3.1</version>
   </dependency>
   <dependency>
       <groupId>javax.annotation</groupId>
       <artifactId>javax.annotation-api</artifactId>
       <version>1.3.2</version>
   </dependency>
   ```
   
   **Use new HTTP Client**:
   ```java
   // OLD: HttpURLConnection
   URL url = new URL("https://api.example.com");
   HttpURLConnection conn = (HttpURLConnection) url.openConnection();
   
   // NEW: Java 11 HTTP Client
   HttpClient client = HttpClient.newHttpClient();
   HttpRequest request = HttpRequest.newBuilder()
       .uri(URI.create("https://api.example.com"))
       .build();
   HttpResponse<String> response = client.send(request, 
       HttpResponse.BodyHandlers.ofString());
   ```

2. **JDK 11 → 17 Migration**:
   
   **Remove Security Manager** (deprecated):
   ```java
   // OLD: Security Manager usage
   SecurityManager sm = System.getSecurityManager();
   if (sm != null) {
       sm.checkPermission(new RuntimePermission("shutdownHooks"));
   }
   
   // NEW: Remove - no longer supported in JDK 17+
   // Implement security through other means (Spring Security, etc.)
   ```
   
   **Update for strong encapsulation**:
   ```bash
   # If accessing internal APIs, add JVM flags (temporary):
   --add-opens java.base/java.lang=ALL-UNNAMED
   --add-opens java.base/java.util=ALL-UNNAMED
   
   # Better: Refactor to avoid internal API usage
   ```

3. **JDK 17 → 21 Migration**:
   
   **Use modern language features**:
   ```java
   // Use Records (JDK 14+)
   // OLD: POJO
   public class Customer {
       private Long id;
       private String name;
       // getters, setters, equals, hashCode, toString
   }
   
   // NEW: Record (immutable data carrier)
   public record CustomerDTO(Long id, String name, String email) {}
   
   // Use Pattern Matching (JDK 16+)
   // OLD: instanceof with cast
   if (obj instanceof String) {
       String s = (String) obj;
       System.out.println(s.toLowerCase());
   }
   
   // NEW: Pattern matching
   if (obj instanceof String s) {
       System.out.println(s.toLowerCase());
   }
   
   // Use Switch Expressions (JDK 14+)
   // OLD: switch statement
   String result;
   switch (day) {
       case MONDAY:
       case FRIDAY:
           result = "Work day";
           break;
       case SATURDAY:
       case SUNDAY:
           result = "Weekend";
           break;
       default:
           result = "Unknown";
   }
   
   // NEW: Switch expression
   String result = switch (day) {
       case MONDAY, FRIDAY -> "Work day";
       case SATURDAY, SUNDAY -> "Weekend";
       default -> "Unknown";
   };
   
   // Use Text Blocks (JDK 15+)
   // OLD: Multiline strings
   String json = "{\n" +
                 "  \"name\": \"John\",\n" +
                 "  \"age\": 30\n" +
                 "}";
   
   // NEW: Text blocks
   String json = """
       {
         "name": "John",
         "age": 30
       }
       """;
   ```

**Way to validate:**
- [ ] Java EE modules handled (JDK 11)
- [ ] Security Manager removed (JDK 17)
- [ ] Strong encapsulation issues resolved
- [ ] Modern language features adopted
- [ ] Application compiles on target JDK
- [ ] All tests pass

---

#### TASK-902: [PATTERN: SPRING_BOOT_VERSION_UPGRADE] Upgrade to Spring Boot 3.x

**Purpose:** Migrate from Spring Boot 2.7.x to Spring Boot 3.x (requires JDK 17+)

**Pattern TAG:** SPRING_BOOT_VERSION_UPGRADE

**Requirements:**
- JDK 17 or 21 installed
- Application running on Spring Boot 2.7.x
- All tests passing

**Actions to do:**

1. **Update Spring Boot version**:
   ```xml
   <!-- pom.xml -->
   <parent>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-parent</artifactId>
       <!-- OLD: <version>2.7.18</version> -->
       <version>3.2.1</version>
   </parent>
   
   <properties>
       <!-- OLD: <java.version>8</java.version> -->
       <java.version>17</java.version>  <!-- or 21 -->
   </properties>
   ```

2. **Migrate javax.* to jakarta.* namespace**:
   ```bash
   # Search and replace across entire codebase
   find src -name "*.java" -exec sed -i 's/import javax\.servlet/import jakarta.servlet/g' {} +
   find src -name "*.java" -exec sed -i 's/import javax\.persistence/import jakarta.persistence/g' {} +
   find src -name "*.java" -exec sed -i 's/import javax\.validation/import jakarta.validation/g' {} +
   find src -name "*.java" -exec sed -i 's/import javax\.annotation/import jakarta.annotation/g' {} +
   find src -name "*.java" -exec sed -i 's/import javax\.transaction/import jakarta.transaction/g' {} +
   ```
   
   **Manual updates needed**:
   ```java
   // OLD: javax imports
   import javax.servlet.http.HttpServletRequest;
   import javax.annotation.PostConstruct;
   import javax.validation.Valid;
   import javax.transaction.Transactional;
   
   // NEW: jakarta imports
   import jakarta.servlet.http.HttpServletRequest;
   import jakarta.annotation.PostConstruct;
   import jakarta.validation.Valid;
   import jakarta.transaction.Transactional;
   ```

3. **Update configuration properties**:
   ```properties
   # application.properties changes
   
   # OLD: Spring Boot 2.x
   spring.jpa.hibernate.ddl-auto=update
   spring.datasource.initialization-mode=always
   
   # NEW: Spring Boot 3.x
   spring.jpa.hibernate.ddl-auto=update
   spring.sql.init.mode=always  # Changed property name
   ```

4. **Update dependencies**:
   ```xml
   <!-- Remove explicit javax.* dependencies -->
   <!-- These are now pulled in as jakarta.* by Spring Boot 3 -->
   
   <!-- Update other dependencies to jakarta-compatible versions -->
   <dependency>
       <groupId>org.hibernate.validator</groupId>
       <artifactId>hibernate-validator</artifactId>
       <!-- Version managed by Spring Boot 3 -->
   </dependency>
   ```

5. **Handle breaking changes**:
   ```java
   // Spring Security changes
   // OLD: WebSecurityConfigurerAdapter (removed in Spring Boot 3)
   @Configuration
   public class SecurityConfig extends WebSecurityConfigurerAdapter {
       @Override
       protected void configure(HttpSecurity http) throws Exception {
           http.authorizeRequests()
               .anyRequest().authenticated();
       }
   }
   
   // NEW: Component-based security
   @Configuration
   public class SecurityConfig {
       @Bean
       public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
           http.authorizeHttpRequests(auth -> auth
               .anyRequest().authenticated()
           );
           return http.build();
       }
   }
   
   // Actuator endpoint changes
   // OLD: management.metrics.export.simple.enabled
   // NEW: management.simple.metrics.export.enabled
   ```

6. **Test thoroughly**:
   ```bash
   # Clean build
   mvn clean install
   
   # Run all tests
   mvn test
   
   # Run with new profile
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

**Way to validate:**
- [ ] Spring Boot 3.x configured
- [ ] JDK 17 or 21 set as target
- [ ] All javax.* imports replaced with jakarta.*
- [ ] Configuration properties updated
- [ ] Dependencies updated
- [ ] Breaking changes addressed
- [ ] Application compiles successfully
- [ ] All tests pass
- [ ] Application runs without errors
- [ ] Endpoints accessible and functional

---

## PHASE 10: LEGACY CODE REFACTORING (ANTIPATTERNS)

**Objective:** Refactor common legacy antipatterns to modern patterns

**Complexity:** MEDIUM-HIGH  
**Estimated Effort:** Variable (ongoing improvement)  
**Dependencies:** Phases 1-8 (Core migration complete)

---

#### TASK-1000: [PATTERN: DEEP_INHERITANCE_REFACTORING] Refactor Deep Inheritance Hierarchies

**Purpose:** Replace deep inheritance (>3 levels) with composition or flatter structures

**Pattern TAG:** DEEP_INHERITANCE_REFACTORING

**Requirements:**
- Identify inheritance hierarchies >3 levels deep
- Legacy code often has BaseBase...Base class patterns

**Actions to do:**

1. **Identify deep hierarchies**:
   ```bash
   # Use IDE or code analysis tools to find inheritance depth
   # Look for patterns like: Base → AbstractBase → GenericBase → Specific
   ```

2. **Refactor using composition**:
   ```java
   // OLD: Deep inheritance (antipattern)
   public abstract class BaseEntity {
       protected Long id;
       protected Date createdDate;
   }
   
   public abstract class AuditableEntity extends BaseEntity {
       protected String createdBy;
       protected Date modifiedDate;
       protected String modifiedBy;
   }
   
   public abstract class BusinessEntity extends AuditableEntity {
       protected String status;
       protected String description;
   }
   
   public class Customer extends BusinessEntity {
       private String name;
       private String email;
   }
   
   // NEW: Composition over inheritance
   public class AuditInfo {
       private String createdBy;
       private Date createdDate;
       private String modifiedBy;
       private Date modifiedDate;
   }
   
   public class Customer {
       private Long id;
       private String name;
       private String email;
       private String status;
       private String description;
       private AuditInfo auditInfo;  // Composed, not inherited
   }
   ```

3. **Use interfaces for behavior**:
   ```java
   // Define interfaces for capabilities
   public interface Auditable {
       AuditInfo getAuditInfo();
       void setAuditInfo(AuditInfo auditInfo);
   }
   
   public interface Identifiable {
       Long getId();
       void setId(Long id);
   }
   
   // Implement multiple interfaces
   public class Customer implements Identifiable, Auditable {
       private Long id;
       private AuditInfo auditInfo;
       // Implementation
   }
   ```

**Way to validate:**
- [ ] Deep hierarchies identified
- [ ] Inheritance depth reduced to ≤3 levels
- [ ] Composition used where appropriate
- [ ] Interfaces define behaviors
- [ ] Tests still pass
- [ ] Code more maintainable

---

#### TASK-1001: [PATTERN: SINGLETON_MODERNIZATION] Modernize Singleton Pattern

**Purpose:** Replace manual Singleton implementations with Spring dependency injection

**Pattern TAG:** SINGLETON_MODERNIZATION

**Requirements:**
- Identify hand-rolled Singleton patterns
- Common in legacy code for "global" access

**Actions to do:**

1. **Identify Singletons**:
   ```bash
   grep -r "private static.*getInstance" src/
   grep -r "private.*constructor" src/
   ```

2. **Refactor to Spring beans**:
   ```java
   // OLD: Manual Singleton (antipattern)
   public class ConfigurationManager {
       private static ConfigurationManager instance;
       private Properties config;
       
       private ConfigurationManager() {
           // Load configuration
           config = loadConfig();
       }
       
       public static ConfigurationManager getInstance() {
           if (instance == null) {
               synchronized (ConfigurationManager.class) {
                   if (instance == null) {
                       instance = new ConfigurationManager();
                   }
               }
           }
           return instance;
       }
       
       public String getProperty(String key) {
           return config.getProperty(key);
       }
   }
   
   // Usage in old code:
   String value = ConfigurationManager.getInstance().getProperty("api.url");
   
   // NEW: Spring @Bean (singleton by default)
   @Configuration
   public class AppConfig {
       @Bean
       public ConfigurationManager configurationManager() {
           return new ConfigurationManager();
       }
   }
   
   @Component
   public class ConfigurationManager {
       private final Properties config;
       
       public ConfigurationManager() {
           config = loadConfig();
       }
       
       public String getProperty(String key) {
           return config.getProperty(key);
       }
   }
   
   // Usage with Spring DI:
   @Service
   public class MyService {
       private final ConfigurationManager configManager;
       
       public MyService(ConfigurationManager configManager) {
           this.configManager = configManager;
       }
       
       public void doSomething() {
           String value = configManager.getProperty("api.url");
       }
   }
   ```

3. **Better: Use Spring @Value or @ConfigurationProperties**:
   ```java
   // Even better: Use Spring's property management
   @Service
   public class MyService {
       @Value("${api.url}")
       private String apiUrl;
       
       @Value("${api.timeout:30000}")
       private int timeout;
   }
   
   // Or for grouped properties:
   @ConfigurationProperties(prefix = "api")
   @Component
   public class ApiConfig {
       private String url;
       private int timeout;
       // Getters and setters
   }
   ```

**Way to validate:**
- [ ] Manual Singletons identified
- [ ] Converted to Spring beans
- [ ] Static getInstance() methods removed
- [ ] Dependency injection used
- [ ] Tests pass
- [ ] Thread-safety maintained

---

#### TASK-1002: [PATTERN: GOD_CLASS_DECOMPOSITION] Decompose God Classes

**Purpose:** Break down large classes (>1000 lines or >20 methods) into smaller, focused classes

**Pattern TAG:** GOD_CLASS_DECOMPOSITION

**Requirements:**
- Identify classes violating Single Responsibility Principle
- Classes handling too many concerns

**Actions to do:**

1. **Identify God Classes**:
   ```bash
   # Find large files
   find src -name "*.java" -exec wc -l {} + | sort -rn | head -20
   
   # Count methods per class (use IDE or script)
   ```

2. **Decompose by responsibility**:
   ```java
   // OLD: God Class (antipattern) - 1500+ lines
   @Service
   public class CustomerService {
       // Customer CRUD
       public Customer createCustomer(...) {}
       public Customer updateCustomer(...) {}
       public void deleteCustomer(...) {}
       
       // Order management
       public Order createOrder(...) {}
       public List<Order> getCustomerOrders(...) {}
       
       // Email notifications
       public void sendWelcomeEmail(...) {}
       public void sendOrderConfirmation(...) {}
       
       // Reporting
       public Report generateCustomerReport(...) {}
       public Statistics calculateStatistics(...) {}
       
       // Payment processing
       public Payment processPayment(...) {}
       public void refundPayment(...) {}
       
       // 20+ more methods...
   }
   
   // NEW: Decomposed into focused services
   @Service
   public class CustomerService {
       private final CustomerDao customerDao;
       
       public Customer create(Customer customer) {}
       public Customer update(Customer customer) {}
       public void delete(Long id) {}
       public Customer findById(Long id) {}
   }
   
   @Service
   public class OrderService {
       private final OrderDao orderDao;
       private final CustomerService customerService;
       
       public Order createOrder(Long customerId, List<OrderItem> items) {}
       public List<Order> findByCustomer(Long customerId) {}
   }
   
   @Service
   public class NotificationService {
       private final EmailSender emailSender;
       
       public void sendWelcomeEmail(Customer customer) {}
       public void sendOrderConfirmation(Order order) {}
   }
   
   @Service
   public class ReportingService {
       public Report generateCustomerReport(Long customerId) {}
       public Statistics calculateStatistics() {}
   }
   
   @Service
   public class PaymentService {
       public Payment processPayment(Order order) {}
       public void refundPayment(Long paymentId) {}
   }
   ```

**Way to validate:**
- [ ] Large classes identified (>1000 lines)
- [ ] Responsibilities separated
- [ ] Each class has single clear purpose
- [ ] Dependencies properly injected
- [ ] Tests refactored and passing
- [ ] Code easier to understand and maintain

---

#### TASK-1003: [PATTERN: STATIC_UTILITY_REFACTORING] Refactor Static Utility Classes

**Purpose:** Convert static utility classes to Spring-managed components for better testability

**Pattern TAG:** STATIC_UTILITY_REFACTORING

**Requirements:**
- Identify utility classes with only static methods
- Hard to test, hard to mock

**Actions to do:**

1. **Identify static utilities**:
   ```bash
   grep -r "public static.*Utils" src/
   grep -r "public final class.*Util" src/
   ```

2. **Refactor to Spring components**:
   ```java
   // OLD: Static utility class (antipattern for complex logic)
   public final class DateUtils {
       private DateUtils() {} // Prevent instantiation
       
       public static String formatDate(Date date) {
           SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
           return sdf.format(date);
       }
       
       public static Date parseDate(String dateStr) throws ParseException {
           SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
           return sdf.parse(dateStr);
       }
       
       public static boolean isWeekend(Date date) {
           Calendar cal = Calendar.getInstance();
           cal.setTime(date);
           int day = cal.get(Calendar.DAY_OF_WEEK);
           return day == Calendar.SATURDAY || day == Calendar.SUNDAY;
       }
   }
   
   // Usage in old code:
   String formatted = DateUtils.formatDate(new Date());
   
   // NEW: Spring @Component
   @Component
   public class DateFormatter {
       private final DateTimeFormatter formatter;
       
       public DateFormatter() {
           this.formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
       }
       
       public String format(LocalDate date) {
           return date.format(formatter);
       }
       
       public LocalDate parse(String dateStr) {
           return LocalDate.parse(dateStr, formatter);
       }
       
       public boolean isWeekend(LocalDate date) {
           DayOfWeek day = date.getDayOfWeek();
           return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
       }
   }
   
   // Usage with Spring DI:
   @Service
   public class MyService {
       private final DateFormatter dateFormatter;
       
       public MyService(DateFormatter dateFormatter) {
           this.dateFormatter = dateFormatter;
       }
       
       public void doSomething() {
           String formatted = dateFormatter.format(LocalDate.now());
       }
   }
   ```

3. **When static is OK**:
   ```java
   // Keep static for true utilities (no state, no dependencies)
   public final class StringUtils {
       private StringUtils() {}
       
       public static boolean isEmpty(String str) {
           return str == null || str.isEmpty();
       }
       
       public static String capitalize(String str) {
           if (isEmpty(str)) return str;
           return str.substring(0, 1).toUpperCase() + str.substring(1);
       }
   }
   ```

**Way to validate:**
- [ ] Static utility classes identified
- [ ] Complex utilities converted to @Component
- [ ] Simple utilities kept static
- [ ] Dependency injection used
- [ ] Unit tests easier to write
- [ ] Can mock dependencies in tests

---

#### TASK-1004: [PATTERN: CHECKED_EXCEPTION_MODERNIZATION] Modernize Exception Handling

**Purpose:** Reduce excessive checked exceptions, use runtime exceptions appropriately

**Pattern TAG:** CHECKED_EXCEPTION_MODERNIZATION

**Requirements:**
- Legacy code often overuses checked exceptions
- Forced exception handling clutters code

**Actions to do:**

1. **Identify exception patterns**:
   ```bash
   grep -r "throws.*Exception" src/
   grep -r "catch (Exception" src/
   ```

2. **Modernize exception handling**:
   ```java
   // OLD: Excessive checked exceptions (antipattern)
   public Customer findCustomer(Long id) throws CustomerNotFoundException, 
                                               DatabaseException, 
                                               ValidationException {
       try {
           validateId(id);
           return customerDao.findById(id);
       } catch (SQLException e) {
           throw new DatabaseException("Database error", e);
       } catch (IllegalArgumentException e) {
           throw new ValidationException("Invalid ID", e);
       }
   }
   
   // Forces callers to handle:
   try {
       Customer customer = service.findCustomer(1L);
   } catch (CustomerNotFoundException e) {
       // handle
   } catch (DatabaseException e) {
       // handle
   } catch (ValidationException e) {
       // handle
   }
   
   // NEW: Use runtime exceptions for unexpected errors
   public Optional<Customer> findCustomer(Long id) {
       validateId(id);  // throws IllegalArgumentException (runtime)
       return customerDao.findById(id);  // Returns Optional
   }
   
   // Or throw runtime exception if not found
   public Customer findCustomerOrThrow(Long id) {
       return customerDao.findById(id)
           .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + id));
   }
   
   // Define custom runtime exceptions
   public class CustomerNotFoundException extends RuntimeException {
       public CustomerNotFoundException(String message) {
           super(message);
       }
   }
   
   // Callers can choose to handle or let it propagate
   Customer customer = service.findCustomerOrThrow(1L);  // No try-catch needed
   
   // Global exception handler catches unhandled exceptions
   @ControllerAdvice
   public class GlobalExceptionHandler {
       @ExceptionHandler(CustomerNotFoundException.class)
       public ResponseEntity<ErrorResponse> handleNotFound(CustomerNotFoundException ex) {
           return ResponseEntity.status(HttpStatus.NOT_FOUND)
               .body(new ErrorResponse(ex.getMessage()));
       }
   }
   ```

3. **Use Optional for missing values**:
   ```java
   // OLD: null checks everywhere
   Customer customer = service.findCustomer(1L);
   if (customer != null) {
       String email = customer.getEmail();
       if (email != null) {
           sendEmail(email);
       }
   }
   
   // NEW: Optional
   service.findCustomer(1L)
       .map(Customer::getEmail)
       .filter(email -> !email.isEmpty())
       .ifPresent(this::sendEmail);
   ```

**Way to validate:**
- [ ] Checked exceptions reviewed
- [ ] Runtime exceptions used appropriately
- [ ] Optional used for missing values
- [ ] Global exception handler implemented
- [ ] Code cleaner and more readable
- [ ] Error handling still robust

---

## CONCLUSION

This comprehensive migration plan provides a structured approach to migrating JBoss EJB 2.x applications to Spring Boot with pure JDBC persistence. The plan covers:

- **10 Phases** of migration from pre-assessment to antipattern refactoring
- **Pure JDBC approach** avoiding JPA complexity
- **Pattern-based tasks** with clear identification tags
- **Application-agnostic** guidance using variables
- **Complete EJB 2.x coverage** (Session, Entity, MDB, Interfaces)
- **Modern Java** migration path (JDK 8 → 21)

**Success Factors:**
- Work incrementally, one component at a time
- Maintain test coverage throughout
- Document decisions and deviations
- Use Phase 0 baseline for comparison
- Leverage Spring Boot's auto-configuration
- Refactor antipatterns as you go

**For detailed reference materials, see:** `MIGRATION_PLAN_APPENDICES.md`

---

**END OF MIGRATION PLAN**


## PHASE 4: WEB SERVICES MIGRATION (SOAP)

**Objective:** Migrate JAX-WS (Java API for XML Web Services) SOAP endpoints from JBoss to Spring Web Services

**Complexity:** MEDIUM  
**Estimated Effort:** 3-5 days  
**Dependencies:** Phase 1 (Spring Boot project structure)

---

#### TASK-400: [PATTERN: JAXWS_ENDPOINT_IDENTIFICATION] Identify JAX-WS Endpoints

**Purpose:** Locate all JAX-WS SOAP endpoints in the JBoss application

**Pattern TAG:** JAXWS_ENDPOINT_IDENTIFICATION

**Requirements:**
- JAX-WS endpoints use @WebService annotation
- May use @SOAPBinding for binding style
- WSDL files may be in WEB-INF/wsdl or META-INF
- May have SEI (Service Endpoint Interface) pattern

**Actions to do:**

1. **Search for JAX-WS annotations**:
   ```bash
   grep -r "@WebService" src/
   grep -r "@WebMethod" src/
   grep -r "@WebParam" src/
   grep -r "@WebResult" src/
   grep -r "@SOAPBinding" src/
   ```

2. **Find WSDL definitions**:
   ```bash
   find . -name "*.wsdl"
   grep -r "webservices" */xml
   grep -r "<webservices>" */xml
   ```

3. **Document for each endpoint**:
   - Service class name
   - SEI (Service Endpoint Interface) if exists
   - Port name and namespace
   - WSDL location
   - SOAP binding style (RPC/Document)
   - Message format (Literal/Encoded)

4. **Create JAXWS_ENDPOINT_INVENTORY.md**:
   ```markdown
   # JAX-WS Endpoint Inventory
   
   ## Endpoint: CustomerService
   - Implementation: com.${BASE_PACKAGE}.ws.CustomerServiceImpl
   - SEI: com.${BASE_PACKAGE}.ws.CustomerService
   - Port Name: CustomerServicePort
   - Namespace: http://ws.${BASE_PACKAGE}.com/
   - WSDL: /wsdl/CustomerService.wsdl
   - Binding: DOCUMENT/LITERAL
   - Operations:
     - getCustomer(Long id)
     - createCustomer(Customer customer)
     - updateCustomer(Customer customer)
   ```

**Way to validate:**
- [ ] All @WebService classes documented
- [ ] WSDL files located
- [ ] SEI patterns identified
- [ ] SOAP binding styles recorded
- [ ] Operation signatures documented

---

#### TASK-401: [PATTERN: JAXWS_TO_SPRING_WS] Migrate JAX-WS to Spring Web Services

**Purpose:** Convert JAX-WS SOAP services to Spring Web Services (contract-first approach)

**Pattern TAG:** JAXWS_TO_SPRING_WS

**Requirements:**
- Spring WS uses contract-first (WSDL/XSD-first) approach
- Endpoints annotated with @Endpoint
- Methods mapped with @PayloadRoot
- JAXB for XML marshalling/unmarshalling

**Actions to do:**

1. **Add Spring WS dependencies** to `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-web-services</artifactId>
   </dependency>
   <dependency>
       <groupId>wsdl4j</groupId>
       <artifactId>wsdl4j</artifactId>
   </dependency>
   ```

2. **Create XSD schema** from existing WSDL or data structures:
   ```xml
   <!-- src/main/resources/xsd/customer.xsd -->
   <?xml version="1.0" encoding="UTF-8"?>
   <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
              xmlns:tns="http://ws.${BASE_PACKAGE}.com/"
              targetNamespace="http://ws.${BASE_PACKAGE}.com/"
              elementFormDefault="qualified">
   
       <xs:element name="getCustomerRequest">
           <xs:complexType>
               <xs:sequence>
                   <xs:element name="id" type="xs:long"/>
               </xs:sequence>
           </xs:complexType>
       </xs:element>
   
       <xs:element name="getCustomerResponse">
           <xs:complexType>
               <xs:sequence>
                   <xs:element name="customer" type="tns:customer"/>
               </xs:sequence>
           </xs:complexType>
       </xs:element>
   
       <xs:complexType name="customer">
           <xs:sequence>
               <xs:element name="id" type="xs:long"/>
               <xs:element name="name" type="xs:string"/>
               <xs:element name="email" type="xs:string"/>
           </xs:sequence>
       </xs:complexType>
   </xs:schema>
   ```

3. **Generate JAXB classes** from XSD:
   ```xml
   <!-- Add to pom.xml -->
   <plugin>
       <groupId>org.codehaus.mojo</groupId>
       <artifactId>jaxb2-maven-plugin</artifactId>
       <version>3.1.0</version>
       <executions>
           <execution>
               <id>xjc</id>
               <goals>
                   <goal>xjc</goal>
               </goals>
           </execution>
       </executions>
       <configuration>
           <sources>
               <source>src/main/resources/xsd</source>
           </sources>
       </configuration>
   </plugin>
   ```

4. **OLD: JAX-WS Service**:
   ```java
   @WebService(
       serviceName = "CustomerService",
       portName = "CustomerServicePort",
       targetNamespace = "http://ws.${BASE_PACKAGE}.com/"
   )
   public class CustomerServiceImpl {
       
       @EJB
       private CustomerFacade customerFacade;
       
       @WebMethod
       public Customer getCustomer(@WebParam(name = "id") Long id) {
           return customerFacade.find(id);
       }
       
       @WebMethod
       public Customer createCustomer(@WebParam(name = "customer") Customer customer) {
           return customerFacade.create(customer);
       }
   }
   ```

5. **NEW: Spring Web Services Endpoint**:
   ```java
   package com.${BASE_PACKAGE}.ws.endpoint;
   
   import org.springframework.beans.factory.annotation.Autowired;
   import org.springframework.ws.server.endpoint.annotation.Endpoint;
   import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
   import org.springframework.ws.server.endpoint.annotation.RequestPayload;
   import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
   
   import com.${BASE_PACKAGE}.ws.generated.*;
   import com.${BASE_PACKAGE}.service.CustomerService;
   
   @Endpoint
   public class CustomerEndpoint {
       
       private static final String NAMESPACE = "http://ws.${BASE_PACKAGE}.com/";
       
       private final CustomerService customerService;
       
       @Autowired
       public CustomerEndpoint(CustomerService customerService) {
           this.customerService = customerService;
       }
       
       @PayloadRoot(namespace = NAMESPACE, localPart = "getCustomerRequest")
       @ResponsePayload
       public GetCustomerResponse getCustomer(@RequestPayload GetCustomerRequest request) {
           Customer customer = customerService.getCustomer(request.getId());
           
           GetCustomerResponse response = new GetCustomerResponse();
           // Map domain Customer to JAXB Customer
           com.${BASE_PACKAGE}.ws.generated.Customer wsCustomer = new com.${BASE_PACKAGE}.ws.generated.Customer();
           wsCustomer.setId(customer.getId());
           wsCustomer.setName(customer.getName());
           wsCustomer.setEmail(customer.getEmail());
           
           response.setCustomer(wsCustomer);
           return response;
       }
       
       @PayloadRoot(namespace = NAMESPACE, localPart = "createCustomerRequest")
       @ResponsePayload
       public CreateCustomerResponse createCustomer(@RequestPayload CreateCustomerRequest request) {
           // Map JAXB Customer to domain Customer
           Customer customer = new Customer();
           customer.setName(request.getCustomer().getName());
           customer.setEmail(request.getCustomer().getEmail());
           
           Customer created = customerService.createCustomer(customer);
           
           CreateCustomerResponse response = new CreateCustomerResponse();
           com.${BASE_PACKAGE}.ws.generated.Customer wsCustomer = new com.${BASE_PACKAGE}.ws.generated.Customer();
           wsCustomer.setId(created.getId());
           wsCustomer.setName(created.getName());
           wsCustomer.setEmail(created.getEmail());
           
           response.setCustomer(wsCustomer);
           return response;
       }
   }
   ```

6. **Create Web Services Configuration**:
   ```java
   package com.${BASE_PACKAGE}.config;
   
   import org.springframework.boot.web.servlet.ServletRegistrationBean;
   import org.springframework.context.ApplicationContext;
   import org.springframework.context.annotation.Bean;
   import org.springframework.context.annotation.Configuration;
   import org.springframework.core.io.ClassPathResource;
   import org.springframework.ws.config.annotation.EnableWs;
   import org.springframework.ws.config.annotation.WsConfigurerAdapter;
   import org.springframework.ws.transport.http.MessageDispatcherServlet;
   import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
   import org.springframework.xml.xsd.SimpleXsdSchema;
   import org.springframework.xml.xsd.XsdSchema;
   
   @EnableWs
   @Configuration
   public class WebServiceConfig extends WsConfigurerAdapter {
       
       @Bean
       public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
           MessageDispatcherServlet servlet = new MessageDispatcherServlet();
           servlet.setApplicationContext(context);
           servlet.setTransformWsdlLocations(true);
           return new ServletRegistrationBean<>(servlet, "/ws/*");
       }
       
       @Bean(name = "customer")
       public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema customerSchema) {
           DefaultWsdl11Definition wsdl11Definition = new DefaultWsdl11Definition();
           wsdl11Definition.setPortTypeName("CustomerPort");
           wsdl11Definition.setLocationUri("/ws");
           wsdl11Definition.setTargetNamespace("http://ws.${BASE_PACKAGE}.com/");
           wsdl11Definition.setSchema(customerSchema);
           return wsdl11Definition;
       }
       
       @Bean
       public XsdSchema customerSchema() {
           return new SimpleXsdSchema(new ClassPathResource("xsd/customer.xsd"));
       }
   }
   ```

7. **Configure application properties**:
   ```properties
   # Web Services Configuration
   spring.webservices.path=/ws
   spring.webservices.servlet.init.wsdl-location-servlet-url-transform=true
   
   # SOAP message logging (optional for debugging)
   logging.level.org.springframework.ws=DEBUG
   ```

8. **Create mapper utility** for JAXB ↔ Domain object conversion:
   ```java
   package com.${BASE_PACKAGE}.ws.mapper;
   
   import org.springframework.stereotype.Component;
   
   @Component
   public class CustomerMapper {
       
       public com.${BASE_PACKAGE}.ws.generated.Customer toJaxb(Customer domain) {
           if (domain == null) return null;
           
           com.${BASE_PACKAGE}.ws.generated.Customer jaxb = new com.${BASE_PACKAGE}.ws.generated.Customer();
           jaxb.setId(domain.getId());
           jaxb.setName(domain.getName());
           jaxb.setEmail(domain.getEmail());
           return jaxb;
       }
       
       public Customer toDomain(com.${BASE_PACKAGE}.ws.generated.Customer jaxb) {
           if (jaxb == null) return null;
           
           Customer domain = new Customer();
           domain.setId(jaxb.getId());
           domain.setName(jaxb.getName());
           domain.setEmail(jaxb.getEmail());
           return domain;
       }
   }
   ```

9. **Test the endpoint**:
   ```bash
   # WSDL should be available at:
   curl http://localhost:8080/ws/customer.wsdl
   
   # Test with SOAP request:
   curl -X POST http://localhost:8080/ws \
     -H "Content-Type: text/xml" \
     -d '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ws="http://ws.${BASE_PACKAGE}.com/">
           <soapenv:Header/>
           <soapenv:Body>
              <ws:getCustomerRequest>
                 <ws:id>1</ws:id>
              </ws:getCustomerRequest>
           </soapenv:Body>
         </soapenv:Envelope>'
   ```

**Way to validate:**
- [ ] Spring WS dependencies added
- [ ] XSD schema created
- [ ] JAXB classes generated
- [ ] @Endpoint created with @PayloadRoot methods
- [ ] WebServiceConfig configured
- [ ] Mapper utilities created
- [ ] WSDL accessible at /ws/*.wsdl
- [ ] SOAP requests return valid responses
- [ ] Business logic properly delegated to @Service
- [ ] Integration tests pass
- [ ] Old JAX-WS classes removed
- [ ] No JBoss dependencies remain

---

## PHASE 5: REST API MIGRATION

**Objective:** Migrate JAX-RS (Java API for RESTful Web Services) REST endpoints from JBoss to Spring MVC REST

**Complexity:** MEDIUM  
**Estimated Effort:** 2-4 days  
**Dependencies:** Phase 1 (Spring Boot project structure)

---

#### TASK-500: [PATTERN: JAXRS_ENDPOINT_IDENTIFICATION] Identify JAX-RS REST Endpoints

**Purpose:** Locate all JAX-RS REST endpoints in the JBoss application

**Pattern TAG:** JAXRS_ENDPOINT_IDENTIFICATION

**Requirements:**
- JAX-RS endpoints use @Path annotation
- HTTP methods: @GET, @POST, @PUT, @DELETE
- May use @Produces, @Consumes for content negotiation
- RestEasy is common JAX-RS implementation in JBoss

**Actions to do:**

1. **Search for JAX-RS annotations**:
   ```bash
   grep -r "@Path" src/
   grep -r "@GET\|@POST\|@PUT\|@DELETE" src/
   grep -r "@Produces\|@Consumes" src/
   grep -r "@PathParam\|@QueryParam" src/
   grep -r "javax.ws.rs" src/
   ```

2. **Find REST configuration**:
   ```bash
   grep -r "Application extends" src/
   grep -r "javax.ws.rs.core.Application" src/
   grep -r "<servlet-class>org.jboss.resteasy" */xml
   ```

3. **Document for each resource**:
   - Resource class name
   - Base @Path
   - HTTP methods and sub-paths
   - Request/response media types
   - Path/query/form parameters

4. **Create JAXRS_ENDPOINT_INVENTORY.md**:
   ```markdown
   # JAX-RS Endpoint Inventory
   
   ## Resource: CustomerResource
   - Class: com.${BASE_PACKAGE}.rest.CustomerResource
   - Base Path: /api/customers
   - Endpoints:
     - GET /api/customers → getAllCustomers()
       - Produces: application/json
     - GET /api/customers/{id} → getCustomer(Long id)
       - Produces: application/json
     - POST /api/customers → createCustomer(Customer)
       - Consumes: application/json
       - Produces: application/json
     - PUT /api/customers/{id} → updateCustomer(Long id, Customer)
     - DELETE /api/customers/{id} → deleteCustomer(Long id)
   ```

**Way to validate:**
- [ ] All @Path classes documented
- [ ] HTTP methods mapped
- [ ] Media types recorded
- [ ] Parameter types identified
- [ ] Exception handlers located

---

#### TASK-501: [PATTERN: JAXRS_TO_SPRING_REST] Migrate JAX-RS to Spring REST

**Purpose:** Convert JAX-RS REST resources to Spring MVC @RestController

**Pattern TAG:** JAXRS_TO_SPRING_REST

**Requirements:**
- Spring MVC uses @RestController
- HTTP methods: @GetMapping, @PostMapping, @PutMapping, @DeleteMapping
- Path variables with @PathVariable
- Query params with @RequestParam
- Request body with @RequestBody

**Actions to do:**

1. **Add Spring Web dependency** (should already be in Spring Boot Starter Web):
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-web</artifactId>
   </dependency>
   ```

2. **OLD: JAX-RS Resource**:
   ```java
   @Path("/api/customers")
   @Produces(MediaType.APPLICATION_JSON)
   @Consumes(MediaType.APPLICATION_JSON)
   public class CustomerResource {
       
       @EJB
       private CustomerFacade customerFacade;
       
       @GET
       public List<Customer> getAllCustomers() {
           return customerFacade.findAll();
       }
       
       @GET
       @Path("/{id}")
       public Customer getCustomer(@PathParam("id") Long id) {
           Customer customer = customerFacade.find(id);
           if (customer == null) {
               throw new NotFoundException("Customer not found");
           }
           return customer;
       }
       
       @POST
       public Response createCustomer(Customer customer) {
           Customer created = customerFacade.create(customer);
           return Response.status(Status.CREATED).entity(created).build();
       }
       
       @PUT
       @Path("/{id}")
       public Customer updateCustomer(@PathParam("id") Long id, Customer customer) {
           customer.setId(id);
           return customerFacade.update(customer);
       }
       
       @DELETE
       @Path("/{id}")
       public Response deleteCustomer(@PathParam("id") Long id) {
           customerFacade.delete(id);
           return Response.noContent().build();
       }
   }
   ```

3. **NEW: Spring REST Controller**:
   ```java
   package com.${BASE_PACKAGE}.rest;
   
   import org.springframework.beans.factory.annotation.Autowired;
   import org.springframework.http.HttpStatus;
   import org.springframework.http.ResponseEntity;
   import org.springframework.web.bind.annotation.*;
   
   import com.${BASE_PACKAGE}.service.CustomerService;
   import com.${BASE_PACKAGE}.model.Customer;
   
   import java.util.List;
   
   @RestController
   @RequestMapping("/api/customers")
   public class CustomerController {
       
       private final CustomerService customerService;
       
       @Autowired
       public CustomerController(CustomerService customerService) {
           this.customerService = customerService;
       }
       
       @GetMapping
       public ResponseEntity<List<Customer>> getAllCustomers() {
           List<Customer> customers = customerService.findAll();
           return ResponseEntity.ok(customers);
       }
       
       @GetMapping("/{id}")
       public ResponseEntity<Customer> getCustomer(@PathVariable Long id) {
           Customer customer = customerService.findById(id);
           if (customer == null) {
               return ResponseEntity.notFound().build();
           }
           return ResponseEntity.ok(customer);
       }
       
       @PostMapping
       public ResponseEntity<Customer> createCustomer(@RequestBody Customer customer) {
           Customer created = customerService.create(customer);
           return ResponseEntity.status(HttpStatus.CREATED).body(created);
       }
       
       @PutMapping("/{id}")
       public ResponseEntity<Customer> updateCustomer(
               @PathVariable Long id, 
               @RequestBody Customer customer) {
           customer.setId(id);
           Customer updated = customerService.update(customer);
           return ResponseEntity.ok(updated);
       }
       
       @DeleteMapping("/{id}")
       public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
           customerService.delete(id);
           return ResponseEntity.noContent().build();
       }
   }
   ```

4. **Create DTO (Data Transfer Object)** if needed to decouple API from domain:
   ```java
   package com.${BASE_PACKAGE}.rest.dto;
   
   import com.fasterxml.jackson.annotation.JsonProperty;
   
   public class CustomerDTO {
       
       @JsonProperty("customer_id")
       private Long id;
       
       @JsonProperty("customer_name")
       private String name;
       
       private String email;
       
       // Getters and setters
   }
   ```

5. **Add exception handler** for REST API errors:
   ```java
   package com.${BASE_PACKAGE}.rest.exception;
   
   import org.springframework.http.HttpStatus;
   import org.springframework.http.ResponseEntity;
   import org.springframework.web.bind.annotation.ControllerAdvice;
   import org.springframework.web.bind.annotation.ExceptionHandler;
   
   @ControllerAdvice
   public class RestExceptionHandler {
       
       @ExceptionHandler(CustomerNotFoundException.class)
       public ResponseEntity<ErrorResponse> handleNotFound(CustomerNotFoundException ex) {
           ErrorResponse error = new ErrorResponse(
               HttpStatus.NOT_FOUND.value(),
               ex.getMessage(),
               System.currentTimeMillis()
           );
           return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
       }
       
       @ExceptionHandler(IllegalArgumentException.class)
       public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
           ErrorResponse error = new ErrorResponse(
               HttpStatus.BAD_REQUEST.value(),
               ex.getMessage(),
               System.currentTimeMillis()
           );
           return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
       }
       
       @ExceptionHandler(Exception.class)
       public ResponseEntity<ErrorResponse> handleGenericError(Exception ex) {
           ErrorResponse error = new ErrorResponse(
               HttpStatus.INTERNAL_SERVER_ERROR.value(),
               "An unexpected error occurred",
               System.currentTimeMillis()
           );
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
       }
   }
   
   class ErrorResponse {
       private int status;
       private String message;
       private long timestamp;
       
       // Constructor, getters, setters
   }
   ```

6. **Configure Jackson** for JSON serialization:
   ```properties
   # application.properties
   spring.jackson.serialization.write-dates-as-timestamps=false
   spring.jackson.date-format=yyyy-MM-dd'T'HH:mm:ss.SSSZ
   spring.jackson.default-property-inclusion=non_null
   ```

7. **Add validation** with Bean Validation:
   ```java
   @PostMapping
   public ResponseEntity<Customer> createCustomer(@Valid @RequestBody Customer customer) {
       Customer created = customerService.create(customer);
       return ResponseEntity.status(HttpStatus.CREATED).body(created);
   }
   
   // In Customer class:
   @NotNull(message = "Name is required")
   @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
   private String name;
   
   @Email(message = "Email must be valid")
   private String email;
   ```

8. **Test the endpoint**:
   ```bash
   # GET all customers
   curl http://localhost:8080/api/customers
   
   # GET single customer
   curl http://localhost:8080/api/customers/1
   
   # POST new customer
   curl -X POST http://localhost:8080/api/customers \
     -H "Content-Type: application/json" \
     -d '{"name":"John Doe","email":"john@example.com"}'
   
   # PUT update customer
   curl -X PUT http://localhost:8080/api/customers/1 \
     -H "Content-Type: application/json" \
     -d '{"id":1,"name":"Jane Doe","email":"jane@example.com"}'
   
   # DELETE customer
   curl -X DELETE http://localhost:8080/api/customers/1
   ```

**Way to validate:**
- [ ] @RestController created
- [ ] HTTP method mappings correct (@GetMapping, @PostMapping, etc.)
- [ ] @PathVariable for path parameters
- [ ] @RequestParam for query parameters
- [ ] @RequestBody for request payload
- [ ] ResponseEntity used for HTTP status control
- [ ] Exception handler implemented
- [ ] JSON serialization works correctly
- [ ] Validation annotations added
- [ ] Integration tests pass
- [ ] Old JAX-RS classes removed
- [ ] No JAX-RS dependencies remain

---

## PHASE 6: CONFIGURATION MIGRATION

**Objective:** Migrate JBoss configuration files to Spring Boot configuration

**Complexity:** LOW-MEDIUM  
**Estimated Effort:** 1-2 days  
**Dependencies:** Phases 1-5 (Core components migrated)

---

#### TASK-600: [PATTERN: CONFIG_FILE_IDENTIFICATION] Identify Configuration Files

**Purpose:** Locate all JBoss-specific configuration files

**Pattern TAG:** CONFIG_FILE_IDENTIFICATION

**Requirements:**
- Identify all JBoss configuration files
- Common JBoss configuration files:
  - `jboss-web.xml` - Web application config
  - `jboss.xml` - EJB configuration
  - `jboss-ejb3.xml` - EJB 3 configuration
  - `*-ds.xml` - DataSource configuration
  - `application.xml` - EAR configuration
  - `jboss-deployment-structure.xml` - Classloading config
  - Properties files referenced in configs

**Actions to do:**
1. Search for JBoss configuration files:
   ```bash
   # Find JBoss-specific config files
   find . -name "jboss*.xml"
   find . -name "*-ds.xml"
   find . -name "application.xml"
   
   # Check standard locations
   ls -la src/main/webapp/WEB-INF/
   ls -la src/main/resources/META-INF/
   ```

2. For each configuration file, document:
   - File name and location
   - Configuration purpose
   - Settings contained (security, datasources, EJB mappings, etc.)
   - Dependencies on JBoss-specific features

3. Create `CONFIG_INVENTORY.md`

**Way to validate:**
- [ ] All JBoss config files documented
- [ ] Configuration purposes understood
- [ ] Dependencies on JBoss features identified
- [ ] Ready for Spring Boot migration

---

#### TASK-601: [PATTERN: CONFIG_TO_SPRING_BOOT] Migrate Configuration to Spring Boot

**Purpose:** Convert JBoss configuration to Spring Boot application.properties/yaml

**Pattern TAG:** CONFIG_TO_SPRING_BOOT

**Requirements:**
- Completed TASK-600
- Understanding of Spring Boot configuration

**Actions to do:**

1. **Migrate DataSource configuration** (already done in Phase 2):
   ```xml
   <!-- OLD: jboss-ds.xml -->
   <datasources>
     <local-tx-datasource>
       <jndi-name>MyAppDS</jndi-name>
       <connection-url>jdbc:postgresql://localhost:5432/mydb</connection-url>
       <driver-class>org.postgresql.Driver</driver-class>
       <user-name>dbuser</user-name>
       <password>dbpass</password>
       <min-pool-size>5</min-pool-size>
       <max-pool-size>50</max-pool-size>
     </local-tx-datasource>
   </datasources>
   ```
   
   ```properties
   # NEW: application.properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
   spring.datasource.username=dbuser
   spring.datasource.password=dbpass
   spring.datasource.hikari.minimum-idle=5
   spring.datasource.hikari.maximum-pool-size=50
   ```

2. **Migrate security configuration**:
   ```xml
   <!-- OLD: jboss-web.xml -->
   <jboss-web>
     <security-domain>java:/jaas/my-domain</security-domain>
     <context-root>/myapp</context-root>
   </jboss-web>
   ```
   
   ```properties
   # NEW: application.properties
   server.servlet.context-path=/myapp
   
   # Security configured via Spring Security
   # See Spring Security migration task
   ```

3. **Migrate logging configuration**:
   ```xml
   <!-- OLD: jboss-log4j.xml or logging configuration -->
   <configuration>
     <appender name="FILE" class="org.apache.log4j.FileAppender">
       <param name="File" value="logs/app.log"/>
     </appender>
   </configuration>
   ```
   
   ```xml
   <!-- NEW: logback-spring.xml -->
   <configuration>
     <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
     
     <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
       <file>logs/app.log</file>
       <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
         <fileNamePattern>logs/app.%d{yyyy-MM-dd}.log</fileNamePattern>
         <maxHistory>30</maxHistory>
       </rollingPolicy>
       <encoder>
         <pattern>%d{yyyy-MM-dd HH:mm:ss} - %msg%n</pattern>
       </encoder>
     </appender>
     
     <root level="INFO">
       <appender-ref ref="FILE"/>
     </root>
   </configuration>
   ```

4. **Migrate environment-specific properties**:
   ```bash
   # OLD: JBoss system properties (-D flags)
   -Dapp.api.url=https://api.example.com
   -Dapp.timeout=30000
   ```
   
   ```properties
   # NEW: application-prod.properties
   app.api.url=https://api.example.com
   app.timeout=30000
   ```

5. **Create profile-specific configurations**:
   - `application-dev.properties` for development
   - `application-test.properties` for testing
   - `application-prod.properties` for production

6. **Remove old configuration files**:
   ```bash
   # Remove JBoss-specific files
   rm src/main/webapp/WEB-INF/jboss-web.xml
   rm src/main/webapp/WEB-INF/jboss.xml
   rm src/main/resources/META-INF/*-ds.xml
   ```

**Way to validate:**
- [ ] All DataSource configs in application.properties
- [ ] Security domain replaced with Spring Security config
- [ ] Logging configured with logback-spring.xml
- [ ] Environment-specific profiles created
- [ ] System properties migrated to application.properties
- [ ] Old JBoss config files removed
- [ ] Application starts with Spring Boot configuration

---

## PHASE 7: TESTING MIGRATION

**Objective:** Migrate and modernize test suite for Spring Boot

**Complexity:** MEDIUM  
**Estimated Effort:** 2-3 days  
**Dependencies:** Phases 1-6 (Components migrated)

---

#### TASK-700: [PATTERN: TEST_IDENTIFICATION] Identify Existing Tests

**Purpose:** Locate and categorize all existing tests

**Pattern TAG:** TEST_IDENTIFICATION

**Requirements:**
- Identify all test types in the application
- Common test patterns:
  - EJB container tests (Arquillian, embedded EJB container)
  - Integration tests with JBoss/WildFly
  - Unit tests with JUnit 4
  - Mock tests with EasyMock or Mockito

**Actions to do:**
1. Search for test files:
   ```bash
   # Find test classes
   find src/test -name "*Test.java"
   find src/test -name "*IT.java"
   
   # Check for test frameworks
   grep -r "@RunWith(Arquillian" src/test/
   grep -r "EJBContainer" src/test/
   grep -r "@Test" src/test/
   ```

2. Categorize tests:
   - Unit tests (no external dependencies)
   - Integration tests (database, EJB container)
   - End-to-end tests
   - Mock-based tests

3. Document testing frameworks used:
   - JUnit version (3, 4, or 5)
   - Mocking framework (Mockito, EasyMock)
   - Container framework (Arquillian, embedded EJB)
   - Database testing (DbUnit, in-memory H2)

4. Create `TEST_INVENTORY.md`

**Way to validate:**
- [ ] All test classes documented
- [ ] Test types categorized
- [ ] Testing frameworks identified
- [ ] Test coverage understood

---

#### TASK-701: [PATTERN: TEST_MIGRATION] Migrate Tests to Spring Boot Test

**Purpose:** Convert tests to use Spring Boot Test framework

**Pattern TAG:** TEST_MIGRATION

**Requirements:**
- Completed TASK-700
- Spring Boot Test dependencies

**Actions to do:**

1. **Add Spring Boot Test dependencies**:
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-test</artifactId>
       <scope>test</scope>
   </dependency>
   <!-- Includes JUnit 5, Mockito, AssertJ, Hamcrest, etc. -->
   ```

2. **Migrate unit tests to JUnit 5**:
   ```java
   // OLD: JUnit 4
   import org.junit.Test;
   import org.junit.Before;
   import org.junit.After;
   import static org.junit.Assert.*;
   
   public class OrderServiceTest {
       
       @Before
       public void setUp() {
           // Setup
       }
       
       @Test
       public void testCreateOrder() {
           // Test logic
           assertEquals(expected, actual);
       }
       
       @After
       public void tearDown() {
           // Cleanup
       }
   }
   
   // NEW: JUnit 5
   import org.junit.jupiter.api.Test;
   import org.junit.jupiter.api.BeforeEach;
   import org.junit.jupiter.api.AfterEach;
   import static org.junit.jupiter.api.Assertions.*;
   
   class OrderServiceTest {
       
       @BeforeEach
       void setUp() {
           // Setup
       }
       
       @Test
       void shouldCreateOrder() {
           // Test logic
           assertEquals(expected, actual);
       }
       
       @AfterEach
       void tearDown() {
           // Cleanup
       }
   }
   ```

3. **Migrate EJB integration tests to Spring Boot Test**:
   ```java
   // OLD: Arquillian test
   @RunWith(Arquillian.class)
   public class OrderServiceIT {
       
       @Deployment
       public static Archive<?> createDeployment() {
           return ShrinkWrap.create(WebArchive.class)
               .addClasses(OrderService.class, OrderDao.class)
               .addAsResource("test-persistence.xml", "META-INF/persistence.xml");
       }
       
       @EJB
       private OrderService orderService;
       
       @Test
       public void testCreateOrder() {
           Order order = orderService.createOrder(customerId, items);
           assertNotNull(order.getId());
       }
   }
   
   // NEW: Spring Boot Test
   @SpringBootTest
   @Transactional
   class OrderServiceIT {
       
       @Autowired
       private OrderService orderService;
       
       @Autowired
       private OrderDao orderDao;
       
       @Test
       void shouldCreateOrder() {
           Order order = orderService.createOrder(customerId, items);
           assertNotNull(order.getId());
           
           // Verify in database
           Order saved = orderDao.findById(order.getId()).orElseThrow();
           assertEquals(order.getId(), saved.getId());
       }
   }
   ```

4. **Add database tests with embedded database**:
   ```java
   @SpringBootTest
   @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
   @Sql(scripts = "/test-data.sql")
   class OrderDaoIT {
       
       @Autowired
       private OrderDao orderDao;
       
       @Test
       void shouldFindOrderById() {
           Optional<Order> order = orderDao.findById(1L);
           assertTrue(order.isPresent());
       }
   }
   ```
   
   ```properties
   # src/test/resources/application-test.properties
   spring.datasource.url=jdbc:h2:mem:testdb
   spring.datasource.driver-class-name=org.h2.Driver
   spring.datasource.username=sa
   spring.datasource.password=
   ```

5. **Mock beans in tests**:
   ```java
   @SpringBootTest
   class OrderServiceTest {
       
       @MockBean
       private OrderDao orderDao;
       
       @Autowired
       private OrderService orderService;
       
       @Test
       void shouldCreateOrder() {
           Order order = new Order();
           when(orderDao.create(any())).thenReturn(order);
           
           Order result = orderService.createOrder(customerId, items);
           assertNotNull(result);
           
           verify(orderDao).create(any());
       }
   }
   ```

6. **Test REST endpoints**:
   ```java
   @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
   class CustomerControllerIT {
       
       @Autowired
       private TestRestTemplate restTemplate;
       
       @Test
       void shouldGetCustomer() {
           ResponseEntity<Customer> response = restTemplate.getForEntity(
               "/api/customers/1", 
               Customer.class
           );
           
           assertEquals(HttpStatus.OK, response.getStatusCode());
           assertNotNull(response.getBody());
       }
   }
   ```

7. **Remove Arquillian dependencies**:
   ```xml
   <!-- Remove from pom.xml -->
   <dependency>
       <groupId>org.jboss.arquillian</groupId>
       <artifactId>arquillian-junit-container</artifactId>
   </dependency>
   ```

**Way to validate:**
- [ ] Spring Boot Test dependency added
- [ ] All tests migrated to JUnit 5
- [ ] @SpringBootTest used for integration tests
- [ ] Embedded database configured for tests
- [ ] @MockBean used for mocking
- [ ] REST endpoint tests added
- [ ] Arquillian dependencies removed
- [ ] All tests pass

---

## PHASE 8: PACKAGING AND DEPLOYMENT

**Objective:** Package Spring Boot application and configure deployment

**Complexity:** LOW-MEDIUM  
**Estimated Effort:** 1-2 days  
**Dependencies:** Phases 1-7 (Application complete)

---

#### TASK-800: [PATTERN: PACKAGING_MIGRATION] Migrate from EAR/WAR to Spring Boot JAR

**Purpose:** Change packaging from JBoss EAR/WAR to Spring Boot executable JAR

**Pattern TAG:** PACKAGING_MIGRATION

**Requirements:**
- Application fully migrated
- Maven build configured

**Actions to do:**

1. **Update pom.xml packaging**:
   ```xml
   <!-- OLD: WAR packaging for JBoss -->
   <packaging>war</packaging>
   
   <!-- NEW: JAR packaging for Spring Boot -->
   <packaging>jar</packaging>
   ```

2. **Configure Spring Boot Maven Plugin**:
   ```xml
   <build>
       <plugins>
           <plugin>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-maven-plugin</artifactId>
               <configuration>
                   <mainClass>${BASE_PACKAGE}.Application</mainClass>
                   <layout>JAR</layout>
               </configuration>
               <executions>
                   <execution>
                       <goals>
                           <goal>repackage</goal>
                       </goals>
                   </execution>
               </executions>
           </plugin>
       </plugins>
   </build>
   ```

3. **Build executable JAR**:
   ```bash
   mvn clean package
   
   # Creates: target/${PROJECT_NAME}-1.0.0.jar
   # This is an executable JAR with embedded Tomcat
   ```

4. **Test execution**:
   ```bash
   java -jar target/${PROJECT_NAME}-1.0.0.jar
   
   # With profile:
   java -jar target/${PROJECT_NAME}-1.0.0.jar --spring.profiles.active=prod
   
   # With external config:
   java -jar target/${PROJECT_NAME}-1.0.0.jar --spring.config.location=file:./config/
   ```

5. **Create startup script**:
   ```bash
   #!/bin/bash
   # start.sh
   
   APP_NAME="${PROJECT_NAME}"
   APP_VERSION="1.0.0"
   JAR_FILE="target/${APP_NAME}-${APP_VERSION}.jar"
   
   JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"
   SPRING_OPTS="--spring.profiles.active=prod"
   
   java $JAVA_OPTS -jar $JAR_FILE $SPRING_OPTS
   ```

6. **Optional: Keep WAR deployment** (if deploying to external Tomcat):
   ```xml
   <packaging>war</packaging>
   
   <dependencies>
       <dependency>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-starter-tomcat</artifactId>
           <scope>provided</scope>
       </dependency>
   </dependencies>
   ```
   
   ```java
   // Extend SpringBootServletInitializer for WAR deployment
   @SpringBootApplication
   public class Application extends SpringBootServletInitializer {
       
       @Override
       protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
           return application.sources(Application.class);
       }
       
       public static void main(String[] args) {
           SpringApplication.run(Application.class, args);
       }
   }
   ```

**Way to validate:**
- [ ] Packaging changed to JAR (or WAR if needed)
- [ ] Spring Boot Maven Plugin configured
- [ ] Executable JAR builds successfully
- [ ] Application runs from command line
- [ ] Startup script created
- [ ] External configuration works

---

#### TASK-801: [PATTERN: DEPLOYMENT_CONFIG] Configure Production Deployment

**Purpose:** Set up production deployment configuration

**Pattern TAG:** DEPLOYMENT_CONFIG

**Requirements:**
- Completed TASK-800
- Understanding of target deployment environment

**Actions to do:**

1. **Create production configuration**:
   ```properties
   # application-prod.properties
   
   # Server configuration
   server.port=8080
   server.servlet.context-path=/myapp
   
   # Database (use environment variables)
   spring.datasource.url=${DB_URL}
   spring.datasource.username=${DB_USERNAME}
   spring.datasource.password=${DB_PASSWORD}
   
   # Connection pool for production
   spring.datasource.hikari.maximum-pool-size=50
   spring.datasource.hikari.minimum-idle=10
   spring.datasource.hikari.connection-timeout=30000
   
   # Logging
   logging.level.root=INFO
   logging.level.${BASE_PACKAGE}=INFO
   logging.file.name=logs/app.log
   logging.file.max-size=100MB
   logging.file.max-history=30
   
   # Actuator security
   management.endpoints.web.exposure.include=health,info,metrics
   management.endpoint.health.show-details=when-authorized
   
   # Security
   spring.security.user.name=${ADMIN_USERNAME}
   spring.security.user.password=${ADMIN_PASSWORD}
   ```

2. **Create systemd service** (for Linux deployment):
   ```ini
   # /etc/systemd/system/myapp.service
   [Unit]
   Description=My Spring Boot Application
   After=syslog.target network.target
   
   [Service]
   User=appuser
   Group=appuser
   
   WorkingDirectory=/opt/myapp
   ExecStart=/usr/bin/java -Xms512m -Xmx2048m -jar /opt/myapp/myapp.jar --spring.profiles.active=prod
   
   SuccessExitStatus=143
   StandardOutput=journal
   StandardError=journal
   SyslogIdentifier=myapp
   
   [Install]
   WantedBy=multi-user.target
   ```
   
   ```bash
   # Enable and start service
   sudo systemctl daemon-reload
   sudo systemctl enable myapp
   sudo systemctl start myapp
   sudo systemctl status myapp
   ```

3. **Create Docker image** (optional):
   ```dockerfile
   # Dockerfile
   FROM eclipse-temurin:8-jre
   
   WORKDIR /app
   
   COPY target/${PROJECT_NAME}-1.0.0.jar app.jar
   
   EXPOSE 8080
   
   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```
   
   ```bash
   # Build and run Docker image
   docker build -t myapp:1.0.0 .
   docker run -p 8080:8080 \
     -e SPRING_PROFILES_ACTIVE=prod \
     -e DB_URL=jdbc:postgresql://db:5432/mydb \
     myapp:1.0.0
   ```

4. **Create deployment documentation**:
   ```markdown
   # DEPLOYMENT.md
   
   ## Prerequisites
   - Java 8+ installed
   - Database accessible
   - Required environment variables set
   
   ## Environment Variables
   - DB_URL: Database JDBC URL
   - DB_USERNAME: Database username
   - DB_PASSWORD: Database password
   - ADMIN_USERNAME: Admin username
   - ADMIN_PASSWORD: Admin password
   
   ## Deployment Steps
   1. Copy JAR file to server: /opt/myapp/
   2. Set environment variables
   3. Start application: sudo systemctl start myapp
   4. Verify: curl http://localhost:8080/actuator/health
   
   ## Monitoring
   - Application logs: journalctl -u myapp -f
   - Health endpoint: /actuator/health
   - Metrics endpoint: /actuator/metrics
   ```

**Way to validate:**
- [ ] Production properties configured
- [ ] Environment variables used for sensitive data
- [ ] Systemd service created (if Linux)
- [ ] Docker image created (if containerized)
- [ ] Deployment documentation written
- [ ] Application deploys successfully in production
- [ ] Health checks pass
- [ ] Logs accessible

---


## PHASE 3C: EJB INTERFACE REMOVAL

### SUBTITLE: Eliminate EJB Interfaces with Spring Dependency Injection

#### TASK-360: [PATTERN: HOME_INTERFACE_REMOVAL] Remove Home and LocalHome Interfaces
**Purpose:** Eliminate Home and LocalHome interfaces by replacing with Spring dependency injection

**Pattern TAG:** HOME_INTERFACE_REMOVAL

**Requirements:**
- All EJBs migrated to Spring components
- Home/LocalHome interfaces identified
- Understanding of create() and finder methods

**Actions to do:**
1. Search for Home interfaces:
   ```bash
   # Find Home interface files
   grep -r "interface.*Home" src/
   grep -r "extends EJBHome" src/
   grep -r "extends EJBLocalHome" src/
   
   # Find Home interface usage
   grep -r "\.create(" src/
   grep -r "\.findByPrimaryKey(" src/
   grep -r "lookup(" src/ | grep Home
   ```

2. For each Home interface, document:
   - Interface name (e.g., CustomerHome)
   - create() methods and their parameters
   - Finder methods (findByPrimaryKey, custom finders)
   - All classes that use this Home interface

3. **Replace create() methods**:
   ```java
   // OLD: Using Home interface
   CustomerHome home = (CustomerHome) context.lookup("java:comp/env/ejb/Customer");
   Customer customer = home.create(name, email);
   
   // NEW: Direct instantiation or factory method
   Customer customer = new Customer();
   customer.setName(name);
   customer.setEmail(email);
   customer = customerDao.create(customer);  // Save to database
   
   // OR use factory method in service
   Customer customer = customerService.createCustomer(name, email);
   ```

4. **Replace finder methods**:
   ```java
   // OLD: Using Home interface finders
   CustomerHome home = (CustomerHome) context.lookup("java:comp/env/ejb/Customer");
   Customer customer = home.findByPrimaryKey(customerId);
   Collection<Customer> customers = home.findByCity("New York");
   
   // NEW: Use repository/DAO methods
   Customer customer = customerDao.findById(customerId)
       .orElseThrow(() -> new RuntimeException("Customer not found"));
   List<Customer> customers = customerDao.findByCity("New York");
   ```

5. **Remove Home interface files**:
   - Delete Home interface file (e.g., `CustomerHome.java`)
   - Delete LocalHome interface file (if separate)
   - Remove from ejb-jar.xml

**Way to validate:**
- [ ] All Home interface files deleted
- [ ] All create() calls replaced with direct instantiation or service calls
- [ ] All finder calls replaced with DAO/repository calls
- [ ] No JNDI lookups for Home interfaces remain
- [ ] Application compiles successfully
- [ ] All tests pass

---

#### TASK-361: [PATTERN: REMOTE_INTERFACE_REMOVAL] Remove Remote and Local Interfaces
**Purpose:** Eliminate Remote and Local component interfaces, keeping business methods in service classes

**Pattern TAG:** REMOTE_INTERFACE_REMOVAL

**Requirements:**
- All EJBs migrated to Spring services
- Remote/Local interfaces identified
- Business methods documented

**Actions to do:**
1. Search for Remote/Local interfaces:
   ```bash
   # Find Remote/Local interface files
   grep -r "extends EJBObject" src/
   grep -r "extends EJBLocalObject" src/
   grep -r "interface.*Remote" src/
   grep -r "interface.*Local" src/
   
   # Find Remote/Local interface usage
   grep -r "@EJB.*Remote" src/
   grep -r "@EJB.*Local" src/
   ```

2. For each Remote/Local interface, document:
   - Interface name
   - Business methods declared
   - Implementation class (EJB)
   - All classes that depend on this interface

3. **Update dependency injection**:
   ```java
   // OLD: Injecting Remote/Local interface
   @EJB
   private CustomerServiceRemote customerService;
   
   @EJB
   private OrderServiceLocal orderService;
   
   // NEW: Inject Spring service directly (no interface needed)
   private final CustomerService customerService;
   private final OrderService orderService;
   
   public MyService(CustomerService customerService, OrderService orderService) {
       this.customerService = customerService;
       this.orderService = orderService;
   }
   ```

4. **Keep business methods in service class** (optional interface):
   ```java
   // OPTION 1: No interface (simpler)
   @Service
   public class CustomerService {
       public Customer findById(Long id) { ... }
       public List<Customer> findAll() { ... }
   }
   
   // OPTION 2: Optional interface (if needed for testing or multiple implementations)
   public interface CustomerService {
       Customer findById(Long id);
       List<Customer> findAll();
   }
   
   @Service
   public class CustomerServiceImpl implements CustomerService {
       public Customer findById(Long id) { ... }
       public List<Customer> findAll() { ... }
   }
   ```

5. **Remove Remote/Local interface files**:
   - Delete Remote interface file (e.g., `CustomerServiceRemote.java`)
   - Delete Local interface file (e.g., `CustomerServiceLocal.java`)
   - Remove from ejb-jar.xml
   - Update imports in dependent classes

**Way to validate:**
- [ ] All Remote/Local interface files deleted
- [ ] Dependencies updated to inject concrete services
- [ ] Business methods preserved in service classes
- [ ] Optional: Plain Java interfaces created for testing (if desired)
- [ ] No @EJB annotations remain
- [ ] Application compiles successfully
- [ ] All tests pass

---

#### TASK-362: [PATTERN: JNDI_LOOKUP_ELIMINATION] Replace JNDI Lookups with Spring DI
**Purpose:** Eliminate all JNDI lookups and replace with Spring dependency injection

**Pattern TAG:** JNDI_LOOKUP_ELIMINATION

**Requirements:**
- All EJBs migrated to Spring components
- JNDI lookup locations identified
- Understanding of InitialContext usage

**Actions to do:**
1. Search for JNDI lookups:
   ```bash
   # Find JNDI lookup code
   grep -r "InitialContext" src/
   grep -r "lookup(" src/
   grep -r "java:comp/env" src/
   grep -r "java:jboss" src/
   grep -r "PortableRemoteObject.narrow" src/
   grep -r "@Resource.*mappedName" src/
   ```

2. Document all JNDI lookup patterns:
   - EJB lookups (Home interfaces, Remote/Local objects)
   - DataSource lookups
   - JMS resource lookups
   - Environment entry lookups
   - Resource adapter lookups

3. **Replace EJB lookups with Spring DI**:
   ```java
   // OLD: JNDI lookup for EJB
   InitialContext ctx = new InitialContext();
   Object ref = ctx.lookup("java:comp/env/ejb/CustomerService");
   CustomerServiceRemote service = (CustomerServiceRemote) 
       PortableRemoteObject.narrow(ref, CustomerServiceRemote.class);
   
   // NEW: Spring constructor injection
   private final CustomerService customerService;
   
   public MyClass(CustomerService customerService) {
       this.customerService = customerService;
   }
   ```

4. **Replace DataSource lookups** (already done in TASK-201):
   ```java
   // OLD: JNDI lookup for DataSource
   InitialContext ctx = new InitialContext();
   DataSource ds = (DataSource) ctx.lookup("java:jboss/datasources/MyDS");
   
   // NEW: Spring auto-configured (already injected in JdbcTemplate)
   // No manual lookup needed
   ```

5. **Replace JMS resource lookups**:
   ```java
   // OLD: JNDI lookup for JMS resources
   InitialContext ctx = new InitialContext();
   ConnectionFactory cf = (ConnectionFactory) ctx.lookup("java:jboss/ConnectionFactory");
   Queue queue = (Queue) ctx.lookup("java:jboss/queue/OrderQueue");
   
   // NEW: Spring JMS configuration (in application.properties)
   # spring.activemq.broker-url=tcp://localhost:61616
   # Queue/Topic names configured in @JmsListener annotations
   ```

6. **Replace environment entries**:
   ```java
   // OLD: JNDI lookup for environment entries
   InitialContext ctx = new InitialContext();
   String apiUrl = (String) ctx.lookup("java:comp/env/apiUrl");
   Integer maxRetries = (Integer) ctx.lookup("java:comp/env/maxRetries");
   
   // NEW: Spring @Value from properties
   @Value("${api.url}")
   private String apiUrl;
   
   @Value("${max.retries}")
   private Integer maxRetries;
   
   // In application.properties:
   # api.url=https://api.example.com
   # max.retries=3
   ```

7. **Remove JNDI configuration files**:
   - Remove jndi.properties (if exists)
   - Remove JNDI entries from ejb-jar.xml
   - Remove JNDI bindings from web.xml

8. **Update to Spring bean references**:
   ```xml
   <!-- OLD: ejb-ref in web.xml or ejb-jar.xml -->
   <ejb-ref>
       <ejb-ref-name>ejb/CustomerService</ejb-ref-name>
       <ejb-ref-type>Session</ejb-ref-type>
       <remote>com.example.CustomerServiceRemote</remote>
   </ejb-ref>
   
   <!-- NEW: No XML needed, use Spring @Autowired or constructor injection -->
   ```

**Way to validate:**
- [ ] All InitialContext usages removed
- [ ] All lookup() calls eliminated
- [ ] All PortableRemoteObject.narrow() calls removed
- [ ] EJBs replaced with Spring @Service injection
- [ ] DataSources auto-configured by Spring
- [ ] JMS resources configured in application.properties
- [ ] Environment entries migrated to @Value or @ConfigurationProperties
- [ ] JNDI configuration files removed
- [ ] Application compiles successfully
- [ ] All tests pass
- [ ] No runtime JNDI errors

---

## PHASE 3B: MESSAGE-DRIVEN BEAN MIGRATION

### SUBTITLE: Message-Driven Beans to Spring JMS

#### TASK-350: [PATTERN: MDB_IDENTIFICATION] Identify Message-Driven Beans
**Purpose:** Locate all Message-Driven Bean (MDB) implementations in the application

**Pattern TAG:** MDB_IDENTIFICATION

**Requirements:**
- Identify all MDB implementations
- Message-Driven Beans:
  - Implement MessageListener interface or use @MessageDriven annotation
  - Have onMessage() method for processing messages
  - Configured with destination (queue or topic)
  - Transaction and acknowledgment settings

**Actions to do:**
1. Search for MDB implementations:
   ```bash
   # Find MDB classes
   grep -r "implements MessageListener" src/
   grep -r "@MessageDriven" src/
   grep -r "onMessage(" src/
   
   # Find in deployment descriptors
   grep -r "<message-driven>" ejb-jar.xml
   grep -r "<message-driven-destination>" ejb-jar.xml
   ```

2. For each MDB found, document:
   - Bean name
   - Bean class
   - Destination name (queue or topic)
   - Destination type (Queue or Topic)
   - Message selector (if any)
   - Transaction attribute
   - Acknowledgment mode
   - Connection factory used
   - Message processing logic
   - Error handling approach

3. Document message patterns:
   - Message types processed (TextMessage, ObjectMessage, etc.)
   - Message properties used
   - Response messages sent (if any)
   - Business logic triggered

4. Create `MDB_INVENTORY.md`

**Way to validate:**
- All MDBs documented
- All destinations identified
- Message processing patterns understood
- Transaction requirements documented
- Ready for Spring JMS migration

---

#### TASK-351: [PATTERN: MDB_TO_SPRING_JMS] Migrate Message-Driven Beans to Spring JMS
**Purpose:** Convert Message-Driven Beans to Spring @JmsListener components

**Pattern TAG:** MDB_TO_SPRING_JMS

**Requirements:**
- Completed TASK-350
- Identified at least one MDB
- Understanding of JMS configuration

**Actions to do:**
For each Message-Driven Bean (migrate one at a time):

1. **Add Spring JMS dependency** to `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-activemq</artifactId>
   </dependency>
   <!-- OR for generic JMS -->
   <dependency>
       <groupId>org.springframework</groupId>
       <artifactId>spring-jms</artifactId>
   </dependency>
   ```

2. **Configure JMS in `application.properties`**:
   ```properties
   # ActiveMQ configuration (if using ActiveMQ)
   spring.activemq.broker-url=tcp://localhost:61616
   spring.activemq.user=${JMS_USER}
   spring.activemq.password=${JMS_PASSWORD}
   
   # JMS settings
   spring.jms.listener.acknowledge-mode=auto
   spring.jms.listener.concurrency=1-10
   spring.jms.pub-sub-domain=false  # false for Queue, true for Topic
   ```

3. **Create Spring JMS listener**:
   ```java
   // OLD: Message-Driven Bean
   @MessageDriven(
       name = "OrderProcessorMDB",
       activationConfig = {
           @ActivationConfigProperty(
               propertyName = "destinationType",
               propertyValue = "javax.jms.Queue"
           ),
           @ActivationConfigProperty(
               propertyName = "destination",
               propertyValue = "queue/orderQueue"
           ),
           @ActivationConfigProperty(
               propertyName = "acknowledgeMode",
               propertyValue = "Auto-acknowledge"
           )
       }
   )
   @TransactionAttribute(TransactionAttributeType.REQUIRED)
   public class OrderProcessorMDB implements MessageListener {
       
       @EJB
       private OrderService orderService;
       
       @Resource
       private MessageDrivenContext context;
       
       public void onMessage(Message message) {
           try {
               if (message instanceof TextMessage) {
                   TextMessage textMessage = (TextMessage) message;
                   String orderXml = textMessage.getText();
                   
                   // Process order
                   orderService.processOrder(orderXml);
                   
               } else if (message instanceof ObjectMessage) {
                   ObjectMessage objMessage = (ObjectMessage) message;
                   Order order = (Order) objMessage.getObject();
                   
                   // Process order
                   orderService.processOrder(order);
               }
           } catch (Exception e) {
               context.setRollbackOnly();
               throw new RuntimeException("Failed to process message", e);
           }
       }
   }
   
   // NEW: Spring JMS Listener
   package ${BASE_PACKAGE}.listener;
   
   import ${BASE_PACKAGE}.model.Order;
   import ${BASE_PACKAGE}.service.OrderService;
   import org.springframework.jms.annotation.JmsListener;
   import org.springframework.messaging.handler.annotation.Payload;
   import org.springframework.stereotype.Component;
   import org.springframework.transaction.annotation.Transactional;
   
   @Component
   public class OrderProcessor {
       
       private final OrderService orderService;
       
       public OrderProcessor(OrderService orderService) {
           this.orderService = orderService;
       }
       
       // For TextMessage (String payload)
       @JmsListener(destination = "orderQueue")
       @Transactional
       public void processOrderXml(@Payload String orderXml) {
           try {
               orderService.processOrder(orderXml);
           } catch (Exception e) {
               // Exception triggers rollback automatically with @Transactional
               throw new RuntimeException("Failed to process order message", e);
           }
       }
       
       // For ObjectMessage (with type conversion)
       @JmsListener(destination = "orderQueue")
       @Transactional
       public void processOrder(@Payload Order order) {
           try {
               orderService.processOrder(order);
           } catch (Exception e) {
               throw new RuntimeException("Failed to process order", e);
           }
       }
   }
   ```

4. **Handle message selectors**:
   ```java
   // OLD: MDB with message selector
   @ActivationConfigProperty(
       propertyName = "messageSelector",
       propertyValue = "OrderType = 'PRIORITY'"
   )
   
   // NEW: Spring JMS with selector
   @JmsListener(
       destination = "orderQueue",
       selector = "OrderType = 'PRIORITY'"
   )
   @Transactional
   public void processPriorityOrder(@Payload Order order) {
       // Process priority orders only
   }
   ```

5. **Handle Topic subscriptions**:
   ```java
   // OLD: MDB for Topic
   @ActivationConfigProperty(
       propertyName = "destinationType",
       propertyValue = "javax.jms.Topic"
   )
   @ActivationConfigProperty(
       propertyName = "destination",
       propertyValue = "topic/notifications"
   )
   
   // NEW: Spring JMS for Topic
   // Configure in application.properties: spring.jms.pub-sub-domain=true
   // Or use subscription:
   @JmsListener(
       destination = "notifications",
       subscription = "notificationSubscription",
       containerFactory = "jmsTopicListenerContainerFactory"
   )
   @Transactional
   public void handleNotification(@Payload String notification) {
       // Handle topic message
   }
   ```

6. **Create JMS configuration class** (if needed for advanced scenarios):
   ```java
   package ${BASE_PACKAGE}.config;
   
   import org.springframework.context.annotation.Bean;
   import org.springframework.context.annotation.Configuration;
   import org.springframework.jms.annotation.EnableJms;
   import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
   import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
   import org.springframework.jms.support.converter.MessageConverter;
   import org.springframework.jms.support.converter.MessageType;
   
   import javax.jms.ConnectionFactory;
   
   @Configuration
   @EnableJms
   public class JmsConfig {
       
       // For Queue listeners
       @Bean
       public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
           ConnectionFactory connectionFactory
       ) {
           DefaultJmsListenerContainerFactory factory = 
               new DefaultJmsListenerContainerFactory();
           factory.setConnectionFactory(connectionFactory);
           factory.setConcurrency("1-10");
           factory.setSessionTransacted(true);
           return factory;
       }
       
       // For Topic listeners
       @Bean
       public DefaultJmsListenerContainerFactory jmsTopicListenerContainerFactory(
           ConnectionFactory connectionFactory
       ) {
           DefaultJmsListenerContainerFactory factory = 
               new DefaultJmsListenerContainerFactory();
           factory.setConnectionFactory(connectionFactory);
           factory.setPubSubDomain(true);  // Enable Topic mode
           factory.setSubscriptionDurable(true);
           factory.setClientId("${spring.application.name}");
           return factory;
       }
       
       // Message converter for JSON
       @Bean
       public MessageConverter jacksonJmsMessageConverter() {
           MappingJackson2MessageConverter converter = 
               new MappingJackson2MessageConverter();
           converter.setTargetType(MessageType.TEXT);
           converter.setTypeIdPropertyName("_type");
           return converter;
       }
   }
   ```

7. **Handle error scenarios**:
   ```java
   // Configure error handler
   @Bean
   public ErrorHandler errorHandler() {
       return new ErrorHandler() {
           @Override
           public void handleError(Throwable t) {
               log.error("Error in JMS listener", t);
               // Implement retry logic or dead letter queue handling
           }
       };
   }
   ```

8. **Remove old MDB files**:
   - Remove MDB implementation class
   - Remove ejb-jar.xml entry for MDB
   - Update JMS destination configuration

**Way to validate:**
- [ ] Spring JMS dependency added
- [ ] JMS configuration in application.properties
- [ ] @JmsListener component created
- [ ] Message processing logic preserved
- [ ] Transaction management via @Transactional
- [ ] Message selectors migrated (if used)
- [ ] Topic subscriptions migrated (if used)
- [ ] Error handling implemented
- [ ] Unit tests pass
- [ ] Integration tests with embedded broker pass
- [ ] No EJB MDB references remain
- [ ] Messages processed correctly

---

#### TASK-302: [PATTERN: STATEFUL_TO_SPRING_SCOPE] Migrate Stateful Session Beans
**Purpose:** Convert Stateful Session Beans to Spring session-scoped beans

**Pattern TAG:** STATEFUL_TO_SPRING_SCOPE

**Requirements:**
- Completed TASK-300
- Identified at least one Stateful Session Bean

**Actions to do:**
For each Stateful Session Bean (migrate one at a time):

1. **Create Spring session-scoped service**:
   ```java
   // OLD: Stateful Session Bean
   @Stateful
   public class ShoppingCartEJB implements ShoppingCart {
       
       private List<CartItem> items = new ArrayList<>();
       private Customer customer;
       
       public void setCustomer(Customer customer) {
           this.customer = customer;
       }
       
       public void addItem(Product product, int quantity) {
           items.add(new CartItem(product, quantity));
       }
       
       public Order checkout() {
           // Create order from cart items
           return order;
       }
       
       @Remove
       public void clear() {
           items.clear();
           customer = null;
       }
       
       @PrePassivate
       public void onPassivate() {
           // Cleanup before passivation
       }
       
       @PostActivate
       public void onActivate() {
           // Restore after activation
       }
   }
   
   // NEW: Spring session-scoped bean
   package ${BASE_PACKAGE}.service;
   
   import ${BASE_PACKAGE}.model.CartItem;
   import ${BASE_PACKAGE}.model.Customer;
   import ${BASE_PACKAGE}.model.Order;
   import ${BASE_PACKAGE}.model.Product;
   import org.springframework.context.annotation.Scope;
   import org.springframework.context.annotation.ScopedProxyMode;
   import org.springframework.stereotype.Component;
   import org.springframework.web.context.WebApplicationContext;
   
   import javax.annotation.PreDestroy;
   import java.io.Serializable;
   import java.util.ArrayList;
   import java.util.List;
   
   @Component
   @Scope(value = WebApplicationContext.SCOPE_SESSION, 
          proxyMode = ScopedProxyMode.TARGET_CLASS)
   public class ShoppingCart implements Serializable {
       
       private static final long serialVersionUID = 1L;
       
       private List<CartItem> items = new ArrayList<>();
       private Customer customer;
       
       private final OrderService orderService;
       
       public ShoppingCart(OrderService orderService) {
           this.orderService = orderService;
       }
       
       public void setCustomer(Customer customer) {
           this.customer = customer;
       }
       
       public void addItem(Product product, int quantity) {
           items.add(new CartItem(product, quantity));
       }
       
       public Order checkout() {
           // Create order from cart items using injected service
           Order order = orderService.createOrderFromCart(customer, items);
           clear();  // Clear cart after checkout
           return order;
       }
       
       @PreDestroy  // Replaces @Remove
       public void clear() {
           items.clear();
           customer = null;
       }
   }
   ```

2. **Handle passivation/activation** (usually not needed in Spring):
   ```java
   // OLD: EJB lifecycle for stateful beans
   @PrePassivate
   public void onPassivate() {
       // Release resources before passivation
   }
   
   @PostActivate
   public void onActivate() {
       // Restore resources after activation
   }
   
   // NEW: Spring doesn't need this
   // HTTP session serialization handles state automatically
   // Just ensure your class implements Serializable
   // Transient fields won't be serialized:
   private transient SomeNonSerializableResource resource;
   
   // If resource initialization is needed, use @PostConstruct
   @PostConstruct
   public void init() {
       // Initialize resources
   }
   ```

3. **Configure session management** in `application.properties`:
   ```properties
   # Session timeout (replaces EJB stateful timeout)
   server.servlet.session.timeout=30m
   
   # Session persistence (optional)
   spring.session.store-type=redis  # or jdbc, hazelcast, etc.
   ```

4. **Remove old EJB files**:
   - Remove Stateful Session Bean class
   - Remove Home interface
   - Remove Remote/Local interface
   - Remove ejb-jar.xml entry

**Way to validate:**
- [ ] Spring session-scoped bean created
- [ ] Class implements Serializable
- [ ] State properly maintained across requests
- [ ] @PreDestroy method cleans up resources
- [ ] Dependencies injected correctly
- [ ] Session timeout configured
- [ ] Unit tests pass
- [ ] Integration tests verify session state
- [ ] No EJB dependencies remain

---
