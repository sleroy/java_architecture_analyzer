# EJB 2.0 to Spring Boot 3.5.7 Dependency Migration Mapping

## Executive Summary

This document provides a comprehensive mapping from the current EJB 2.0 application dependencies to Spring Boot 3.5.7 equivalents, targeting Java 21 compatibility.

## Migration Overview

| Category | EJB 2.0 Dependencies | Spring Boot 3.5.7 Equivalent | Status |
|----------|----------------------|------------------------------|---------|
| **Core Framework** | Java EE 7 APIs | Spring Boot 3.5.7 | ✅ Replace |
| **Dependency Injection** | CDI/EJB | Spring Context | ✅ Replace |
| **Persistence** | JPA 2.0/Hibernate | Spring Data JPA | ✅ Upgrade |
| **REST APIs** | JAX-RS 1.1 | Spring Web MVC | ✅ Replace |
| **SOAP Services** | JAX-WS | Spring Web Services | ✅ Replace |
| **Messaging** | JMS/MDB | Spring JMS/ActiveMQ | ✅ Replace |
| **Validation** | Bean Validation | Spring Validation | ✅ Upgrade |
| **Testing** | Arquillian | Spring Boot Test | ✅ Replace |

## Detailed Dependency Mappings

### 1. Core EJB and Java EE APIs

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|-------------------|--------------------------------|---------|-----------------|
| `org.jboss.spec.javax.ejb:jboss-ejb-api_3.1_spec:1.0.2.Final` | `org.springframework.boot:spring-boot-starter:3.5.7` | 3.5.7 | **REMOVE** - Replace with Spring components |
| `javax:javaee-web-api:7.0` | `org.springframework.boot:spring-boot-starter-web:3.5.7` | 3.5.7 | **REMOVE** - Use Spring Web starter |
| `org.wildfly.bom:wildfly-javaee7:10.1.0.Final` | N/A | - | **REMOVE** - No longer needed |

**Breaking Changes:**
- `@Stateless`, `@Stateful` → `@Service`, `@Component`
- `@EJB` → `@Autowired` or `@Inject`
- EJB lifecycle methods → Spring lifecycle annotations

### 2. Dependency Injection (CDI)

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|-------------------|--------------------------------|---------|-----------------|
| `javax.enterprise:cdi-api` | `org.springframework.boot:spring-boot-starter:3.5.7` | 3.5.7 | **INCLUDED** - Built into Spring Boot |
| `javax.inject:javax.inject:1` | `jakarta.inject:jakarta.inject-api:2.0.1` | 2.0.1 | **OPTIONAL** - Spring supports JSR-330 |

**Breaking Changes:**
- `@Named` → `@Component` (or keep `@Named` with JSR-330)
- `@ApplicationScoped` → `@Component` (singleton by default)
- `@RequestScoped` → `@RequestScope`

### 3. Persistence Layer

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|-------------------|--------------------------------|---------|-----------------|
| `org.hibernate.javax.persistence:hibernate-jpa-2.0-api:1.0.1.Final` | `org.springframework.boot:spring-boot-starter-data-jpa:3.5.7` | 3.5.7 | **UPGRADE** - JPA 3.1 with Hibernate 6.x |
| `org.hibernate:hibernate-validator` | `org.springframework.boot:spring-boot-starter-validation:3.5.7` | 3.5.7 | **INCLUDED** - Hibernate Validator 8.x |

**Breaking Changes:**
- `javax.persistence.*` → `jakarta.persistence.*`
- Update `persistence.xml` to Jakarta namespace
- Hibernate 6.x API changes

### 4. REST Services (JAX-RS → Spring Web MVC)

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|-------------------|--------------------------------|---------|-----------------|
| `org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_1.1_spec:1.0.1.Final` | `org.springframework.boot:spring-boot-starter-web:3.5.7` | 3.5.7 | **REPLACE** - Use Spring MVC |
| `javax.ws.rs:jsr311-api:1.1.1` | N/A | - | **REMOVE** - Not needed |

