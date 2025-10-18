# EJB to Spring Boot Migration Strategy

## Target Architecture
- Spring Boot 3.x + Spring Data JPA + Spring Transaction
- Jakarta namespace (javax → jakarta)
- Spring Security with method-level annotations

## Key Mappings
- Session Beans → @Service + @Transactional
- Entity Beans → JPA @Entity + Spring Data repositories
- JNDI lookups → @Autowired dependency injection
- MDB → @JmsListener
- EJB Timers → @Scheduled

## Migration Approach
- Deterministic codemods for 80-90% of changes
- OpenRewrite recipes for AST transformations
- LLM assistance for rule generation and mapping tables
- Modular monolith first, then selective service extraction

## Toolchain
- OpenRewrite for Java + XML transformations
- jQAssistant + Neo4j for dependency analysis
- Semgrep for detecting remaining EJB patterns
- Spring Boot Test + Testcontainers for validation