# EJB 2.0 Stateless Session Bean Example

This directory contains an example of an EJB 2.0 Stateless Session Bean (SLSB) for order management.

## ⚠️ Important Note

**This is legacy technology from EJB 2.0 specification (circa 2001)**. Modern applications should use EJB 3.x (with annotations) or Spring services instead. These examples are provided for:
- Understanding legacy codebases
- Migration reference
- Historical context
- Educational purposes

## OrderService Stateless Session Bean Example

This example demonstrates a simple order processing service using an EJB 2.0 Stateless Session Bean. It includes remote and local interfaces, a home interface, a local home interface, and the bean implementation.

### Components

#### 1. Remote Interface (`OrderService.java`)

```java
public interface OrderService extends EJBObject {
    String placeOrder(String customerId, String productId, int quantity) throws RemoteException;
    String getOrderDetails(String orderId) throws RemoteException;
    List<String> listOrdersByCustomer(String customerId) throws RemoteException;
    boolean updateOrderStatus(String orderId, String newStatus) throws RemoteException;
    boolean cancelOrder(String orderId) throws RemoteException;
}
```

**Purpose**: Defines the remote client view of the session bean. Used for access across JVMs.

#### 2. Remote Home Interface (`OrderServiceHome.java`)

```java
public interface OrderServiceHome extends EJBHome {
    OrderService create() throws CreateException, RemoteException;
}
```

**Purpose**: Factory interface for creating remote references to the session bean.

#### 3. Local Interface (`OrderServiceLocal.java`)

```java
public interface OrderServiceLocal extends EJBLocalObject {
    String placeOrder(String customerId, String productId, int quantity);
    String getOrderDetails(String orderId);
    List<String> listOrdersByCustomer(String customerId);
    boolean updateOrderStatus(String orderId, String newStatus);
    boolean cancelOrder(String orderId);
}
```

**Purpose**: Local client view for access within the same JVM (more efficient, no `RemoteException`).

#### 4. Local Home Interface (`OrderServiceLocalHome.java`)

```java
public interface OrderServiceLocalHome extends EJBLocalHome {
    OrderServiceLocal create() throws CreateException;
}
```

**Purpose**: Factory interface for creating local references to the session bean.

#### 5. Bean Class (`OrderServiceBean.java`)

```java
public class OrderServiceBean implements SessionBean {
    // Business methods implementation
    public String placeOrder(String customerId, String productId, int quantity) { /* ... */ }
    public String getOrderDetails(String orderId) { /* ... */ }
    // ... other business methods

    // EJB lifecycle methods
    public void ejbCreate() { /* ... */ }
    public void setSessionContext(SessionContext sessionContext) { /* ... */ }
    public void ejbActivate() { /* ... */ }
    public void ejbPassivate() { /* ... */ }
    public void ejbRemove() { /* ... */ }
}
```

**Purpose**: Implements the business logic of the session bean and EJB lifecycle methods.

#### 6. Deployment Descriptor (`ejb-jar.xml`)

```xml
<session>
    <ejb-name>OrderServiceBean</ejb-name>
    <home>com.example.ejbapp.ejb2.session.OrderServiceHome</home>
    <remote>com.example.ejbapp.ejb2.session.OrderService</remote>
    <local-home>com.example.ejbapp.ejb2.session.OrderServiceLocalHome</local-home>
    <local>com.example.ejbapp.ejb2.session.OrderServiceLocal</local>
    <ejb-class>com.example.ejbapp.ejb2.session.OrderServiceBean</ejb-class>
    <session-type>Stateless</session-type>
    <transaction-type>Container</transaction-type>
</session>
```

**Purpose**: Declares the session bean, its interfaces, and transaction management type.

## Usage Example (Client-side)

### Remote Client

```java
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Properties;

public class OrderServiceClient {
    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
        props.put(Context.PROVIDER_URL, "http-remoting://localhost:8080"); // Adjust as per your JBoss/WildFly setup

        Context ctx = new InitialContext(props);
        OrderServiceHome home = (OrderServiceHome) ctx.lookup("ejb:/demo-ejb2-project//OrderServiceBean!com.example.ejbapp.ejb2.session.OrderServiceHome");
        OrderService orderService = home.create();

        String orderId1 = orderService.placeOrder("CUST001", "PROD001", 2);
        System.out.println("Placed order: " + orderId1);
        System.out.println("Order details: " + orderService.getOrderDetails(orderId1));

        orderService.updateOrderStatus(orderId1, "SHIPPED");
        System.out.println("Updated order details: " + orderService.getOrderDetails(orderId1));

        orderService.cancelOrder(orderId1);
        System.out.println("Cancelled order details: " + orderService.getOrderDetails(orderId1));

        ctx.close();
    }
}
```

### Local Client (within the same application)

```java
import javax.naming.Context;
import javax.naming.InitialContext;

public class OrderServiceLocalClient {
    public static void main(String[] args) throws Exception {
        Context ctx = new InitialContext();
        OrderServiceLocalHome localHome = (OrderServiceLocalHome) ctx.lookup("java:comp/env/ejb/OrderServiceLocal"); // JNDI lookup for local home
        OrderServiceLocal orderServiceLocal = localHome.create();

        String orderId2 = orderServiceLocal.placeOrder("CUST002", "PROD002", 1);
        System.out.println("Placed local order: " + orderId2);
        System.out.println("Local order details: " + orderServiceLocal.getOrderDetails(orderId2));

        ctx.close();
    }
}
```

## Deployment

To deploy this EJB 2.0 session bean, you would typically package it within an `EJB-JAR` file and then deploy that EJB-JAR into an application server like JBoss AS 7/WildFly. The `ejb-jar.xml` deployment descriptor is crucial for the container to understand and manage the bean.

For this `demo-ejb2-project`, the `ejb-jar.xml` would need to be placed in `src/main/resources/META-INF/` or merged with an existing one, and the compiled classes would be part of the WAR file.

## Migration to EJB 3.x (Modern Approach)

**Before (EJB 2.0 SLSB)**:
- Requires Remote/Local interfaces, Home/Local Home interfaces, and a Bean class.
- Extensive XML configuration in `ejb-jar.xml`.

**After (EJB 3.x SLSB)**:
- Only requires a single Bean class with `@Stateless` annotation.
- Business interfaces are optional (local by default, remote with `@Remote`).
- No XML configuration needed for basic setup.

```java
// EJB 3.x Stateless Session Bean
@Stateless
public class OrderServiceEJB3 implements OrderServiceLocal, OrderService {
    // ... business methods
}
```

This significantly reduces boilerplate code and simplifies development.
