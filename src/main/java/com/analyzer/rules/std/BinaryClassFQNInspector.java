package com.analyzer.rules.std;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.GraphRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.binary.AbstractASMInspector;
import com.analyzer.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * Inspector that extracts fully qualified names from binary Java class files.
 * This inspector reads .class files using ASM and extracts the package name
 * and class name to set the fully qualified name on ProjectFile objects.
 * <p>
 * Required by AbstractClassLoaderBasedInspector which depends on
 * projectFile.getFullyQualifiedName() being set.
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_BINARY }, produces = {
        InspectorTags.TAG_JAVA_FULLY_QUALIFIED_NAME, InspectorTags.TAG_JAVA_PACKAGE_NAME,
        InspectorTags.TAG_JAVA_CLASS_NAME })
public class BinaryClassFQNInspector extends AbstractASMInspector {

    private static final Logger logger = LoggerFactory.getLogger(BinaryClassFQNInspector.class);

    private final GraphRepository graphRepository;

    @Inject
    public BinaryClassFQNInspector(ResourceResolver resourceResolver, GraphRepository graphRepository) {
        super(resourceResolver);
        this.graphRepository = graphRepository;
    }

    @Override
    public String getName() {
        return "Binary Class FQN Inspector";
    }

    @Override
    protected ASMClassVisitor createClassVisitor(ProjectFile projectFile, NodeDecorator<ProjectFile> projectFileDecorator) {
        return new FQNExtractorVisitor(projectFile, projectFileDecorator);
    }

    /**
     * ASM ClassVisitor that extracts fully qualified name from bytecode.
     */
    private static class FQNExtractorVisitor extends ASMClassVisitor {

        public FQNExtractorVisitor(ProjectFile projectFile, NodeDecorator<ProjectFile> projectFileDecorator) {
            super(projectFile, projectFileDecorator);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            if (name != null) {
                // Convert internal name (com/example/MyClass) to fully qualified name
                // (com.example.MyClass)
                String fullyQualifiedName = name.replace('/', '.');

                // Extract package and class name
                String packageName = "";
                String className = fullyQualifiedName;

                int lastDotIndex = fullyQualifiedName.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    packageName = fullyQualifiedName.substring(0, lastDotIndex);
                    className = fullyQualifiedName.substring(lastDotIndex + 1);
                }

                // Set the fully qualified name using ProjectFile's method
                //projectFile.setFullQualifiedName(packageName, className);
                projectFile.setFullQualifiedName(packageName, className);
                logger.debug("Extracted FQN from binary class: {} (package: {}, class: {})",
                        fullyQualifiedName, packageName, className);
            }
        }
    }
}
