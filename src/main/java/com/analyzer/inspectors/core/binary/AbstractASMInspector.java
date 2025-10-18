package com.analyzer.inspectors.core.binary;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.model.ProjectFile;
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
 * <p>
 * Subclasses must implement createClassVisitor() to provide their specific
 * analysis logic.
 * The class visitor should extend ASMClassVisitor to provide the analysis
 * result.
 */

public abstract class AbstractASMInspector extends AbstractBinaryClassInspector {

    /**
     * Creates an AbstractASMInspector with the specified ResourceResolver.
     *
     * @param resourceResolver the resolver for accessing class file resources
     */
    protected AbstractASMInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    protected final void analyzeClassFile(ProjectFile projectFile, ResourceLocation binaryLocation,
                                          InputStream classInputStream, ProjectFileDecorator projectFileDecorator) throws IOException {
        try {
            // Validate input stream before ASM processing
            if (classInputStream == null) {
                projectFileDecorator.error("Class input stream is null for: " + binaryLocation);
                return;
            }

            // Read all bytes first to validate content exists
            byte[] classBytes = classInputStream.readAllBytes();

            // Check for empty class files
            if (classBytes.length == 0) {
                projectFileDecorator.error("Empty class file (0 bytes): " + binaryLocation);
                return;
            }

            // Check for minimum valid class file size (basic Java class is at least 100+
            // bytes)
            if (classBytes.length < 50) {
                projectFileDecorator.error(
                        "Class file too small (" + classBytes.length + " bytes), likely corrupted: " + binaryLocation);
                return;
            }

            // Create ClassReader from validated bytes
            ClassReader classReader = new ClassReader(classBytes);

            // Create the analysis visitor
            ASMClassVisitor visitor = createClassVisitor(projectFile, projectFileDecorator);

            // Perform the analysis
            classReader.accept(visitor, 0);

        } catch (IOException e) {
            projectFileDecorator.error("Error reading class file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // ASM ClassReader throws IllegalArgumentException for invalid bytecode
            projectFileDecorator.error(
                    "Invalid bytecode format in: " + binaryLocation + " - " + e.getMessage());
        } catch (Exception e) {
            projectFileDecorator.error("ASM analysis error: " + e.getMessage());
        }
    }

    /**
     * Creates a class visitor for analyzing the bytecode.
     * Subclasses must implement this method to provide their specific analysis
     * logic.
     *
     * @param projectFile     the project file being analyzed
     * @param projectFileDecorator
     * @return an ASMClassVisitor that will perform the analysis
     */
    protected abstract ASMClassVisitor createClassVisitor(ProjectFile projectFile, ProjectFileDecorator projectFileDecorator);

    /**
     * Base class for ASM class visitors that store analysis results using ProjectFileDecorator.
     * Subclasses should extend this class and implement their analysis logic
     * in the various visit methods, then use setTag() to store results or reportError() for errors.
     */
    public static abstract class ASMClassVisitor extends ClassVisitor {

        protected final ProjectFile projectFile;
        protected final ProjectFileDecorator projectFileDecorator;

        /**
         * Creates an ASMClassVisitor with the specified project file and result decorator.
         *
         * @param projectFile the project file being analyzed
         * @param projectFileDecorator the result decorator for storing results
         */
        protected ASMClassVisitor(ProjectFile projectFile, ProjectFileDecorator projectFileDecorator) {
            super(org.objectweb.asm.Opcodes.ASM9);
            this.projectFile = projectFile;
            this.projectFileDecorator = projectFileDecorator;
        }

        /**
         * Sets a tag value on the project file.
         *
         * @param tagName the tag name
         * @param value the tag value
         */
        protected void setTag(String tagName, Object value) {
            projectFileDecorator.setTag(tagName, value);
        }

        /**
         * Reports an error using the result decorator.
         *
         * @param errorMessage the error message
         */
        protected void reportError(String errorMessage) {
            projectFileDecorator.error(errorMessage);
        }
    }
}
