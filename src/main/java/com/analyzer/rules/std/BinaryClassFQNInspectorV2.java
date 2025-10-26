package com.analyzer.rules.std;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.graph.ProjectFileRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.inspectors.core.binary.AbstractASMClassInspector;
import com.analyzer.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import static com.analyzer.core.inspector.InspectorTags.*;

/**
 * Class-centric FQN inspector that extracts identification information from
 * bytecode.
 * This is a Phase 3 migration demonstrating the new class-centric architecture.
 * <p>
 * Key Differences from BinaryClassFQNInspector:
 * - Extends AbstractASMClassInspector (class-centric) instead of
 * AbstractASMInspector (file-centric)
 * - Receives JavaClassNode as input instead of ProjectFile
 * - Writes identification info to JavaClassNode properties
 * - Uses NodeDecorator&lt;JavaClassNode&gt; for property aggregation
 * <p>
 * Extracts and stores:
 * - Fully Qualified Name (FQN)
 * - Package Name
 * - Simple Class Name
 * <p>
 * These properties are written to JavaClassNode for class identification.
 *
 * @author Phase 3 - Class-Centric Architecture Migration
 * @since Phase 3 - Systematic Inspector Migration
 */
@InspectorDependencies(produces = {
        TAG_JAVA_PACKAGE_NAME,
        TAG_JAVA_CLASS_NAME })
public class BinaryClassFQNInspectorV2 extends AbstractASMClassInspector {

    private static final Logger logger = LoggerFactory.getLogger(BinaryClassFQNInspectorV2.class);

    // Property keys for JavaClassNode identification
    public static final String PROP_PACKAGE_NAME = "java.package.name";
    public static final String PROP_SIMPLE_CLASS_NAME = "java.class.simple_name";

    @Inject
    public BinaryClassFQNInspectorV2(ProjectFileRepository projectFileRepository,
            ResourceResolver resourceResolver) {
        super(projectFileRepository, resourceResolver);
    }

    @Override
    public String getName() {
        return "Binary Class FQN Inspector V2 (Class-Centric ASM)";
    }

    @Override
    protected ASMClassNodeVisitor createClassVisitor(JavaClassNode classNode,
            NodeDecorator<JavaClassNode> decorator) {
        // Find the ProjectFile associated with this class node to also update its FQN
        return findProjectFile(classNode)
                .map(projectFile -> new FQNExtractorVisitor(classNode, decorator, projectFile))
                .orElseGet(() -> new FQNExtractorVisitor(classNode, decorator, null));
    }

    /**
     * ASM visitor that extracts fully qualified name information from bytecode.
     */
    private static class FQNExtractorVisitor extends ASMClassNodeVisitor {

        private final com.analyzer.core.model.ProjectFile projectFile;

        protected FQNExtractorVisitor(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator,
                com.analyzer.core.model.ProjectFile projectFile) {
            super(classNode, decorator);
            this.projectFile = projectFile;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
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

                // Write identification info to JavaClassNode
                // Note: FQN is already the node ID, but we store components for convenience
                setProperty(PROP_PACKAGE_NAME, packageName);
                setProperty(PROP_SIMPLE_CLASS_NAME, className);
                setProperty(PROP_JAVA_FULLY_QUALIFIED_NAME, fullyQualifiedName);

                // Also enable tags for backward compatibility with dependency system
                enableTag(TAG_JAVA_PACKAGE_NAME);
                enableTag(TAG_JAVA_CLASS_NAME);

                // Also update the associated ProjectFile with the FQN
                if (projectFile != null) {
                    projectFile.setFullQualifiedName(packageName, className);
                    logger.debug("Set FQN on ProjectFile: {} (package: {}, class: {})",
                            fullyQualifiedName, packageName, className);
                }

                logger.debug("Extracted FQN from binary class: {} (package: {}, class: {})",
                        fullyQualifiedName, packageName, className);
            }

            super.visit(version, access, name, signature, superName, interfaces);
        }
    }
}
