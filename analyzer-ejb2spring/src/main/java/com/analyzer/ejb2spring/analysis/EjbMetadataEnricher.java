package com.analyzer.ejb2spring.analysis;

import com.analyzer.api.graph.GraphNode;
import com.analyzer.dev.analysis.ClassMetadataExtractor.ClassMetadata;
import com.analyzer.dev.analysis.ClassMetadataExtractor.FieldMetadata;
import com.analyzer.dev.analysis.ClassMetadataExtractor.MethodMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enriches generic Java class metadata with EJB-specific analysis.
 * 
 * <p>
 * This analyzer adds EJB-specific context to help AI make migration decisions:
 * </p>
 * <ul>
 * <li>Identifies EJB types (Stateless, Stateful, Entity, MDB)</li>
 * <li>Detects dependency injection patterns (@EJB, @Resource)</li>
 * <li>Identifies transaction attributes</li>
 * <li>Flags security annotations</li>
 * <li>Detects lifecycle methods</li>
 * </ul>
 * 
 * <p>
 * <b>Usage</b>: After extracting generic metadata with ClassMetadataExtractor,
 * pass it to this enricher to add EJB-specific insights.
 * </p>
 * 
 * @since 1.0.0
 */
public class EjbMetadataEnricher {

    private static final Logger logger = LoggerFactory.getLogger(EjbMetadataEnricher.class);

    /**
     * Enriches class metadata with EJB-specific analysis from parsed source.
     * 
     * @param metadata generic class metadata
     * @return enriched metadata
     */
    public EnrichedEjbMetadata enrich(ClassMetadata metadata) {
        EnrichedEjbMetadata enriched = new EnrichedEjbMetadata();
        enriched.setBaseMetadata(metadata);

        // Identify EJB type
        identifyEjbType(metadata, enriched);

        // Analyze dependencies
        analyzeDependencies(metadata, enriched);

        // Identify transaction methods
        identifyTransactionalMethods(metadata, enriched);

        // Detect security annotations
        detectSecurityAnnotations(metadata, enriched);

        // Identify lifecycle methods
        identifyLifecycleMethods(metadata, enriched);

        // Calculate migration complexity
        calculateMigrationComplexity(enriched);

        return enriched;
    }

    /**
     * Enriches metadata from GraphNode (database-backed approach).
     * 
     * <p>
     * <b>Performance Optimization</b>: Instead of re-parsing 4M LOC, reads cached
     * analysis from H2 graph database. This is significantly faster for large
     * codebases.
     * </p>
     * 
     * <p>
     * Use this method when:
     * </p>
     * <ul>
     * <li>GraphNode already contains analysis data (tags, properties)</li>
     * <li>Source files haven't changed since analysis</li>
     * <li>Fast migration execution is priority</li>
     * </ul>
     * 
     * @param node GraphNode from H2 database (from GRAPH_QUERY)
     * @return enriched metadata
     */
    public EnrichedEjbMetadata enrichFromGraphNode(GraphNode node) {
        EnrichedEjbMetadata enriched = new EnrichedEjbMetadata();

        // Create ClassMetadata from GraphNode properties
        ClassMetadata metadata = reconstructMetadataFromNode(node);
        enriched.setBaseMetadata(metadata);

        // Identify EJB type from tags
        identifyEjbTypeFromTags(node, enriched);

        // Extract dependencies from node properties
        extractDependenciesFromNode(node, enriched);

        // Extract other cached analysis
        extractTransactionalMethodsFromNode(node, enriched);
        extractSecurityAnnotationsFromNode(node, enriched);
        extractLifecycleMethodsFromNode(node, enriched);

        // Calculate or read complexity
        extractComplexityFromNode(node, enriched);

        logger.debug("Enriched metadata from GraphNode: {}", node.getId());
        return enriched;
    }

    private ClassMetadata reconstructMetadataFromNode(GraphNode node) {
        ClassMetadata metadata = new ClassMetadata();

        // Basic info from node properties
        metadata.setFullyQualifiedName(node.getStringProperty("fullyQualifiedName", node.getId()));
        metadata.setSimpleName(node.getStringProperty("simpleName", ""));
        metadata.setPackageName(node.getStringProperty("packageName", ""));

        // Try to read cached metadata from node properties
        // This assumes inspectors stored metadata as JSON in node.properties
        if (node.hasProperty("class.metadata")) {
            // Metadata was cached - use it
            logger.debug("Using cached metadata from node");
        } else {
            logger.debug("No cached metadata - basic info only");
        }

        return metadata;
    }

