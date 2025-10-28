package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;

import java.util.HashSet;
import java.util.Set;

/**
 * File detector for legacy framework configuration files commonly found in EJB
 * applications.
 * Detects files from frameworks that may need special consideration during
 * Spring migration.
 * <p>
 * This inspector operates on configuration files (XML, properties, etc.) and
 * stores
 * analysis data directly on ProjectFile, not on JavaClassNode, since
 * configuration
 * files are not Java classes.
 */
@InspectorDependencies(requires = {InspectorTags.TAG_SOURCE_FILE}, produces = {
        "legacy_framework",
        "migration_priority" })
public class LegacyFrameworkDetector implements Inspector<ProjectFile> {

    /**
     * Consolidated POJO for legacy framework analysis results
     */
    public static class LegacyFrameworkAnalysis {
        public final boolean hasLegacyFramework;
        public final FrameworkType frameworkType;
        public final String fileName;
        public final int migrationPriority;
        public final Set<String> detectedPatterns;
        public final String configType;

        public LegacyFrameworkAnalysis(boolean hasLegacyFramework, FrameworkType frameworkType,
                String fileName, int migrationPriority,
                Set<String> detectedPatterns, String configType) {
            this.hasLegacyFramework = hasLegacyFramework;
            this.frameworkType = frameworkType;
            this.fileName = fileName;
            this.migrationPriority = migrationPriority;
            this.detectedPatterns = detectedPatterns;
            this.configType = configType;
        }
    }

    /**
     * Enum for framework types - simple values as per guidelines
     */
    public enum FrameworkType {
        EJB, STRUTS, HIBERNATE, SPRING_LEGACY, JSF, AXIS, LOG4J,
        CONNECTION_POOL, BUILD, WEB, SECURITY, UNKNOWN
    }

    // Predefined collections for better performance - follow guideline #6
    private static final Set<String> EJB_FILES = Set.of(
            "ejb-jar.xml", "persistence.xml", "orm.xml", "jboss.xml", "jbosscmp-jdbc.xml",
            "weblogic-ejb-jar.xml", "websphere-ejb-jar.xml", "sun-ejb-jar.xml", "glassfish-ejb-jar.xml");

    private static final Set<String> STRUTS_FILES = Set.of(
            "struts-config.xml", "validation.xml", "validator-rules.xml", "tiles-defs.xml", "struts.properties");

    private static final Set<String> HIBERNATE_FILES = Set.of(
            "hibernate.cfg.xml", "hibernate.properties", "ehcache.xml");

    private static final Set<String> SPRING_LEGACY_FILES = Set.of(
            "applicationContext.xml", "spring-servlet.xml", "spring-beans.xml", "spring.xml");

    private static final Set<String> JSF_FILES = Set.of(
            "faces-config.xml", "navigation.xml");

    private static final Set<String> AXIS_FILES = Set.of(
            "server-config.wsdd", "client-config.wsdd", "deploy.wsdd", "undeploy.wsdd");

    private static final Set<String> LOG4J_FILES = Set.of(
            "log4j.xml", "log4j.properties");

    private static final Set<String> CONNECTION_POOL_FILES = Set.of(
            "c3p0.properties", "dbcp.properties");

    private static final Set<String> BUILD_FILES = Set.of(
            "build.properties", "project.xml", "maven.xml");

    private static final Set<String> WEB_FRAMEWORK_FILES = Set.of(
            "tapestry.xml", "velocity.properties", "freemarker.properties");

    private static final Set<String> SECURITY_FILES = Set.of(
            "acegi-security.xml", "security.xml");

    public LegacyFrameworkDetector() {
        // No dependencies needed - operates directly on ProjectFile
    }

    @Override
    public String getName() {
        return "Legacy Framework Detector";
    }

    @Override
    public boolean supports(ProjectFile objectToAnalyze) {
        return objectToAnalyze.hasFileExtension("xml") || objectToAnalyze.hasFileExtension("properties") || objectToAnalyze.hasFileExtension("wsdd");
    }

    // Removed supports() method - trust @InspectorDependencies completely

