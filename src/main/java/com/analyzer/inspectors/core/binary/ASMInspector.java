package com.analyzer.inspectors.core.binary;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstract base class for binary class inspectors that use ASM for bytecode analysis.
 * Implements the template method pattern to provide a consistent framework for ASM-based
 * class analysis.
 * 
 * Subclasses must implement createClassVisitor() to provide their specific analysis logic.
 * The class visitor should extend ASMClassVisitor to provide the analysis result.
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
    protected final InspectorResult analyzeClassFile(Clazz clazz, ResourceLocation binaryLocation,
            InputStream classInputStream) throws IOException {
        try {
            // Read the class bytes using ASM
            ClassReader classReader = new ClassReader(classInputStream);
            
            // Create the analysis visitor
            ASMClassVisitor visitor = createClassVisitor(clazz);
            
            // Perform the analysis
            classReader.accept(visitor, 0);
            
            // Return the result from the visitor
            return visitor.getResult();
            
        } catch (IOException e) {
            return InspectorResult.error(getColumnName(), "Error reading class file: " + e.getMessage());
        } catch (Exception e) {
            return InspectorResult.error(getColumnName(), "ASM analysis error: " + e.getMessage());
        }
    }

    /**
     * Creates a class visitor for analyzing the bytecode.
     * Subclasses must implement this method to provide their specific analysis logic.
     * 
     * @param clazz the class being analyzed
     * @return an ASMClassVisitor that will perform the analysis
     */
    protected abstract ASMClassVisitor createClassVisitor(Clazz clazz);

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
            this.result = new InspectorResult(tagName, value);
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
