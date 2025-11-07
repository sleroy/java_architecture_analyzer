# Migration Strategy: JBoss EJB 2 to Spring Boot

## Overview

This document outlines the strategy for migrating the Unicorn application from JBoss EJB 2 to Spring Boot 3.5.7.

## Migration Approach

We will use a strangler fig pattern approach:
1. Build new Spring Boot application in parallel
2. Migrate components incrementally
3. Maintain both versions during transition
4. Cut over when Spring Boot version reaches feature parity

## EJB to Spring Mapping

- @Stateless → @Service + @Transactional
- @Entity (CMP) → @Entity (JPA) + JpaRepository
- @MessageDriven → @JmsListener or Spring Integration
- @Inject → @Autowired or Constructor Injection
- @Produces → @Bean
- persistence.xml → application.properties
- @Path (JAX-RS) → @RequestMapping
- EntityManager → JpaRepository

## Success Criteria

Migration will be considered successful when:
- All EJB components migrated to Spring equivalents
- All REST endpoints functional in Spring Boot
- All tests passing
- Performance meets or exceeds baseline
- Zero data loss during cutover
- Application runs without JBoss dependencies