    private void identifyEjbTypeFromTags(GraphNode node, EnrichedEjbMetadata enriched) {
        // Read from node tags (much faster than parsing)
        if (node.hasTag("ejb.stateless.session_bean")) {
            enriched.setEjbType(EjbType.STATELESS_SESSION_BEAN);
        } else if (node.hasTag("ejb.stateful.session_bean")) {
            enriched.setEjbType(EjbType.STATEFUL_SESSION_BEAN);
        } else if (node.hasTag("ejb.singleton.session_bean")) {
            enriched.setEjbType(EjbType.SINGLETON_SESSION_BEAN);
        } else if (node.hasTag("ejb.message_driven_bean")) {
            enriched.setEjbType(EjbType.MESSAGE_DRIVEN_BEAN);
        } else if (node.hasTag("ejb.entity_bean")) {
            enriched.setEjbType(EjbType.ENTITY_BEAN);
        }
    }

    private void extractDependenciesFromNode(GraphNode node, EnrichedEjbMetadata enriched) {
        // Read from node properties if available
        if (node.hasProperty("ejb.dependencies")) {
            @SuppressWarnings("unchecked")
            List<String> deps = (List<String>) node.getProperty("ejb.dependencies");
            enriched.setEjbDependencies(deps != null ? deps : new ArrayList<>());
        }

        if (node.hasProperty("resource.dependencies")) {
            @SuppressWarnings("unchecked")
            List<String> deps = (List<String>) node.getProperty("resource.dependencies");
            enriched.setResourceDependencies(deps != null ? deps : new ArrayList<>());
        }

        enriched.setDependencyCount(
                enriched.getEjbDependencies().size() + enriched.getResourceDependencies().size());
    }

    private void extractTransactionalMethodsFromNode(GraphNode node, EnrichedEjbMetadata enriched) {
        if (node.hasProperty("transactional.methods")) {
            @SuppressWarnings("unchecked")
            List<String> methods = (List<String>) node.getProperty("transactional.methods");
            enriched.setTransactionalMethods(methods != null ? methods : new ArrayList<>());
        }
    }

    private void extractSecurityAnnotationsFromNode(GraphNode node, EnrichedEjbMetadata enriched) {
        if (node.hasProperty("security.annotations")) {
            @SuppressWarnings("unchecked")
            Map<String, String> security = (Map<String, String>) node.getProperty("security.annotations");
            enriched.setSecurityAnnotations(security != null ? security : new HashMap<>());
        }
    }

    private void extractLifecycleMethodsFromNode(GraphNode node, EnrichedEjbMetadata enriched) {
        if (node.hasProperty("lifecycle.methods")) {
            @SuppressWarnings("unchecked")
            List<String> methods = (List<String>) node.getProperty("lifecycle.methods");
            enriched.setLifecycleMethods(methods != null ? methods : new ArrayList<>());
        }
    }

    private void extractComplexityFromNode(GraphNode node, EnrichedEjbMetadata enriched) {
        if (node.hasProperty("migration.complexity")) {
            String complexity = node.getStringProperty("migration.complexity", "MEDIUM");
            enriched.setMigrationComplexity(MigrationComplexity.valueOf(complexity));
        } else {
            // Calculate if not cached
            calculateMigrationComplexity(enriched);
        }
    }

    private void identifyEjbType(ClassMetadata metadata, EnrichedEjbMetadata enriched) {
        List<String> annotations = metadata.getAnnotations();

        if (annotations.contains("Stateless")) {
            enriched.setEjbType(EjbType.STATELESS_SESSION_BEAN);
        } else if (annotations.contains("Stateful")) {
            enriched.setEjbType(EjbType.STATEFUL_SESSION_BEAN);
        } else if (annotations.contains("Singleton")) {
            enriched.setEjbType(EjbType.SINGLETON_SESSION_BEAN);
        } else if (annotations.contains("MessageDriven")) {
            enriched.setEjbType(EjbType.MESSAGE_DRIVEN_BEAN);
        } else if (annotations.contains("Entity")) {
            // Check if it's EJB 2.x Entity bean (different from JPA @Entity)
            boolean hasEjbEntityMethods = metadata.getMethods().stream()
                    .anyMatch(m -> m.getName().startsWith("ejbCreate")
                            || m.getName().startsWith("ejbRemove")
                            || m.getName().startsWith("ejbActivate"));
            if (hasEjbEntityMethods) {
                enriched.setEjbType(EjbType.ENTITY_BEAN);
            }
        }

        // Check for EJB interfaces
        List<String> interfaces = metadata.getImplementedInterfaces();
        if (interfaces.contains("SessionBean")) {
            enriched.setHasSessionBeanInterface(true);
        }
        if (interfaces.contains("EntityBean")) {
            enriched.setHasEntityBeanInterface(true);
        }
        if (interfaces.contains("MessageDrivenBean")) {
            enriched.setHasMessageDrivenBeanInterface(true);
        }
    }

