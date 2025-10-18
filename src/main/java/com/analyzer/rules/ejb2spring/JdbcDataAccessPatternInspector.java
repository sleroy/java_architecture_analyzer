package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.binary.AbstractASMInspector;
import com.analyzer.inspectors.core.detection.BinaryClassDetector;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.tree.*;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inspector P2-01: JDBC Data Access Pattern Inspector
 * <p>
 * Detects and analyzes direct JDBC usage patterns in Java applications.
 * This inspector is critical for EJB to Spring Boot migration analysis,
 * identifying
 * JDBC Connection, PreparedStatement, ResultSet patterns and data access
 * layers.
 * <p>
 * Phase 2 - JDBC-focused Migration Inspector (Priority 1)
 */
@InspectorDependencies(need = {
        BinaryClassDetector.class
}, produces = {
        EjbMigrationTags.DATA_ACCESS_LAYER,
        EjbMigrationTags.MIGRATION_COMPLEXITY_LOW,
        EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM,
        EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH
})
public class JdbcDataAccessPatternInspector extends AbstractASMInspector {

    private static final Set<String> JDBC_CONNECTION_TYPES = Set.of(
            "java/sql/Connection", "javax/sql/DataSource");

    private static final Set<String> JDBC_STATEMENT_TYPES = Set.of(
            "java/sql/Statement", "java/sql/PreparedStatement", "java/sql/CallableStatement");

    private static final Set<String> JDBC_RESULT_TYPES = Set.of(
            "java/sql/ResultSet", "java/sql/ResultSetMetaData");

    private static final Set<String> DATA_ACCESS_PATTERNS = Set.of(
            "DAO", "Repository", "DataAccess", "Persistence", "Mapper");
    private final ClassNodeRepository classNodeRepository;
    private final Map<String, JdbcDataAccessMetadata> jdbcAccessCache;

    @Inject
    public JdbcDataAccessPatternInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver);
        this.classNodeRepository = classNodeRepository;
        this.jdbcAccessCache = new ConcurrentHashMap<>();
    }

    // Removed supports() method - trust @InspectorDependencies completely

    @Override
    protected ASMClassVisitor createClassVisitor(ProjectFile projectFile, ProjectFileDecorator projectFileDecorator) {
        JavaClassNode classNode = classNodeRepository.getOrCreateClassNodeByFqn(projectFile.getFullyQualifiedName())
                .orElseThrow();
        classNode.setProjectFileId(projectFile.getId());
        return new JdbcDataAccessVisitor(projectFile, projectFileDecorator, classNode);
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

    @Override
    public String getName() {
        return "JDBC Data Access Pattern Inspector (P2-01)";
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
     * ASM visitor that analyzes JDBC data access patterns in Java classes
     */
    private class JdbcDataAccessVisitor extends ASMClassVisitor {
        private final ClassNode classNode;
        private final JavaClassNode graphNode;
        private JdbcDataAccessMetadata metadata;

        public JdbcDataAccessVisitor(ProjectFile projectFile, ProjectFileDecorator projectFileDecorator,
                JavaClassNode graphNode) {
            super(projectFile, projectFileDecorator);
            this.classNode = new ClassNode();
            this.graphNode = graphNode;

            // Basic tags are now set in supports() method to ensure they're always present
            // Still set them here as backup for detailed analysis
            setBasicTags();
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            classNode.visit(version, access, name, signature, superName, interfaces);

            // Initialize metadata for any supported file
            metadata = new JdbcDataAccessMetadata(name, extractClassName(name));

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitEnd() {
            // Basic tags are already set in supports() method
            // Set them again here as backup
            setBasicTags();

            // Do detailed JDBC analysis for all classes
            if (metadata != null) {
                analyzeJdbcUsage();
                setDetailedAnalysisResults();
            }
            super.visitEnd();
        }

        private void setBasicTags() {
            // Tags go on ProjectFile via ProjectFileDecorator (dependency chains)
            projectFileDecorator.setTag(EjbMigrationTags.DATA_ACCESS_LAYER, true);
            // Complexity set in detailed analysis with proper enum value

            // Properties go on ClassNode (analysis data)
            List<String> recommendations = List.of("Consider migrating to Spring JdbcTemplate or Spring Data JPA");
            graphNode.setProperty("jdbc_data_access.spring_recommendations", String.join("; ", recommendations));
        }

        private void setDetailedAnalysisResults() {
            // Honor produces contract - set tags on ProjectFile (dependency chain)
            setProducedTags();

            // Store detailed analysis data as consolidated POJO on ClassNode (avoid toJson
            // anti-pattern)
            JdbcDataAccessAnalysisResult analysisResult = createAnalysisResult();
            graphNode.setProperty("jdbc_data_access_analysis", analysisResult);

            // Cache metadata for graph creation
            jdbcAccessCache.put(classNode.name, metadata);
        }

        /**
         * Honor produces contract - set key identification tags on ProjectFile,
         * detailed analysis on ClassNode
         * Following guideline #9: Tags for quick identification, Properties for
         * detailed data structures
         */
        private void setProducedTags() {
            ProjectFileDecorator decorator = this.projectFileDecorator;

            // Key identification tags on ProjectFile (for dependency chains and quick
            // identification)
            decorator.setTag(EjbMigrationTags.DATA_ACCESS_LAYER, true);

            // Set appropriate complexity tag from constants (guideline #11: honor produces contract)
            String complexity = assessMigrationComplexity(metadata);
            switch (complexity) {
                case "LOW":
                    decorator.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_LOW, true);
                    break;
                case "MEDIUM":
                    decorator.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
                    break;
                case "HIGH":
                    decorator.setTag(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH, true);
                    break;
            }

            // Detailed analysis data as properties on ClassNode (following guideline #9)
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

            graphNode.setProperty("jdbc_features", features);
        }

        /**
         * Create consolidated analysis result POJO to avoid multiple properties
         * anti-pattern
         */
        private JdbcDataAccessAnalysisResult createAnalysisResult() {
            JdbcDataAccessAnalysisResult result = new JdbcDataAccessAnalysisResult();
            result.className = metadata.className;
            result.simpleName = metadata.simpleName;
            result.connectionUsageCount = metadata.getConnectionUsageCount();
            result.preparedStatementCount = metadata.getPreparedStatementCount();
            result.statementUsageCount = metadata.getStatementUsageCount();
            result.resultSetProcessingCount = metadata.getResultSetProcessingCount();
            result.sqlQueryCount = metadata.getSqlQueries().size();
            result.hasConnectionUsage = metadata.hasConnectionUsage();
            result.hasPreparedStatementUsage = metadata.hasPreparedStatementUsage();
            result.hasResultSetProcessing = metadata.hasResultSetProcessing();
            result.hasManualMapping = metadata.hasManualMapping();
            result.isDataAccessObject = metadata.isDataAccessObject();
            result.dataAccessPattern = metadata.dataAccessPattern;
            result.migrationComplexity = assessMigrationComplexity(metadata);
            result.springRecommendations = generateSpringRecommendations(metadata);
            result.sqlQueries = new ArrayList<>(metadata.getSqlQueries());
            return result;
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
                    if (instruction instanceof MethodInsnNode methodCall) {
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
    }

    /**
     * Consolidated analysis result POJO to avoid multiple properties anti-pattern
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
     * JDBC features data structure for ClassNode properties (guideline #9)
     * Stores detailed analysis flags as structured data rather than individual tags
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
