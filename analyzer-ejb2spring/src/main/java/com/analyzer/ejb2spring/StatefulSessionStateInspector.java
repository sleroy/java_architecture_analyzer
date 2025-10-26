package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.graph.ProjectFileRepository;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.dev.inspectors.binary.AbstractASMClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import javax.inject.Inject;
import java.util.*;

/**
 * Class-centric stateful session state inspector - Phase 3 migration.
 * 
 * <p>
 * Analyzes stateful session beans to identify state management patterns,
 * conversational state variables, and cross-method state dependencies.
 * Provides recommendations for migrating to Spring scope patterns.
 * </p>
 * 
 * <p>
 * <strong>Analysis Performed:</strong>
 * </p>
 * <ul>
 * <li>State field identification and role classification</li>
 * <li>Cross-method dependency tracking</li>
 * <li>Complexity assessment based on field types and interactions</li>
 * <li>Spring scope migration strategy determination</li>
 * <li>Serialization and collection pattern detection</li>
 * </ul>
 * 
 * <p>
 * <strong>Key Differences from StatefulSessionStateInspector:</strong>
 * </p>
 * <ul>
 * <li>Extends AbstractASMClassInspector (class-centric) instead of
 * AbstractASMInspector (file-centric)</li>
 * <li>Receives JavaClassNode directly instead of creating it</li>
 * <li>Writes all analysis results to JavaClassNode properties</li>
 * <li>Uses NodeDecorator for type-safe property access</li>
 * <li>Simplified lifecycle without supports() method</li>
 * </ul>
 * 
 * <p>
 * <strong>Migration Strategies Generated:</strong>
 * </p>
 * <ul>
 * <li>SESSION_SCOPED - For user-data and collection-heavy beans</li>
 * <li>REQUEST_SCOPED - For low-complexity calculated state</li>
 * <li>STATELESS_REFACTOR - For simple state that can be externalized</li>
 * <li>MANUAL_REVIEW - For critical complexity requiring detailed analysis</li>
 * </ul>
 * 
 * @since Phase 3 - Systematic Inspector Migration
 * @see StatefulSessionStateInspector Original file-centric version
 */
@InspectorDependencies(requires = {
        EjbMigrationTags.EJB_STATEFUL_SESSION_BEAN
}, produces = {
        EjbMigrationTags.STATEFUL_SESSION_STATE,
        EjbMigrationTags.CONVERSATIONAL_STATE,
        EjbMigrationTags.CROSS_METHOD_DEPENDENCY,
        EjbMigrationTags.SPRING_SCOPE_MIGRATION
})
public class StatefulSessionStateInspector extends AbstractASMClassInspector {

    @Inject
    public StatefulSessionStateInspector(ProjectFileRepository projectFileRepository,
            ResourceResolver resourceResolver) {
        super(projectFileRepository, resourceResolver);
    }

    @Override
    public String getName() {
        return "Stateful Session State Inspector V2 (Class-Centric ASM)";
    }

    @Override
    protected ASMClassNodeVisitor createClassVisitor(JavaClassNode classNode,
            NodeDecorator<JavaClassNode> decorator) {
        return new StatefulBeanAnalysisVisitor(classNode, decorator);
    }

    // Utility methods for field and method analysis
    private static boolean isSerializableType(String descriptor) {
        String type = Type.getType(descriptor).getClassName();
        return type.equals("java.io.Serializable") ||
                type.startsWith("java.lang.") ||
                type.startsWith("java.util.") ||
                type.endsWith("Serializable");
    }

    private static boolean isCollectionType(String descriptor) {
        String type = Type.getType(descriptor).getClassName();
        return type.startsWith("java.util.List") ||
                type.startsWith("java.util.Set") ||
                type.startsWith("java.util.Map") ||
                type.startsWith("java.util.Collection") ||
                type.endsWith("[]");
    }

    private static StateFieldRole determineFieldRole(String name, String descriptor) {
        String lowerName = name.toLowerCase();

        if (lowerName.contains("user") || lowerName.contains("customer") ||
                lowerName.contains("session") || lowerName.contains("context")) {
            return StateFieldRole.USER_DATA;
        }

        if (lowerName.contains("total") || lowerName.contains("count") ||
                lowerName.contains("sum") || lowerName.contains("calc")) {
            return StateFieldRole.CALCULATED;
        }

        if (lowerName.contains("cache") || lowerName.contains("buffer")) {
            return StateFieldRole.CACHE;
        }

        return StateFieldRole.BUSINESS_DATA;
    }

