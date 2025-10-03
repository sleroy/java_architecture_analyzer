package com.analyzer.inspectors.binary;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstract base class for inspectors that use the ASM library to analyze class
 * files.
 * Provides common ASM functionality that can be reused by specific ASM-based
 * inspectors.
 * 
 * This class handles:
 * - Opening and reading class files using ResourceResolver
 * - Creating ASM ClassReader instances
 * - Providing template methods for ASM-based analysis
 * - Error handling for ASM operations
 */
public abstract class ASMInspector extends BinaryClassInspector {

    private static final Logger logger = LoggerFactory.getLogger(ASMInspector.class);

    public ASMInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    /**
     * Template method that handles the common ASM workflow:
     * 1. Opens the class file stream
     * 2. Creates an ASM ClassReader
     * 3. Calls the abstract method to create a ClassVisitor
     * 4. Accepts the visitor to parse the bytecode
     * 5. Returns the analysis result
     */
    @Override
    protected final InspectorResult analyzeClassFile(Clazz clazz, ResourceLocation binaryLocation,
            InputStream classInputStream) throws IOException {
        try {
            logger.debug("Starting ASM analysis for class: {}", clazz.getClassName());

            // Read all bytes from the stream
            byte[] classBytes = classInputStream.readAllBytes();
            logger.debug("Read {} bytes from class file", classBytes.length);

            // Create ASM ClassReader
            ClassReader classReader = new ClassReader(classBytes);
            logger.debug("Created ASM ClassReader for class: {}", classReader.getClassName());

            // Create the specific ClassVisitor implementation
            ASMClassVisitor visitor = createClassVisitor(clazz);

            // Parse the class file using ASM
            classReader.accept(visitor, ClassReader.SKIP_DEBUG);
            logger.debug("ASM analysis completed for class: {}", clazz.getClassName());

            // Get the result from the visitor
            return visitor.getResult();

        } catch (IllegalArgumentException e) {
            logger.error("Invalid class file format for {}: {}", clazz.getClassName(), e.getMessage());
            return InspectorResult.error(getName(), "Invalid class format: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during ASM analysis of class {}: {}", clazz.getClassName(), e.getMessage(),
                    e);
            return InspectorResult.error(getName(), "Analysis error: " + e.getMessage());
        }
    }

    /**
     * Abstract method that subclasses must implement to create their specific
     * ClassVisitor.
     * This visitor will be used to extract the required information from the class
     * file.
     * 
     * @param clazz The class being analyzed
     * @return A ClassVisitor implementation that will extract the required data
     */
    protected abstract ASMClassVisitor createClassVisitor(Clazz clazz);

    /**
     * Abstract base class for ClassVisitor implementations used by ASMInspector
     * subclasses.
     * Provides a common structure for storing and retrieving analysis results.
     */
    public abstract static class ASMClassVisitor extends ClassVisitor {

        protected final String inspectorName;
        protected final Clazz clazz;
        protected InspectorResult result;

        public ASMClassVisitor(String inspectorName, Clazz clazz) {
            super(Opcodes.ASM9);
            this.inspectorName = inspectorName;
            this.clazz = clazz;
        }

        /**
         * Gets the analysis result. Should be called after the ClassReader has accepted
         * this visitor.
         * 
         * @return The result of the analysis, or an error result if analysis failed
         */
        public final InspectorResult getResult() {
            if (result == null) {
                return InspectorResult.error(inspectorName, "Analysis not completed");
            }
            return result;
        }

        /**
         * Sets a successful result with the given value.
         */
        protected final void setSuccessResult(Object value) {
            this.result = new InspectorResult(inspectorName, value);
        }

        /**
         * Sets an error result with the given message.
         */
        protected final void setErrorResult(String errorMessage) {
            this.result = InspectorResult.error(inspectorName, errorMessage);
        }

        /**
         * Sets a not-applicable result.
         */
        protected final void setNotApplicableResult() {
            this.result = InspectorResult.notApplicable(inspectorName);
        }
    }
}
