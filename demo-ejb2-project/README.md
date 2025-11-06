# Demo EJB 2.0 Project

A consolidated, single-module Maven project demonstrating EJB 2.0 technologies, modern EJB 3.1, SOAP web services, and REST endpoints. This unified WAR project provides a comprehensive reference for understanding legacy EJB patterns and facilitating migration to modern Java EE / Jakarta EE architectures.

## 🎯 Project Overview

This demonstration project includes:

- **EJB 3.1 Beans**: Modern stateless session beans for business logic
- **EJB 2.0 Examples**: Legacy patterns for migration reference
  - CMP (Container Managed Persistence) Entity Beans
  - Message-Driven Beans (MDB) for asynchronous processing
- **SOAP Web Services**: JAX-WS based SOAP endpoints
- **REST Services**: JAX-RS based REST endpoints
- **JSF Web Interface**: JavaServer Faces pages for UI
- **JPA Entities**: Java Persistence API entities
- **CDI Integration**: Contexts and Dependency Injection

## 📁 Project Structure

```
demo-ejb2-project/
├── pom.xml                          # Maven configuration
├── README.md                        # This file
└── src/
    ├── main/
    │   ├── java/com/example/ejbapp/
    │   │   ├── model/               # JPA entities
    │   │   ├── data/                # Data access layer (repositories)
    │   │   ├── service/             # EJB 3.1 business services
    │   │   ├── controller/          # JSF managed beans
    │   │   ├── rest/                # REST endpoints
    │   │   ├── soap/                # SOAP web services
    │   │   ├── ejb2/                # EJB 2.0 examples
    │   │   │   ├── cmp/             # CMP Entity Beans
    │   │   │   └── mdb/             # Message-Driven Beans
    │   │   ├── util/                # Utility classes
    │   │   └── fakedata/            # Data generators
    │   ├── resources/
    │   │   ├── META-INF/
    │   │   │   ├── persistence.xml  # JPA configuration
    │   │   │   └── beans.xml        # CDI configuration
    │   │   └── import.sql           # Database initialization
    │   └── webapp/
    │       ├── WEB-INF/
    │       │   ├── beans.xml
    │       │   └── faces-config.xml
    │       ├── index.xhtml          # JSF pages
    │       └── resources/           # CSS, JS, images
    └── test/
        ├── java/                    # Test classes
        └── resources/               # Test configuration
```

## 🚀 Quick Start

### Prerequisites

- **Java Development Kit (JDK) 8** or later
- **Apache Maven 3.x**
- **JBoss AS 7.x** or **WildFly 8.x+**
- **PostgreSQL** or **H2** database (configured in persistence.xml)

### Build the Project

```bash
# Navigate to project directory
cd demo-ejb2-project

# Clean and build
mvn clean package

# The WAR file will be created at:
# target/demo-ejb2-project.war
```

### Deploy to JBoss/WildFly

#### Option 1: Copy to Deployments Directory

```bash
# Copy WAR to JBoss/WildFly
cp target/demo-ejb2-project.war $JBOSS_HOME/standalone/deployments/

# Start server (if not running)
$JBOSS_HOME/bin/standalone.sh   # Linux/Mac
$JBOSS_HOME/bin/standalone.bat  # Windows
```

#### Option 2: Use Management CLI

```bash
# Start JBoss/WildFly
$JBOSS_HOME/bin/standalone.sh

# In another terminal, use CLI
$JBOSS_HOME/bin/jboss-cli.sh --connect

# Deploy the WAR
deploy target/demo-ejb2-project.war
```

#### Option 3: Use Maven Plugin

Add to pom.xml:
```xml
<plugin>
    <groupId>org.wildfly.plugins</groupId>
    <artifactId>wildfly-maven-plugin</artifactId>
    <version>2.0.2.Final</version>
</plugin>
```

Then deploy:
```bash
mvn wildfly:deploy
```

## 🔧 Configuration

### Database Configuration

Edit `src/main/resources/META-INF/persistence.xml`:

```xml
<jta-data-source>java:jboss/datasources/ExampleDS</jta-data-source>
```

**Configure DataSource in JBoss/WildFly**:

1. Edit `$JBOSS_HOME/standalone/configuration/standalone.xml`
2. Add datasource configuration:

```xml
<datasource jndi-name="java:jboss/datasources/ExampleDS" pool-name="ExampleDS">
    <connection-url>jdbc:h2:mem:test;DB_CLOSE_DELAY=-1</connection-url>
    <driver>h2</driver>
    <security>
        <user-name>sa</user-name>
        <password></password>
    </security>
</datasource>
```

### JMS Configuration (for MDBs)

The project includes Message-Driven Beans that require JMS destinations.

**Configure JMS Queues and Topics**:

Add to `standalone.xml`:

```xml
<subsystem xmlns="urn:jboss:domain:messaging-activemq:1.0">
    <server name="default">
        <!-- Add JMS Queue -->
        <jms-queue name="OrderQueue" entries="java:/jms/queue/OrderQueue"/>
        
        <!-- Add JMS Topic -->
        <jms-topic name="NotificationTopic" entries="java:/jms/topic/NotificationTopic"/>
    </server>
</subsystem>
```

Or use CLI:

```bash
/subsystem=messaging-activemq/server=default/jms-queue=OrderQueue:add(entries=["java:/jms/queue/OrderQueue"])
/subsystem=messaging-activemq/server=default/jms-topic=NotificationTopic:add(entries=["java:/jms/topic/NotificationTopic"])
```

## 📡 Available Endpoints

After deployment, the following endpoints are available:

### Web Interface

- **Main Page**: http://localhost:8080/demo-ejb2-project/
- **JSF Interface**: http://localhost:8080/demo-ejb2-project/index.xhtml

### REST API