**Breaking Changes:**
- `@Path` → `@RequestMapping` or `@GetMapping`/`@PostMapping`
- `@GET`, `@POST` → `@GetMapping`, `@PostMapping`
- `@PathParam` → `@PathVariable`
- `@QueryParam` → `@RequestParam`
- `@Consumes`/`@Produces` → `consumes`/`produces` in mapping annotations

### 5. SOAP Web Services

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|-------------------|--------------------------------|---------|-----------------|
| `javax.xml.ws:jaxws-api:2.3.1` | `org.springframework.boot:spring-boot-starter-web-services:3.5.7` | 3.5.7 | **REPLACE** - Spring WS |
| `javax.jws:javax.jws-api:1.1` | N/A | - | **REMOVE** - Use Spring WS annotations |
| `javax.xml.bind:jaxb-api:2.3.1` | `jakarta.xml.bind:jakarta.xml.bind-api:4.0.2` | 4.0.2 | **UPGRADE** - Jakarta JAXB |
| `org.glassfish.jaxb:jaxb-runtime:2.3.1` | `org.glassfish.jaxb:jaxb-runtime:4.0.5` | 4.0.5 | **UPGRADE** - Jakarta JAXB runtime |

**Breaking Changes:**
- `@WebService` → `@Endpoint` (Spring WS)
- `javax.xml.bind.*` → `jakarta.xml.bind.*`
- WSDL-first approach recommended with Spring WS

### 6. JMS and Message-Driven Beans

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|-------------------|--------------------------------|---------|-----------------|
| `org.jboss.spec.javax.jms:jboss-jms-api_2.0_spec` | `org.springframework.boot:spring-boot-starter-activemq:3.5.7` | 3.5.7 | **REPLACE** - Spring JMS |

**Breaking Changes:**
- `@MessageDriven` → `@JmsListener`
- `MessageListener.onMessage()` → `@JmsListener` method
- EJB container-managed transactions → Spring `@Transactional`

### 7. API Documentation (Swagger)

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|-------------------|--------------------------------|---------|-----------------|
| `com.wordnik:swagger-jaxrs_2.10:1.3.1` | `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0` | 2.7.0 | **REPLACE** - Modern OpenAPI 3 |

**Breaking Changes:**
- Swagger 1.x annotations → OpenAPI 3 annotations
- `@Api` → `@Tag`
- `@ApiOperation` → `@Operation`

### 8. Database and Connection Pooling

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|-------------------|--------------------------------|---------|-----------------|
| `com.h2database:h2:2.2.220` | `com.h2database:h2:2.3.232` | 2.3.232 | **UPGRADE** - Latest H2 |
| `commons-dbcp:commons-dbcp:1.4` | `com.zaxxer:HikariCP:6.2.1` | 6.2.1 | **REPLACE** - HikariCP (default in Spring Boot) |
| `commons-pool:commons-pool:1.6` | N/A | - | **REMOVE** - Not needed with HikariCP |

### 9. Testing Framework

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|-------------------|--------------------------------|---------|-----------------|
| `junit:junit:4.13.1` | `org.springframework.boot:spring-boot-starter-test:3.5.7` | 3.5.7 | **UPGRADE** - JUnit 5 included |
| `org.jboss.arquillian.junit:arquillian-junit-container` | N/A | - | **REMOVE** - Use Spring Boot Test |
| `org.jboss.as:jboss-as-arquillian-container-*` | N/A | - | **REMOVE** - Use `@SpringBootTest` |

**Breaking Changes:**
- JUnit 4 → JUnit 5
- Arquillian tests → Spring Boot integration tests
- `@Test` imports change to `org.junit.jupiter.api.Test`

### 10. Logging

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|-------------------|--------------------------------|---------|-----------------|
| `org.slf4j:slf4j-api:1.7.36` | `org.springframework.boot:spring-boot-starter-logging:3.5.7` | 3.5.7 | **INCLUDED** - Logback by default |