    private void analyzeDependencies(ClassMetadata metadata, EnrichedEjbMetadata enriched) {
        List<String> ejbDependencies = new ArrayList<>();
        List<String> resourceDependencies = new ArrayList<>();

        for (FieldMetadata field : metadata.getFields()) {
            if (field.getAnnotations().contains("EJB")) {
                ejbDependencies.add(field.getName() + ":" + field.getType());
            }
            if (field.getAnnotations().contains("Resource")) {
                resourceDependencies.add(field.getName() + ":" + field.getType());
            }
        }

        enriched.setEjbDependencies(ejbDependencies);
        enriched.setResourceDependencies(resourceDependencies);
        enriched.setDependencyCount(ejbDependencies.size() + resourceDependencies.size());
    }

    private void identifyTransactionalMethods(ClassMetadata metadata, EnrichedEjbMetadata enriched) {
        List<String> transactionalMethods = new ArrayList<>();

        for (MethodMetadata method : metadata.getMethods()) {
            // Methods with @TransactionAttribute
            if (method.getAnnotations().contains("TransactionAttribute")) {
                transactionalMethods.add(method.getName());
            }
            // Heuristic: public methods that mutate data are transactional
            else if (method.isPublic() && isMutatingMethod(method.getName())) {
                transactionalMethods.add(method.getName());
            }
        }

        enriched.setTransactionalMethods(transactionalMethods);
    }

    private void detectSecurityAnnotations(ClassMetadata metadata, EnrichedEjbMetadata enriched) {
        Map<String, String> securityAnnotations = new HashMap<>();

        // Class-level security
        for (String annotation : metadata.getAnnotations()) {
            if (isSecurityAnnotation(annotation)) {
                securityAnnotations.put("class", annotation);
            }
        }

        // Method-level security
        for (MethodMetadata method : metadata.getMethods()) {
            for (String annotation : method.getAnnotations()) {
                if (isSecurityAnnotation(annotation)) {
                    securityAnnotations.put(method.getName(), annotation);
                }
            }
        }

        enriched.setSecurityAnnotations(securityAnnotations);
    }

    private void identifyLifecycleMethods(ClassMetadata metadata, EnrichedEjbMetadata enriched) {
        List<String> lifecycleMethods = new ArrayList<>();

        for (MethodMetadata method : metadata.getMethods()) {
            String name = method.getName();
            List<String> annotations = method.getAnnotations();

            // Annotated lifecycle methods
            if (annotations.contains("PostConstruct")
                    || annotations.contains("PreDestroy")
                    || annotations.contains("PostActivate")
                    || annotations.contains("PrePassivate")) {
                lifecycleMethods.add(name);
            }

            // EJB 2.x lifecycle methods (by convention)
            if (name.equals("ejbCreate") || name.equals("ejbRemove")
                    || name.equals("ejbActivate") || name.equals("ejbPassivate")
                    || name.equals("ejbLoad") || name.equals("ejbStore")
                    || name.equals("setSessionContext") || name.equals("setEntityContext")
                    || name.equals("setMessageDrivenContext")) {
                lifecycleMethods.add(name);
            }
        }

        enriched.setLifecycleMethods(lifecycleMethods);
    }

    private void calculateMigrationComplexity(EnrichedEjbMetadata enriched) {
        int complexityScore = 0;

        // EJB type complexity
        switch (enriched.getEjbType()) {
            case STATELESS_SESSION_BEAN:
                complexityScore += 1; // Simple
                break;
            case STATEFUL_SESSION_BEAN:
                complexityScore += 3; // Complex (state management)
                break;
            case ENTITY_BEAN:
                complexityScore += 4; // Very complex (persistence)
                break;
            case MESSAGE_DRIVEN_BEAN:
                complexityScore += 2; // Moderate (JMS)
                break;
            case SINGLETON_SESSION_BEAN:
                complexityScore += 2; // Moderate (concurrency)
                break;
        }

        // Dependency complexity
        complexityScore += enriched.getDependencyCount();

        // Security adds complexity
        if (!enriched.getSecurityAnnotations().isEmpty()) {
            complexityScore += 1;
        }

        // Lifecycle methods add complexity
        complexityScore += enriched.getLifecycleMethods().size();

        // Determine complexity level
        if (complexityScore <= 3) {
            enriched.setMigrationComplexity(MigrationComplexity.LOW);
        } else if (complexityScore <= 7) {
            enriched.setMigrationComplexity(MigrationComplexity.MEDIUM);
        } else {
            enriched.setMigrationComplexity(MigrationComplexity.HIGH);
        }
    }

