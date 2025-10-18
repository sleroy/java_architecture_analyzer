# Roadmap

## Vision
Build scalable toolkit for analyzing and transforming large Java codebases (4M+ LOC) from legacy EJB 2/J2EE to modern Spring Boot 3/Jakarta EE.

## Phases

### Phase 0 - Foundation ✅
- CLI with modular commands
- Parse Java + XML descriptors
- OpenRewrite + jQAssistant integration
- YAML recipes and Semgrep detectors

### Phase 1 - Migration Engine
- Manifest parser for transactions/roles/MDB configs
- Java Visitors: JNDI→DI, XML→annotations, Timers→Scheduled
- Parallel module processing
- Regression testing

### Phase 2 - GenAI Assistance
- Bedrock/Claude integration for recipe generation
- LLM-generated OpenRewrite rules from examples
- Human review and version control loop

### Phase 3 - Library/Plugin
- Maven/Gradle plugin
- JSON/YAML API
- REST endpoints for automation

### Phase 4 - Dashboard/Reports
- Progress dashboards (HTML + JSON)
- Per-module metrics
- Neo4j dependency visualization