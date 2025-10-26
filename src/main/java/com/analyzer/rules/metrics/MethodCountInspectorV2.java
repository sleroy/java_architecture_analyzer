package com.analyzer.rules.metrics;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.graph.ProjectFileRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.inspectors.core.binary.AbstractASMClassInspector;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.MethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * Class-centric method count inspector that analyzes JavaClassNode using ASM
 * bytecode analysis.
 * This is a Phase 3 migration demonstrating the new class-centric architecture.
 * <p>
 * Key Differences from MethodCountInspector:
 * - Extends AbstractASMClassInspector (class-centric) instead of
 * AbstractASMInspector (file-centric)
 * - Receives JavaClassNode as input instead of ProjectFile
 * - Writes metrics directly to JavaClassNode properties
 * - Uses NodeDecorator&lt;JavaClassNode&gt; for property aggregation
 * <p>
 * Counts all method declarations in a Java class including:
 * - Regular methods
 * - Constructors
 * - Static initializers
 * - Bridge methods (compiler-generated)
 * - Synthetic methods
 * <p>
 * The method count is written to JavaClassNode.PROP_METHOD_COUNT property.
 *
 * @author Phase 3 - Class-Centric Architecture Migration
 * @since Phase 3 - Systematic Inspector Migration
 */
@InspectorDependencies(requires = {  }, produces = {} // Produces properties on
                                                                                      // JavaClassNode, not tags
)
public class MethodCountInspectorV2 extends AbstractASMClassInspector {

    private static final Logger logger = LoggerFactory.getLogger(MethodCountInspectorV2.class);

    @Inject
    public MethodCountInspectorV2(ProjectFileRepository projectFileRepository,
            ResourceResolver resourceResolver) {
        super(projectFileRepository, resourceResolver);
    }

    @Override
    public String getName() {
        return "Method Count Inspector V2 (Class-Centric ASM)";
    }

    @Override
    protected ASMClassNodeVisitor createClassVisitor(JavaClassNode classNode,
            NodeDecorator<JavaClassNode> decorator) {
        return new MethodCountVisitor(classNode, decorator);
    }

    /**
     * ASM visitor that counts method declarations during bytecode traversal.
     */
    private static class MethodCountVisitor extends ASMClassNodeVisitor {
        private int methodCount = 0;
        private String className;

        protected MethodCountVisitor(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator) {
            super(classNode, decorator);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            // Reset counter for each class
            methodCount = 0;
            this.className = name.replace('/', '.');
            logger.debug("Starting method count analysis for class: {}", this.className);
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                String signature, String[] exceptions) {
            methodCount++;
            logger.debug("Found method: {} with descriptor: {} (count: {})",
                    name, descriptor, methodCount);

            // Return parent's visitMethod - we don't need to analyze method bodies
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        @Override
        public void visitEnd() {
            logger.debug("Method count analysis complete for {}. Total methods: {}",
                    className, methodCount);

            // Write method count to JavaClassNode using decorator
            // This is the KEY DIFFERENCE: metric goes to JavaClassNode, not ProjectFile
            setProperty(JavaClassNode.PROP_METHOD_COUNT, methodCount);

            super.visitEnd();
        }
    }
}