    private static boolean isEjbLifecycleMethod(String name) {
        return name.equals("ejbCreate") || name.equals("ejbRemove") ||
                name.equals("ejbActivate") || name.equals("ejbPassivate") ||
                name.equals("setSessionContext");
    }

    private static MethodRole determineMethodRole(String name) {
        if (isEjbLifecycleMethod(name)) {
            return MethodRole.LIFECYCLE;
        }

        if (name.startsWith("get") || name.startsWith("is") ||
                name.startsWith("find") || name.startsWith("list")) {
            return MethodRole.ACCESSOR;
        }

        if (name.startsWith("set") || name.startsWith("add") ||
                name.startsWith("remove") || name.startsWith("update")) {
            return MethodRole.MUTATOR;
        }

        if (name.equals("ejbCreate") || name.contains("init")) {
            return MethodRole.INITIALIZER;
        }

        if (name.equals("ejbRemove") || name.contains("cleanup") || name.contains("clear")) {
            return MethodRole.CLEANUP;
        }

        return MethodRole.BUSINESS;
    }

    /**
     * Enumeration of state field roles for classification.
     */
    private enum StateFieldRole {
        USER_DATA,
        BUSINESS_DATA,
        CALCULATED,
        CACHE
    }

    /**
     * Enumeration of method roles for lifecycle and access pattern analysis.
     */
    private enum MethodRole {
        INITIALIZER,
        MUTATOR,
        ACCESSOR,
        CLEANUP,
        LIFECYCLE,
        BUSINESS
    }

    /**
     * Data class representing a state field in the stateful bean.
     */
    private static class StateField {
        String name;
        String descriptor;
        String type;
        boolean isSerializable;
        boolean isCollection;
        StateFieldRole role;
        int readers;
        int writers;

        public StateField() {
        }
    }

    /**
     * Data class representing a method in the stateful bean.
     */
    private static class StateMethod {
        String name;
        String descriptor;
        String signature;
        boolean isEjbLifecycle;
        MethodRole role;
        Set<String> readsFields;
        Set<String> writesFields;

        public StateMethod() {
        }
    }

    /**
     * Comprehensive analysis data POJO for stateful bean state patterns.
     * This consolidates all analysis results into a single serializable object.
     */
    private static class StateAnalysisData {
        int fieldCount;
        int crossMethodDependencies;
        String complexity;
        String migrationStrategy;
        List<String> recommendations;
        List<StateField> fieldMetadata;

        public StateAnalysisData() {
            this.recommendations = new ArrayList<>();
            this.fieldMetadata = new ArrayList<>();
            this.migrationStrategy = "SESSION_SCOPED";
            this.complexity = "LOW";
        }
    }

    /**
     * ASM visitor that analyzes stateful session bean state patterns.
     * Tracks field usage, method interactions, and generates migration
     * recommendations.
     */
    private static class StatefulBeanAnalysisVisitor extends ASMClassNodeVisitor {
        private final List<StateField> stateFields = new ArrayList<>();
        private final List<StateMethod> stateMethods = new ArrayList<>();
        private final Map<String, Set<String>> fieldReaders = new HashMap<>();
        private final Map<String, Set<String>> fieldWriters = new HashMap<>();

        private String className;

        protected StatefulBeanAnalysisVisitor(JavaClassNode classNode,
                NodeDecorator<JavaClassNode> decorator) {
            super(classNode, decorator);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            this.className = name.replace('/', '.');
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            // Skip static and final fields for state analysis
            if ((access & Opcodes.ACC_STATIC) == 0 && (access & Opcodes.ACC_FINAL) == 0) {
                StateField field = new StateField();
                field.name = name;
                field.descriptor = descriptor;
                field.type = Type.getType(descriptor).getClassName();
                field.isSerializable = isSerializableType(descriptor);
                field.isCollection = isCollectionType(descriptor);
                field.role = determineFieldRole(name, descriptor);

                stateFields.add(field);
                fieldReaders.put(name, new HashSet<>());
                fieldWriters.put(name, new HashSet<>());
            }

            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
            StateMethod method = new StateMethod();
            method.name = name;
            method.descriptor = descriptor;
            method.signature = name + descriptor;
            method.isEjbLifecycle = isEjbLifecycleMethod(name);
            method.role = determineMethodRole(name);

            stateMethods.add(method);

            return new StateFieldTrackingVisitor(method, className, fieldReaders, fieldWriters);
        }

