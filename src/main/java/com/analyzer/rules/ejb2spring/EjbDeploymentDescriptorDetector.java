package com.analyzer.rules.ejb2spring;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.graph.ClassNodeRepository;
import com.analyzer.core.inspector.Inspector;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.detection.SourceFileTagDetector;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * File detector for EJB deployment descriptor XML files.
 * Detects standard J2EE deployment descriptors used in EJB applications.
 */
@InspectorDependencies(need = {SourceFileTagDetector.class}, produces = {
        EjbDeploymentDescriptorDetector.TAGS.TAG_DESCRIPTOR_TYPE,
        EjbDeploymentDescriptorDetector.TAGS.TAG_FILE_TYPE,
        EjbMigrationTags.EJB_DEPLOYMENT_DESCRIPTOR,
        EjbMigrationTags.VENDOR_DEPLOYMENT_DESCRIPTOR,
        EjbMigrationTags.JPA_CONVERSION_CANDIDATE,
        EjbMigrationTags.MIGRATION_COMPLEXITY_LOW,
        EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM,
        EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH
})
public class EjbDeploymentDescriptorDetector implements Inspector<ProjectFile> {

    private final String name;
    private final String tag;
    private final Set<String> descriptorFiles;
    private final int priority;
    private final ClassNodeRepository classNodeRepository;
    public EjbDeploymentDescriptorDetector(ClassNodeRepository classNodeRepository) {
        this.name = "EJB Deployment Descriptor Detector";
        this.tag = "ejb_deployment_descriptor";
        this.priority = 20; // High priority for deployment descriptors
        this.classNodeRepository = classNodeRepository;

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

    public void decorate(ProjectFile projectFile, ProjectFileDecorator projectFileDecorator) {
        classNodeRepository.getOrCreateClassNodeByFqn(projectFile.getFullyQualifiedName()).ifPresent(classNode -> {
            classNode.setProjectFileId(projectFile.getId());
            String fileName = projectFile.getFileName().toLowerCase();

            // Set optimized tags - only useful ones without duplication
            classNode.setProperty(TAGS.TAG_DESCRIPTOR_TYPE, fileName);
            classNode.setProperty(TAGS.TAG_FILE_TYPE, "deployment_descriptor");

            // Set EJB-specific migration tags from EjbMigrationTags (no redundant priority
            // tags)
            if ("ejb-jar.xml".equals(fileName)) {
                classNode.setProperty(EjbMigrationTags.EJB_DEPLOYMENT_DESCRIPTOR, true);
                classNode.setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH, true);
            } else if ("persistence.xml".equals(fileName) || "orm.xml".equals(fileName)) {
                classNode.setProperty(EjbMigrationTags.JPA_CONVERSION_CANDIDATE, true);
                classNode.setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
            } else if ("web.xml".equals(fileName) || "application.xml".equals(fileName)) {
                classNode.setProperty(EjbMigrationTags.VENDOR_DEPLOYMENT_DESCRIPTOR, true);
                classNode.setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);
            } else {
                classNode.setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_LOW, true);
            }
        });
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
