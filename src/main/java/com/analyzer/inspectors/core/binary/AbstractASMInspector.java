package com.analyzer.inspectors.core.binary;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstract base class for binary class inspectors that use ASM for bytecode
 * analysis.
 * Implements the template method pattern to provide a consistent framework for
 * ASM-based
 * class analysis.
 * 
 * Subclasses must implement createClassVisitor() to provide their specific
 * analysis logic.
 * The class visitor should extend ASMClassVisitor to provide the analysis
 * result.
 */
public abstract class ASMInspector extends BinaryClassInspector {

    /**
     * Creates an ASMInspector with the specified ResourceResolver.
     * 
     * @param resourceResolver the resolver for accessing class file resources
     */
    protected ASMInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    protected final InspectorResult analyzeClassFile(ProjectFile projectFile, ResourceLocation binaryLocation,
            InputStream classInputStream) throws IOException {
        try {
            // Validate input stream before ASM processing
            if (classInputStream == null) {
                return InspectorResult.error(getColumnName(), "Class input stream is null for: " + binaryLocation);
            }

            // Read all bytes first to validate content exists
            byte[] classBytes = classInputStream.readAllBytes();

            // Check for empty class files
            if (classBytes.length == 0) {
                return InspectorResult.error(getColumnName(), "Empty class file (0 bytes): " + binaryLocation);
            }

            // Check for minimum valid class file size (basic Java class is at least 100+
            // bytes)
            if (classBytes.length < 50) {
                return InspectorResult.error(getColumnName(),
                        "Class file too small (" + classBytes.length + " bytes), likely corrupted: " + binaryLocation);
            }

            // Create ClassReader from validated bytes
            ClassReader classReader = new ClassReader(classBytes);

            // Create the analysis visitor
            ASMClassVisitor visitor = createClassVisitor(projectFile);

            // Perform the analysis
            classReader.accept(visitor, 0);

            // Return the result from the visitor
            return visitor.getResult();

        } catch (IOException e) {
            return InspectorResult.error(getColumnName(), "Error reading class file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // ASM ClassReader throws IllegalArgumentException for invalid bytecode
            return InspectorResult.error(getColumnName(),
                    "Invalid bytecode format in: " + binaryLocation + " - " + e.getMessage());
        } catch (Exception e) {
            return InspectorResult.error(getColumnName(), "ASM analysis error: " + e.getMessage());
        }
    }

    /**
     * Creates a class visitor for analyzing the bytecode.
     * Subclasses must implement this method to provide their specific analysis
     * logic.
     * 
     * @param projectFile the project file being analyzed
     * @return an ASMClassVisitor that will perform the analysis
     */
    protected abstract ASMClassVisitor createClassVisitor(ProjectFile projectFile);

    /**
     * Base class for ASM class visitors that provide analysis results.
     * Subclasses should extend this class and implement their analysis logic
     * in the various visit methods, then call setResult() to store the result.
     */
    public static abstract class ASMClassVisitor extends ClassVisitor {

        private InspectorResult result;
        private final String tagName;

        /**
         * Creates an ASMClassVisitor with the specified inspector name.
         * 
         * @param tagName the name of the tag (for result identification)
         */
        protected ASMClassVisitor(String tagName) {
            super(org.objectweb.asm.Opcodes.ASM9);
            this.tagName = tagName;
        }

        /**
         * Sets the analysis result.
         * 
         * @param value the analysis result value
         */
        protected void setResult(Object value) {
            this.result = InspectorResult.success(tagName, value);
        }

        /**
         * Sets an error result.
         * 
         * @param errorMessage the error message
         */
        protected void setError(String errorMessage) {
            this.result = InspectorResult.error(tagName, errorMessage);
        }

        /**
         * Gets the analysis result.
         * Should be called after the visitor has completed its analysis.
         * 
         * @return the analysis result
         */
        public InspectorResult getResult() {
            if (result == null) {
                return InspectorResult.error(tagName, "No result set by visitor");
            }
            return result;
        }
    }
}
