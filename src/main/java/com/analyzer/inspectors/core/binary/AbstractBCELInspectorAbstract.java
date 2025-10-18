package com.analyzer.inspectors.core.binary;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.inspector.InspectorResult;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstract base class for binary class inspectors that use Apache BCEL for bytecode analysis.
 * Provides parsed JavaClass to subclasses for comprehensive bytecode examination.
 * 
 * This inspector handles the parsing of Java class files using the Apache BCEL library
 * and provides the resulting JavaClass representation to subclasses for analysis.
 * BCEL offers high-level abstractions for bytecode analysis and is an excellent
 * alternative to ASM for many use cases.
 * 
 * Subclasses must implement getName(), getColumnName(), and analyzeJavaClass() methods.
 */
public abstract class AbstractBCELInspectorAbstract extends AbstractBinaryClassInspector {

    /**
     * Creates a AbstractBCELInspectorAbstract with the specified ResourceResolver.
     * 
     * @param resourceResolver the resolver for accessing class file resources
     */
    protected AbstractBCELInspectorAbstract(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    protected final void analyzeClassFile(ProjectFile clazz, ResourceLocation binaryLocation,
                                          InputStream classInputStream, ProjectFileDecorator projectFileDecorator) throws IOException {
        try {
            // Parse class file using BCEL
            ClassParser classParser = new ClassParser(classInputStream, clazz.getClassName());
            JavaClass javaClass = classParser.parse();
            
            analyzeJavaClass(javaClass, clazz, projectFileDecorator);
            
        } catch (ClassFormatException e) {
            projectFileDecorator.error("BCEL class format error: " + e.getMessage());
        } catch (IOException e) {
            projectFileDecorator.error( "Error reading class file: " + e.getMessage());
        } catch (Exception e) {
            projectFileDecorator.error( "BCEL analysis error: " + e.getMessage());
        }
    }

    /**
     * Analyzes the BCEL JavaClass representation and returns the analysis result.
     * Subclasses implement specific BCEL analysis logic here.
     * <p>
     * The JavaClass provides high-level access to bytecode information including:
     * - Class metadata (modifiers, superclass, interfaces)
     * - Fields and their attributes
     * - Methods and their bytecode
     * - Annotations and attributes
     * - Constant pool information
     * <p>
     * BCEL's JavaClass offers more abstraction than ASM's raw bytecode visiting,
     * making it easier to implement certain types of analysis.
     *
     * @param javaClass       the BCEL JavaClass representation of the bytecode
     * @param clazz           the class being analyzed
     * @param projectFileDecorator
     * @return the result of bytecode analysis
     */
    protected abstract InspectorResult analyzeJavaClass(JavaClass javaClass, ProjectFile clazz, ProjectFileDecorator projectFileDecorator);
}