    @Override
    public void inspect(ProjectFile projectFile, NodeDecorator<ProjectFile> decorator) {
        String fileName = projectFile.getFileName() != null ? projectFile.getFileName().toLowerCase() : "";

        // Analyze file to create consolidated result
        LegacyFrameworkAnalysis analysisResult = analyzeLegacyFramework(fileName);

        if (!analysisResult.hasLegacyFramework) {
            return; // Nothing to do if no legacy framework detected
        }

        // Honor produces contract - set tags on ProjectFile (dependency chain)
        setProducedTags(decorator, analysisResult);

        // Store detailed analysis data directly on ProjectFile as consolidated POJO
        // Configuration files are not Java classes, so data belongs on ProjectFile, not
        // JavaClassNode
        decorator.setProperty("legacy_framework_analysis", analysisResult);
    }

    /**
     * Analyze legacy framework configuration files using predefined collections
     */
    private LegacyFrameworkAnalysis analyzeLegacyFramework(String fileName) {
        Set<String> detectedPatterns = new HashSet<>();
        FrameworkType frameworkType = FrameworkType.UNKNOWN;
        int migrationPriority = 0;
        String configType = "unknown";

        // Use predefined collections with contains() - guideline #6
        if (EJB_FILES.contains(fileName)) {
            frameworkType = FrameworkType.EJB;
            migrationPriority = 10; // Highest priority for EJB migration
            configType = "ejb_config";
            detectedPatterns.add("ejb_deployment_descriptor");
        } else if (STRUTS_FILES.contains(fileName)) {
            frameworkType = FrameworkType.STRUTS;
            migrationPriority = 8;
            configType = "struts_config";
            detectedPatterns.add("struts_configuration");
        } else if (HIBERNATE_FILES.contains(fileName)) {
            frameworkType = FrameworkType.HIBERNATE;
            migrationPriority = 7;
            configType = "hibernate_config";
            detectedPatterns.add("hibernate_configuration");
        } else if (SPRING_LEGACY_FILES.contains(fileName)) {
            frameworkType = FrameworkType.SPRING_LEGACY;
            migrationPriority = 6;
            configType = "spring_legacy_config";
            detectedPatterns.add("spring_legacy_configuration");
        } else if (JSF_FILES.contains(fileName)) {
            frameworkType = FrameworkType.JSF;
            migrationPriority = 5;
            configType = "jsf_config";
            detectedPatterns.add("jsf_configuration");
        } else if (AXIS_FILES.contains(fileName)) {
            frameworkType = FrameworkType.AXIS;
            migrationPriority = 4;
            configType = "axis_config";
            detectedPatterns.add("axis_webservice");
        } else if (LOG4J_FILES.contains(fileName)) {
            frameworkType = FrameworkType.LOG4J;
            migrationPriority = 3;
            configType = "log4j_config";
            detectedPatterns.add("log4j_configuration");
        } else if (CONNECTION_POOL_FILES.contains(fileName)) {
            frameworkType = FrameworkType.CONNECTION_POOL;
            migrationPriority = 6;
            configType = "connection_pool_config";
            detectedPatterns.add("connection_pool_configuration");
        } else if (BUILD_FILES.contains(fileName)) {
            frameworkType = FrameworkType.BUILD;
            migrationPriority = 2;
            configType = "build_config";
            detectedPatterns.add("legacy_build_configuration");
        } else if (WEB_FRAMEWORK_FILES.contains(fileName)) {
            frameworkType = FrameworkType.WEB;
            migrationPriority = 4;
            configType = "web_framework_config";
            detectedPatterns.add("web_framework_configuration");
        } else if (SECURITY_FILES.contains(fileName)) {
            frameworkType = FrameworkType.SECURITY;
            migrationPriority = 7;
            configType = "security_config";
            detectedPatterns.add("security_framework_configuration");
        }

        boolean hasLegacyFramework = frameworkType != FrameworkType.UNKNOWN;

        return new LegacyFrameworkAnalysis(hasLegacyFramework, frameworkType, fileName,
                migrationPriority, detectedPatterns, configType);
    }

    /**
     * Set tags on ProjectFile to honor the produces contract
     */
    private void setProducedTags(NodeDecorator<ProjectFile> decorator, LegacyFrameworkAnalysis analysis) {
        // Set simple boolean tag for legacy framework detection
        decorator.enableTag("legacy_framework");

        // Set migration priority as property
        decorator.setProperty("migration_priority", analysis.migrationPriority);
    }
}
