package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.dev.inspectors.source.AbstractJavaParserInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.rules.std.ApplicationPackageTagInspector;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Inspector I-0213: Custom Data Transfer Pattern Inspector
 * 
 * Detects custom data transfer patterns in JDBC-based EJB applications
 * including:
 * - Data Transfer Objects (DTOs) used with JDBC ResultSet
 * - Value Objects (VOs) for data transfer
 * - Manual ResultSet to object mapping patterns
 * - Custom object mapping logic without ORM frameworks
 * - Hand-written field assignment patterns
 * 
 * Provides Spring Boot conversion recommendations for JdbcTemplate and
 * RowMapper patterns.
 * 
 * Phase 2 - JDBC-Focused EJB Migration Analysis
 */
@InspectorDependencies(need = {
        ApplicationPackageTagInspector.class
}, produces = {
        EjbMigrationTags.TAG_DATA_TRANSFER_OBJECT,
        EjbMigrationTags.TAG_VALUE_OBJECT,
        EjbMigrationTags.TAG_MAPPING_CUSTOM_LOGIC,
        EjbMigrationTags.TAG_MAPPING_RESULTSET_MANUAL,
        EjbMigrationTags.TAG_JDBC_DTO_USAGE
})
public class CustomDataTransferPatternJavaSourceInspector extends AbstractJavaParserInspector {

    private static final String INSPECTOR_ID = "I-0213";
    private static final String INSPECTOR_NAME = "Custom Data Transfer Pattern Inspector";

    private static final Set<String> DTO_INDICATORS = Set.of(
            "DTO", "DataTransferObject", "Data", "Bean", "Entity", "Record", "Info", "Details");

    private static final Set<String> VALUE_OBJECT_INDICATORS = Set.of(
            "VO", "ValueObject", "Value", "Immutable", "ReadOnly", "View");

    private static final Set<String> JDBC_RESULTSET_METHODS = Set.of(
            "getString", "getInt", "getLong", "getDouble", "getFloat", "getBoolean",
            "getDate", "getTime", "getTimestamp", "getBigDecimal", "getObject",
            "getByte", "getShort", "getBytes", "getClob", "getBlob");

    private static final Set<String> MAPPING_METHOD_PATTERNS = Set.of(
            "mapRow", "mapResultSet", "createFrom", "fromResultSet", "populate",
            "fill", "convert", "transform", "build", "extract");

    private final ClassNodeRepository classNodeRepository;

    @Inject
    public CustomDataTransferPatternJavaSourceInspector(ResourceResolver resourceResolver,
            ClassNodeRepository classNodeRepository, LocalCache localCache) {
        super(resourceResolver, localCache);
        this.classNodeRepository = classNodeRepository;
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        return super.supports(projectFile);
    }

