# EJB 2.0 to Spring Boot 3.5.7 Migration Mapping

## Executive Summary

This document provides a comprehensive mapping from the EJB 2.0 application dependencies to Spring Boot 3.5.7 equivalents, targeting Java 21 compatibility.

## Migration Overview

| Category | EJB 2.0 Components | Spring Boot 3.5.7 Replacement | Status |
|----------|-------------------|--------------------------------|---------|
| **Container** | JBoss AS 7/WildFly | Embedded Tomcat/Jetty | ✅ Direct |
| **Business Logic** | EJB 3.1 + EJB 2.0 | Spring Services + @Transactional | ✅ Direct |
| **Persistence** | JPA 2.0 + Hibernate | Spring Data JPA 3.x | ✅ Direct |
| **REST APIs** | JAX-RS 1.1 | Spring Web MVC | ✅ Direct |
| **SOAP Services** | JAX-WS | Spring Web Services | ⚠️ Manual |
| **Dependency Injection** | CDI 1.1 | Spring Context | ✅ Direct |
| **Messaging** | JMS 2.0 + MDB | Spring JMS + @JmsListener | ✅ Direct |
| **Validation** | Bean Validation | Spring Validation | ✅ Direct |
| **Web UI** | JSF 2.1 | Spring MVC + Thymeleaf | ⚠️ Manual |

## Detailed Dependency Mapping

### 1. Core Spring Boot Dependencies

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Notes |
|---------------------|--------------------------------|---------|-------|
| `org.wildfly.bom:wildfly-javaee7` | `org.springframework.boot:spring-boot-starter-parent` | 3.5.7 | Parent POM for dependency management |

### 2. EJB and Business Logic

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|---------------------|--------------------------------|---------|-----------------|
| `org.jboss.spec.javax.ejb:jboss-ejb-api_3.1_spec` | `org.springframework:spring-context` | 6.2.1 | Replace @Stateless with @Service |
| `javax.enterprise:cdi-api` | `org.springframework:spring-context` | 6.2.1 | Replace @Inject with @Autowired |
| `javax:javaee-web-api` | `org.springframework.boot:spring-boot-starter-web` | 3.5.7 | Full web stack replacement |

**Breaking Changes:**
- `@Stateless` → `@Service`
- `@Inject` → `@Autowired` or constructor injection
- `@EJB` → `@Autowired`
- Remove `@Local`/`@Remote` interfaces

### 3. Persistence Layer

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|---------------------|--------------------------------|---------|-----------------|
| `org.hibernate.javax.persistence:hibernate-jpa-2.0-api` | `org.springframework.boot:spring-boot-starter-data-jpa` | 3.5.7 | Includes JPA 3.1 + Hibernate 6.x |
| `com.h2database:h2` | `com.h2database:h2` | 2.3.232 | Keep H2, update version |
| `commons-dbcp:commons-dbcp` | **REMOVE** | - | Spring Boot uses HikariCP by default |
| `commons-pool:commons-pool` | **REMOVE** | - | Not needed with HikariCP |

**Breaking Changes:**
- `persistence.xml` → `application.yml` configuration
- `@PersistenceContext` → `@Autowired JpaRepository`
- Manual EntityManager → Spring Data repositories

### 4. REST Services

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|---------------------|--------------------------------|---------|-----------------|
| `org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_1.1_spec` | `org.springframework.boot:spring-boot-starter-web` | 3.5.7 | Included in web starter |

**Breaking Changes:**
- `@Path` → `@RequestMapping`
- `@GET/@POST` → `@GetMapping/@PostMapping`
- `@PathParam` → `@PathVariable`
- `@QueryParam` → `@RequestParam`

### 5. SOAP Web Services

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|---------------------|--------------------------------|---------|-----------------|
| `javax.xml.ws:jaxws-api` | `org.springframework.boot:spring-boot-starter-web-services` | 3.5.7 | Spring WS approach |
| `javax.jws:javax.jws-api` | `org.springframework.ws:spring-ws-core` | 4.0.11 | Contract-first approach |
| `javax.xml.bind:jaxb-api` | `jakarta.xml.bind:jakarta.xml.bind-api` | 4.0.2 | Jakarta namespace |
| `org.glassfish.jaxb:jaxb-runtime` | `org.glassfish.jaxb:jaxb-runtime` | 4.0.5 | Update version |

**Breaking Changes:**
- `@WebService` → Spring WS contract-first approach
- Manual WSDL generation required
- Namespace changes: `javax.*` → `jakarta.*`

### 6. Messaging (JMS)

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|---------------------|--------------------------------|---------|-----------------|
| `org.jboss.spec.javax.jms:jboss-jms-api_2.0_spec` | `org.springframework.boot:spring-boot-starter-activemq` | 3.5.7 | Embedded ActiveMQ |

**Breaking Changes:**
- `@MessageDriven` → `@JmsListener`
- `ejb-jar.xml` MDB config → Java configuration
- Manual message acknowledgment → Spring abstractions

### 7. Validation

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|---------------------|--------------------------------|---------|-----------------|
| `org.hibernate:hibernate-validator` | `org.springframework.boot:spring-boot-starter-validation` | 3.5.7 | Includes Hibernate Validator 8.x |

**Breaking Changes:**
- `javax.validation.*` → `jakarta.validation.*`