        @Override
        public void visitEnd() {
            analyzeStatefulBean();
            super.visitEnd();
        }

        /**
         * Performs comprehensive stateful bean analysis and writes results to
         * JavaClassNode.
         */
        private void analyzeStatefulBean() {
            if (stateFields.isEmpty()) {
                // No state fields found - this is unusual for a stateful bean
                setProperty("stateful_state.no_fields_detected", true);
                return;
            }

            // Calculate complexity metrics
            int stateFieldCount = stateFields.size();
            int crossMethodDeps = calculateCrossMethodDependencies();
            String complexity = assessComplexity(stateFieldCount, crossMethodDeps);
            String migrationStrategy = determineMigrationStrategy(complexity, stateFields);

            // Create consolidated analysis data POJO
            StateAnalysisData analysisData = new StateAnalysisData();
            analysisData.fieldCount = stateFieldCount;
            analysisData.crossMethodDependencies = crossMethodDeps;
            analysisData.complexity = complexity;
            analysisData.migrationStrategy = migrationStrategy;
            analysisData.recommendations = generateRecommendations(complexity, migrationStrategy, stateFields);
            analysisData.fieldMetadata = createFieldMetadataObjects(stateFields);

            // Write comprehensive analysis data to JavaClassNode as single property
            setProperty("stateful_state.analysis_data", analysisData);

            // Enable tags for dependency resolution
            enableTag(EjbMigrationTags.STATEFUL_SESSION_STATE);
            enableTag(EjbMigrationTags.CONVERSATIONAL_STATE);
            enableTag(EjbMigrationTags.SPRING_SCOPE_MIGRATION);

            if (crossMethodDeps > 0) {
                enableTag(EjbMigrationTags.CROSS_METHOD_DEPENDENCY);
            }

            // Add summary properties for quick access
            setProperty("stateful_state.field_count", stateFieldCount);
            setProperty("stateful_state.complexity", complexity);
            setProperty("stateful_state.migration_strategy", migrationStrategy);
        }

        /**
         * Calculates the number of cross-method dependencies by analyzing field access
         * patterns.
         */
        private int calculateCrossMethodDependencies() {
            int dependencies = 0;

            for (String fieldName : fieldReaders.keySet()) {
                Set<String> readers = fieldReaders.get(fieldName);
                Set<String> writers = fieldWriters.get(fieldName);

                // Count fields that are written by some methods and read by others
                if (!readers.isEmpty() && !writers.isEmpty()) {
                    for (String writer : writers) {
                        for (String reader : readers) {
                            if (!writer.equals(reader)) {
                                dependencies++;
                                break; // Count each field only once
                            }
                        }
                        if (dependencies > 0)
                            break; // Already counted this field
                    }
                }
            }

            return dependencies;
        }

        /**
         * Assesses the complexity of state management based on multiple factors.
         */
        private String assessComplexity(int stateFieldCount, int crossMethodDeps) {
            int complexityScore = stateFieldCount * 2 + crossMethodDeps * 3;

            // Add complexity for special field types
            for (StateField field : stateFields) {
                if (field.isCollection)
                    complexityScore += 2;
                if (field.isSerializable)
                    complexityScore += 1;
                if (field.role == StateFieldRole.CALCULATED)
                    complexityScore += 2;
            }

            if (complexityScore <= 8)
                return "LOW";
            if (complexityScore <= 16)
                return "MEDIUM";
            if (complexityScore <= 24)
                return "HIGH";
            return "CRITICAL";
        }