    @Override
    protected void analyzeCompilationUnit(CompilationUnit cu, ProjectFile projectFile,
            NodeDecorator projectFileDecorator) {

        DataTransferPatternDetector detector = new DataTransferPatternDetector();
        cu.accept(detector, null);

        DataTransferMetadata metadata = detector.getMetadata();

        classNodeRepository.getOrCreateClassNode(cu).ifPresent(classNode -> {
            classNode.setProjectFileId(projectFile.getId());

            // Set pattern detection tags
            boolean isDataTransferObject = metadata.isDataTransferObject();
            boolean isValueObject = metadata.isValueObject();
            boolean hasCustomMappingLogic = metadata.hasCustomMappingLogic();
            boolean hasManualResultSetMapping = metadata.hasManualResultSetMapping();
            boolean usesJdbcWithDtos = metadata.usesJdbcWithDtos();

            // Set tags on ProjectFile for dependency chain
            projectFile.setProperty(EjbMigrationTags.TAG_DATA_TRANSFER_OBJECT, isDataTransferObject);
            projectFile.setProperty(EjbMigrationTags.TAG_VALUE_OBJECT, isValueObject);
            projectFile.setProperty(EjbMigrationTags.TAG_MAPPING_CUSTOM_LOGIC, hasCustomMappingLogic);
            projectFile.setProperty(EjbMigrationTags.TAG_MAPPING_RESULTSET_MANUAL, hasManualResultSetMapping);
            projectFile.setProperty(EjbMigrationTags.TAG_JDBC_DTO_USAGE, usesJdbcWithDtos);

            if (isDataTransferObject || isValueObject || hasCustomMappingLogic ||
                    hasManualResultSetMapping || usesJdbcWithDtos) {

                // Set migration metrics
                String complexityLevel = assessMigrationComplexity(metadata);
                double complexityValue = switch (complexityLevel) {
                    case "LOW" -> EjbMigrationTags.COMPLEXITY_LOW;
                    case "MEDIUM" -> EjbMigrationTags.COMPLEXITY_MEDIUM;
                    case "HIGH" -> EjbMigrationTags.COMPLEXITY_HIGH;
                    default -> EjbMigrationTags.COMPLEXITY_MEDIUM;
                };
                projectFileDecorator.getMetrics().setMaxMetric(EjbMigrationTags.TAG_METRIC_MIGRATION_COMPLEXITY,
                        complexityValue);
                projectFileDecorator.getMetrics().setMaxMetric(EjbMigrationTags.TAG_METRIC_MIGRATION_PRIORITY,
                        EjbMigrationTags.PRIORITY_MEDIUM);

                // Create consolidated analysis result
                DataTransferAnalysisResult analysisResult = new DataTransferAnalysisResult(
                        isDataTransferObject,
                        isValueObject,
                        hasCustomMappingLogic,
                        hasManualResultSetMapping,
                        usesJdbcWithDtos,
                        assessMigrationComplexity(metadata),
                        metadata.getPatternType(),
                        metadata.getFieldCount(),
                        metadata.getMappingMethodCount(),
                        metadata.getResultSetCallCount(),
                        generateMigrationRecommendations(metadata));

                // Set single consolidated property on ClassNode for analysis data
                classNode.setProperty("data_transfer_analysis", analysisResult);
            }
        });
    }

    public String assessMigrationComplexity(DataTransferMetadata metadata) {
        int complexityScore = 0;

        if (metadata.isDataTransferObject()) {
            complexityScore += 1; // Base complexity for DTO
            if (metadata.getFieldCount() > 10) {
                complexityScore += 2; // High complexity for many fields
            } else if (metadata.getFieldCount() > 5) {
                complexityScore += 1; // Medium complexity for some fields
            }
        }

        if (metadata.isValueObject()) {
            complexityScore += 1; // Value Object complexity
            if (metadata.hasImmutablePattern()) {
                complexityScore += 1; // Additional complexity for immutable patterns
            }
        }

        if (metadata.hasCustomMappingLogic()) {
            complexityScore += 2; // Custom mapping logic complexity
            if (metadata.getMappingMethodCount() > 3) {
                complexityScore += 1; // Multiple mapping methods
            }
        }

        if (metadata.hasManualResultSetMapping()) {
            complexityScore += 2; // Manual ResultSet mapping complexity
            if (metadata.getResultSetCallCount() > 10) {
                complexityScore += 2; // Many ResultSet field extractions
            } else if (metadata.getResultSetCallCount() > 5) {
                complexityScore += 1; // Medium ResultSet usage
            }
        }

        if (metadata.usesJdbcWithDtos()) {
            complexityScore += 1; // JDBC integration complexity
        }

        if (complexityScore <= 3) {
            return "LOW";
        } else if (complexityScore <= 7) {
            return "MEDIUM";
        } else {
            return "HIGH";
        }
    }

