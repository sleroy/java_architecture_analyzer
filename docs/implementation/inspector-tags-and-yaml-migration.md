# Inspector Tags and YAML Migration Strategy

**Date:** 2025-11-02
**Purpose:** Document the correct architecture for EJB2→Spring migration using Inspector tags

## ✅ Key Architectural Decision

**NO GRAPH_SEARCH** - Rely purely on:
```
Inspectors → Tags → GRAPH_QUERY
```

## 📋 Available Tags from Inspectors

### From JdbcDataAccessPatternInspector

**Primary Tag:**
- `DATA_ACCESS_LAYER` = `"dataAccess.layer"`

**Node Properties Set:**
- `jdbc_features` (JdbcAnalysisFeatures object)
  - `hasConnectionUsage`
  - `hasPreparedStatementUsage`
  - `hasResultSetProcessing`
  - `isJdbcTemplateCandidate`
- `jdbc_data_access_analysis` (JdbcDataAccessAnalysisResult object)
  - `migrationComplexity`
  - `springRecommendations` (List<String>)
  - `sqlQueries` (List<String>)

### From EjbBinaryClassInspector

**Session Bean Tags:**
- `EJB_SESSION_BEAN` = `"ejb.sessionBean"`
- `EJB_STATELESS_SESSION_BEAN` = `"ejb.stateless.sessionBean"`
- `EJB_STATEFUL_SESSION_BEAN` = `"ejb.stateful.sessionBean"`

**Entity Bean Tags:**
- `EJB_ENTITY_BEAN` = `"ejb.entityBean"`
- `EJB_CMP_ENTITY` = `"ejb.cmp.entityBean"`
- `EJB_BMP_ENTITY` = `"ejb.bmp.entityBean"`

**Spring Conversion Tags:**
- `TAG_SPRING_SERVICE_CONVERSION` = `"spring.conversion.service"`
- `TAG_SPRING_COMPONENT_CONVERSION` = `"spring.conversion.component"`
- `JPA_ENTITY_CONVERSION` = `"jpa.conversion.entity"`

## 🔄 YAML Migration Pattern

### OLD: grep-based searching
```yaml
- type: "COMMAND"
  command: "grep -r 'getConnection()' src/"
  output-variable: "jdbc_wrapper_classes"
```

### NEW: Tag-based querying
```yaml
- type: "GRAPH_QUERY"
  query-type: "BY_TAGS"
  tags:
    - "dataAccess.layer"
  output-variable: "jdbc_wrapper_nodes"
```

## 📊 Properties Available in FreeMarker Templates

When using JavaClassNode objects from GRAPH_QUERY results:

```ftl
<#-- Basic node properties -->
${current_item.fullyQualifiedName}
${current_item.packageName}
${current_item.simpleName}
${current_item.sourceFilePath}

<#-- JDBC analysis properties -->
${current_item.properties.jdbc_features.hasConnectionUsage}
${current_item.properties.jdbc_features.hasPreparedStatementUsage}
${current_item.properties.jdbc_features.isJdbcTemplateCandidate}

<#-- Migration recommendations -->
${current_item.properties.jdbc_data_access_analysis.migrationComplexity}

<#list current_item.properties.jdbc_data_access_analysis.springRecommendations as rec>
- ${rec}
</#list>

<#-- SQL queries found -->
<#list current_item.properties.jdbc_data_access_analysis.sqlQueries as sql>
```sql
${sql}
```
</#list>
```

## 🎯 Migration Workflow

### Phase 0: One-Time Analysis
```bash
java -jar analyzer.jar analyze /path/to/project
```

**What Happens:**
1. AnalysisEngine runs ALL inspectors
2. JdbcDataAccessPatternInspector detects JDBC usage, adds `dataAccess.layer` tag
3. EjbBinaryClassInspector detects EJB components, adds appropriate tags
4. All tags and properties persisted to H2 database

### Phase 1+: Migration Execution
```yaml
# Query pre-tagged nodes - NO searching!
- type: "GRAPH_QUERY"
  query-type: "BY_TAGS"
  tags: ["dataAccess.layer"]
  output-variable: "jdbc_nodes"

# Use tagged nodes for generation
- type: "AI_PROMPT_BATCH"
  items-variable: "jdbc_nodes"
  prompt-template: |
    Convert to Spring DAO:
    Class: ${current_item.fullyQualifiedName}
    ...
```

## ✅ Benefits

1. **Performance**: Tags pre-computed during analysis
2. **Consistency**: All detection logic in inspectors
3. **Maintainability**: One place to update patterns
4. **Separation**: Analysis vs Migration phases
5. **No Duplication**: Detection logic not in YAML

## 🚧 Known Issues Fixed

### Issue 1: Wrong Tag Names in YAML
**Problem:** YAML used `"database.access"` and `"jdbc.wrapper"` which don't exist
**Solution:** Use actual tag `"dataAccess.layer"` from `EjbMigrationTags.DATA_ACCESS_LAYER`

### Issue 2: Wrong Property Name
**Problem:** YAML used `batch-variable` instead of `items-variable`
**Solution:** Fixed to `items-variable` in AI_PROMPT_BATCH blocks

### Issue 3: String vs JavaClassNode confusion
**Problem:** grep returns strings, but templates expect JavaClassNode objects
**Solution:** Use GRAPH_QUERY which returns JavaClassNode objects with all properties

## 📝 Next Steps

1. ✅ Fix `batch-variable` → `items-variable` (DONE)
2. ✅ Read EjbMigrationTags.java for tag names (DONE)
3. Update phase2-jdbc-migration.yaml to use correct tags
4. Remove all grep/COMMAND file searches
5. Implement ANALYZE_FILE block for generated files
6. Test end-to-end workflow

## 📚 References

- `analyzer-ejb2spring/src/main/java/com/analyzer/rules/ejb2spring/EjbMigrationTags.java`
- `analyzer-ejb2spring/src/main/java/com/analyzer/rules/ejb2spring/JdbcDataAccessPatternInspector.java`
- `analyzer-ejb2spring/src/main/java/com/analyzer/rules/ejb2spring/EjbBinaryClassInspector.java`
