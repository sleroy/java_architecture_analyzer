package com.analyzer.rules.ejb2spring;

import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.api.resource.ResourceResolver;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects classes that contain only constants and are candidates for moving to
 * Spring Boot's application.yml configuration.
 *
 * <p>
 * Configuration Constant patterns typically include:
 * </p>
 * <ul>
 * <li>Classes with "Constants", "Config", "Properties", or "Settings" in their
 * name</li>
 * <li>Classes with only static final fields</li>
 * <li>Classes with a high percentage of literal constants</li>
 * <li>Classes that define configuration paths, URLs, timeouts, etc.</li>
 * </ul>
 *
 * <p>
 * Migration Target: Spring Boot application.yml and @ConfigurationProperties
 * </p>
 *
 * @see <a href=
 * "https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config">Spring
 * Boot External Configuration</a>
 */
@InspectorDependencies(requires = InspectorTags.TAG_JAVA_IS_SOURCE, produces = {
        EjbMigrationTags.TAG_SPRING_CONFIG_CONVERSION,
        EjbMigrationTags.TAG_CODE_MODERNIZATION,
})
public class ConfigurationConstantsInspector extends AbstractJavaClassInspector {

    private static final List<String> CONFIG_CLASS_NAME_PATTERNS = Arrays.asList(
            "Constants", "Config", "Properties", "Settings", "Params", "Options",
            "Configuration", "Parameter", "Property");

    private static final List<String> CONFIG_FIELD_NAME_PATTERNS = Arrays.asList(
            "URL", "HOST", "PORT", "PATH", "ENDPOINT", "TIMEOUT", "SIZE", "MAX", "MIN",
            "DEFAULT", "ENABLED", "DISABLED", "MODE", "FORMAT", "SERVER", "SERVICE",
            "CONNECTION", "RETRY", "BUFFER", "CACHE", "THRESHOLD");

    @Inject
    public ConfigurationConstantsInspector(final ResourceResolver resourceResolver, final ClassNodeRepository classNodeRepository, final LocalCache localCache) {
        super(resourceResolver, classNodeRepository, localCache);
    }

    @Override
    public String getName() {
        return "Configuration Constants Detector";
    }

    @Override
    protected void analyzeClass(final ProjectFile projectFile, final JavaClassNode classNode, final TypeDeclaration<?> type,
                                final NodeDecorator<ProjectFile> nodeDecorator) {

        if (!(type instanceof final ClassOrInterfaceDeclaration classDecl)) {
            return;
        }

        final ConfigConstantsDetector detector = new ConfigConstantsDetector();
        type.accept(detector, null);

        if (detector.isConfigConstants()) {
            final ConfigConstantsInfo info = detector.getConfigConstantsInfo();

            // Set tags according to the produces contract
            nodeDecorator.enableTag(EjbMigrationTags.TAG_SPRING_CONFIG_CONVERSION);
            nodeDecorator.enableTag(EjbMigrationTags.TAG_CODE_MODERNIZATION);
            nodeDecorator.getMetrics().setMaxMetric(EjbMigrationTags.TAG_METRIC_MIGRATION_COMPLEXITY, EjbMigrationTags.COMPLEXITY_LOW);

            // Set custom tags for more detailed analysis
            nodeDecorator.setProperty("config.constants.detected", true);
            nodeDecorator.setProperty("spring.target.application_yml", true);

            if (0.5 < info.configRelatedNamePercentage) {
                nodeDecorator.setProperty("config.naming.consistent", true);
            }

            if (5 < info.stringConstantsCount) {
                nodeDecorator.setProperty("config.string_heavy", true);
            }

            if (0 < info.nestedConstantCount) {
                nodeDecorator.setProperty("config.hierarchical", true);
            }

            // Set property on class node for detailed analysis
            classNode.setProperty("config.constants.analysis", info);

            // Set analysis statistics
            nodeDecorator.setMetric("config.constants.total", info.totalConstantsCount);
            nodeDecorator.setMetric("config.constants.strings", info.stringConstantsCount);
            nodeDecorator.setMetric("config.constants.numeric", info.numericConstantsCount);
            nodeDecorator.setMetric("config.constants.boolean", info.booleanConstantsCount);
            nodeDecorator.setMetric("config.constants.nested", info.nestedConstantCount);
        }
    }

    /**
     * Visitor that detects Configuration Constants classes by analyzing the AST.
     */
    private static class ConfigConstantsDetector extends VoidVisitorAdapter<Void> {
        private final ConfigConstantsInfo info = new ConfigConstantsInfo();
        private boolean isConfigConstants;
        private int totalFieldCount;
        private int nonConstantFieldCount;
        private int methodCount;
        private int configRelatedNameCount;

