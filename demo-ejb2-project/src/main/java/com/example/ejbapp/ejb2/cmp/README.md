# EJB 2.0 CMP (Container Managed Persistence) Entity Bean

This directory contains examples of EJB 2.0 Container Managed Persistence (CMP) Entity Beans - a legacy Java EE technology for managing persistent entities.

## ⚠️ Important Note

**This is legacy technology from EJB 2.0 specification (circa 2001)**. Modern applications should use JPA (Java Persistence API) with annotations instead. These examples are provided for:
- Understanding legacy codebases
- Migration reference
- Historical context
- Educational purposes

## What is CMP?

Container Managed Persistence (CMP) was the EJB 2.0 approach to object-relational mapping where:
- The container (application server) manages all persistence operations
- Developers declare fields in deployment descriptors
- Abstract getter/setter methods are implemented by the container at runtime
- Queries are written in EJB-QL (EJB Query Language)

## Product CMP Entity Bean Example

### Components

#### 1. Remote Interface (`Product.java`)
```java
public interface Product extends EJBObject {
    String getProductId() throws RemoteException;
    String getProductName() throws RemoteException;
    void setProductName(String name) throws RemoteException;
    // ... other getters/setters and business methods
}
```

**Purpose**: Defines the remote client view of the entity bean. Used for access across JVMs.

#### 2. Remote Home Interface (`ProductHome.java`)
```java
public interface ProductHome extends EJBHome {
    Product create(...) throws CreateException, RemoteException;
    Product findByPrimaryKey(String productId) throws FinderException, RemoteException;
    Collection<Product> findAll() throws FinderException, RemoteException;
    // ... other finder methods
}
```

**Purpose**: Factory interface for creating and finding entity beans remotely.

#### 3. Local Interface (`ProductLocal.java`)
```java
public interface ProductLocal extends EJBLocalObject {
    String getProductId();
    String getProductName();
    void setProductName(String name);
    // ... no RemoteException
}
```

**Purpose**: Local client view for access within the same JVM (more efficient).

#### 4. Local Home Interface (`ProductLocalHome.java`)
```java
public interface ProductLocalHome extends EJBLocalHome {
    ProductLocal create(...) throws CreateException;
    ProductLocal findByPrimaryKey(String productId) throws FinderException;
    // ... no RemoteException
}
```

**Purpose**: Factory interface for creating and finding entity beans locally.

#### 5. Bean Class (`ProductBean.java`)
```java
public abstract class ProductBean implements EntityBean {
    // Abstract CMP field accessors - implemented by container
    public abstract String getProductId();
    public abstract void setProductId(String productId);
    
    // Business methods
    public boolean isInStock() {
        return getQuantityInStock() > 0;
    }
    
    // EJB lifecycle methods
    public void ejbActivate() { }
    public void ejbPassivate() { }
    public void ejbLoad() { }
    public void ejbStore() { }
}
```

**Purpose**: Bean implementation with abstract CMP field accessors and business logic.

#### 6. Deployment Descriptor (`ejb-jar.xml`)
```xml
<entity>
    <ejb-name>ProductBean</ejb-name>
    <cmp-version>2.x</cmp-version>
    <abstract-schema-name>Product</abstract-schema-name>
    
    <!-- CMP Fields -->
    <cmp-field><field-name>productId</field-name></cmp-field>
    <cmp-field><field-name>productName</field-name></cmp-field>
    
    <!-- Primary Key -->
    <primkey-field>productId</primkey-field>
    
    <!-- EJB-QL Queries -->
    <query>
        <query-method>
            <method-name>findAll</method-name>
        </query-method>
        <ejb-ql>SELECT OBJECT(p) FROM Product p</ejb-ql>
    </query>
</entity>
```

**Purpose**: Declares CMP fields, primary key, and EJB-QL queries.

## CMP Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      Client Code                         │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│              Home Interface (Factory)                    │
│  - create()  - findByPrimaryKey()  - findAll()         │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│          Remote/Local Interface (Entity View)            │
│  - getters()  - setters()  - business methods()         │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│               Bean Class (Implementation)                │
│  - Abstract CMP accessors (container-implemented)       │
│  - Business logic                                        │
│  - Lifecycle methods                                     │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│            EJB Container (Application Server)            │
│  - Implements abstract CMP methods                      │
│  - Manages persistence to database                      │
│  - Handles transactions                                  │
│  - Executes EJB-QL queries                              │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
          ┌──────────────┐
          │   Database   │
          └──────────────┘
```

## Usage Examples

### Remote Access

```java
// Lookup home interface via JNDI
Context ctx = new InitialContext();
ProductHome home = (ProductHome) ctx.lookup("java:comp/env/ejb/Product");

// Create new product
Product product = home.create(
    "P001",
    "Laptop",
    "High-performance laptop",
    new BigDecimal("1299.99"),
    50,
    "Electronics"
);

// Find product by primary key
Product foundProduct = home.findByPrimaryKey("P001");

// Use product
String name = foundProduct.getProductName();
foundProduct.setPrice(new BigDecimal("1199.99"));
boolean inStock = foundProduct.isInStock();

// Find products by category
Collection<Product> electronics = home.findByCategory("Electronics");

// Find all products
Collection<Product> allProducts = home.findAll();
```

### Local Access (More Efficient)

```java
// Lookup local home interface
ProductLocalHome localHome = (ProductLocalHome) ctx.lookup("java:comp/env/ejb/ProductLocal");