    private List<String> generateMigrationRecommendations(DataTransferMetadata metadata) {
        List<String> recommendations = new ArrayList<>();

        if (metadata.isDataTransferObject()) {
            recommendations.add("Convert DTO to Spring Data @Entity or simple POJO with @JsonProperty");
            if (metadata.getFieldCount() > 10) {
                recommendations.add("Consider splitting large DTO into smaller, focused data classes");
            }
        }

        if (metadata.isValueObject()) {
            recommendations.add("Convert Value Object to record class or @Value annotation");
            recommendations.add("Use Spring @ConfigurationProperties for configuration value objects");
        }

        if (metadata.hasManualResultSetMapping()) {
            recommendations.add("Replace manual ResultSet mapping with Spring JdbcTemplate RowMapper");
            recommendations.add("Consider using @Query with custom result mapping or Spring Data projections");
            if (metadata.getResultSetCallCount() > 10) {
                recommendations.add("Use BeanPropertyRowMapper for automatic field mapping");
            }
        }

        if (metadata.hasCustomMappingLogic()) {
            recommendations.add("Replace custom mapping with Spring @Mapper or ModelMapper integration");
            recommendations.add("Use Spring Converter framework for type conversions");
        }

        if (metadata.usesJdbcWithDtos()) {
            recommendations.add("Migrate to Spring Data JPA repositories with entity mapping");
            recommendations.add("Use @Repository with JdbcTemplate for lightweight data access");
        }

        return recommendations;
    }

    @Override
    public String getName() {
        return INSPECTOR_NAME + " (" + INSPECTOR_ID + ")";
    }

    /**
     * Visitor that detects custom data transfer patterns by analyzing class
     * structure,
     * field patterns, mapping methods, and JDBC ResultSet usage.
     */
    private static class DataTransferPatternDetector extends VoidVisitorAdapter<Void> {
        private final DataTransferMetadata metadata = new DataTransferMetadata();
        private String currentClassName;

        public DataTransferMetadata getMetadata() {
            return metadata;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            if (classDecl.isInterface()) {
                super.visit(classDecl, arg);
                return;
            }

            currentClassName = classDecl.getNameAsString();

            // Check if this is a Data Transfer Object
            if (isDataTransferObjectClass(currentClassName)) {
                metadata.setDataTransferObject(true);
                metadata.setPatternType("DATA_TRANSFER_OBJECT");
                analyzeDataTransferStructure(classDecl);
            }

            // Check if this is a Value Object
            if (isValueObjectClass(currentClassName)) {
                metadata.setValueObject(true);
                if (metadata.isDataTransferObject()) {
                    metadata.setPatternType("COMPOSITE_DATA_PATTERN");
                } else {
                    metadata.setPatternType("VALUE_OBJECT");
                }
                analyzeValueObjectStructure(classDecl);
            }

            // Analyze field structure and patterns
            analyzeFieldStructure(classDecl);

            // Check for custom mapping logic
            analyzeMappingMethods(classDecl);

            // Check for manual ResultSet mapping patterns
            analyzeResultSetMapping(classDecl);

            // Check for JDBC usage with DTOs
            analyzeJdbcDtoUsage(classDecl);

            super.visit(classDecl, arg);
        }

        private boolean isDataTransferObjectClass(String className) {
            return DTO_INDICATORS.stream().anyMatch(className::contains);
        }

        private boolean isValueObjectClass(String className) {
            return VALUE_OBJECT_INDICATORS.stream().anyMatch(className::contains);
        }

        private void analyzeDataTransferStructure(ClassOrInterfaceDeclaration classDecl) {
            int fieldCount = 0;
            int getterCount = 0;
            int setterCount = 0;

            // Count fields
            for (FieldDeclaration field : classDecl.getFields()) {
                fieldCount += field.getVariables().size();
            }

            // Count getters and setters
            for (MethodDeclaration method : classDecl.getMethods()) {
                String methodName = method.getNameAsString();
                if (isGetter(method)) {
                    getterCount++;
                } else if (isSetter(method)) {
                    setterCount++;
                }
            }

            metadata.setFieldCount(fieldCount);
            metadata.setGetterCount(getterCount);
            metadata.setSetterCount(setterCount);

            // Determine if it's primarily a data container
            boolean isPrimaryDataContainer = fieldCount > 0 &&
                    (getterCount + setterCount) >= fieldCount &&
                    hasMinimalBusinessLogic(classDecl);

            metadata.setPrimaryDataContainer(isPrimaryDataContainer);
        }