### 11. Report Generation (Optional)

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|-------------------|--------------------------------|---------|-----------------|
| `jasperreports:jasperreports:3.5.3` | `net.sf.jasperreports:jasperreports:7.0.1` | 7.0.1 | **UPGRADE** - Modern JasperReports |

## Complete Spring Boot 3.5.7 POM Dependencies

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
        <version>3.5.7</version>
        <relativePath/>
    </parent>

    <groupId>com.example.ejbapp</groupId>
    <artifactId>demo-spring-boot-project</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>21</java.version>
        <springdoc.version>2.7.0</springdoc.version>
        <jasperreports.version>7.0.1</jasperreports.version>
    </properties>

    <dependencies>
        <!-- Core Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web-services</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-activemq</artifactId>
        </dependency>
        
        <!-- Database -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        
        <!-- API Documentation -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        
        <!-- Optional: JSR-330 Support -->
        <dependency>
            <groupId>jakarta.inject</groupId>
            <artifactId>jakarta.inject-api</artifactId>
        </dependency>
        
        <!-- Optional: Report Generation -->
        <dependency>
            <groupId>net.sf.jasperreports</groupId>
            <artifactId>jasperreports</artifactId>
            <version>${jasperreports.version}</version>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
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

## Migration Strategy

### Phase 1: Infrastructure Migration
1. **Update Java version** to 21
2. **Replace Maven parent** with Spring Boot parent
3. **Update dependencies** according to mapping table
4. **Configure application properties** (replace XML configuration)

### Phase 2: Code Migration
1. **EJB to Spring Components**
   - Replace `@Stateless` with `@Service`
   - Replace `@EJB` with `@Autowired`
   - Convert EJB interfaces to Spring service interfaces

2. **JAX-RS to Spring MVC**
   - Replace JAX-RS annotations with Spring MVC
   - Update REST endpoint mappings
   - Migrate exception handling

3. **JMS/MDB to Spring JMS**
   - Replace `@MessageDriven` with `@JmsListener`
   - Configure JMS with Spring Boot auto-configuration

4. **SOAP Services**
   - Migrate to Spring Web Services
   - Update WSDL contracts if needed

### Phase 3: Testing Migration
1. **Replace Arquillian** with Spring Boot Test
2. **Update JUnit 4** to JUnit 5
3. **Create integration tests** with `@SpringBootTest`

## Key Breaking Changes Summary

| Area | Breaking Change | Solution |
|------|----------------|----------|
| **Namespace** | `javax.*` → `jakarta.*` | Update all imports |
| **EJB Annotations** | `@Stateless`, `@EJB` | Use Spring annotations |
| **JAX-RS** | JAX-RS annotations | Spring MVC annotations |
| **JMS** | `@MessageDriven` | `@JmsListener` |
| **Testing** | Arquillian | Spring Boot Test |
| **Packaging** | WAR deployment | Executable JAR |
| **Server** | JBoss/WildFly | Embedded Tomcat |

## Estimated Migration Effort

- **Low Complexity**: Database, logging, basic services (1-2 weeks)
- **Medium Complexity**: REST endpoints, validation (2-3 weeks)  
- **High Complexity**: SOAP services, JMS, complex EJB patterns (3-4 weeks)
- **Testing Migration**: 1-2 weeks

**Total Estimated Effort**: 7-11 weeks for complete migration

## Benefits of Migration

1. **Modern Framework**: Latest Spring Boot with Java 21 support
2. **Simplified Deployment**: Executable JAR vs WAR deployment
3. **Better Testing**: Comprehensive Spring Boot Test support
4. **Auto-configuration**: Reduced XML configuration
5. **Cloud Ready**: Better containerization and cloud deployment
6. **Performance**: Modern JVM optimizations and reactive support
7. **Security**: Latest security patches and features