        public boolean isConfigConstants() {
            return isConfigConstants;
        }

        public ConfigConstantsInfo getConfigConstantsInfo() {
            return info;
        }

        @Override
        public void visit(final ClassOrInterfaceDeclaration classDecl, final Void arg) {
            super.visit(classDecl, arg);

            // Check if class name indicates a config/constants class
            final String className = classDecl.getNameAsString();
            for (final String pattern : CONFIG_CLASS_NAME_PATTERNS) {
                if (className.contains(pattern)) {
                    info.hasConfigNamePattern = true;
                    break;
                }
            }

            // Analyze the results
            if (0 < totalFieldCount) {
                // Calculate constants percentage
                final double constantPercentage = (totalFieldCount - nonConstantFieldCount) / (double) totalFieldCount;
                info.constantPercentage = constantPercentage;

                // Calculate config-related name percentage
                info.configRelatedNamePercentage = configRelatedNameCount / (double) totalFieldCount;

                // Determine if this is a configuration constants class
                if ((0.8 <= constantPercentage && 3 <= totalFieldCount) ||
                        (info.hasConfigNamePattern && 0.5 <= constantPercentage) ||
                        (0.4 < info.configRelatedNamePercentage && 0.7 <= constantPercentage)) {
                    isConfigConstants = true;
                }
            }
        }

        @Override
        public void visit(final FieldDeclaration field, final Void arg) {
            super.visit(field, arg);

            for (final VariableDeclarator var : field.getVariables()) {
                totalFieldCount++;

                // Check if it's a constant (static final)
                final boolean isConstant = field.isStatic() && field.isFinal();
                if (!isConstant) {
                    nonConstantFieldCount++;
                    continue;
                }

                final String fieldName = var.getNameAsString();

                // Check if field name is config-related
                for (final String pattern : CONFIG_FIELD_NAME_PATTERNS) {
                    if (fieldName.toUpperCase().contains(pattern)) {
                        configRelatedNameCount++;
                        info.configFields.add(fieldName);
                        break;
                    }
                }

                // Analyze constant value if present
                if (var.getInitializer().isPresent()) {
                    final Expression expr = var.getInitializer().get();

                    // Count different types of constants
                    info.totalConstantsCount++;

                    if (expr instanceof StringLiteralExpr) {
                        info.stringConstantsCount++;
                        // Check for nested config paths in strings (e.g., "app.config.timeout")
                        final String value = ((StringLiteralExpr) expr).getValue();
                        if (value.contains(".") && !value.startsWith("http") && !value.startsWith("jdbc")) {
                            final String[] parts = value.split("\\.");
                            if (2 < parts.length) {
                                info.nestedConstantCount++;
                                info.nestedPaths.add(value);
                            }
                        }
                    } else if (expr instanceof LiteralExpr) {
                        final String exprStr = expr.toString().toLowerCase();
                        if ("true".equals(exprStr) || "false".equals(exprStr)) {
                            info.booleanConstantsCount++;
                        } else if (exprStr.matches("\\d+(\\.\\d+)?[lfd]?")) {
                            info.numericConstantsCount++;
                        }
                    }
                }
            }
        }

        @Override
        public void visit(final MethodDeclaration method, final Void arg) {
            super.visit(method, arg);
            methodCount++;

            // Check if this is a utility method to get configuration
            final String methodName = method.getNameAsString().toLowerCase();
            if (methodName.startsWith("get") || methodName.startsWith("is") ||
                    methodName.contains("config") || methodName.contains("property")) {
                info.configAccessMethodCount++;
            }
        }
    }

    /**
     * Data class to hold Configuration Constants pattern analysis information
     */
    public static class ConfigConstantsInfo {
        public int totalConstantsCount;
        public int stringConstantsCount;
        public int numericConstantsCount;
        public int booleanConstantsCount;
        public int nestedConstantCount;
        public int configAccessMethodCount;
        public double constantPercentage;
        public double configRelatedNamePercentage;
        public boolean hasConfigNamePattern;
        public List<String> configFields = new ArrayList<>();
        public List<String> nestedPaths = new ArrayList<>();

        @Override
        public String toString() {
            return String.format(
                    "ConfigConstants{totalConstants=%d, stringConstants=%d, numericConstants=%d, " +
                            "booleanConstants=%d, nestedPaths=%d, constantPercentage=%.2f, " +
                            "configNamePercentage=%.2f, hasConfigName=%s, paths=%s}",
                    totalConstantsCount,
                    stringConstantsCount,
                    numericConstantsCount,
                    booleanConstantsCount,
                    nestedConstantCount,
                    constantPercentage,
                    configRelatedNamePercentage,
                    hasConfigNamePattern,
                    nestedPaths.toString());
        }
    }
}
