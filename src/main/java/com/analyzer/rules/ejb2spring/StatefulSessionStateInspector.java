package com.analyzer.rules.ejb2spring;

import com.analyzer.core.*;
import com.analyzer.core.graph.GraphAwareInspector;
import com.analyzer.core.graph.GraphRepository;
import com.analyzer.core.graph.GraphNode;
import com.analyzer.core.graph.GraphEdge;
import com.analyzer.inspectors.core.binary.AbstractASMInspector;
import com.analyzer.resource.ResourceLocation;

import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.util.*;

/**
 * Inspector I-0905: Stateful Session State Inspector
 * 
 * Analyzes stateful session beans to identify state management patterns,
 * conversational state variables, and cross-method state dependencies.
 * Provides recommendations for migrating to Spring scope patterns.
 */
@InspectorDependencies(need = { SessionBeanInspector.class, EjbDeploymentDescriptorDetector.class })
public class StatefulSessionStateInspector extends AbstractASMInspector implements GraphAwareInspector {

    private GraphRepository graphRepository;

    @Override
    public void setGraphRepository(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    @Override
    public String getName() {
        return "I-0905 Stateful Session State Inspector";
    }

    @Override
    public boolean supports(ProjectFile file) {
        return file.hasTag(EjbMigrationTags.STATEFUL_SESSION_BEAN) &&
               file.hasTag(InspectorTags.BINARY_CLASS);
    }

    @Override
    protected void analyzeBinaryClass(ProjectFile projectFile, ResourceLocation location, 
                                     ResultDecorator resultDecorator) throws IOException {
        try {
            StatefulBeanAnalysis analysis = analyzeStatefulBean(projectFile, location);
            
            if (analysis.getStateFields().isEmpty()) {
                resultDecorator.notApplicable();
                return;
            }

            // Set analysis tags
            setAnalysisTags(projectFile, analysis);
            
            // Create graph nodes and edges
            if (graphRepository != null) {
                createGraphRepresentation(analysis, projectFile);
            }
            
            resultDecorator.success("stateful_session_state.analysis", 
                String.format("Found %d state fields, %d cross-method dependencies, complexity: %s",
                    analysis.getStateFields().size(),
                    analysis.getCrossMethodDependencies().size(),
                    analysis.getComplexity()));
                    
        } catch (Exception e) {
            resultDecorator.error("Failed to analyze stateful session bean: " + e.getMessage());
        }
    }
    
    private StatefulBeanAnalysis analyzeStatefulBean(ProjectFile projectFile, ResourceLocation location) 
            throws IOException {
        StatefulBeanAnalysis analysis = new StatefulBeanAnalysis();
        analysis.setBeanClassName(projectFile.getStringTag("class_name"));
        analysis.setBeanName(projectFile.getStringTag("ejb_name", analysis.getBeanClassName()));
        
        byte[] classBytes = readBinaryContent(location);
        ClassReader classReader = new ClassReader(classBytes);
        
        StatefulBeanClassVisitor visitor = new StatefulBeanClassVisitor(analysis);
        classReader.accept(visitor, ClassReader.EXPAND_FRAMES);
        
        // Perform state flow analysis
        analyzeStateFlows(analysis);
        
        // Assess complexity and migration strategy
        assessComplexityAndStrategy(analysis);
        
        return analysis;
    }
    
    private void analyzeStateFlows(StatefulBeanAnalysis analysis) {
        Map<String, Set<String>> stateFlowGraph = new HashMap<>();
        
        for (StateMethod method : analysis.getStateMethods()) {
            Set<String> affectedFields = new HashSet<>();
            affectedFields.addAll(method.getReadsFields());
            affectedFields.addAll(method.getWritesFields());
            stateFlowGraph.put(method.getMethodName(), affectedFields);
        }
        
        analysis.setStateFlowGraph(stateFlowGraph);
        
        // Identify cross-method dependencies
        identifyCrossMethodDependencies(analysis);
        
        // Detect conversational workflows  
        detectConversationalWorkflows(analysis);
    }
    
    private void identifyCrossMethodDependencies(StatefulBeanAnalysis analysis) {
        for (StateField field : analysis.getStateFields()) {
            if (field.getWriterMethods().size() > 0 && field.getReaderMethods().size() > 0) {
                for (String writer : field.getWriterMethods()) {
                    for (String reader : field.getReaderMethods()) {
                        if (!writer.equals(reader)) {
                            analysis.addCrossMethodDependency(writer, reader, field.getFieldName());
                        }
                    }
                }
            }
        }
    }
    
    private void detectConversationalWorkflows(StatefulBeanAnalysis analysis) {
        Set<String> methodNames = analysis.getStateMethods().stream()
            .map(StateMethod::getMethodName)
            .collect(java.util.stream.Collectors.toSet());
            
        // Shopping cart pattern: add -> calculate -> checkout
        if (hasMethodPattern(methodNames, "add.*", "calculate.*", "checkout|submit|finish")) {
            analysis.addWorkflowPattern("SHOPPING_CART");
        }
        
        // Wizard pattern: next -> previous -> finish
        if (hasMethodPattern(methodNames, "next|forward", "previous|back", "finish|complete")) {
            analysis.addWorkflowPattern("WIZARD");
        }
        
        // Builder pattern: set* -> build/create
        if (hasMethodPattern(methodNames, "set.*", "build|create|construct")) {
            analysis.addWorkflowPattern("BUILDER");
        }
    }
    
    private boolean hasMethodPattern(Set<String> methodNames, String... patterns) {
        boolean[] found = new boolean[patterns.length];
        
        for (String methodName : methodNames) {
            for (int i = 0; i < patterns.length; i++) {
                if (methodName.matches(patterns[i])) {
                    found[i] = true;
                }
            }
        }
        
        // All patterns must be found
        for (boolean f : found) {
            if (!f) return false;
        }
        return true;
    }
    
    private void assessComplexityAndStrategy(StatefulBeanAnalysis analysis) {
        ConversationalComplexity complexity = assessComplexity(analysis);
        analysis.setComplexity(complexity);
        
        SpringMigrationStrategy strategy = determineMigrationStrategy(analysis, complexity);
        analysis.setMigrationStrategy(strategy);
    }
    
    private ConversationalComplexity assessComplexity(StatefulBeanAnalysis analysis) {
        int stateFieldCount = analysis.getStateFields().size();
        int crossMethodDeps = analysis.getCrossMethodDependencies().size();
        int workflowPatterns = analysis.getWorkflowPatterns().size();
        
        int complexityScore = 0;
        complexityScore += stateFieldCount * 2;
        complexityScore += crossMethodDeps * 3;
        complexityScore += workflowPatterns * 5;
        
        // Special cases that increase complexity
        if (hasSerializableState(analysis)) complexityScore += 5;
        if (hasCollectionState(analysis)) complexityScore += 3;
        if (hasEjbLifecycleMethods(analysis)) complexityScore += 3;
        
        if (complexityScore <= 10) return ConversationalComplexity.LOW;
        if (complexityScore <= 25) return ConversationalComplexity.MEDIUM;
        if (complexityScore <= 40) return ConversationalComplexity.HIGH;
        return ConversationalComplexity.CRITICAL;
    }
    
    private boolean hasSerializableState(StatefulBeanAnalysis analysis) {
        return analysis.getStateFields().stream().anyMatch(StateField::isSerializable);
    }
    
    private boolean hasCollectionState(StatefulBeanAnalysis analysis) {
        return analysis.getStateFields().stream().anyMatch(StateField::isCollection);
    }
    
    private boolean hasEjbLifecycleMethods(StatefulBeanAnalysis analysis) {
        return analysis.getStateMethods().stream().anyMatch(StateMethod::isEjbLifecycle);
    }
    
    private SpringMigrationStrategy determineMigrationStrategy(
            StatefulBeanAnalysis analysis, ConversationalComplexity complexity) {
        
        if (complexity == ConversationalComplexity.CRITICAL) {
            return SpringMigrationStrategy.MANUAL_REVIEW;
        }
        
        if (hasWebSessionPatterns(analysis)) {
            return SpringMigrationStrategy.SESSION_SCOPED;
        }
        
        if (hasConversationalPatterns(analysis)) {
            return SpringMigrationStrategy.CONVERSATION_SCOPED;
        }
        
        if (complexity == ConversationalComplexity.LOW && canBeStateless(analysis)) {
            return SpringMigrationStrategy.STATELESS_REFACTOR;
        }
        
        return SpringMigrationStrategy.SESSION_SCOPED;
    }
    
    private boolean hasWebSessionPatterns(StatefulBeanAnalysis analysis) {
        return analysis.getWorkflowPatterns().contains("SHOPPING_CART");
    }
    
    private boolean hasConversationalPatterns(StatefulBeanAnalysis analysis) {
        return analysis.getWorkflowPatterns().contains("WIZARD") ||
               analysis.getWorkflowPatterns().contains("MULTI_STEP_TRANSACTION");
    }
    
    private boolean canBeStateless(StatefulBeanAnalysis analysis) {
        // Simple heuristic: if state is only used for caching or temporary calculation
        return analysis.getStateFields().stream()
            .allMatch(field -> field.getRole() == StateFieldRole.CACHE || 
                              field.getRole() == StateFieldRole.CALCULATED);
    }
    
    private void setAnalysisTags(ProjectFile projectFile, StatefulBeanAnalysis analysis) {
        projectFile.setTag(EjbMigrationTags.STATEFUL_SESSION_STATE, true);
        projectFile.setTag(EjbMigrationTags.CONVERSATIONAL_STATE, true);
        
        if (!analysis.getCrossMethodDependencies().isEmpty()) {
            projectFile.setTag(EjbMigrationTags.CROSS_METHOD_DEPENDENCY, true);
        }
        
        projectFile.setTag(EjbMigrationTags.SPRING_SCOPE_MIGRATION, true);
        
        // Set analysis metadata
        projectFile.setTag("stateful_session_state.field_count", analysis.getStateFields().size());
        projectFile.setTag("stateful_session_state.method_count", analysis.getStateMethods().size());
        projectFile.setTag("stateful_session_state.cross_dependencies", analysis.getCrossMethodDependencies().size());
        projectFile.setTag("stateful_session_state.workflow_patterns", String.join(",", analysis.getWorkflowPatterns()));
        projectFile.setTag("stateful_session_state.complexity", analysis.getComplexity().toString());
        projectFile.setTag("stateful_session_state.migration_strategy", analysis.getMigrationStrategy().toString());
    }
    
    private void createGraphRepresentation(StatefulBeanAnalysis analysis, ProjectFile projectFile) {
        // Create stateful session bean node
        Map<String, Object> nodeProperties = new HashMap<>();
        nodeProperties.put("beanName", analysis.getBeanName());
        nodeProperties.put("className", analysis.getBeanClassName());
        nodeProperties.put("stateFieldCount", analysis.getStateFields().size());
        nodeProperties.put("stateMethodCount", analysis.getStateMethods().size());
        nodeProperties.put("crossMethodDependencies", analysis.getCrossMethodDependencies().size());
        nodeProperties.put("workflowPatterns", analysis.getWorkflowPatterns());
        nodeProperties.put("conversationalComplexity", analysis.getComplexity().name());
        nodeProperties.put("migrationStrategy", analysis.getMigrationStrategy().name());
        
        String nodeId = "STATEFUL_BEAN:" + analysis.getBeanName();
        GraphNode beanNode = new GraphNode(nodeId, "StatefulSessionBean", nodeProperties);
        graphRepository.addNode(beanNode);
        
        // Create edges for state relationships
        createStateRelationshipEdges(analysis);
    }
    
    private void createStateRelationshipEdges(StatefulBeanAnalysis analysis) {
        for (Map.Entry<String, Map<String, Set<String>>> entry : 
             analysis.getCrossMethodDependencies().entrySet()) {
            
            String sourceMethod = entry.getKey();
            for (Map.Entry<String, Set<String>> targetEntry : entry.getValue().entrySet()) {
                String targetMethod = targetEntry.getKey();
                Set<String> sharedFields = targetEntry.getValue();
                
                Map<String, Object> edgeProps = new HashMap<>();
                edgeProps.put("sharedFields", sharedFields);
                edgeProps.put("dependencyType", "STATE_FLOW");
                
                String sourceNodeId = "METHOD:" + analysis.getBeanName() + "." + sourceMethod;
                String targetNodeId = "METHOD:" + analysis.getBeanName() + "." + targetMethod;
                
                GraphEdge stateEdge = new GraphEdge(sourceNodeId, targetNodeId, "STATE_DEPENDENCY", edgeProps);
                graphRepository.addEdge(stateEdge);
            }
        }
    }

    // ASM Visitor classes
    private class StatefulBeanClassVisitor extends ClassVisitor {
        private StatefulBeanAnalysis analysis;
        private String currentClassName;
        
        public StatefulBeanClassVisitor(StatefulBeanAnalysis analysis) {
            super(ASM9);
            this.analysis = analysis;
        }
        
        @Override
        public void visit(int version, int access, String name, String signature,
                         String superName, String[] interfaces) {
            this.currentClassName = name.replace('/', '.');
            if (analysis.getBeanClassName() == null) {
                analysis.setBeanClassName(currentClassName);
            }
        }
        
        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                      String signature, Object value) {
            
            // Skip static and final fields for state analysis
            if ((access & ACC_STATIC) == 0 && (access & ACC_FINAL) == 0) {
                StateField field = new StateField();
                field.setFieldName(name);
                field.setFieldType(Type.getType(descriptor).getClassName());
                field.setFinal((access & ACC_FINAL) != 0);
                field.setSerializable(isSerializableType(descriptor));
                field.setCollection(isCollectionType(descriptor));
                field.setRole(determineFieldRole(name, descriptor));
                
                analysis.addStateField(field);
            }
            
            return super.visitField(access, name, descriptor, signature, value);
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                       String signature, String[] exceptions) {
            
            StateMethod method = new StateMethod();
            method.setMethodName(name);
            method.setMethodSignature(name + descriptor);
            method.setEjbLifecycle(isEjbLifecycleMethod(name));
            method.setRole(determineMethodRole(name));
            
            analysis.addStateMethod(method);
            
            return new StateMethodVisitor(method, analysis, currentClassName);
        }
        
        private boolean isSerializableType(String descriptor) {
            String typeName = Type.getType(descriptor).getClassName();
            return typeName.equals("java.io.Serializable") ||
                   typeName.startsWith("java.lang.") ||
                   typeName.startsWith("java.util.");
        }
        
        private boolean isCollectionType(String descriptor) {
            String typeName = Type.getType(descriptor).getClassName();
            return typeName.startsWith("java.util.List") ||
                   typeName.startsWith("java.util.Set") ||
                   typeName.startsWith("java.util.Map") ||
                   typeName.startsWith("java.util.Collection");
        }
        
        private StateFieldRole determineFieldRole(String name, String descriptor) {
            String lowerName = name.toLowerCase();
            if (lowerName.contains("cache") || lowerName.contains("temp")) {
                return StateFieldRole.CACHE;
            }
            if (lowerName.contains("total") || lowerName.contains("sum") || lowerName.contains("count")) {
                return StateFieldRole.CALCULATED;
            }
            return StateFieldRole.USER_DATA;
        }
        
        private boolean isEjbLifecycleMethod(String name) {
            return name.startsWith("ejbCreate") || name.startsWith("ejbRemove") ||
                   name.startsWith("ejbActivate") || name.startsWith("ejbPassivate");
        }
        
        private MethodRole determineMethodRole(String name) {
            if (isEjbLifecycleMethod(name)) {
                return MethodRole.INITIALIZER;
            }
            if (name.startsWith("set") || name.startsWith("add") || name.startsWith("remove")) {
                return MethodRole.MUTATOR;
            }
            if (name.startsWith("get") || name.startsWith("is") || name.startsWith("find")) {
                return MethodRole.ACCESSOR;
            }
            if (name.startsWith("clear") || name.startsWith("reset") || name.startsWith("cleanup")) {
                return MethodRole.CLEANUP;
            }
            return MethodRole.MUTATOR; // Default
        }
    }
    