        private void analyzeValueObjectStructure(ClassOrInterfaceDeclaration classDecl) {
            boolean hasImmutablePattern = true;
            boolean hasOnlyGetters = true;

            // Check for immutable patterns (final fields, no setters)
            for (FieldDeclaration field : classDecl.getFields()) {
                if (!field.isFinal()) {
                    hasImmutablePattern = false;
                    break;
                }
            }

            // Check for setters (should not exist in pure value objects)
            for (MethodDeclaration method : classDecl.getMethods()) {
                if (isSetter(method)) {
                    hasOnlyGetters = false;
                    break;
                }
            }

            metadata.setImmutablePattern(hasImmutablePattern && hasOnlyGetters);
        }

        private void analyzeFieldStructure(ClassOrInterfaceDeclaration classDecl) {
            Set<String> fieldTypes = new HashSet<>();

            for (FieldDeclaration field : classDecl.getFields()) {
                Type fieldType = field.getElementType();
                fieldTypes.add(fieldType.toString());
            }

            // Check for common data transfer field types
            boolean hasDataTransferTypes = fieldTypes.stream()
                    .anyMatch(type -> type.contains("String") || type.contains("Integer") || type.contains("Long") ||
                            type.contains("Double") || type.contains("Date") || type.contains("BigDecimal") ||
                            type.contains("Boolean") || type.contains("List") || type.contains("Set"));

            metadata.setHasDataTransferTypes(hasDataTransferTypes);
        }

        private void analyzeMappingMethods(ClassOrInterfaceDeclaration classDecl) {
            int mappingMethodCount = 0;
            boolean hasCustomMappingLogic = false;

            for (MethodDeclaration method : classDecl.getMethods()) {
                String methodName = method.getNameAsString();

                // Check for mapping method name patterns
                if (MAPPING_METHOD_PATTERNS.stream()
                        .anyMatch(pattern -> methodName.toLowerCase().contains(pattern.toLowerCase()))) {
                    mappingMethodCount++;
                    hasCustomMappingLogic = true;
                }

                // Check for methods that take ResultSet parameters
                boolean hasResultSetParam = method.getParameters().stream()
                        .anyMatch(param -> param.getType().toString().contains("ResultSet"));

                if (hasResultSetParam) {
                    hasCustomMappingLogic = true;
                    mappingMethodCount++;
                }
            }

            metadata.setMappingMethodCount(mappingMethodCount);
            metadata.setCustomMappingLogic(hasCustomMappingLogic);
        }

        private void analyzeResultSetMapping(ClassOrInterfaceDeclaration classDecl) {
            final int[] resultSetCallCount = { 0 };
            boolean[] hasManualMapping = { false };

            classDecl.findAll(MethodCallExpr.class).forEach(methodCall -> {
                String methodName = methodCall.getNameAsString();

                // Check for ResultSet getter methods
                if (JDBC_RESULTSET_METHODS.contains(methodName)) {
                    resultSetCallCount[0]++;
                    hasManualMapping[0] = true;
                }

                // Check for ResultSet.next() calls which indicate manual iteration
                if ("next".equals(methodName) && methodCall.getScope().isPresent()) {
                    String scopeType = methodCall.getScope().get().toString();
                    if (scopeType.toLowerCase().contains("resultset") || scopeType.contains("rs")) {
                        hasManualMapping[0] = true;
                    }
                }
            });

            // Look for assignment patterns from ResultSet to fields
            classDecl.findAll(AssignExpr.class).forEach(assignment -> {
                if (assignment.getValue() instanceof MethodCallExpr methodCall) {
                    if (JDBC_RESULTSET_METHODS.contains(methodCall.getNameAsString())) {
                        hasManualMapping[0] = true;
                    }
                }
            });

            metadata.setResultSetCallCount(resultSetCallCount[0]);
            metadata.setManualResultSetMapping(hasManualMapping[0]);
        }

