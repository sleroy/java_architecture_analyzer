package com.analyzer.inspectors.rules.binary;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.inspectors.core.binary.ASMInspector;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.MethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inspector that counts the number of method declarations in a Java class using bytecode analysis.
 * This rule helps assess class complexity and identify classes with too many methods.
 * 
 * Uses ASM to analyze compiled bytecode instead of source code, providing more accurate
 * method counting that includes:
 * - Regular methods
 * - Constructors
 * - Static initializers
 * - Bridge methods (compiler-generated)
 * - Synthetic methods
 * 
 * Returns the total count of methods found in the class bytecode.
 */
public class MethodCountInspector extends ASMInspector {

    private static final Logger logger = LoggerFactory.getLogger(MethodCountInspector.class);

    public MethodCountInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    public String getName() {
        return "method-count";
    }

    @Override
    public String getColumnName() {
        return "method_count";
    }

    @Override
    public String getDescription() {
        return "Counts the number of method declarations in a Java class using bytecode analysis";
    }

    @Override
    protected ASMClassVisitor createClassVisitor(Clazz clazz) {
        return new MethodCountVisitor(getName());
    }

    /**
     * ASM ClassVisitor to count methods in bytecode.
     */
    private static class MethodCountVisitor extends ASMClassVisitor {
        private int methodCount = 0;

        public MethodCountVisitor(String inspectorName) {
            super(inspectorName);
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
            setResult(methodCount);
        }
    }
}
