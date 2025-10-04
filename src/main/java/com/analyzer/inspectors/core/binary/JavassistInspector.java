package com.analyzer.inspectors.core.binary;

import com.analyzer.core.ProjectFile;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import javassist.ClassPool;
import javassist.CtClass;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Abstract base class for binary class inspectors that use Javassist for bytecode analysis.
 * Provides parsed CtClass to subclasses for runtime-focused bytecode examination.
 * 
 * This inspector handles the parsing of Java class files using the Javassist library
 * and provides the resulting CtClass representation to subclasses for analysis.
 * Javassist offers a simpler, reflection-like API for bytecode manipulation and is
 * particularly well-suited for runtime scenarios and dynamic class modification.
 * 
 * Subclasses must implement getName(), getColumnName(), and analyzeCtClass() methods.
 */
public abstract class JavassistInspector extends BinaryClassInspector {

    private final ClassPool classPool;

    /**
     * Creates a JavassistInspector with the default ClassPool.
     * 
     * @param resourceResolver the resolver for accessing class file resources
     */
    protected JavassistInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
        this.classPool = ClassPool.getDefault();
    }

    /**
     * Creates a JavassistInspector with a custom ClassPool.
     * This allows subclasses to customize class loading behavior, add classpath entries, etc.
     * 
     * @param resourceResolver the resolver for accessing class file resources
     * @param customClassPool the customized ClassPool instance to use
     */
    protected JavassistInspector(ResourceResolver resourceResolver, ClassPool customClassPool) {
        super(resourceResolver);
        if (customClassPool == null) {
            throw new IllegalArgumentException("ClassPool cannot be null");
        }
        this.classPool = customClassPool;
    }

    @Override
    protected final InspectorResult analyzeClassFile(ProjectFile clazz, ResourceLocation binaryLocation,
                    InputStream classInputStream) throws IOException {
        try {
            // Read class bytes and create CtClass
            byte[] classBytes = classInputStream.readAllBytes();
            CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classBytes));
            
            return analyzeCtClass(ctClass, clazz);
            
        } catch (IOException e) {
            return InspectorResult.error(getColumnName(), "Error reading class file: " + e.getMessage());
        } catch (RuntimeException e) {
            return InspectorResult.error(getColumnName(), "Javassist runtime error: " + e.getMessage());
        } catch (Exception e) {
            return InspectorResult.error(getColumnName(), "Javassist analysis error: " + e.getMessage());
        }
    }

    /**
     * Analyzes the Javassist CtClass representation and returns the analysis result.
     * Subclasses implement specific Javassist analysis logic here.
     * 
     * The CtClass provides a high-level, reflection-like interface to bytecode including:
     * - Class metadata and modifiers
     * - Fields and methods with their signatures
     * - Annotations and generic information
     * - Simple bytecode manipulation capabilities
     * - Runtime-oriented analysis features
     * 
     * Javassist's CtClass offers the simplest API among bytecode libraries,
     * making it ideal for straightforward analysis tasks and runtime scenarios.
     * 
     * @param ctClass the Javassist CtClass representation of the bytecode
     * @param clazz the class being analyzed
     * @return the result of bytecode analysis
     */
    protected abstract InspectorResult analyzeCtClass(CtClass ctClass, ProjectFile clazz);

    /**
     * Gets the ClassPool used by this inspector.
     * Subclasses can use this to load additional classes or customize class loading.
     * 
     * @return the ClassPool instance
     */
    protected ClassPool getClassPool() {
        return classPool;
    }
}
