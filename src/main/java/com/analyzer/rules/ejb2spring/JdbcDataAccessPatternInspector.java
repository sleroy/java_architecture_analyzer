package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.graph.ProjectFileRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.inspectors.core.binary.AbstractASMClassInspector;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Class-centric JDBC Data Access Pattern Inspector - Phase 3 migration.
 * 
 * <p>
 * Detects and analyzes direct JDBC usage patterns in Java applications.
 * This inspector is critical for EJB to Spring Boot migration analysis,
 * identifying JDBC Connection, PreparedStatement, ResultSet patterns and data
 * access layers.
 * </p>
 * 
 * <p>
 * <strong>Key Differences from JdbcDataAccessPatternInspector:</strong>
 * </p>
 * <ul>
 * <li>Extends AbstractASMClassInspector (class-centric) instead of
 * AbstractASMInspector (file-centric)</li>
 * <li>Receives JavaClassNode directly instead of creating it</li>
 * <li>Writes all analysis results to JavaClassNode properties</li>
 * <li>Uses NodeDecorator for type-safe property access</li>
 * <li>Simplified constructor with standard injection pattern</li>
 * </ul>
 * 
 * @since Phase 3 - Systematic Inspector Migration
 * @see JdbcDataAccessPatternInspector Original file-centric version
 */
@InspectorDependencies(requires = {  }, produces = {
        EjbMigrationTags.DATA_ACCESS_LAYER,
        EjbMigrationTags.MIGRATION_COMPLEXITY_LOW,
        EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM,
        EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH
})
public class JdbcDataAccessPatternInspector extends AbstractASMClassInspector {

    private static final Logger logger = LoggerFactory.getLogger(JdbcDataAccessPatternInspector.class);

    private static final Set<String> JDBC_CONNECTION_TYPES = Set.of(
            "java/sql/Connection", "javax/sql/DataSource");

    private static final Set<String> JDBC_STATEMENT_TYPES = Set.of(
            "java/sql/Statement", "java/sql/PreparedStatement", "java/sql/CallableStatement");

    private static final Set<String> JDBC_RESULT_TYPES = Set.of(
            "java/sql/ResultSet", "java/sql/ResultSetMetaData");

    private static final Set<String> DATA_ACCESS_PATTERNS = Set.of(
            "DAO", "Repository", "DataAccess", "Persistence", "Mapper");

    @Inject
    public JdbcDataAccessPatternInspector(ProjectFileRepository projectFileRepository,
            ResourceResolver resourceResolver) {
        super(projectFileRepository, resourceResolver);
    }

    @Override
    public String getName() {
        return "JDBC Data Access Pattern Inspector V2 (Class-Centric ASM)";
    }

    @Override
    protected ASMClassNodeVisitor createClassVisitor(JavaClassNode classNode,
            NodeDecorator<JavaClassNode> decorator) {
        return new JdbcDataAccessVisitor(classNode, decorator);
    }

    /**
     * ASM visitor that analyzes JDBC data access patterns using class-centric
     * architecture.
     * Analyzes JDBC Connection, Statement, ResultSet usage and provides Spring
     * migration recommendations.
     */
    private static class JdbcDataAccessVisitor extends ASMClassNodeVisitor {

        private final ClassNode classNode;
        private JdbcDataAccessMetadata metadata;

