# Analyses Catalog

Columns: **Name**, **Purpose**, **Technology**, **Implementation details**

| Analysis | Purpose | Technology | Implementation details |
|---|---|---|---|
| A-00: Module Graph | Determine module boundaries and dependencies | Parse POM/Gradle; jQAssistant/Neo4j | Build `module -> module` graph, used to parallelize migration and resolve cross-module references |
| A-01: Type Index | Global map of FQN → file/class | ASM (bytecode) + JavaParser (fallback) | Record package, supertypes, interfaces, source path, jar owner |
| A-02: Inheritance Tree | Understand class/interface hierarchy | ASM (superName, interfaces) | Create edges `extends`/`implements`; compute subclasses (reverse index) |
| A-03: Field & Method Facts | Collect field modifiers, writes/reads, method flags | ASM visitors | Track `PUTFIELD/GETFIELD`, `ACC_SYNCHRONIZED`, `MONITORENTER/EXIT`, ThreadLocal types |
| A-04: Mutability Fixpoint | Infer `inferredMutable` via is-a / has-a | Worklist on TypeTable | Iterate until no change: declaredMutable OR mutable supertype OR mutable field type |
| A-05: Cross-Method Def–Use | Identify conversational state within a class | JavaParser (AST) + bytecode facts | For each field, track writer methods and reader methods; if writer≠reader → conversational |
| A-06: JNDI Resolution | Map `lookup("jndi/name")` → bean/resource | Descriptor parsing + constant propagation | Link to `ejb-ref`, vendor bindings, datasource/jms destinations |
| A-07: Descriptor Linking | Map `ejb-jar.xml` beans → types and methods | JAXB/Jackson XML | Build manifest: tx attributes, security roles, MDB activation config |
| A-08: Finder Query Map | Map `find*` → EJB-QL | XML + interface scan | Create `finder → query` mapping for repository generation |
| A-09: Call Graph (lightweight) | Know callers/callees for selected classes | OpenRewrite search or JavaParser symbol solver | Per module, track methods referencing targets; avoid whole-program CFA |
| A-10: Synchronized & Threading Map | Identify threading assumptions | ASM | Catalog synchronized methods/blocks; ThreadLocal usage |
| A-11: Resource Map | Datasources, JMS, env-entries | XML parsing | Emit `application.yml` seeds |
| A-12: javax→jakarta Readiness | Ensure Boot 3 compatibility | OpenRewrite/Eclipse Transformer dry-run | Count remaining `javax.*` and blockers |
| A-13: Web Session State Map | Session attributes → consumers | web.xml + source scan | Locate `HttpSession` usage and keys |
| A-14: BMP JDBC Map | Inline JDBC in entity beans | ASM + JavaParser | Find DAO-like code inside entities for extraction |

> All analyses output JSON into a shared `/build/jaa/` manifest for rules to consume.