    private boolean isMutatingMethod(String methodName) {
        String lower = methodName.toLowerCase();
        return lower.startsWith("save") || lower.startsWith("update")
                || lower.startsWith("delete") || lower.startsWith("create")
                || lower.startsWith("remove") || lower.startsWith("persist")
                || lower.startsWith("merge") || lower.startsWith("insert");
    }

    private boolean isSecurityAnnotation(String annotation) {
        return annotation.equals("RolesAllowed")
                || annotation.equals("PermitAll")
                || annotation.equals("DenyAll")
                || annotation.equals("DeclareRoles")
                || annotation.equals("RunAs");
    }

    /**
     * Enriched metadata with EJB-specific analysis.
     */
    public static class EnrichedEjbMetadata {
        private ClassMetadata baseMetadata;
        private EjbType ejbType;
        private boolean hasSessionBeanInterface;
        private boolean hasEntityBeanInterface;
        private boolean hasMessageDrivenBeanInterface;
        private List<String> ejbDependencies = new ArrayList<>();
        private List<String> resourceDependencies = new ArrayList<>();
        private int dependencyCount;
        private List<String> transactionalMethods = new ArrayList<>();
        private Map<String, String> securityAnnotations = new HashMap<>();
        private List<String> lifecycleMethods = new ArrayList<>();
        private MigrationComplexity migrationComplexity;

        // Getters and setters
        public ClassMetadata getBaseMetadata() {
            return baseMetadata;
        }

        public void setBaseMetadata(ClassMetadata baseMetadata) {
            this.baseMetadata = baseMetadata;
        }

        public EjbType getEjbType() {
            return ejbType;
        }

        public void setEjbType(EjbType ejbType) {
            this.ejbType = ejbType;
        }

        public boolean isHasSessionBeanInterface() {
            return hasSessionBeanInterface;
        }

        public void setHasSessionBeanInterface(boolean hasSessionBeanInterface) {
            this.hasSessionBeanInterface = hasSessionBeanInterface;
        }

        public boolean isHasEntityBeanInterface() {
            return hasEntityBeanInterface;
        }

        public void setHasEntityBeanInterface(boolean hasEntityBeanInterface) {
            this.hasEntityBeanInterface = hasEntityBeanInterface;
        }

        public boolean isHasMessageDrivenBeanInterface() {
            return hasMessageDrivenBeanInterface;
        }

        public void setHasMessageDrivenBeanInterface(boolean hasMessageDrivenBeanInterface) {
            this.hasMessageDrivenBeanInterface = hasMessageDrivenBeanInterface;
        }

        public List<String> getEjbDependencies() {
            return ejbDependencies;
        }

        public void setEjbDependencies(List<String> ejbDependencies) {
            this.ejbDependencies = ejbDependencies;
        }

        public List<String> getResourceDependencies() {
            return resourceDependencies;
        }

        public void setResourceDependencies(List<String> resourceDependencies) {
            this.resourceDependencies = resourceDependencies;
        }

        public int getDependencyCount() {
            return dependencyCount;
        }

        public void setDependencyCount(int dependencyCount) {
            this.dependencyCount = dependencyCount;
        }

        public List<String> getTransactionalMethods() {
            return transactionalMethods;
        }

        public void setTransactionalMethods(List<String> transactionalMethods) {
            this.transactionalMethods = transactionalMethods;
        }

        public Map<String, String> getSecurityAnnotations() {
            return securityAnnotations;
        }

        public void setSecurityAnnotations(Map<String, String> securityAnnotations) {
            this.securityAnnotations = securityAnnotations;
        }

        public List<String> getLifecycleMethods() {
            return lifecycleMethods;
        }

        public void setLifecycleMethods(List<String> lifecycleMethods) {
            this.lifecycleMethods = lifecycleMethods;
        }

        public MigrationComplexity getMigrationComplexity() {
            return migrationComplexity;
        }

        public void setMigrationComplexity(MigrationComplexity migrationComplexity) {
            this.migrationComplexity = migrationComplexity;
        }
    }

    /**
     * EJB types.
     */
    public enum EjbType {
        STATELESS_SESSION_BEAN,
        STATEFUL_SESSION_BEAN,
        SINGLETON_SESSION_BEAN,
        ENTITY_BEAN,
        MESSAGE_DRIVEN_BEAN,
        UNKNOWN
    }

    /**
     * Migration complexity levels.
     */
    public enum MigrationComplexity {
        LOW, MEDIUM, HIGH
    }
}