### 8. API Documentation

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|---------------------|--------------------------------|---------|-----------------|
| `com.wordnik:swagger-jaxrs_2.10` | `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.7.0 | OpenAPI 3 support |

**Breaking Changes:**
- Swagger 1.x annotations → OpenAPI 3 annotations
- `@Api` → `@Tag`
- `@ApiOperation` → `@Operation`

### 9. Web UI (JSF Replacement)

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|---------------------|--------------------------------|---------|-----------------|
| `org.jboss.spec.javax.faces:jboss-jsf-api_2.1_spec` | `org.springframework.boot:spring-boot-starter-thymeleaf` | 3.5.7 | Modern template engine |

**Breaking Changes:**
- JSF pages → Thymeleaf templates
- Managed beans → Spring MVC controllers
- Complete UI rewrite required

### 10. Reporting

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|---------------------|--------------------------------|---------|-----------------|
| `jasperreports:jasperreports:3.5.3` | `net.sf.jasperreports:jasperreports` | 7.0.1 | Major version update |

**Breaking Changes:**
- API changes in JasperReports 7.x
- Dependency updates required

### 11. Testing

| Original Dependency | Spring Boot 3.5.7 Replacement | Version | Migration Notes |
|---------------------|--------------------------------|---------|-----------------|
| `junit:junit:4.13.1` | `org.springframework.boot:spring-boot-starter-test` | 3.5.7 | Includes JUnit 5 + Mockito |
| `org.jboss.arquillian.*` | `org.springframework.boot:spring-boot-starter-test` | 3.5.7 | Spring Boot Test slices |

**Breaking Changes:**
- JUnit 4 → JUnit 5
- Arquillian → Spring Boot Test
- `@Test` annotations differ

## Complete Spring Boot 3.5.7 POM

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
            <artifactId>spring-boot-starter-activemq</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web-services</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
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

        <!-- Reporting -->
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

## Dependencies to Remove

| Dependency | Reason |
|------------|--------|
| `org.wildfly.bom:wildfly-javaee7` | Replaced by Spring Boot parent |
| `javax.enterprise:cdi-api` | Spring Context provides DI |
| `org.jboss.spec.javax.ejb:jboss-ejb-api_3.1_spec` | No EJBs in Spring Boot |
| `org.jboss.spec.javax.jms:jboss-jms-api_2.0_spec` | Spring JMS abstractions |
| `org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_1.1_spec` | Spring Web MVC |
| `org.jboss.spec.javax.faces:jboss-jsf-api_2.1_spec` | Thymeleaf replacement |
| `commons-dbcp:commons-dbcp` | HikariCP is default |
| `commons-pool:commons-pool` | Not needed |
| `org.jboss.arquillian.*` | Spring Boot Test |

## Migration Effort Estimation

| Component | Effort Level | Estimated Time |
|-----------|--------------|----------------|
| **EJB → Spring Services** | Medium | 2-3 weeks |
| **JAX-RS → Spring MVC** | Low | 1 week |
| **JPA Migration** | Low | 3-5 days |
| **JMS/MDB → Spring JMS** | Medium | 1-2 weeks |
| **SOAP Services** | High | 3-4 weeks |
| **JSF → Thymeleaf** | High | 4-6 weeks |
| **Testing Migration** | Medium | 2 weeks |
| **Configuration** | Low | 2-3 days |

**Total Estimated Effort: 14-20 weeks**

## Key Migration Considerations

### 1. Namespace Changes
- All `javax.*` packages → `jakarta.*` in Spring Boot 3.x
- Update imports across the codebase

### 2. Configuration Migration
- `persistence.xml` → `application.yml`
- `ejb-jar.xml` → Java configuration
- `web.xml` → Spring Boot auto-configuration

### 3. Transaction Management
- Container-managed transactions → `@Transactional`
- Manual transaction demarcation → Spring's declarative approach

### 4. Security
- Java EE security → Spring Security
- Role-based access control migration required

### 5. Deployment
- WAR deployment → Executable JAR
- Application server → Embedded container

## Recommended Migration Strategy

1. **Phase 1**: Core Services (EJB → Spring Services)
2. **Phase 2**: REST APIs (JAX-RS → Spring MVC)
3. **Phase 3**: Persistence Layer (JPA configuration)
4. **Phase 4**: Messaging (MDB → Spring JMS)
5. **Phase 5**: Web Services (SOAP migration)
6. **Phase 6**: UI Layer (JSF → Thymeleaf)
7. **Phase 7**: Testing and Integration

## Compatibility Matrix

| Technology | EJB 2.0 Version | Spring Boot 3.5.7 Version | Java 21 Compatible |
|------------|-----------------|----------------------------|-------------------|
| JPA | 2.0 | 3.1 | ✅ |
| Bean Validation | 1.1 | 3.0 | ✅ |
| JMS | 2.0 | 3.1 | ✅ |
| Servlet API | 3.1 | 6.0 | ✅ |
| JAX-RS | 1.1 | N/A (Spring MVC) | ✅ |
| JAX-WS | 2.0 | 4.0 (Jakarta) | ✅ |

---

**Generated**: $(date)  
**Target**: Spring Boot 3.5.7 + Java 21  
**Source**: EJB 2.0 Demo Project Analysis
