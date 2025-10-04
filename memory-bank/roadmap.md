# 🧭 Java Architecture Analyzer — Roadmap

> Goal: build a scalable, repeatable, open-source toolkit for analyzing and
> transforming large Java codebases (4 M LOC+) from legacy EJB 2 / J2EE
> architectures toward modern Spring Boot 3 / Jakarta EE.

---

## Phase 0 — Foundation (✅ In Progress)
**Objectives**
- Create CLI (`jaa`) with modular commands (`analyze`, `apply`, `report`)
- Parse Java + XML descriptors (ejb-jar.xml, vendor configs)
- Produce a migration **manifest.json** (transaction + security mapping)
- Integrate OpenRewrite + jQAssistant
- Provide YAML-based recipes and Semgrep detectors
- Establish CI (GitHub Actions)

**Deliverables**
- `App.java`, CLI commands  
- Initial OpenRewrite recipes (EJB → Spring stereotypes, JNDI → DI)  
- Detectors (`semgrep/ejb2-detectors.yml`)  
- `jqassistant/rules.adoc`  
- CI workflow `.github/workflows/migrate.yml`

---

## Phase 1 — Migration Engine 🧩
**Objectives**
- Extend **manifest parser** to extract all transactions, roles, and MDB activation configs
- Implement Java Visitors:
  - `ReplaceJndiVisitor`
  - `ApplyTransactionalFromManifest`
  - `ApplyRolesFromManifest`
  - `TimerToScheduledVisitor`
  - `MdbToJmsListenerVisitor`
- Add incremental module processing and parallel execution
- Add regression unit-tests for each recipe
- Publish a runnable “mini-sample” migration (EJB2 module → Spring Boot)

**Milestone:** 0.5 – “Deterministic Migrator”

---

## Phase 2 — GenAI Assisted Recipes 🤖
**Objectives**
- Add a **GenAI adapter** layer (Amazon Bedrock / Claude Sonnet)
- Automatically generate draft recipes from before/after examples
- Use LLMs to:
  - Generate OpenRewrite YAML or Java Visitors
  - Summarize compilation errors after recipe runs
  - Propose mapping tables from `ejb-jar.xml` to Spring annotations
- Keep human-review and version-control loop (“safe AI” principle)

**Milestone:** 0.7 – “AI-Assisted Codemods”

---

## Phase 3 — Refactor as Library / Plugin 📦
**Objectives**
- Expose core as a reusable **Java library** and **Maven/Gradle plugin**
- Support JSON/YAML API for external orchestration
- Add configuration file `.jaa.yml` for recipe activation
- Add REST endpoint (`/analyze`, `/apply`) for automation

**Milestone:** 0.9 – “Extensible Toolkit”

---

## Phase 4 — Dashboard & Reports 📊
**Objectives**
- Generate progress dashboards (HTML + JSON)
- Display per-module metrics: LOC, classes migrated, rules applied, failures
- Integrate with GitHub Pages or an internal portal
- Export Neo4j queries for dependency visualization

**Milestone:** 1.0 – “Insight & Governance Release”

---

## Phase 5 — Post-Migration Modernization 🧱
**Objectives**
- Add recipes for further modernization:
  - `Spring Data JPA` → `Repository` patterns
  - CDI → Spring DI refactors
  - Jakarta → Quarkus/Micronaut adapters
- Enable performance profiling (JMH / Micrometer) integration
- Support containerized testing with Testcontainers

---

## Governance & Maintenance
- Semantic versioning (`vX.Y.Z`)
- `main` → stable; `feat/*` → experimental
- Issue labels:
  - `analyzer`
  - `recipe`
  - `visitor`
  - `genai`
  - `reporting`

---

## Vision Statement
> Create a deterministic + AI-augmented modernization pipeline  
> that can handle multi-million-line Java applications with safety,  
> reproducibility, and measurable quality gains.