        /**
         * Determines the most appropriate migration strategy based on complexity and
         * field patterns.
         */
        private String determineMigrationStrategy(String complexity, List<StateField> fields) {
            if ("CRITICAL".equals(complexity)) {
                return "MANUAL_REVIEW";
            }

            // Analyze field patterns to determine best Spring scope
            boolean hasUserData = fields.stream().anyMatch(f -> f.role == StateFieldRole.USER_DATA);
            boolean hasCalculated = fields.stream().anyMatch(f -> f.role == StateFieldRole.CALCULATED);
            boolean hasCollections = fields.stream().anyMatch(f -> f.isCollection);

            if (hasUserData && hasCollections) {
                return "SESSION_SCOPED";
            }

            if (hasCalculated && "LOW".equals(complexity)) {
                return "REQUEST_SCOPED";
            }

            if ("LOW".equals(complexity)) {
                return "STATELESS_REFACTOR";
            }

            return "SESSION_SCOPED";
        }

        /**
         * Generates specific migration recommendations based on analysis results.
         */
        private List<String> generateRecommendations(String complexity, String strategy, List<StateField> fields) {
            List<String> recommendations = new ArrayList<>();

            switch (strategy) {
                case "SESSION_SCOPED":
                    recommendations.add("Convert to @Scope(\"session\") Spring bean");
                    recommendations.add("Consider using ScopedProxyMode.TARGET_CLASS");
                    break;
                case "REQUEST_SCOPED":
                    recommendations.add("Convert to @Scope(\"request\") Spring bean");
                    break;
                case "STATELESS_REFACTOR":
                    recommendations.add("Refactor to stateless service with external state storage");
                    recommendations.add("Consider using database or Redis for state persistence");
                    break;
                case "MANUAL_REVIEW":
                    recommendations.add("Complex state requires manual migration review");
                    recommendations.add("Consider breaking into smaller, focused services");
                    break;
            }

            // Add specific recommendations based on field types
            long serializableFields = fields.stream().filter(f -> f.isSerializable).count();
            if (serializableFields > 0) {
                recommendations.add("Review " + serializableFields + " serializable fields for session storage");
            }

            long collectionFields = fields.stream().filter(f -> f.isCollection).count();
            if (collectionFields > 0) {
                recommendations.add("Optimize " + collectionFields + " collection fields for memory usage");
            }

            return recommendations;
        }

        /**
         * Creates detailed field metadata objects with usage statistics.
         */
        private List<StateField> createFieldMetadataObjects(List<StateField> fields) {
            List<StateField> fieldMetadata = new ArrayList<>();

            for (StateField originalField : fields) {
                StateField field = new StateField();
                field.name = originalField.name;
                field.type = originalField.type;
                field.role = originalField.role;
                field.isCollection = originalField.isCollection;
                field.isSerializable = originalField.isSerializable;

                Set<String> readers = fieldReaders.get(field.name);
                Set<String> writers = fieldWriters.get(field.name);
                field.readers = readers != null ? readers.size() : 0;
                field.writers = writers != null ? writers.size() : 0;

                fieldMetadata.add(field);
            }

            return fieldMetadata;
        }
    }

    /**
     * Method visitor that tracks field access patterns within methods.
     * Used to identify cross-method state dependencies.
     */
    private static class StateFieldTrackingVisitor extends MethodVisitor {
        private final StateMethod currentMethod;
        private final String className;
        private final Map<String, Set<String>> fieldReaders;
        private final Map<String, Set<String>> fieldWriters;
        private final Set<String> readsFields = new HashSet<>();
        private final Set<String> writesFields = new HashSet<>();

        public StateFieldTrackingVisitor(StateMethod method, String className,
                Map<String, Set<String>> fieldReaders,
                Map<String, Set<String>> fieldWriters) {
            super(Opcodes.ASM9);
            this.currentMethod = method;
            this.className = className;
            this.fieldReaders = fieldReaders;
            this.fieldWriters = fieldWriters;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            // Track field access patterns for this class only
            if (owner.replace('/', '.').equals(className)) {
                switch (opcode) {
                    case Opcodes.GETFIELD:
                        readsFields.add(name);
                        fieldReaders.computeIfAbsent(name, k -> new HashSet<>()).add(currentMethod.name);
                        break;
                    case Opcodes.PUTFIELD:
                        writesFields.add(name);
                        fieldWriters.computeIfAbsent(name, k -> new HashSet<>()).add(currentMethod.name);
                        break;
                }
            }

            super.visitFieldInsn(opcode, owner, name, descriptor);
        }

        @Override
        public void visitEnd() {
            currentMethod.readsFields = readsFields;
            currentMethod.writesFields = writesFields;
            super.visitEnd();
        }
    }
}
