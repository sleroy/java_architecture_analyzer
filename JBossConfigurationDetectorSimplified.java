package com.analyzer.rules.ejb2spring;

import com.analyzer.core.*;
import com.analyzer.inspectors.core.detection.FileExtensionInspector;
import com.analyzer.rules.std.SourceFileTagInspector;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Simplified JBoss Configuration Detector
 */
@InspectorDependencies(need = { FileExtensionInspector.class, SourceFileTagInspector.class }, 
                      produces = { /* same as original */ })
public class JBossConfigurationDetectorSimplified implements Inspector<ProjectFile> {

    public static class TAGS {
        // Same constants as original...
        public static final String TAG_DETECTOR_JBOSS_CONFIGURATION = "jboss_configuration_detector.jboss_configuration";
        public static final String TAG_JBOSS_EJB_DESCRIPTOR = "jboss_configuration_detector.jboss_ejb_descriptor";
        public static final String TAG_JBOSS_WEB_DESCRIPTOR = "jboss_configuration_detector.jboss_web_descriptor";
        public static final String TAG_JBOSS_CMP_MAPPING = "jboss_configuration_detector.jboss_cmp_mapping";
        public static final String TAG_JBOSS_DATASOURCE = "jboss_configuration_detector.jboss_datasource";
        public static final String TAG_JBOSS_SECURITY_CONFIG = "jboss_configuration_detector.jboss_security_config";
        public static final String TAG_FILE_TYPE = "jboss_configuration_detector.file_type";
        public static final String TAG_JBOSS = "jboss_configuration_detector.jboss";
        public static final String TAG_EJB = "jboss_configuration_detector.ejb";
        public static final String TAG_ENTITY_BEAN_MAPPING = "jboss_configuration_detector.config.entity_bean_mapping";
        public static final String TAG_CONFIG_DATASOURCE = "jboss_configuration_detector.config.datasource";
        public static final String TAG_JBOSS_MBEAN_SERVICE = "jboss_configuration_detector.config.jboss_mbean_service";
        public static final String TAG_CONFIG_SERVICE = "jboss_configuration_detector.config.service";
        public static final String TAG_CONFIG_SECURITY = "jboss_configuration_detector.config.security";
        public static final String TAG_JBOSS_MESSAGING_CONFIG = "jboss_configuration_detector.config.jboss_messaging";
        public static final String TAG_CONFIG_MESSAGING = "jboss_configuration_detector.config.messaging";
        public static final String TAG_CONFIG_JNDI = "jboss_configuration_detector.config.jndi";
        public static final String TAG_MIGRATION_PRIORITY = "jboss_configuration_detector.migration_priority";
    }

    public enum Priority { HIGH, MEDIUM, LOW }

    public record ConfigRule(String pattern, boolean isRegex, Priority priority, String... tags) {
        public boolean matches(String fileName) {
            return isRegex ? fileName.matches(pattern) : pattern.equals(fileName);
        }
    }

