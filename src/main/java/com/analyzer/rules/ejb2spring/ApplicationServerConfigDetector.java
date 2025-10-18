package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.inspector.Inspector;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.detection.SourceFileTagDetector;

import java.util.*;

/**
 * File detector for application server configuration files.
 * Detects configuration files used by various J2EE application servers.
 */
@InspectorDependencies(requires = {}, need = { SourceFileTagDetector.class }, produces = {
        ApplicationServerConfigDetector.TAGS.TAG_APP_SERVER_CONFIG,
        ApplicationServerConfigDetector.TAGS.TAG_FILE_TYPE_SERVER_CONFIG,
        ApplicationServerConfigDetector.TAGS.TAG_APP_SERVER_GENERAL,
        ApplicationServerConfigDetector.TAGS.TAG_SERVER_TOMCAT,
        ApplicationServerConfigDetector.TAGS.TAG_SERVER_WEBLOGIC,
        ApplicationServerConfigDetector.TAGS.TAG_SERVER_WEBSPHERE,
        ApplicationServerConfigDetector.TAGS.TAG_SERVER_GLASSFISH,
        ApplicationServerConfigDetector.TAGS.TAG_SERVER_ORACLE_AS,
        ApplicationServerConfigDetector.TAGS.TAG_CONFIG_TOMCAT,
        ApplicationServerConfigDetector.TAGS.TAG_CONFIG_WEBLOGIC,
        ApplicationServerConfigDetector.TAGS.TAG_CONFIG_WEBSPHERE,
        ApplicationServerConfigDetector.TAGS.TAG_CONFIG_GLASSFISH,
        ApplicationServerConfigDetector.TAGS.TAG_CONFIG_ORACLE_AS,
        ApplicationServerConfigDetector.TAGS.TAG_CONFIG_SECURITY,
        ApplicationServerConfigDetector.TAGS.TAG_CONFIG_DATASOURCE,
        ApplicationServerConfigDetector.TAGS.TAG_CONFIG_MESSAGING,
        ApplicationServerConfigDetector.TAGS.TAG_CONFIG_WEB,
        ApplicationServerConfigDetector.TAGS.TAG_MIGRATION_PRIORITY })
public class ApplicationServerConfigDetector implements Inspector<ProjectFile> {

    private final String name;
    private final String tag;
    private final Set<String> serverConfigFiles;
    private final Set<String> serverConfigPatterns;
    private final int priority;
    private final ClassNodeRepository classNodeRepository;

