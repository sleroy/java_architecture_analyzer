package com.analyzer.rules.ejb2spring;
import com.analyzer.core.cache.LocalCache;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * File detector for EJB deployment descriptor XML files.
 * Detects standard J2EE deployment descriptors used in EJB applications.
 * <p>
 * This inspector operates on configuration files (XML) and stores
 * analysis data directly on ProjectFile, not on JavaClassNode, since
 * configuration
 * files are not Java classes.
 */
@InspectorDependencies(
        requires = {
                InspectorTags.TAG_SOURCE_FILE
        },
        produces = {
        EjbDeploymentDescriptorDetector.TAGS.TAG_DESCRIPTOR_TYPE,
        EjbDeploymentDescriptorDetector.TAGS.TAG_FILE_TYPE,
        com.analyzer.rules.ejb2spring.EjbMigrationTags.EJB_DEPLOYMENT_DESCRIPTOR,
        com.analyzer.rules.ejb2spring.EjbMigrationTags.VENDOR_DEPLOYMENT_DESCRIPTOR,
        com.analyzer.rules.ejb2spring.EjbMigrationTags.JPA_CONVERSION_CANDIDATE,
})
public class EjbDeploymentDescriptorDetector implements Inspector<ProjectFile> {

    private final String name;
    private final String tag;
    private final Set<String> descriptorFiles;
    private final int priority;

    public EjbDeploymentDescriptorDetector() {
        this.name = "EJB Deployment Descriptor Detector";
        this.tag = "ejb_deployment_descriptor";
        this.priority = 20; // High priority for deployment descriptors

        // Standard J2EE deployment descriptors
        this.descriptorFiles = new HashSet<>(Arrays.asList(
                "application-client.xml",
                "application.xml",
                "beans.xml", // CDI beans descriptor
                "domain.xml", // WildFly domain configurations
                "ejb-jar.xml",
                "faces-config.xml", // JSF configuration
                "jboss-ds.xml", // JBoss DataSource configurations
                "jboss-ejb3.xml", // JBoss EJB 3.x configurations
                "jboss-web.xml", // JBoss web application configurations
                "jboss.xml", // Legacy JBoss configurations
                "orm.xml", // JPA ORM mapping
                "persistence.xml", // JPA persistence configuration
                "portlet.xml", // Portlet descriptor
                "ra.xml", // Resource adapter descriptor
                "standalone.xml", // WildFly standalone configurations
                "web.xml"));
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean supports(ProjectFile projectFile) {
        if (projectFile == null || projectFile.getFileName() == null) {
            return false;
        }

        String fileName = projectFile.getFileName().toLowerCase();
        return descriptorFiles.contains(fileName);
    }

    public void inspect(ProjectFile projectFile, NodeDecorator<ProjectFile> projectFileDecorator) {
        String fileName = projectFile.getFileName().toLowerCase();

        // Configuration files are not Java classes, so all data belongs on ProjectFile
        // Set optimized tags - honor produces contract
        projectFileDecorator.setProperty(TAGS.TAG_DESCRIPTOR_TYPE, fileName);
        projectFileDecorator.setProperty(TAGS.TAG_FILE_TYPE, "deployment_descriptor");

        // Set EJB-specific migration tags from EjbMigrationTags
        if ("ejb-jar.xml".equals(fileName)) {
            projectFileDecorator.enableTag(com.analyzer.rules.ejb2spring.EjbMigrationTags.EJB_DEPLOYMENT_DESCRIPTOR);
        } else if ("persistence.xml".equals(fileName) || "orm.xml".equals(fileName)) {
            projectFileDecorator.enableTag(com.analyzer.rules.ejb2spring.EjbMigrationTags.JPA_CONVERSION_CANDIDATE);
        } else if ("web.xml".equals(fileName) || "application.xml".equals(fileName)) {
            projectFileDecorator.enableTag(com.analyzer.rules.ejb2spring.EjbMigrationTags.VENDOR_DEPLOYMENT_DESCRIPTOR);
        } else {
        }
    }

    /**
     * Get the tag used by this detector
     */
    public String getTag() {
        return tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        EjbDeploymentDescriptorDetector that = (EjbDeploymentDescriptorDetector) o;
        return Objects.equals(name, that.name) && Objects.equals(descriptorFiles, that.descriptorFiles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, descriptorFiles);
    }

    @Override
    public String toString() {
        return "EjbDeploymentDescriptorDetector{" +
                "name='" + name + '\'' +
                ", tag='" + tag + '\'' +
                ", descriptorFiles=" + descriptorFiles +
                ", priority=" + priority +
                '}';
    }

    public static class TAGS {
        public static final String TAG_DESCRIPTOR_TYPE = "ejb_deployment_descriptor_detector.descriptor_type";
        public static final String TAG_FILE_TYPE = "ejb_deployment_descriptor_detector.file_type";
    }
}
