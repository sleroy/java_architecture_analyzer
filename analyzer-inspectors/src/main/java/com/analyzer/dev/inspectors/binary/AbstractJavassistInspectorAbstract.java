package com.analyzer.dev.inspectors.binary;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.api.resource.ResourceResolver;
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
public abstract class AbstractJavassistInspectorAbstract extends AbstractBinaryClassInspector {

    private final ClassPool classPool;

    /**
     * Creates a AbstractJavassistInspectorAbstract with the default ClassPool.
     * 
     * @param resourceResolver the resolver for accessing class file resources
     */
    protected AbstractJavassistInspectorAbstract(ResourceResolver resourceResolver, LocalCache localCache) {
        super(resourceResolver, localCache);
        this.classPool = ClassPool.getDefault();
    }

    /**
     * Creates a AbstractJavassistInspectorAbstract with a custom ClassPool.
     * This allows subclasses to customize class loading behavior, add classpath entries, etc.
     * 
     * @param resourceResolver the resolver for accessing class file resources
     * @param customClassPool the customized ClassPool instance to use
     */
    protected AbstractJavassistInspectorAbstract(ResourceResolver resourceResolver, ClassPool customClassPool, LocalCache localCache) {
        super(resourceResolver, localCache);
        if (customClassPool == null) {
            throw new IllegalArgumentException("ClassPool cannot be null");
        }
        this.classPool = customClassPool;
    }

    @Override
    protected final void analyzeClassFile(ProjectFile clazz, ResourceLocation binaryLocation,
                                          InputStream classInputStream, NodeDecorator<ProjectFile> projectFileDecorator) throws IOException {
        try {
            // Read class bytes and create CtClass
            byte[] classBytes = classInputStream.readAllBytes();
            CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classBytes));
            
            analyzeCtClass(ctClass, clazz, projectFileDecorator);
            
        } catch (IOException e) {
            projectFileDecorator.error( "Error reading class file: " + e.getMessage());
        } catch (RuntimeException e) {
            projectFileDecorator.error( "Javassist runtime error: " + e.getMessage());
        } catch (Exception e) {
            projectFileDecorator.error( "Javassist analysis error: " + e.getMessage());
        }
    }

    /**
     * Analyzes the Javassist CtClass representation and returns the analysis result.
     * Subclasses implement specific Javassist analysis logic here.
     * <p>
     * The CtClass provides a high-level, reflection-like interface to bytecode including:
     * - Class metadata and modifiers
     * - Fields and methods with their signatures
     * - Annotations and generic information
     * - Simple bytecode manipulation capabilities
     * - Runtime-oriented analysis features
     * <p>
     * Javassist's CtClass offers the simplest API among bytecode libraries,
     * making it ideal for straightforward analysis tasks and runtime scenarios.
     *
     * @param ctClass         the Javassist CtClass representation of the bytecode
     * @param clazz           the class being analyzed
     * @param projectFileDecorator
     * @return the result of bytecode analysis
     */
    protected abstract void analyzeCtClass(CtClass ctClass, ProjectFile clazz, NodeDecorator<ProjectFile> projectFileDecorator);

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
