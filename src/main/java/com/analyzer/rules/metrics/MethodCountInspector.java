package com.analyzer.rules.metrics;
import com.analyzer.core.inspector.InspectorDependencies;

import com.analyzer.core.export.ResultDecorator;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.inspectors.core.binary.AbstractASMInspector;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.MethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inspector that counts the number of method declarations in a Java class using
 * bytecode analysis.
 * This rule helps assess class complexity and identify classes with too many
 * methods.
 * <p>
 * Uses ASM to analyze compiled bytecode instead of source code, providing more
 * accurate
 * method counting that includes:
 * - Regular methods
 * - Constructors
 * - Static initializers
 * - Bridge methods (compiler-generated)
 * - Synthetic methods
 * <p>
 * Returns the total count of methods found in the class bytecode.
 */
@InspectorDependencies(
        requires = { InspectorTags.TAG_JAVA_IS_BINARY },
        produces = {MethodCountInspectorAbstractAbstractASMInspector.TAG_METHOD_COUNT})
public class MethodCountInspectorAbstractAbstractASMInspector extends AbstractASMInspector {

    private static final Logger logger = LoggerFactory.getLogger(MethodCountInspectorAbstractAbstractASMInspector.class);
    public static final String TAG_METHOD_COUNT = "method_count";

    public MethodCountInspectorAbstractAbstractASMInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    public String getName() {
        return "Method count inspector (BINARY)";
    }

    public String getColumnName() {
        return TAG_METHOD_COUNT;
    }

    @Override
    protected ASMClassVisitor createClassVisitor(ProjectFile projectFile, ResultDecorator resultDecorator) {
        return new MethodCountVisitor(projectFile, resultDecorator);
    }

    /**
     * ASM ClassVisitor to count methods in bytecode.
     */
    private static class MethodCountVisitor extends ASMClassVisitor {
        private int methodCount = 0;

        public MethodCountVisitor(ProjectFile projectFile, ResultDecorator resultDecorator) {
            super(projectFile, resultDecorator);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                          String[] interfaces) {
            // Reset counter for each class
            methodCount = 0;
            logger.debug("Starting method count analysis for class: {}", name);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                         String[] exceptions) {
            methodCount++;
            logger.debug("Found method: {} with descriptor: {} (count: {})", name, descriptor, methodCount);

            // Return null as we don't need to analyze method bodies
            return null;
        }

        @Override
        public void visitEnd() {
            logger.debug("Method count analysis complete. Total methods: {}", methodCount);
            setTag(TAG_METHOD_COUNT, methodCount);
        }
    }
}
