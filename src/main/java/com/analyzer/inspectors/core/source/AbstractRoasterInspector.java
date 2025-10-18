package com.analyzer.inspectors.core.source;

import com.analyzer.core.export.ProjectFileDecorator;
import com.analyzer.core.inspector.InspectorResult;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaSource;

import java.io.IOException;

/**
 * Abstract base class for source file inspectors that use JBoss Forge Roaster for analysis.
 * Provides parsed JavaSource to subclasses for code generation and manipulation-focused analysis.
 * 
 * This inspector handles the parsing of Java source files using the JBoss Forge Roaster library
 * and provides the resulting JavaSource representation to subclasses for analysis. Roaster is
 * particularly well-suited for code generation scenarios and offers a fluent API for source
 * code manipulation and analysis.
 * 
 * Subclasses must implement getName(), getColumnName(), and analyzeJavaSource() methods.
 */
public abstract class AbstractRoasterInspector extends AbstractSourceFileInspector {

    /**
     * Creates a AbstractRoasterInspector with the specified ResourceResolver.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     */
    protected AbstractRoasterInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    protected final void analyzeSourceFile(ProjectFile clazz, ResourceLocation sourceLocation, ProjectFileDecorator projectFileDecorator)
            throws IOException {
        try {
            String content = readFileContent(sourceLocation);
            
            // Parse the source code using Roaster
            Object parsedSource = Roaster.parse(content);
            
            if (parsedSource == null) {
                projectFileDecorator.error( "Failed to parse source file with Roaster");
                return ;
            }
            
            // Cast to JavaSource if it's a source representation
            if (parsedSource instanceof JavaSource) {
                JavaSource<?> javaSource = (JavaSource<?>) parsedSource;
                analyzeJavaSource(javaSource, clazz, projectFileDecorator);
                return ;
            } else {
                projectFileDecorator.error( "Roaster did not return a JavaSource object");
            }
            
        } catch (IOException e) {
            projectFileDecorator.error( "Error reading source file: " + e.getMessage());
        } catch (Exception e) {
            projectFileDecorator.error( "Roaster analysis error: " + e.getMessage());
        }
    }

    /**
     * Analyzes the Roaster JavaSource representation and returns the analysis result.
     * Subclasses implement specific Roaster analysis logic here.
     * <p>
     * The JavaSource provides a fluent API for source code analysis including:
     * - Class, interface, enum, and annotation declarations
     * - Fields, methods, and constructors with full metadata
     * - Import statements and package information
     * - Annotations and generic type information
     * - Code generation and transformation capabilities
     * <p>
     * Roaster's JavaSource is particularly powerful for scenarios involving:
     * - Code generation and templating
     * - Source code transformation and refactoring
     * - Architecture analysis requiring code structure understanding
     * - Custom code quality metrics
     *
     * @param javaSource      the Roaster JavaSource representation of the source file
     * @param clazz           the class being analyzed
     * @param projectFileDecorator
     * @return the result of Roaster analysis
     */
    protected abstract InspectorResult analyzeJavaSource(JavaSource<?> javaSource, ProjectFile clazz, ProjectFileDecorator projectFileDecorator);
}
