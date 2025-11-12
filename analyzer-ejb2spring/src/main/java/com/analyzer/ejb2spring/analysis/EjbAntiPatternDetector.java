package com.analyzer.ejb2spring.analysis;

import com.analyzer.dev.analysis.ClassMetadataExtractor.ClassMetadata;
import com.analyzer.dev.analysis.ClassMetadataExtractor.FieldMetadata;
import com.analyzer.dev.analysis.ClassMetadataExtractor.MethodMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects EJB anti-patterns that require special attention during migration.
 * 
 * <p>
 * <b>Token Optimization Strategy</b>: Pre-filter classes to send only
 * problematic ones to AI.
 * Instead of AI analyzing 100 classes (250,000 tokens), this detector
 * identifies
 * the 20 with issues (4,000 tokens to AI).
 * </p>
 * 
 * <p>
 * <b>Detected Anti-Patterns</b>:
 * </p>
 * <ul>
 * <li>Mutable state in stateless beans (violates EJB contract)</li>
 * <li>Factory methods (need @Configuration class)</li>
 * <li>Non-thread-safe patterns (caching, singletons)</li>
 * <li>Improper resource management</li>
 * <li>Direct JNDI lookups (need Spring DI)</li>
 * <li>Thread management (not allowed in EJB)</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class EjbAntiPatternDetector {

    /**
     * Detects anti-patterns in EJB class metadata.
     * 
     * @param metadata class metadata
     * @param ejbType  EJB type (if known)
     * @return list of detected anti-patterns
     */
    public List<AntiPattern> detect(ClassMetadata metadata,
            EjbMetadataEnricher.EjbType ejbType) {
        List<AntiPattern> patterns = new ArrayList<>();

        // Mutable state in stateless beans
        if (ejbType == EjbMetadataEnricher.EjbType.STATELESS_SESSION_BEAN) {
            detectMutableState(metadata, patterns);
        }

        // Factory patterns
        detectFactoryPattern(metadata, patterns);

        // Non-thread-safe patterns
        detectThreadSafetyIssues(metadata, patterns);

        // Resource management issues
        detectResourceManagementIssues(metadata, patterns);

        // JNDI lookups
        detectJndiLookups(metadata, patterns);

        // Thread management
        detectThreadManagement(metadata, patterns);

        return patterns;
    }

    private void detectMutableState(ClassMetadata metadata, List<AntiPattern> patterns) {
        for (FieldMetadata field : metadata.getFields()) {
            // Mutable fields in stateless beans are problematic
            if (!field.isFinal() && !field.isStatic() && !isAllowedMutableField(field)) {
                patterns.add(new AntiPattern(
                        AntiPatternType.MUTABLE_STATE_IN_STATELESS_BEAN,
                        "Field '" + field.getName() + "' is mutable in stateless bean",
                        Severity.HIGH,
                        "field:" + field.getName(),
                        "Convert to constructor injection or make final"));
            }
        }
    }

    private void detectFactoryPattern(ClassMetadata metadata, List<AntiPattern> patterns) {
        for (MethodMetadata method : metadata.getMethods()) {
            String name = method.getName().toLowerCase();

            // Factory method patterns
            if ((name.startsWith("create") || name.startsWith("get") || name.startsWith("build"))
                    && method.isStatic()
                    && !method.getReturnType().equals("void")) {
                patterns.add(new AntiPattern(
                        AntiPatternType.FACTORY_METHOD,
                        "Static factory method '" + method.getName() + "'",
                        Severity.MEDIUM,
                        "method:" + method.getName(),
                        "Move to @Configuration class with @Bean methods"));
            }
        }
    }

    private void detectThreadSafetyIssues(ClassMetadata metadata, List<AntiPattern> patterns) {
        for (FieldMetadata field : metadata.getFields()) {
            String type = field.getType();

            // Collections without proper synchronization
            if ((type.contains("HashMap") || type.contains("ArrayList") || type.contains("HashSet"))
                    && !field.isFinal()
                    && !field.getAnnotations().contains("Inject")) {
                patterns.add(new AntiPattern(
                        AntiPatternType.NON_THREAD_SAFE_COLLECTION,
                        "Non-thread-safe collection '" + field.getName() + "'",
                        Severity.HIGH,
                        "field:" + field.getName(),
                        "Use ConcurrentHashMap or make thread-safe"));
            }

            // Caching in stateless beans
            if (type.contains("Cache") || field.getName().toLowerCase().contains("cache")) {
                patterns.add(new AntiPattern(
                        AntiPatternType.CACHING_IN_STATELESS_BEAN,
                        "Caching field '" + field.getName() + "' in stateless bean",
                        Severity.HIGH,
                        "field:" + field.getName(),
                        "Use Spring Cache abstraction with proper scope"));
            }
        }
    }

    private void detectResourceManagementIssues(ClassMetadata metadata, List<AntiPattern> patterns) {
        // Check for manual resource management
        for (MethodMetadata method : metadata.getMethods()) {
            String name = method.getName();

            // Manual connection management
            if (name.contains("Connection") || name.contains("Statement") || name.contains("ResultSet")) {
                patterns.add(new AntiPattern(
                        AntiPatternType.MANUAL_RESOURCE_MANAGEMENT,
                        "Manual resource management in '" + name + "'",
                        Severity.MEDIUM,
                        "method:" + name,
                        "Use Spring's JdbcTemplate or proper resource management"));
            }
        }

        // Fields that should be injected
        for (FieldMetadata field : metadata.getFields()) {
            if (field.getType().contains("DataSource") && !hasResourceAnnotation(field)) {
                patterns.add(new AntiPattern(
                        AntiPatternType.UNINJECTED_RESOURCE,
                        "DataSource field '" + field.getName() + "' not injected",
                        Severity.MEDIUM,
                        "field:" + field.getName(),
                        "Use @Autowired to inject DataSource"));
            }
        }
    }

    private void detectJndiLookups(ClassMetadata metadata, List<AntiPattern> patterns) {
        // Check imports for JNDI usage
        if (metadata.getImports().stream()
                .anyMatch(imp -> imp.contains("javax.naming") || imp.contains("InitialContext"))) {
            patterns.add(new AntiPattern(
                    AntiPatternType.JNDI_LOOKUP,
                    "Class uses JNDI lookups",
                    Severity.HIGH,
                    "class",
                    "Replace with Spring dependency injection"));
        }

        // Check for JNDI method patterns
        for (MethodMetadata method : metadata.getMethods()) {
            String name = method.getName().toLowerCase();
            if (name.contains("lookup") || name.contains("jndi")) {
                patterns.add(new AntiPattern(
                        AntiPatternType.JNDI_LOOKUP,
                        "JNDI lookup method '" + method.getName() + "'",
                        Severity.HIGH,
                        "method:" + method.getName(),
                        "Replace with Spring dependency injection"));
            }
        }
    }

    private void detectThreadManagement(ClassMetadata metadata, List<AntiPattern> patterns) {
        // Check for thread-related imports
        if (metadata.getImports().stream()
                .anyMatch(imp -> imp.contains("java.util.concurrent")
                        || imp.contains("Thread")
                        || imp.contains("Executor"))) {
            patterns.add(new AntiPattern(
                    AntiPatternType.THREAD_MANAGEMENT,
                    "Class manages threads (not allowed in EJB)",
                    Severity.HIGH,
                    "class",
                    "Use Spring's @Async or proper thread pool management"));
        }
    }

    private boolean isAllowedMutableField(FieldMetadata field) {
        // Logger fields are OK
        if (field.getType().contains("Logger")) {
            return true;
        }

        // CDI Event sources are OK (they're proxies)
        if (field.getAnnotations().contains("Inject") && field.getType().contains("Event")) {
            return true;
        }

        // EntityManager is OK in stateless beans
        if (field.getType().contains("EntityManager")) {
            return true;
        }

        return false;
    }

    private boolean hasResourceAnnotation(FieldMetadata field) {
        return field.getAnnotations().contains("Resource")
                || field.getAnnotations().contains("EJB")
                || field.getAnnotations().contains("Inject")
                || field.getAnnotations().contains("Autowired");
    }

    /**
     * Anti-pattern detection result.
     */
    public static class AntiPattern {
        private final AntiPatternType type;
        private final String description;
        private final Severity severity;
        private final String location;
        private final String recommendation;

        public AntiPattern(AntiPatternType type, String description, Severity severity,
                String location, String recommendation) {
            this.type = type;
            this.description = description;
            this.severity = severity;
            this.location = location;
            this.recommendation = recommendation;
        }

        public AntiPatternType getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getLocation() {
            return location;
        }

        public String getRecommendation() {
            return recommendation;
        }
    }

    /**
     * Types of anti-patterns.
     */
    public enum AntiPatternType {
        MUTABLE_STATE_IN_STATELESS_BEAN,
        FACTORY_METHOD,
        NON_THREAD_SAFE_COLLECTION,
        CACHING_IN_STATELESS_BEAN,
        MANUAL_RESOURCE_MANAGEMENT,
        UNINJECTED_RESOURCE,
        JNDI_LOOKUP,
        THREAD_MANAGEMENT
    }

    /**
     * Severity levels.
     */
    public enum Severity {
        LOW, MEDIUM, HIGH
    }
}