        protected JdbcDataAccessVisitor(JavaClassNode graphNode, NodeDecorator<JavaClassNode> decorator) {
            super(graphNode, decorator);
            this.classNode = new ClassNode();
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            classNode.visit(version, access, name, signature, superName, interfaces);

            // Initialize metadata
            metadata = new JdbcDataAccessMetadata(name, extractClassName(name));

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitEnd() {
            // Analyze JDBC usage patterns
            analyzeJdbcUsage();

            // Set analysis results
            if (hasJdbcUsage()) {
                setJdbcAnalysisResults();
            }

            super.visitEnd();
        }

        private boolean hasJdbcUsage() {
            return metadata.hasConnectionUsage() ||
                    metadata.hasPreparedStatementUsage() ||
                    metadata.hasResultSetProcessing() ||
                    !metadata.getSqlQueries().isEmpty();
        }

        private void analyzeJdbcUsage() {
            if (classNode.methods != null) {
                for (MethodNode method : classNode.methods) {
                    analyzeMethodForJdbcPatterns(method);
                }
            }

            // Check class name for data access patterns
            analyzeClassNamePattern(classNode.name);
        }

        private void analyzeMethodForJdbcPatterns(MethodNode method) {
            if (method.instructions != null) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode methodCall = (MethodInsnNode) instruction;
                        analyzeJdbcMethodCall(methodCall, method);
                    } else if (instruction instanceof TypeInsnNode) {
                        TypeInsnNode typeInstruction = (TypeInsnNode) instruction;
                        analyzeJdbcTypeUsage(typeInstruction, method);
                    } else if (instruction instanceof LdcInsnNode) {
                        LdcInsnNode ldcInstruction = (LdcInsnNode) instruction;
                        analyzeSqlStringLiteral(ldcInstruction, method);
                    }
                }
            }
        }

        private void analyzeJdbcMethodCall(MethodInsnNode methodCall, MethodNode containingMethod) {
            String owner = methodCall.owner;
            String methodName = methodCall.name;

            // Check for JDBC Connection methods
            if (JDBC_CONNECTION_TYPES.contains(owner)) {
                metadata.addConnectionUsage(methodName, containingMethod.name);

                if ("prepareStatement".equals(methodName)) {
                    metadata.addPreparedStatementUsage(methodName, containingMethod.name);
                } else if ("createStatement".equals(methodName)) {
                    metadata.addStatementUsage(methodName, containingMethod.name);
                }
            }

            // Check for JDBC Statement methods
            if (JDBC_STATEMENT_TYPES.contains(owner)) {
                if (owner.contains("PreparedStatement")) {
                    metadata.addPreparedStatementUsage(methodName, containingMethod.name);
                } else {
                    metadata.addStatementUsage(methodName, containingMethod.name);
                }

                if ("executeQuery".equals(methodName) || "executeUpdate".equals(methodName)) {
                    metadata.addSqlExecution(methodName, containingMethod.name);
                }
            }

            // Check for ResultSet processing
            if (JDBC_RESULT_TYPES.contains(owner)) {
                metadata.addResultSetProcessing(methodName, containingMethod.name);

                if (methodName.startsWith("get")) {
                    metadata.addManualMapping(methodName, containingMethod.name);
                }
            }
        }

        private void analyzeJdbcTypeUsage(TypeInsnNode typeInstruction, MethodNode containingMethod) {
            String type = typeInstruction.desc;

            // Check for JDBC type instantiation or casting
            if (JDBC_CONNECTION_TYPES.contains(type) ||
                    JDBC_STATEMENT_TYPES.contains(type) ||
                    JDBC_RESULT_TYPES.contains(type)) {

                metadata.addJdbcTypeUsage(type, containingMethod.name);
            }
        }

        private void analyzeSqlStringLiteral(LdcInsnNode ldcInstruction, MethodNode containingMethod) {
            if (ldcInstruction.cst instanceof String) {
                String stringValue = (String) ldcInstruction.cst;

                // Check for SQL keywords in string literals
                if (isSqlQuery(stringValue)) {
                    metadata.addSqlQuery(stringValue.trim(), containingMethod.name);
                }
            }
        }

        private void analyzeClassNamePattern(String className) {
            String simpleName = extractClassName(className);

            // Check for data access patterns in class name
            for (String pattern : DATA_ACCESS_PATTERNS) {
                if (simpleName.toLowerCase().contains(pattern.toLowerCase())) {
                    metadata.setDataAccessPattern(pattern);
                    break;
                }
            }
        }

        private void setJdbcAnalysisResults() {
            // Write all results to JavaClassNode properties (class-centric)
            enableTag(EjbMigrationTags.DATA_ACCESS_LAYER);

            // Set appropriate complexity tag
            String complexity = assessMigrationComplexity(metadata);
            switch (complexity) {
                case "LOW":
                    enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_LOW);
                    break;
                case "MEDIUM":
                    enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM);
                    break;
                case "HIGH":
                    enableTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH);
                    break;
            }

            // Store JDBC features as structured data
            JdbcAnalysisFeatures features = new JdbcAnalysisFeatures();
            features.hasConnectionUsage = metadata.hasConnectionUsage();
            features.hasPreparedStatementUsage = metadata.hasPreparedStatementUsage();
            features.hasResultSetProcessing = metadata.hasResultSetProcessing();
            features.hasStatementUsage = metadata.hasStatementUsage();
            features.hasSqlQueryPattern = !metadata.getSqlQueries().isEmpty();
            features.isDataAccessObject = metadata.isDataAccessObject();
            features.hasManualMapping = metadata.hasManualMapping();
            features.isJdbcTemplateCandidate = metadata.isJdbcTemplateCandidate();
            features.isRepositoryPatternCandidate = metadata.isRepositoryPatternCandidate();

            setProperty("jdbc_features", features);

            // Create consolidated analysis result
            JdbcDataAccessAnalysisResult analysisResult = new JdbcDataAccessAnalysisResult();
            analysisResult.className = metadata.className;
            analysisResult.simpleName = metadata.simpleName;
            analysisResult.connectionUsageCount = metadata.getConnectionUsageCount();
            analysisResult.preparedStatementCount = metadata.getPreparedStatementCount();
            analysisResult.statementUsageCount = metadata.getStatementUsageCount();
            analysisResult.resultSetProcessingCount = metadata.getResultSetProcessingCount();
            analysisResult.sqlQueryCount = metadata.getSqlQueries().size();
            analysisResult.hasConnectionUsage = metadata.hasConnectionUsage();
            analysisResult.hasPreparedStatementUsage = metadata.hasPreparedStatementUsage();
            analysisResult.hasResultSetProcessing = metadata.hasResultSetProcessing();
            analysisResult.hasManualMapping = metadata.hasManualMapping();
            analysisResult.isDataAccessObject = metadata.isDataAccessObject();
            analysisResult.dataAccessPattern = metadata.dataAccessPattern;
            analysisResult.migrationComplexity = complexity;
            analysisResult.springRecommendations = generateSpringRecommendations(metadata);
            analysisResult.sqlQueries = new ArrayList<>(metadata.getSqlQueries());

            setProperty("jdbc_data_access_analysis", analysisResult);

            logger.debug("Detected JDBC usage in class: {} (complexity: {})", classNode.name, complexity);
        }

        private String extractClassName(String internalName) {
            int lastSlash = internalName.lastIndexOf('/');
            return lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
        }

        private boolean isSqlQuery(String stringValue) {
            if (stringValue == null || stringValue.trim().isEmpty()) {
                return false;
            }

            String upperValue = stringValue.trim().toUpperCase();
            return upperValue.startsWith("SELECT ") ||
                    upperValue.startsWith("INSERT ") ||
                    upperValue.startsWith("UPDATE ") ||
                    upperValue.startsWith("DELETE ") ||
                    upperValue.startsWith("CREATE ") ||
                    upperValue.startsWith("ALTER ") ||
                    upperValue.startsWith("DROP ");
        }

        private String assessMigrationComplexity(JdbcDataAccessMetadata metadata) {
            int complexityScore = 0;

            // Base complexity for JDBC usage
            if (metadata.hasConnectionUsage() || metadata.hasPreparedStatementUsage() ||
                    metadata.hasResultSetProcessing()) {
                complexityScore += 2;
            }

            // Add complexity for each JDBC pattern
            complexityScore += metadata.getConnectionUsageCount();
            complexityScore += metadata.getPreparedStatementCount();
            complexityScore += metadata.getResultSetProcessingCount();

            // Add complexity for manual mapping
            if (metadata.hasManualMapping()) {
                complexityScore += 3;
            }

            // Add complexity for SQL queries
            complexityScore += metadata.getSqlQueries().size();

            // Reduce complexity if it's already a data access pattern
            if (metadata.isDataAccessObject()) {
                complexityScore = Math.max(1, complexityScore - 2);
            }

            // Classify complexity
            if (complexityScore <= 3) {
                return "LOW";
            } else if (complexityScore <= 8) {
                return "MEDIUM";
            } else {
                return "HIGH";
            }
        }

        private List<String> generateSpringRecommendations(JdbcDataAccessMetadata metadata) {
            List<String> recommendations = new ArrayList<>();

            if (metadata.hasConnectionUsage()) {
                recommendations.add("Replace manual Connection management with Spring DataSource configuration");
            }

            if (metadata.hasPreparedStatementUsage()) {
                recommendations.add("Convert PreparedStatement usage to Spring JdbcTemplate");
            }

            if (metadata.hasResultSetProcessing() && metadata.hasManualMapping()) {
                recommendations.add("Replace manual ResultSet mapping with Spring RowMapper");
            }

            if (metadata.isDataAccessObject()) {
                recommendations.add("Convert DAO pattern to Spring @Repository with JdbcTemplate");
            }

            if (!metadata.getSqlQueries().isEmpty()) {
                recommendations.add("Consider using Spring Data JPA for complex queries");
            }

            if (recommendations.isEmpty()) {
                recommendations.add("Consider migrating to Spring Boot with Spring Data");
            }

            return recommendations;
        }
    }

    // Metadata class for JDBC data access analysis
    public static class JdbcDataAccessMetadata {
        private final String className;
        private final String simpleName;
        private final List<String> connectionUsage = new ArrayList<>();
        private final List<String> preparedStatementUsage = new ArrayList<>();
        private final List<String> statementUsage = new ArrayList<>();
        private final List<String> resultSetProcessing = new ArrayList<>();
        private final List<String> manualMapping = new ArrayList<>();
        private final List<String> sqlQueries = new ArrayList<>();
        private final List<String> jdbcTypeUsage = new ArrayList<>();
        private String dataAccessPattern;

        public JdbcDataAccessMetadata(String className, String simpleName) {
            this.className = className;
            this.simpleName = simpleName;
        }

        public void addConnectionUsage(String method, String containingMethod) {
            connectionUsage.add(containingMethod + "." + method);
        }

        public void addPreparedStatementUsage(String method, String containingMethod) {
            preparedStatementUsage.add(containingMethod + "." + method);
        }

        public void addStatementUsage(String method, String containingMethod) {
            statementUsage.add(containingMethod + "." + method);
        }

        public void addResultSetProcessing(String method, String containingMethod) {
            resultSetProcessing.add(containingMethod + "." + method);
        }

        public void addManualMapping(String method, String containingMethod) {
            manualMapping.add(containingMethod + "." + method);
        }

        public void addSqlQuery(String query, String containingMethod) {
            sqlQueries.add(query);
        }

        public void addSqlExecution(String method, String containingMethod) {
            // Track SQL execution methods
        }

        public void addJdbcTypeUsage(String type, String containingMethod) {
            jdbcTypeUsage.add(type + " in " + containingMethod);
        }

        public void setDataAccessPattern(String pattern) {
            this.dataAccessPattern = pattern;
        }

        // Getters
        public boolean hasConnectionUsage() {
            return !connectionUsage.isEmpty();
        }

        public boolean hasPreparedStatementUsage() {
            return !preparedStatementUsage.isEmpty();
        }

        public boolean hasStatementUsage() {
            return !statementUsage.isEmpty();
        }

        public boolean hasResultSetProcessing() {
            return !resultSetProcessing.isEmpty();
        }

        public boolean hasManualMapping() {
            return !manualMapping.isEmpty();
        }

        public int getConnectionUsageCount() {
            return connectionUsage.size();
        }

        public int getPreparedStatementCount() {
            return preparedStatementUsage.size();
        }

        public int getStatementUsageCount() {
            return statementUsage.size();
        }

        public int getResultSetProcessingCount() {
            return resultSetProcessing.size();
        }

        public List<String> getSqlQueries() {
            return sqlQueries;
        }

        public boolean isDataAccessObject() {
            return dataAccessPattern != null;
        }

        public boolean isJdbcTemplateCandidate() {
            return hasPreparedStatementUsage() || hasStatementUsage();
        }

        public boolean isRepositoryPatternCandidate() {
            return isDataAccessObject();
        }
    }

    /**
     * Consolidated analysis result POJO
     */
    public static class JdbcDataAccessAnalysisResult {
        public String className;
        public String simpleName;
        public int connectionUsageCount;
        public int preparedStatementCount;
        public int statementUsageCount;
        public int resultSetProcessingCount;
        public int sqlQueryCount;
        public boolean hasConnectionUsage;
        public boolean hasPreparedStatementUsage;
        public boolean hasResultSetProcessing;
        public boolean hasManualMapping;
        public boolean isDataAccessObject;
        public String dataAccessPattern;
        public String migrationComplexity;
        public List<String> springRecommendations;
        public List<String> sqlQueries;
    }

    /**
     * JDBC features data structure for ClassNode properties
     */
    public static class JdbcAnalysisFeatures {
        public boolean hasConnectionUsage;
        public boolean hasPreparedStatementUsage;
        public boolean hasResultSetProcessing;
        public boolean hasStatementUsage;
        public boolean hasSqlQueryPattern;
        public boolean isDataAccessObject;
        public boolean hasManualMapping;
        public boolean isJdbcTemplateCandidate;
        public boolean isRepositoryPatternCandidate;
    }
}