// Create and use (no RemoteException)
ProductLocal product = localHome.create(...);
product.setProductName("Updated Name");
product.updateStock(10);
```

## EJB-QL Query Examples

The deployment descriptor defines queries using EJB-QL:

```xml
<!-- Find all products -->
<ejb-ql>
    SELECT OBJECT(p) FROM Product p
</ejb-ql>

<!-- Find by category -->
<ejb-ql>
    SELECT OBJECT(p) FROM Product p WHERE p.category = ?1
</ejb-ql>

<!-- Find by price less than -->
<ejb-ql>
    SELECT OBJECT(p) FROM Product p WHERE p.price < ?1
</ejb-ql>

<!-- Find products in stock -->
<ejb-ql>
    SELECT OBJECT(p) FROM Product p WHERE p.quantityInStock > 0
</ejb-ql>
```

## Limitations of EJB 2.0 CMP

1. **Verbose**: Requires multiple interfaces and XML configuration
2. **Not Object-Oriented**: Separate interfaces from implementation
3. **Limited Query Language**: EJB-QL less powerful than modern JPQL
4. **No Annotations**: Everything configured in XML
5. **Complex Relationships**: CMR (Container Managed Relationships) are complex
6. **Poor Performance**: Remote interfaces add overhead
7. **Vendor Lock-in**: Container-specific implementations
8. **Hard to Test**: Requires full EJB container

## Migration to JPA (Modern Approach)

### EJB 2.0 CMP → JPA Entity

**Before (EJB 2.0 CMP)**:
```java
// ProductBean.java + 4 interfaces + ejb-jar.xml
public abstract class ProductBean implements EntityBean {
    public abstract String getProductId();
    public abstract void setProductId(String id);
    // ... XML deployment descriptor required
}
```

**After (JPA)**:
```java
// Just one class with annotations
@Entity
@Table(name = "products")
public class Product {
    @Id
    private String productId;
    
    @Column(name = "product_name")
    private String productName;
    
    private BigDecimal price;
    
    // ... standard POJOs, no interfaces needed
}
```

### Finder Methods → JPQL/Criteria API

**Before (EJB 2.0)**:
```xml
<query>
    <method-name>findByCategory</method-name>
    <ejb-ql>SELECT OBJECT(p) FROM Product p WHERE p.category = ?1</ejb-ql>
</query>
```

**After (JPA)**:
```java
@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
    List<Product> findByCategory(String category);
    
    @Query("SELECT p FROM Product p WHERE p.price < :maxPrice")
    List<Product> findByPriceLessThan(@Param("maxPrice") BigDecimal maxPrice);
}
```

## Comparison: EJB 2.0 CMP vs JPA

| Feature | EJB 2.0 CMP | JPA |
|---------|-------------|-----|
| Configuration | XML only | Annotations + optional XML |
| Interfaces | 5 (Remote, RemoteHome, Local, LocalHome, Bean) | 1 (Entity class) |
| Queries | EJB-QL in XML | JPQL/Criteria API, annotations |
| Relationships | CMR in XML | Annotations (@OneToMany, etc.) |
| Inheritance | Limited | Full support |
| Caching | Container-dependent | Standardized (2nd level cache) |
| Testing | Requires EJB container | Easy with standard JUnit |
| Portability | Poor | Excellent |
| Performance | Moderate | Better |
| Learning Curve | Steep | Moderate |

## When You Might See CMP

- Legacy applications from early 2000s
- Older WebLogic, WebSphere, JBoss applications
- Systems not yet migrated to Java EE 5+
- Mainframe integration systems
- Enterprise applications predating Spring Framework

## Migration Strategy

### Phase 1: Analysis
1. Identify all CMP entity beans
2. Document relationships (CMR)
3. List finder methods and EJB-QL queries
4. Analyze business logic in bean classes

### Phase 2: Create JPA Entities
1. Convert bean classes to JPA entities
2. Add @Entity, @Table annotations
3. Convert CMP fields to JPA fields with @Column
4. Map relationships using JPA annotations
5. Convert business methods

### Phase 3: Create Repositories
1. Create Spring Data JPA repositories
2. Convert finder methods to repository methods
3. Migrate EJB-QL to JPQL

### Phase 4: Update Clients
1. Replace JNDI lookups with dependency injection
2. Remove remote interface usage
3. Use repositories instead of home interfaces
4. Remove RemoteException handling

### Phase 5: Testing
1. Create unit tests
2. Create integration tests
3. Performance testing
4. Validation

## Additional Resources

- [EJB 2.0 Specification](https://download.oracle.com/otn-pub/jcp/ejb-2.0-fr-spec-oth-JSpec/ejb-2_0-fr-spec.pdf)
- [JPA 2.2 Specification](https://download.oracle.com/otn-pub/jcp/persistence-2_2-mrel-spec/JavaPersistence.pdf)
- [Migrating from EJB to Spring](https://spring.io/guides)
- [Modern Java Persistence with JPA](https://vladmihalcea.com/tutorials/hibernate/)

## Summary

EJB 2.0 CMP represents an important milestone in Java enterprise history but is now considered legacy technology. Understanding it is valuable for:
- Maintaining existing systems
- Migration projects
- Historical context
- Appreciating modern JPA simplicity

**For new projects, always use JPA with Spring Data JPA.**
