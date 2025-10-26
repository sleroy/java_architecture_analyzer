package com.analyzer.core.db;

import com.analyzer.core.db.repository.GraphRepository;
import com.analyzer.core.db.serializer.GraphDatabaseDeserializer;
import com.analyzer.core.db.serializer.GraphDatabaseSerializer;
import com.analyzer.core.model.Project;
import com.analyzer.core.model.ProjectFile;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Example demonstrating H2 + MyBatis graph database usage.
 * Shows how to serialize and deserialize projects with preserved node IDs.
 */
public class GraphDatabaseExample {

    public static void main(String[] args) {
        try {
            // Initialize database
            Path dbPath = Paths.get("output/analysis.db");
            GraphDatabaseConfig config = new GraphDatabaseConfig();
            config.initialize(dbPath);
            System.out.println("✓ Database initialized at: " + dbPath);

            // Create sample project
            Project project = createSampleProject();
            System.out.println("✓ Created sample project with " + project.getProjectFiles().size() + " files");

            // Serialize to database
            GraphDatabaseSerializer serializer = new GraphDatabaseSerializer(config);
            serializer.serialize(project);
            System.out.println("✓ Project serialized to database");

            // Query using repository
            GraphRepository repo = new GraphRepository(config);
            var stats = repo.getStatistics();
            System.out.println("✓ Graph statistics: " + stats);

            // Show that IDs are preserved
            var javaNodes = repo.findNodesByType("java");
            System.out.println("\n📋 Java nodes with preserved IDs:");
            for (var node : javaNodes) {
                System.out.println("  - ID: " + node.getId());
                System.out.println("    Label: " + node.getDisplayLabel());
            }

            // Find by tag
            var ejbBeans = repo.findNodesByTag("ejb.session_bean");
            System.out.println("\n🏷  Nodes tagged 'ejb.session_bean': " + ejbBeans.size());
            ejbBeans.forEach(id -> System.out.println("  - " + id));

            // Deserialize back to Project
            GraphDatabaseDeserializer deserializer = new GraphDatabaseDeserializer(config);
            Project loadedProject = deserializer.deserializeLatest();
            System.out.println("\n✓ Project deserialized: " + loadedProject.getProjectFiles().size() + " files loaded");

            // Verify IDs match
            boolean allMatch = loadedProject.getProjectFiles().values().stream()
                    .allMatch(file -> file.getId().equals(file.getFilePath().toString()));
            System.out.println("✓ All node IDs preserved: " + allMatch);

            // Show sample node
            if (!loadedProject.getProjectFiles().isEmpty()) {
                ProjectFile sample = loadedProject.getProjectFiles().values().iterator().next();
                System.out.println("\n📄 Sample Node:");
                System.out.println("  ID: " + sample.getId());
                System.out.println("  Type: " + determineNodeType(sample));
                System.out.println("  Properties: " + sample.getNodeProperties().size());
                System.out.println("  Tags: " + sample.getTags());
            }

            System.out.println("\n✅ Example complete!");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Project createSampleProject() {
        Path projectRoot = Paths.get("/home/user/sample-project");
        Project project = new Project(projectRoot, "Sample EJB Project");

        // Add sample Java file
        Path javaFile = projectRoot.resolve("src/main/java/com/example/HelloBean.java");
        ProjectFile file = new ProjectFile(javaFile, projectRoot);
        file.setProperty("java.fullyQualifiedName", "com.example.HelloBean");
        file.setProperty("java.packageName", "com.example");
        file.setProperty("java.className", "HelloBean");
        file.addTag("java.detected");
        file.addTag("ejb.session_bean");
        project.addProjectFile(file);

        // Add sample XML file
        Path xmlFile = projectRoot.resolve("src/main/resources/META-INF/ejb-jar.xml");
        ProjectFile xmlF = new ProjectFile(xmlFile, projectRoot);
        xmlF.setProperty("xml.type", "ejb-descriptor");
        xmlF.addTag("xml.detected");
        xmlF.addTag("ejb.descriptor");
        project.addProjectFile(xmlF);

        return project;
    }

    private static String determineNodeType(ProjectFile file) {
        String fileName = file.getFileName().toLowerCase();
        if (fileName.endsWith(".java"))
            return "java";
        if (fileName.endsWith(".xml"))
            return "xml";
        return "file";
    }
}