    private class StateMethodVisitor extends MethodVisitor {
        private StateMethod currentMethod;
        private StatefulBeanAnalysis analysis;
        private String currentClassName;
        
        public StateMethodVisitor(StateMethod method, StatefulBeanAnalysis analysis, String className) {
            super(ASM9);
            this.currentMethod = method;
            this.analysis = analysis;
            this.currentClassName = className;
        }
        
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            // Track field access patterns
            if (owner.equals(currentClassName.replace('.', '/'))) {
                switch (opcode) {
                    case GETFIELD:
                        currentMethod.addReadField(name);
                        analysis.addFieldReader(name, currentMethod.getMethodName());
                        break;
                    case PUTFIELD:
                        currentMethod.addWriteField(name);
                        analysis.addFieldWriter(name, currentMethod.getMethodName());
                        break;
                }
            }
            
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }
    }
    
    // Enums
    public enum ConversationalComplexity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum SpringMigrationStrategy {
        SESSION_SCOPED, REQUEST_SCOPED, CONVERSATION_SCOPED, STATELESS_REFACTOR, MANUAL_REVIEW
    }
    
    public enum StateFieldRole {
        USER_DATA, CALCULATED, CACHE
    }
    
    public enum MethodRole {
        INITIALIZER, MUTATOR, ACCESSOR, CLEANUP
    }
    
    // Analysis data classes
    public static class StatefulBeanAnalysis {
        private String beanClassName;
        private String beanName;
        private List<StateField> stateFields = new ArrayList<>();
        private List<StateMethod> stateMethods = new ArrayList<>();
        private Map<String, Set<String>> stateFlowGraph = new HashMap<>();
        private Map<String, Map<String, Set<String>>> crossMethodDependencies = new HashMap<>();
        private Set<String> workflowPatterns = new HashSet<>();
        private ConversationalComplexity complexity;
        private SpringMigrationStrategy migrationStrategy;
        
        // Getters and setters
        public String getBeanClassName() { return beanClassName; }
        public void setBeanClassName(String beanClassName) { this.beanClassName = beanClassName; }
        
        public String getBeanName() { return beanName; }
        public void setBeanName(String beanName) { this.beanName = beanName; }
        
        public List<StateField> getStateFields() { return stateFields; }
        public void addStateField(StateField field) { this.stateFields.add(field); }
        
        public List<StateMethod> getStateMethods() { return stateMethods; }
        public void addStateMethod(StateMethod method) { this.stateMethods.add(method); }
        
        public Map<String, Set<String>> getStateFlowGraph() { return stateFlowGraph; }
        public void setStateFlowGraph(Map<String, Set<String>> stateFlowGraph) { this.stateFlowGraph = stateFlowGraph; }
        
        public Map<String, Map<String, Set<String>>> getCrossMethodDependencies() { return crossMethodDependencies; }
        
        public void addCrossMethodDependency(String writer, String reader, String fieldName) {
            crossMethodDependencies.computeIfAbsent(writer, k -> new HashMap<>())
                .computeIfAbsent(reader, k -> new HashSet<>())
                .add(fieldName);
        }
        
        public Set<String> getWorkflowPatterns() { return workflowPatterns; }
        public void addWorkflowPattern(String pattern) { this.workflowPatterns.add(pattern); }
        
        public ConversationalComplexity getComplexity() { return complexity; }
        public void setComplexity(ConversationalComplexity complexity) { this.complexity = complexity; }
        
        public SpringMigrationStrategy getMigrationStrategy() { return migrationStrategy; }
        public void setMigrationStrategy(SpringMigrationStrategy migrationStrategy) { this.migrationStrategy = migrationStrategy; }
        
        public void addFieldReader(String fieldName, String methodName) {
            for (StateField field : stateFields) {
                if (field.getFieldName().equals(fieldName)) {
                    field.addReaderMethod(methodName);
                    break;
                }
            }
        }
        
        public void addFieldWriter(String fieldName, String methodName) {
            for (StateField field : stateFields) {
                if (field.getFieldName().equals(fieldName)) {
                    field.addWriterMethod(methodName);
                    break;
                }
            }
        }
    }
    
    public static class StateField {
        private String fieldName;
        private String fieldType;
        private boolean isFinal;
        private boolean isSerializable;
        private boolean isCollection;
        private Set<String> readerMethods = new HashSet<>();
        private Set<String> writerMethods = new HashSet<>();
        private StateFieldRole role;
        
        // Getters and setters
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
        
        public boolean isFinal() { return isFinal; }
        public void setFinal(boolean isFinal) { this.isFinal = isFinal; }
        
        public boolean isSerializable() { return isSerializable; }
        public void setSerializable(boolean isSerializable) { this.isSerializable = isSerializable; }
        
        public boolean isCollection() { return isCollection; }
        public void setCollection(boolean isCollection) { this.isCollection = isCollection; }
        
        public Set<String> getReaderMethods() { return readerMethods; }
        public void addReaderMethod(String method) { this.readerMethods.add(method); }
        
        public Set<String> getWriterMethods() { return writerMethods; }
        public void addWriterMethod(String method) { this.writerMethods.add(method); }
        
        public StateFieldRole getRole() { return role; }
        public void setRole(StateFieldRole role) { this.role = role; }
    }
    
    public static class StateMethod {
        private String methodName;
        private String methodSignature;
        private Set<String> readsFields = new HashSet<>();
        private Set<String> writesFields = new HashSet<>();
        private MethodRole role;
        private boolean isEjbLifecycle;
        
        // Getters and setters
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        
        public String getMethodSignature() { return methodSignature; }
        public void setMethodSignature(String methodSignature) { this.methodSignature = methodSignature; }
        
        public Set<String> getReadsFields() { return readsFields; }
        public void addReadField(String field) { this.readsFields.add(field); }
        
        public Set<String> getWritesFields() { return writesFields; }
        public void addWriteField(String field) { this.writesFields.add(field); }
        
        public MethodRole getRole() { return role; }
        public void setRole(MethodRole role) { this.role = role; }
        
        public boolean isEjbLifecycle() { return isEjbLifecycle; }
        public void setEjbLifecycle(boolean isEjbLifecycle) { this.isEjbLifecycle = isEjbLifecycle; }
    }
}