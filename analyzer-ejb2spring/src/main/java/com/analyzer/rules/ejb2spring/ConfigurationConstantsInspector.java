package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.analyzer.api.resource.ResourceResolver;
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
@InspectorDependencies(requires = {InspectorTags.TAG_JAVA_IS_SOURCE}, produces = {
        EjbMigrationTags.SPRING_CONFIG_CONVERSION,
        EjbMigrationTags.CODE_MODERNIZATION,
        EjbMigrationTags.MIGRATION_COMPLEXITY_LOW
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
    public ConfigurationConstantsInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classNodeRepository);
    }

    @Override
    public String getName() {
        return "Configuration Constants Detector";
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                NodeDecorator<ProjectFile> nodeDecorator) {

        if (!(type instanceof ClassOrInterfaceDeclaration)) {
            return;
        }

        ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) type;
        ConfigConstantsDetector detector = new ConfigConstantsDetector();
        type.accept(detector, null);

        if (detector.isConfigConstants()) {
            ConfigConstantsInfo info = detector.getConfigConstantsInfo();

            // Set tags according to the produces contract
            nodeDecorator.setProperty(EjbMigrationTags.SPRING_CONFIG_CONVERSION, true);
            nodeDecorator.setProperty(EjbMigrationTags.CODE_MODERNIZATION, true);
            nodeDecorator.setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_LOW, true);

            // Set custom tags for more detailed analysis
            nodeDecorator.setProperty("config.constants.detected", true);
            nodeDecorator.setProperty("spring.target.application_yml", true);

            if (info.configRelatedNamePercentage > 0.5) {
                nodeDecorator.setProperty("config.naming.consistent", true);
            }

            if (info.stringConstantsCount > 5) {
                nodeDecorator.setProperty("config.string_heavy", true);
            }

            if (info.nestedConstantCount > 0) {
                nodeDecorator.setProperty("config.hierarchical", true);
            }

            // Set property on class node for detailed analysis
            classNode.setProperty("config.constants.analysis", info.toString());

            // Set analysis statistics
            nodeDecorator.setProperty("config.constants.total", info.totalConstantsCount);
            nodeDecorator.setProperty("config.constants.strings", info.stringConstantsCount);
            nodeDecorator.setProperty("config.constants.numeric", info.numericConstantsCount);
            nodeDecorator.setProperty("config.constants.boolean", info.booleanConstantsCount);
            nodeDecorator.setProperty("config.constants.nested", info.nestedConstantCount);
        }
    }

    /**
     * Visitor that detects Configuration Constants classes by analyzing the AST.
     */
    private static class ConfigConstantsDetector extends VoidVisitorAdapter<Void> {
        private final ConfigConstantsInfo info = new ConfigConstantsInfo();
        private boolean isConfigConstants = false;
        private int totalFieldCount = 0;
        private int nonConstantFieldCount = 0;
        private int methodCount = 0;
        private int configRelatedNameCount = 0;

        public boolean isConfigConstants() {
            return isConfigConstants;
        }

        public ConfigConstantsInfo getConfigConstantsInfo() {
            return info;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            super.visit(classDecl, arg);

            // Check if class name indicates a config/constants class
            String className = classDecl.getNameAsString();
            for (String pattern : CONFIG_CLASS_NAME_PATTERNS) {
                if (className.contains(pattern)) {
                    info.hasConfigNamePattern = true;
                    break;
                }
            }

            // Analyze the results
            if (totalFieldCount > 0) {
                // Calculate constants percentage
                double constantPercentage = (totalFieldCount - nonConstantFieldCount) / (double) totalFieldCount;
                info.constantPercentage = constantPercentage;

                // Calculate config-related name percentage
                info.configRelatedNamePercentage = configRelatedNameCount / (double) totalFieldCount;

                // Determine if this is a configuration constants class
                if ((constantPercentage >= 0.8 && totalFieldCount >= 3) ||
                        (info.hasConfigNamePattern && constantPercentage >= 0.5) ||
                        (info.configRelatedNamePercentage > 0.4 && constantPercentage >= 0.7)) {
                    isConfigConstants = true;
                }
            }
        }

        @Override
        public void visit(FieldDeclaration field, Void arg) {
            super.visit(field, arg);

            for (VariableDeclarator var : field.getVariables()) {
                totalFieldCount++;

                // Check if it's a constant (static final)
                boolean isConstant = field.isStatic() && field.isFinal();
                if (!isConstant) {
                    nonConstantFieldCount++;
                    continue;
                }

                String fieldName = var.getNameAsString();

                // Check if field name is config-related
                for (String pattern : CONFIG_FIELD_NAME_PATTERNS) {
                    if (fieldName.toUpperCase().contains(pattern)) {
                        configRelatedNameCount++;
                        info.configFields.add(fieldName);
                        break;
                    }
                }

                // Analyze constant value if present
                if (var.getInitializer().isPresent()) {
                    Expression expr = var.getInitializer().get();

                    // Count different types of constants
                    info.totalConstantsCount++;

                    if (expr instanceof StringLiteralExpr) {
                        info.stringConstantsCount++;
                        // Check for nested config paths in strings (e.g., "app.config.timeout")
                        String value = ((StringLiteralExpr) expr).getValue();
                        if (value.contains(".") && !value.startsWith("http") && !value.startsWith("jdbc")) {
                            String[] parts = value.split("\\.");
                            if (parts.length > 2) {
                                info.nestedConstantCount++;
                                info.nestedPaths.add(value);
                            }
                        }
                    } else if (expr instanceof LiteralExpr) {
                        String exprStr = expr.toString().toLowerCase();
                        if (exprStr.equals("true") || exprStr.equals("false")) {
                            info.booleanConstantsCount++;
                        } else if (exprStr.matches("\\d+(\\.\\d+)?[lfd]?")) {
                            info.numericConstantsCount++;
                        }
                    }
                }
            }
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);
            methodCount++;

            // Check if this is a utility method to get configuration
            String methodName = method.getNameAsString().toLowerCase();
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
        public int totalConstantsCount = 0;
        public int stringConstantsCount = 0;
        public int numericConstantsCount = 0;
        public int booleanConstantsCount = 0;
        public int nestedConstantCount = 0;
        public int configAccessMethodCount = 0;
        public double constantPercentage = 0.0;
        public double configRelatedNamePercentage = 0.0;
        public boolean hasConfigNamePattern = false;
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