        private void analyzeJdbcDtoUsage(ClassOrInterfaceDeclaration classDecl) {
            boolean usesJdbcWithDtos = (metadata.isDataTransferObject() || metadata.isValueObject()) &&
                    metadata.hasManualResultSetMapping();

            // Check for JDBC imports or usage combined with DTO patterns

            // Check for method parameters that suggest JDBC DTO usage
            for (MethodDeclaration method : classDecl.getMethods()) {
                boolean hasResultSetParam = method.getParameters().stream()
                        .anyMatch(param -> param.getType().toString().contains("ResultSet"));

                boolean hasConnectionParam = method.getParameters().stream()
                        .anyMatch(param -> param.getType().toString().contains("Connection"));

                if (hasResultSetParam || hasConnectionParam) {
                    usesJdbcWithDtos = true;
                    break;
                }
            }

            metadata.setJdbcWithDtos(usesJdbcWithDtos);
        }

        private boolean hasMinimalBusinessLogic(ClassOrInterfaceDeclaration classDecl) {
            int businessMethodCount = 0;

            for (MethodDeclaration method : classDecl.getMethods()) {
                if (!isConstructor(method) && !isGetter(method) && !isSetter(method) &&
                        !isToString(method) && !isHashCodeEquals(method)) {
                    businessMethodCount++;
                }
            }

            // Consider minimal if less than 20% of methods are business logic
            int totalMethods = classDecl.getMethods().size();
            return totalMethods == 0 || (businessMethodCount * 5 < totalMethods);
        }

        private boolean isGetter(MethodDeclaration method) {
            String methodName = method.getNameAsString();
            return ((methodName.startsWith("get") && methodName.length() > 3) ||
                    (methodName.startsWith("is") && methodName.length() > 2)) &&
                    method.getParameters().isEmpty() &&
                    !method.getType().toString().equals("void");
        }

        private boolean isSetter(MethodDeclaration method) {
            String methodName = method.getNameAsString();
            return methodName.startsWith("set") && methodName.length() > 3 &&
                    method.getParameters().size() == 1 &&
                    method.getType().toString().equals("void");
        }

        private boolean isConstructor(MethodDeclaration method) {
            return method.getNameAsString().equals(currentClassName);
        }

        private boolean isToString(MethodDeclaration method) {
            return "toString".equals(method.getNameAsString()) &&
                    method.getParameters().isEmpty();
        }

        private boolean isHashCodeEquals(MethodDeclaration method) {
            String name = method.getNameAsString();
            return "hashCode".equals(name) || "equals".equals(name);
        }
    }

    /**
     * Consolidated analysis result for data transfer pattern inspection
     */
    public static class DataTransferAnalysisResult {
        private final boolean isDataTransferObject;
        private final boolean isValueObject;
        private final boolean hasCustomMappingLogic;
        private final boolean hasManualResultSetMapping;
        private final boolean usesJdbcWithDtos;
        private final String migrationComplexity;
        private final String patternType;
        private final int fieldCount;
        private final int mappingMethodCount;
        private final int resultSetCallCount;
        private final List<String> migrationRecommendations;

        public DataTransferAnalysisResult(boolean isDataTransferObject, boolean isValueObject,
                boolean hasCustomMappingLogic, boolean hasManualResultSetMapping,
                boolean usesJdbcWithDtos, String migrationComplexity, String patternType,
                int fieldCount, int mappingMethodCount, int resultSetCallCount,
                List<String> migrationRecommendations) {
            this.isDataTransferObject = isDataTransferObject;
            this.isValueObject = isValueObject;
            this.hasCustomMappingLogic = hasCustomMappingLogic;
            this.hasManualResultSetMapping = hasManualResultSetMapping;
            this.usesJdbcWithDtos = usesJdbcWithDtos;
            this.migrationComplexity = migrationComplexity;
            this.patternType = patternType;
            this.fieldCount = fieldCount;
            this.mappingMethodCount = mappingMethodCount;
            this.resultSetCallCount = resultSetCallCount;
            this.migrationRecommendations = migrationRecommendations != null ? new ArrayList<>(migrationRecommendations)
                    : new ArrayList<>();
        }

        // Getters
        public boolean isDataTransferObject() {
            return isDataTransferObject;
        }

        public boolean isValueObject() {
            return isValueObject;
        }

        public boolean hasCustomMappingLogic() {
            return hasCustomMappingLogic;
        }

        public boolean hasManualResultSetMapping() {
            return hasManualResultSetMapping;
        }