    public ApplicationServerConfigDetector(ClassNodeRepository classNodeRepository) {
        this.name = "Application Server Configuration Detector";
        this.tag = TAGS.TAG_APP_SERVER_CONFIG;
        this.priority = 16; // High priority for server config files
        this.classNodeRepository = classNodeRepository;

        // Common application server configuration files
        this.serverConfigFiles = new HashSet<>(Arrays.asList(
                // Tomcat configuration
                "server.xml", // Tomcat server configuration
                "context.xml", // Tomcat context configuration
                "web.xml", // Web application descriptor (already in deployment descriptors but important
                // here too)
                "tomcat-users.xml", // Tomcat user authentication

                // WebLogic configuration
                "weblogic.xml", // WebLogic-specific deployment descriptor
                "weblogic-ejb-jar.xml", // WebLogic EJB deployment descriptor
                "weblogic-application.xml", // WebLogic application descriptor
                "config.xml", // WebLogic domain configuration

                // WebSphere configuration
                "ibm-web-bnd.xml", // WebSphere web binding
                "ibm-web-ext.xml", // WebSphere web extensions
                "ibm-ejb-jar-bnd.xml", // WebSphere EJB binding
                "ibm-ejb-jar-ext.xml", // WebSphere EJB extensions
                "ibm-application-bnd.xml", // WebSphere application binding
                "ibm-application-ext.xml", // WebSphere application extensions

                // Oracle Application Server (OC4J)
                "orion-web.xml", // OC4J web configuration
                "orion-ejb-jar.xml", // OC4J EJB configuration
                "orion-application.xml", // OC4J application configuration
                "data-sources.xml", // OC4J data sources

                // Generic J2EE server configs
                "sun-web.xml", // Sun/GlassFish web descriptor
                "sun-ejb-jar.xml", // Sun/GlassFish EJB descriptor
                "sun-application.xml", // Sun/GlassFish application descriptor
                "glassfish-web.xml", // GlassFish web descriptor
                "glassfish-ejb-jar.xml", // GlassFish EJB descriptor

                // Security and authentication
                "jaas.config", // JAAS configuration
                "security-policy.xml", // Security policy configuration
                "ssl.conf", // SSL configuration

                // Messaging and JMS
                "jms.xml", // JMS configuration
                "activemq.xml", // ActiveMQ configuration
                "rabbit.conf" // RabbitMQ configuration
        ));

        // Pattern-based matching for server config files
        this.serverConfigPatterns = new HashSet<>(Arrays.asList(
                "*-web.xml", // Any web-specific server config
                "*ejb-jar.xml", // Any EJB-specific server config
                "*-application.xml", // Any application-specific server config
                "server*.xml", // Server configuration variants
                "context*.xml", // Context configuration variants
                "*-bnd.xml", // Binding files (WebSphere)
                "*-ext.xml", // Extension files (WebSphere)
                "*-ds.xml", // DataSource files (already in JBoss but general pattern)
                "*.properties", // Configuration properties files
                "*.conf", // Configuration files
                "*.config" // Configuration files
        ));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        // Trust @InspectorDependencies - it handles ALL filtering
        String fileName = projectFile.getFileName().toLowerCase();

        // Check exact filename matches
        if (serverConfigFiles.contains(fileName)) {
            return true;
        }

        // Check pattern matches
        return serverConfigPatterns.stream().anyMatch(pattern -> matchesPattern(fileName, pattern));
    }

