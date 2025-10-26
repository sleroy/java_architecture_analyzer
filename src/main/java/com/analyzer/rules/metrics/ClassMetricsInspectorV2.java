package com.analyzer.rules.metrics;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.graph.JavaClassNode;
import com.analyzer.core.graph.ProjectFileRepository;
import com.analyzer.core.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.inspectors.core.binary.AbstractASMClassInspector;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;




/**
 * Class-centric metrics inspector that analyzes JavaClassNode using ASM bytecode analysis.
 * This is the Phase 2 proof-of-concept migration demonstrating the new architecture.
 * <p>
 * Key Differences from ClassMetricsInspector:
 * - Extends AbstractASMClassInspector (class-centric) instead of AbstractASMInspector (file-centric)
 * - Receives JavaClassNode as input instead of ProjectFile
 * - Writes metrics directly to JavaClassNode properties
 * - Uses NodeDecorator<JavaClassNode> for property aggregation
 * <p>
 * Metrics Calculated:
 * - Method Count: Total number of methods in the class
 * - Field Count: Total number of fields
 * - Cyclomatic Complexity: Sum of all method complexities (WMC)
 * - Weighted Methods Per Class (WMC): Same as cyclomatic complexity
 * - Efferent Coupling (Ce): Number of classes this class depends on
 *
 * @since Phase 2 - Class-Centric Architecture Refactoring
 */
@InspectorDependencies(
        requires = {},  // Will be populated based on dependencies
        produces = {}   // Produces properties on JavaClassNode, not tags
)
public class ClassMetricsInspectorV2 extends AbstractASMClassInspector {

    @Inject
    public ClassMetricsInspectorV2(ProjectFileRepository projectFileRepository,
                                   ResourceResolver resourceResolver) {
        super(projectFileRepository, resourceResolver);
    }

    @Override
    public String getName() {
        return "Class Metrics Inspector V2 (Class-Centric ASM)";
    }

    @Override
    protected ASMClassNodeVisitor createClassVisitor(JavaClassNode classNode,
                                                     NodeDecorator<JavaClassNode> decorator) {
        return new ClassMetricsVisitor(classNode, decorator);
    }

    /**
     * ASM visitor that collects class metrics during bytecode traversal.
     * Calculates method count, field count, complexity, and coupling metrics.
     */
    private static class ClassMetricsVisitor extends ASMClassNodeVisitor {
        private int methodCount = 0;
        private int fieldCount = 0;
        private final List<CyclomaticComplexityMethodVisitor> methodVisitors = new ArrayList<>();
        private final Set<String> efferentCouplings = new HashSet<>();
        private String className;

        protected ClassMetricsVisitor(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator) {
            super(classNode, decorator);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.className = name.replace('/', '.');

            // Track coupling to superclass
            if (superName != null) {
                addCoupling(Type.getObjectType(superName).getClassName());
            }

            // Track coupling to interfaces
            if (interfaces != null) {
                for (String anInterface : interfaces) {
                    addCoupling(Type.getObjectType(anInterface).getClassName());
                }
            }

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor,
                                                         String signature, Object value) {
            fieldCount++;

            // Track coupling from field type
            addCoupling(Type.getType(descriptor).getClassName());

            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            methodCount++;
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

            // Create complexity visitor and store for later aggregation
            CyclomaticComplexityMethodVisitor complexityVisitor =
                    new CyclomaticComplexityMethodVisitor(mv);
            methodVisitors.add(complexityVisitor);

            // Track coupling from method signature
            Type methodType = Type.getMethodType(descriptor);
            addCoupling(methodType.getReturnType().getClassName());
            for (Type argType : methodType.getArgumentTypes()) {
                addCoupling(argType.getClassName());
            }

            return complexityVisitor;
        }

        @Override
        public void visitEnd() {
            // Calculate WMC (Weighted Methods per Class) by summing method complexities
            int weightedMethodsPerClass = 0;
            for (CyclomaticComplexityMethodVisitor visitor : methodVisitors) {
                weightedMethodsPerClass += visitor.getComplexity();
            }

            // Write metrics to JavaClassNode using decorator
            // This is the KEY DIFFERENCE: metrics go to JavaClassNode, not ProjectFile
            setProperty(JavaClassNode.PROP_METHOD_COUNT, methodCount);
            setProperty(JavaClassNode.PROP_FIELD_COUNT, fieldCount);
            setProperty(JavaClassNode.PROP_CYCLOMATIC_COMPLEXITY, weightedMethodsPerClass);
            setProperty(JavaClassNode.PROP_WEIGHTED_METHODS, weightedMethodsPerClass);
            setProperty(JavaClassNode.PROP_EFFERENT_COUPLING, efferentCouplings.size());

            super.visitEnd();
        }

        /**
         * Adds a coupling to another class, filtering out java.* packages and self-references.
         */
        private void addCoupling(String className) {
            if (className != null &&
                    !className.startsWith("java.") &&
                    !className.equals(this.className)) {
                efferentCouplings.add(className);
            }
        }
    }

    /**
     * Method visitor that calculates cyclomatic complexity for a single method.
     * Complexity is calculated by counting decision points (if, switch, loops, etc.).
     */
    private static class CyclomaticComplexityMethodVisitor extends MethodVisitor {
        private int complexity = 1;  // Base complexity is 1

        public CyclomaticComplexityMethodVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
            // Count conditional jumps (if statements)
            if (opcode == Opcodes.IF_ICMPEQ || opcode == Opcodes.IF_ICMPNE ||
                    opcode == Opcodes.IF_ICMPLT || opcode == Opcodes.IF_ICMPGE ||
                    opcode == Opcodes.IF_ICMPGT || opcode == Opcodes.IF_ICMPLE ||
                    opcode == Opcodes.IF_ACMPEQ || opcode == Opcodes.IF_ACMPNE ||
                    opcode == Opcodes.IFEQ || opcode == Opcodes.IFNE ||
                    opcode == Opcodes.IFLT || opcode == Opcodes.IFGE ||
                    opcode == Opcodes.IFGT || opcode == Opcodes.IFLE ||
                    opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL) {
                complexity++;
            }
            super.visitJumpInsn(opcode, label);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, org.objectweb.asm.Label dflt,
                                         org.objectweb.asm.Label... labels) {
            // Each case in a switch adds to complexity
            complexity += labels.length;
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }

        @Override
        public void visitLookupSwitchInsn(org.objectweb.asm.Label dflt, int[] keys,
                                          org.objectweb.asm.Label[] labels) {
            // Each case in a switch adds to complexity
            complexity += labels.length;
            super.visitLookupSwitchInsn(dflt, keys, labels);
        }

        public int getComplexity() {
            return complexity;
        }
    }
}