        public boolean usesJdbcWithDtos() {
            return usesJdbcWithDtos;
        }

        public String getMigrationComplexity() {
            return migrationComplexity;
        }

        public String getPatternType() {
            return patternType;
        }

        public int getFieldCount() {
            return fieldCount;
        }

        public int getMappingMethodCount() {
            return mappingMethodCount;
        }

        public int getResultSetCallCount() {
            return resultSetCallCount;
        }

        public List<String> getMigrationRecommendations() {
            return new ArrayList<>(migrationRecommendations);
        }
    }

    /**
     * Data class to hold custom data transfer pattern analysis metadata
     */
    public static class DataTransferMetadata {
        private boolean isDataTransferObject = false;
        private boolean isValueObject = false;
        private boolean hasCustomMappingLogic = false;
        private boolean hasManualResultSetMapping = false;
        private boolean usesJdbcWithDtos = false;
        private boolean hasImmutablePattern = false;
        private boolean isPrimaryDataContainer = false;
        private boolean hasDataTransferTypes = false;
        private int fieldCount = 0;
        private int getterCount = 0;
        private int setterCount = 0;
        private int mappingMethodCount = 0;
        private int resultSetCallCount = 0;
        private String patternType;

        // Getters and setters
        public boolean isDataTransferObject() {
            return isDataTransferObject;
        }

        public void setDataTransferObject(boolean isDataTransferObject) {
            this.isDataTransferObject = isDataTransferObject;
        }

        public boolean isValueObject() {
            return isValueObject;
        }

        public void setValueObject(boolean isValueObject) {
            this.isValueObject = isValueObject;
        }

        public boolean hasCustomMappingLogic() {
            return hasCustomMappingLogic;
        }

        public void setCustomMappingLogic(boolean hasCustomMappingLogic) {
            this.hasCustomMappingLogic = hasCustomMappingLogic;
        }

        public boolean hasManualResultSetMapping() {
            return hasManualResultSetMapping;
        }

        public void setManualResultSetMapping(boolean hasManualResultSetMapping) {
            this.hasManualResultSetMapping = hasManualResultSetMapping;
        }

        public boolean usesJdbcWithDtos() {
            return usesJdbcWithDtos;
        }

        public void setJdbcWithDtos(boolean usesJdbcWithDtos) {
            this.usesJdbcWithDtos = usesJdbcWithDtos;
        }

        public boolean hasImmutablePattern() {
            return hasImmutablePattern;
        }

        public void setImmutablePattern(boolean hasImmutablePattern) {
            this.hasImmutablePattern = hasImmutablePattern;
        }

        public boolean isPrimaryDataContainer() {
            return isPrimaryDataContainer;
        }

        public void setPrimaryDataContainer(boolean isPrimaryDataContainer) {
            this.isPrimaryDataContainer = isPrimaryDataContainer;
        }

        public boolean hasDataTransferTypes() {
            return hasDataTransferTypes;
        }

        public void setHasDataTransferTypes(boolean hasDataTransferTypes) {
            this.hasDataTransferTypes = hasDataTransferTypes;
        }

        public int getFieldCount() {
            return fieldCount;
        }

        public void setFieldCount(int fieldCount) {
            this.fieldCount = fieldCount;
        }

        public int getGetterCount() {
            return getterCount;
        }

        public void setGetterCount(int getterCount) {
            this.getterCount = getterCount;
        }

        public int getSetterCount() {
            return setterCount;
        }

        public void setSetterCount(int setterCount) {
            this.setterCount = setterCount;
        }

        public int getMappingMethodCount() {
            return mappingMethodCount;
        }

        public void setMappingMethodCount(int mappingMethodCount) {
            this.mappingMethodCount = mappingMethodCount;
        }

        public int getResultSetCallCount() {
            return resultSetCallCount;
        }

        public void setResultSetCallCount(int resultSetCallCount) {
            this.resultSetCallCount = resultSetCallCount;
        }

        public String getPatternType() {
            return patternType;
        }

        public void setPatternType(String patternType) {
            this.patternType = patternType;
        }
    }
}