    @Override
    public void decorate(ProjectFile projectFile, ProjectFileDecorator projectFileDecorator) {
        classNodeRepository.getOrCreateClassNodeByFqn(projectFile.getFullyQualifiedName()).ifPresent(classNode -> {
            classNode.setProjectFileId(projectFile.getId());
            String fileName = projectFile.getFileName().toLowerCase();

            // Honor produces contract - set ALL produced tags on ProjectFile for dependency
            // chains
            projectFileDecorator.setTag(TAGS.TAG_APP_SERVER_CONFIG, true);
            projectFileDecorator.setTag(TAGS.TAG_FILE_TYPE_SERVER_CONFIG, true);
            projectFileDecorator.setTag(TAGS.TAG_APP_SERVER_GENERAL, true);

            // Set analysis properties on ClassNode for export data
            classNode.setProperty(TAGS.TAG_APP_SERVER_CONFIG, true);
            classNode.setProperty(TAGS.TAG_FILE_TYPE_SERVER_CONFIG, true);
            classNode.setProperty(TAGS.TAG_APP_SERVER_GENERAL, true);

            // Set server-specific tags and properties
            if (isTomcatConfig(fileName)) {
                projectFileDecorator.setTag(TAGS.TAG_SERVER_TOMCAT, true);
                projectFileDecorator.setTag(TAGS.TAG_CONFIG_TOMCAT, true);
                classNode.setProperty(TAGS.TAG_SERVER_TOMCAT, true);
                classNode.setProperty(TAGS.TAG_CONFIG_TOMCAT, true);
            } else if (isWebLogicConfig(fileName)) {
                projectFileDecorator.setTag(TAGS.TAG_SERVER_WEBLOGIC, true);
                projectFileDecorator.setTag(TAGS.TAG_CONFIG_WEBLOGIC, true);
                classNode.setProperty(TAGS.TAG_SERVER_WEBLOGIC, true);
                classNode.setProperty(TAGS.TAG_CONFIG_WEBLOGIC, true);
            } else if (isWebSphereConfig(fileName)) {
                projectFileDecorator.setTag(TAGS.TAG_SERVER_WEBSPHERE, true);
                projectFileDecorator.setTag(TAGS.TAG_CONFIG_WEBSPHERE, true);
                classNode.setProperty(TAGS.TAG_SERVER_WEBSPHERE, true);
                classNode.setProperty(TAGS.TAG_CONFIG_WEBSPHERE, true);
            } else if (isGlassFishConfig(fileName)) {
                projectFileDecorator.setTag(TAGS.TAG_SERVER_GLASSFISH, true);
                projectFileDecorator.setTag(TAGS.TAG_CONFIG_GLASSFISH, true);
                classNode.setProperty(TAGS.TAG_SERVER_GLASSFISH, true);
                classNode.setProperty(TAGS.TAG_CONFIG_GLASSFISH, true);
            } else if (isOracleConfig(fileName)) {
                projectFileDecorator.setTag(TAGS.TAG_SERVER_ORACLE_AS, true);
                projectFileDecorator.setTag(TAGS.TAG_CONFIG_ORACLE_AS, true);
                classNode.setProperty(TAGS.TAG_SERVER_ORACLE_AS, true);
                classNode.setProperty(TAGS.TAG_CONFIG_ORACLE_AS, true);
            }

            // Set configuration type tags and properties
            if (isSecurityConfig(fileName)) {
                projectFileDecorator.setTag(TAGS.TAG_CONFIG_SECURITY, true);
                projectFileDecorator.setTag(TAGS.TAG_MIGRATION_PRIORITY, true);
                classNode.setProperty(TAGS.TAG_CONFIG_SECURITY, true);
                classNode.setProperty(TAGS.TAG_MIGRATION_PRIORITY, "HIGH");
            } else if (isDataSourceConfig(fileName)) {
                projectFileDecorator.setTag(TAGS.TAG_CONFIG_DATASOURCE, true);
                projectFileDecorator.setTag(TAGS.TAG_MIGRATION_PRIORITY, true);
                classNode.setProperty(TAGS.TAG_CONFIG_DATASOURCE, true);
                classNode.setProperty(TAGS.TAG_MIGRATION_PRIORITY, "MEDIUM");
            } else if (isMessagingConfig(fileName)) {
                projectFileDecorator.setTag(TAGS.TAG_CONFIG_MESSAGING, true);
                projectFileDecorator.setTag(TAGS.TAG_MIGRATION_PRIORITY, true);
                classNode.setProperty(TAGS.TAG_CONFIG_MESSAGING, true);
                classNode.setProperty(TAGS.TAG_MIGRATION_PRIORITY, "MEDIUM");
            } else if (isWebConfig(fileName)) {
                projectFileDecorator.setTag(TAGS.TAG_CONFIG_WEB, true);
                projectFileDecorator.setTag(TAGS.TAG_MIGRATION_PRIORITY, true);
                classNode.setProperty(TAGS.TAG_CONFIG_WEB, true);
                classNode.setProperty(TAGS.TAG_MIGRATION_PRIORITY, "MEDIUM");
            } else {
                projectFileDecorator.setTag(TAGS.TAG_MIGRATION_PRIORITY, true);
                classNode.setProperty(TAGS.TAG_MIGRATION_PRIORITY, "LOW");
            }
        });
    }

    /**
     * Check if filename matches a simple pattern (supports * wildcard)
     */
    private boolean matchesPattern(String fileName, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }

        if (!pattern.contains("*")) {
            return fileName.equals(pattern);
        }