- **Base URL**: http://localhost:8080/demo-ejb2-project/rest/
- **Swagger UI**: http://localhost:8080/demo-ejb2-project/o2c.html
- **Members API**: http://localhost:8080/demo-ejb2-project/rest/members

### SOAP Web Services

#### Member Service
- **WSDL**: http://localhost:8080/demo-ejb2-project/MemberWebService?wsdl
- **Operations**: registerMember, getMemberById, getMemberByEmail, getAllMembers, updateMember, deleteMember

#### Calculator Service
- **WSDL**: http://localhost:8080/demo-ejb2-project/CalculatorWebService?wsdl
- **Operations**: add, subtract, multiply, divide, calculatePercentage, calculateCompoundInterest

### Testing SOAP Services

**Using cURL**:

```bash
curl -X POST http://localhost:8080/demo-ejb2-project/MemberWebService \
  -H "Content-Type: text/xml" \
  -d '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:soap="
  .com/">
   <soapenv:Body>
      <soap:registerMember>
         <memberRequest>
            <name>John Doe</name>
            <email>john@example.com</email>
            <phoneNumber>1234567890</phoneNumber>
         </memberRequest>
      </soap:registerMember>
   </soapenv:Body>
</soapenv:Envelope>'
```

**Using SoapUI**:
1. Create new SOAP project
2. Enter WSDL URL
3. Generate requests
4. Execute

## 🏗️ EJB 2.0 Examples

The project includes comprehensive EJB 2.0 examples for migration reference:

### CMP Entity Beans (`ejb2/cmp/`)

Container Managed Persistence entity bean example:
- `Product.java` - Remote interface
- `ProductHome.java` - Remote home interface
- `ProductLocal.java` - Local interface
- `ProductLocalHome.java` - Local home interface
- `ProductBean.java` - Bean implementation
- `ejb-jar.xml` - Deployment descriptor with EJB-QL queries

**See detailed documentation**: `src/main/java/com.example/ejbapp/ejb2/cmp/README.md`

### Stateless Session Beans (`ejb2/session/`)

Stateless Session Bean example for order processing:
- `OrderService.java` - Remote interface
- `OrderServiceHome.java` - Remote home interface
- `OrderServiceLocal.java` - Local interface
- `OrderServiceLocalHome.java` - Local home interface
- `OrderServiceBean.java` - Bean implementation
- `ejb-jar.xml` - Deployment descriptor

**See detailed documentation**: `src/main/java/com/example/ejbapp/ejb2/session/README.md`

### Message-Driven Beans (`ejb2/mdb/`)

Asynchronous message processing examples:
- `OrderProcessorMDB.java` - Queue-based MDB (point-to-point)
- `NotificationMDB.java` - Topic-based MDB (publish-subscribe)
- `MessageSenderExample.java` - JMS message sender
- `ejb-jar.xml` - MDB deployment descriptor

**See detailed documentation**: `src/main/java/com/example/ejbapp/ejb2/mdb/README.md`

### Testing Message-Driven Beans

```bash
# Compile and run message sender
cd demo-ejb2-project
mvn compile
mvn exec:java -Dexec.mainClass="com.example.ejbapp.ejb2.mdb.MessageSenderExample"
```

## 🧪 Testing

### Run Unit Tests

```bash
mvn test
```

### Run Integration Tests with Arquillian

```bash
# With managed JBoss AS
mvn clean test -Parq-jbossas-managed

# With remote JBoss AS (server must be running)
mvn clean test -Parq-jbossas-remote
```

## 📚 Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 8+ | Programming language |
| Maven | 3.x | Build tool |
| Java EE | 7 | Enterprise framework |
| EJB | 3.1 / 2.0 | Business components |
| JPA | 2.0 | Persistence |
| CDI | 1.1 | Dependency injection |
| JAX-RS | 1.1 | REST services |
| JAX-WS | 2.0 | SOAP services |
| JSF | 2.1 | Web framework |
| JMS | 1.1 | Messaging |
| JBoss AS / WildFly | 7.x / 8.x+ | Application server |

## 🔍 Troubleshooting

### Issue: WAR deployment fails

**Check**:
1. Server logs: `$JBOSS_HOME/standalone/log/server.log`
2. Deployment scanner: `$JBOSS_HOME/standalone/deployments/demo-ejb2-project.war.failed`
3. Dependencies in pom.xml

### Issue: SOAP services not accessible

**Check**:
1. WAR is fully deployed
2. No port conflicts (default: 8080)
3. Firewall settings
4. Access WSDL URL directly

### Issue: JMS messages not processed

**Check**:
1. JMS destinations are configured
2. MDB is deployed (check logs)
3. Connection factory is available
4. Messages are being sent to correct destination

### Issue: Database connection fails

**Check**:
1. DataSource configured in standalone.xml
2. Database driver is available
3. Database is running
4. Connection URL, username, password are correct

## 📖 Additional Documentation

- **SOAP Services**: `src/main/java/com/example/ejbapp/soap/README.md`
- **EJB 2.0 CMP**: `src/main/java/com/example/ejbapp/ejb2/cmp/README.md`
- **EJB 2.0 MDB**: `src/main/java/com/example/ejbapp/ejb2/mdb/README.md`

## 📄 License

Apache License 2.0

## 🤝 Contributing

This is a demonstration project for educational purposes. Feel free to use it as a reference for:
- Understanding EJB 2.0 legacy patterns
- Learning EJB 3.1 best practices
- Migrating from EJB 2.0 to modern Java EE
- SOAP and REST web service development

## 📞 Support

For issues or questions:
1. Check the detailed README files in each package
2. Review JBoss/WildFly documentation
3. Consult Java EE specifications

---

**Purpose**: EJB 2.0 demonstration and migration reference for Java Architecture Analyzer
