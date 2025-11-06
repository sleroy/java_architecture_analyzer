# EJB 2.0 Era JDBC CRUD Example

This package demonstrates a complete JDBC-based CRUD application using patterns and anti-patterns typical of the EJB 2.0 era (2000-2010).

## Overview

This is an authentic representation of how enterprise Java applications were built before modern frameworks like Spring Boot became dominant. It includes many patterns that were considered "best practices" at the time but are now recognized as anti-patterns.

## Architecture

```
jdbc/
├── framework/          # Infrastructure layer (static singletons)
│   ├── ConnectionManager.java      # Connection pool manager
│   ├── JdbcHelper.java             # JDBC utility methods
│   └── TransactionManager.java     # ThreadLocal transaction management
├── model/              # Domain model
│   └── Customer.java               # Simple JavaBean
├── dao/                # Data Access Layer
│   └── CustomerDAO.java            # Static singleton DAO
├── service/            # Business Logic Layer
│   ├── AbstractEntityManager.java  # Generic base class (Map-based API)
│   └── CustomerManager.java        # Customer service implementation
├── rest/               # REST Web Services
│   └── CustomerRestService.java    # JAX-RS endpoint
├── soap/               # SOAP Web Services
│   └── CustomerSoapService.java    # JAX-WS endpoint
└── demo/               # Demo/Test
    └── CustomerDemo.java           # Standalone demo application
```

## Key Features

### Authentic EJB 2.0 Era Patterns

1. **Static Singleton Pattern**
   - `ConnectionManager.getInstance()`
   - `CustomerDAO.getInstance()`
   - Eager initialization
   - No dependency injection

2. **Generic Base Class with Map-based API**
   - `AbstractEntityManager` with methods like `listAll()`, `addRecord(Map data)`
   - Loss of type safety (requires casting everywhere)
   - Map parameters with string keys
   - Untyped return values

3. **Manual Transaction Management**
   - ThreadLocal-based transaction context
   - Manual begin/commit/rollback
   - No declarative transactions

4. **Connection Pooling**
   - Apache Commons DBCP (very common in that era)
   - Manual configuration

5. **H2 In-Memory Database**
   - No external database required
   - Schema initialized automatically

## Anti-Patterns Demonstrated

This code intentionally demonstrates anti-patterns that were common in the EJB 2.0 era:

- ❌ **Static Singletons** - Tight coupling, difficult to test
- ❌ **God Objects** - Too many responsibilities
- ❌ **Loss of Type Safety** - Everything is Object/Map
- ❌ **Manual Resource Management** - Pre-try-with-resources
- ❌ **Swallowing Exceptions** - `closeQuietly()` methods
- ❌ **ThreadLocal Memory Leaks** - If not cleaned up properly
- ❌ **No Dependency Injection** - Direct instantiation everywhere
- ❌ **Generic Exceptions** - Methods throw `Exception`

## Database Schema

Single table: `CUSTOMER`

| Column     | Type          | Description                    |
|------------|---------------|--------------------------------|
| ID         | INTEGER       | Primary key (auto-increment)   |
| NAME       | VARCHAR(100)  | Customer name                  |
| EMAIL      | VARCHAR(100)  | Customer email                 |
| PHONE      | VARCHAR(20)   | Customer phone                 |
| CREATED_AT | TIMESTAMP     | Creation timestamp             |

## Usage

### 1. Run the Demo Application

```bash
cd demo-ejb2-project
mvn clean compile
mvn exec:java -Dexec.mainClass="com.example.ejbapp.jdbc.demo.CustomerDemo"
```

### 2. Use the REST API

Deploy the WAR file and access:

```bash
# List all customers
GET http://localhost:8080/demo-ejb2-project/rest/jdbc/customers

# Get customer by ID
GET http://localhost:8080/demo-ejb2-project/rest/jdbc/customers/1

# Add customer
POST http://localhost:8080/demo-ejb2-project/rest/jdbc/customers
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john@example.com",
  "phone": "555-0100"
}

# Update customer
PUT http://localhost:8080/demo-ejb2-project/rest/jdbc/customers/1
Content-Type: application/json

{
  "name": "John Doe Updated",
  "email": "john.updated@example.com"
}

# Delete customer
DELETE http://localhost:8080/demo-ejb2-project/rest/jdbc/customers/1

# Count customers
GET http://localhost:8080/demo-ejb2-project/rest/jdbc/customers/count
```

### 3. Use the SOAP API

The SOAP service is available at:
```
http://localhost:8080/demo-ejb2-project/CustomerSoapService?wsdl
```

Available operations:
- `listAllCustomers()`
- `getCustomerById(int id)`
- `addCustomer(String name, String email, String phone)`
- `updateCustomer(int id, String name, String email, String phone)`
- `deleteCustomer(int id)`
- `countCustomers()`

### 4. Programmatic Usage

```java
// Create manager
CustomerManager manager = new CustomerManager();

// Add customer using Map-based API
Map data = new HashMap();
data.put("name", "John Doe");
data.put("email", "john@example.com");
data.put("phone", "555-0100");
Object customer = manager.addRecord(data); // Returns Object!

// List all customers
List customers = manager.listAll(); // Returns untyped List!

// Cast to Customer (required!)
Customer c = (Customer) customers.get(0);
```

## Code Characteristics

### JDK 6-8 Compatible

- No lambda expressions
- No method references
- No streams API
- Traditional for loops
- Raw types (unparameterized generics)
- Manual resource management

### Pre-Modern Framework Era

- No Spring Framework
- No dependency injection
- No JPA/Hibernate
- Pure JDBC
- Manual transaction management
- Static utility classes

## Educational Value

This code demonstrates:

1. **How enterprise Java looked before modern frameworks**
2. **Why frameworks like Spring became so popular**
3. **Common patterns that seemed like good ideas at the time**
4. **Evolution of Java enterprise development**

## Modern Alternatives

Today, this code would be written with:

- Spring Boot
- Spring Data JPA
- @Transactional annotations
- Dependency injection
- Type-safe APIs
- Lambda expressions
- Try-with-resources
- Proper generics

## Testing

The `CustomerDemo` class provides a complete demonstration of all CRUD operations using the generic Map-based API.

## Dependencies

- H2 Database 1.4.196
- Apache Commons DBCP 1.4
- Apache Commons Pool 1.6
- Java EE 7 APIs (provided)

## Author Notes

This code is intentionally written in the style of the EJB 2.0 era (2000-2010) to demonstrate patterns that were common during that period. While functional, it includes many anti-patterns that should be avoided in modern applications.

**Use for educational purposes only!**