        String regex = pattern.replace("*", ".*");
        return fileName.matches(regex);
    }

    /**
     * Check if file is Tomcat-specific configuration
     */
    private boolean isTomcatConfig(String fileName) {
        return fileName.equals("server.xml") || fileName.equals("context.xml") ||
                fileName.equals("tomcat-users.xml");
    }

    /**
     * Check if file is WebLogic-specific configuration
     */
    private boolean isWebLogicConfig(String fileName) {
        return fileName.startsWith("weblogic") || fileName.equals("config.xml");
    }

    /**
     * Check if file is WebSphere-specific configuration
     */
    private boolean isWebSphereConfig(String fileName) {
        return fileName.startsWith("ibm-");
    }

    /**
     * Check if file is GlassFish-specific configuration
     */
    private boolean isGlassFishConfig(String fileName) {
        return fileName.startsWith("glassfish-") || fileName.startsWith("sun-");
    }

    /**
     * Check if file is Oracle Application Server configuration
     */
    private boolean isOracleConfig(String fileName) {
        return fileName.startsWith("orion-") || fileName.equals("data-sources.xml");
    }

    /**
     * Check if file is security-related configuration
     */
    private boolean isSecurityConfig(String fileName) {
        return fileName.contains("security") || fileName.contains("jaas") ||
                fileName.contains("ssl") || fileName.contains("auth");
    }

    /**
     * Check if file is data source configuration
     */
    private boolean isDataSourceConfig(String fileName) {
        return fileName.contains("data-source") || fileName.contains("datasource") ||
                fileName.endsWith("-ds.xml");
    }

    /**
     * Check if file is messaging configuration
     */
    private boolean isMessagingConfig(String fileName) {
        return fileName.contains("jms") || fileName.contains("activemq") ||
                fileName.contains("rabbit") || fileName.contains("messaging");
    }

    /**
     * Check if file is web-specific configuration
     */
    private boolean isWebConfig(String fileName) {
        return fileName.contains("web") && fileName.endsWith(".xml");
    }

    /**
     * Get the tag used by this detector
     */
    public String getTag() {
        return tag;
    }

    /**
     * Get the server config files this detector matches
     */
    public Set<String> getServerConfigFiles() {
        return Collections.unmodifiableSet(serverConfigFiles);
    }

    /**
     * Get the server config patterns this detector matches
     */
    public Set<String> getServerConfigPatterns() {
        return Collections.unmodifiableSet(serverConfigPatterns);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ApplicationServerConfigDetector that = (ApplicationServerConfigDetector) o;
        return Objects.equals(name, that.name) && Objects.equals(serverConfigFiles, that.serverConfigFiles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, serverConfigFiles);
    }

    @Override
    public String toString() {
        return "ApplicationServerConfigDetector{" +
                "name='" + name + '\'' +
                ", tag='" + tag + '\'' +
                ", serverConfigFiles=" + serverConfigFiles +
                ", serverConfigPatterns=" + serverConfigPatterns +
                ", priority=" + priority +
                '}';
    }

    public static class TAGS {
        public static final String TAG_APP_SERVER_CONFIG = "application_server_config_detector.app_server_config";

        // File Type Tags
        public static final String TAG_FILE_TYPE_SERVER_CONFIG = "application_server_config_detector.server_config";
        public static final String TAG_APP_SERVER_GENERAL = "application_server_config_detector.app_server";

        // Application Server Types
        public static final String TAG_SERVER_TOMCAT = "application_server_config_detector.server.tomcat";
        public static final String TAG_SERVER_WEBLOGIC = "application_server_config_detector.server.weblogic";
        public static final String TAG_SERVER_WEBSPHERE = "application_server_config_detector.server.websphere";
        public static final String TAG_SERVER_GLASSFISH = "application_server_config_detector.server.glassfish";
        public static final String TAG_SERVER_ORACLE_AS = "application_server_config_detector.server.oracle_as";

        // Server Configuration Types
        public static final String TAG_CONFIG_TOMCAT = "application_server_config_detector.config.tomcat";
        public static final String TAG_CONFIG_WEBLOGIC = "application_server_config_detector.config.weblogic";
        public static final String TAG_CONFIG_WEBSPHERE = "application_server_config_detector.config.websphere";
        public static final String TAG_CONFIG_GLASSFISH = "application_server_config_detector.config.glassfish";
        public static final String TAG_CONFIG_ORACLE_AS = "application_server_config_detector.config.oracle_as";

        // Configuration Categories
        public static final String TAG_CONFIG_SECURITY = "application_server_config_detector.config.security";
        public static final String TAG_CONFIG_DATASOURCE = "application_server_config_detector.config.datasource";
        public static final String TAG_CONFIG_MESSAGING = "application_server_config_detector.config.messaging";
        public static final String TAG_CONFIG_WEB = "application_server_config_detector.config.web";

        // Migration Priority
        public static final String TAG_MIGRATION_PRIORITY = "application_server_config_detector.migration_priority";
    }
}