    private static final List<ConfigRule> CONFIG_RULES = List.of(
        // High priority EJB descriptors
        new ConfigRule("jboss.xml", false, Priority.HIGH, TAGS.TAG_JBOSS_EJB_DESCRIPTOR),
        new ConfigRule("jboss-ejb3.xml", false, Priority.HIGH, TAGS.TAG_JBOSS_EJB_DESCRIPTOR),
        
        // CMP mappings
        new ConfigRule("jbosscmp-jdbc.xml", false, Priority.HIGH, TAGS.TAG_JBOSS_CMP_MAPPING, TAGS.TAG_ENTITY_BEAN_MAPPING),
        new ConfigRule("jaws.xml", false, Priority.HIGH, TAGS.TAG_JBOSS_CMP_MAPPING, TAGS.TAG_ENTITY_BEAN_MAPPING),
        new ConfigRule("standardjaws.xml", false, Priority.HIGH, TAGS.TAG_JBOSS_CMP_MAPPING, TAGS.TAG_ENTITY_BEAN_MAPPING),
        new ConfigRule("standardjbosscmp-jdbc.xml", false, Priority.HIGH, TAGS.TAG_JBOSS_CMP_MAPPING, TAGS.TAG_ENTITY_BEAN_MAPPING),
        
        // Security
        new ConfigRule("login-config.xml", false, Priority.HIGH, TAGS.TAG_JBOSS_SECURITY_CONFIG, TAGS.TAG_CONFIG_SECURITY),
        
        // JNDI
        new ConfigRule("jndi.properties", false, Priority.HIGH, TAGS.TAG_CONFIG_JNDI),
        
        // Medium priority configs
        new ConfigRule("jboss-web.xml", false, Priority.MEDIUM, TAGS.TAG_JBOSS_WEB_DESCRIPTOR),
        new ConfigRule("jboss-app.xml", false, Priority.MEDIUM),
        new ConfigRule("jboss-client.xml", false, Priority.MEDIUM),
        new ConfigRule("jboss-service.xml", false, Priority.MEDIUM, TAGS.TAG_JBOSS_MBEAN_SERVICE, TAGS.TAG_CONFIG_SERVICE),
        
        // Pattern-based rules
        new ConfigRule(".*-ds\\.xml", true, Priority.MEDIUM, TAGS.TAG_JBOSS_DATASOURCE, TAGS.TAG_CONFIG_DATASOURCE),
        new ConfigRule(".*-service\\.xml", true, Priority.MEDIUM, TAGS.TAG_JBOSS_MBEAN_SERVICE, TAGS.TAG_CONFIG_SERVICE),
        new ConfigRule("jboss-.*\\.xml", true, Priority.MEDIUM),
        new ConfigRule(".*-jboss\\.xml", true, Priority.MEDIUM),
        
        // Messaging configs
        new ConfigRule("hornetq-configuration.xml", false, Priority.MEDIUM, TAGS.TAG_JBOSS_MESSAGING_CONFIG, TAGS.TAG_CONFIG_MESSAGING),
        new ConfigRule("hornetq-jms.xml", false, Priority.MEDIUM, TAGS.TAG_JBOSS_MESSAGING_CONFIG, TAGS.TAG_CONFIG_MESSAGING),
        new ConfigRule("jbossmq-destinations-service.xml", false, Priority.MEDIUM, TAGS.TAG_JBOSS_MESSAGING_CONFIG, TAGS.TAG_CONFIG_MESSAGING),
        new ConfigRule("jbossmq-state.xml", false, Priority.MEDIUM, TAGS.TAG_JBOSS_MESSAGING_CONFIG, TAGS.TAG_CONFIG_MESSAGING),
        
        // Low priority
        new ConfigRule("roles.properties", false, Priority.LOW),
        new ConfigRule("users.properties", false, Priority.LOW),
        new ConfigRule("auth.conf", false, Priority.LOW)
    );

    @Override
    public String getName() {
        return "JBoss Configuration Detector";
    }

    public boolean supports(ProjectFile projectFile) {
        if (projectFile?.getFileName() == null) return false;
        
        String fileName = projectFile.getFileName().toLowerCase();
        return CONFIG_RULES.stream().anyMatch(rule -> rule.matches(fileName));
    }

    public void decorate(ProjectFile projectFile, ResultDecorator resultDecorator) {
        String fileName = projectFile.getFileName().toLowerCase();
        
        // Set common tags
        setCommonTags(projectFile);
        
        // Find matching rule and apply specific tags
        CONFIG_RULES.stream()
            .filter(rule -> rule.matches(fileName))
            .findFirst()
            .ifPresentOrElse(
                rule -> applyRule(projectFile, rule),
                () -> projectFile.setTag(TAGS.TAG_MIGRATION_PRIORITY, Priority.LOW.name())
            );
    }

    private void setCommonTags(ProjectFile projectFile) {
        projectFile.setTag(TAGS.TAG_DETECTOR_JBOSS_CONFIGURATION, true);
        projectFile.setTag(TAGS.TAG_FILE_TYPE, "jboss_config");
        projectFile.setTag(TAGS.TAG_JBOSS, true);
        projectFile.setTag(TAGS.TAG_EJB, true);
    }

    private void applyRule(ProjectFile projectFile, ConfigRule rule) {
        // Set priority
        projectFile.setTag(TAGS.TAG_MIGRATION_PRIORITY, rule.priority().name());
        
        // Set specific tags
        Arrays.stream(rule.tags()).forEach(tag -> projectFile.setTag(tag, true));
    }
}