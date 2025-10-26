package com.analyzer.dev.inspectors.source;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;

/**
 * Abstract base class for source file inspectors that use JavaParser for
 * AST-based analysis.
 * Provides parsed CompilationUnit to subclasses for sophisticated source code
 * analysis.
 * 
 * <p>
 * This inspector automatically inherits the SOURCE_FILE dependency from
 * AbstractSourceFileInspector
 * and adds Java-specific dependencies to ensure JavaParser-based analysis only
 * runs on files
 * that have been:
 * </p>
 * <ul>
 * <li>Identified as source files by SourceFileDetector</li>
 * <li>Detected as Java language files</li>
 * <li>Confirmed to have Java source code available</li>
 * </ul>
 * 
 * <p>
 * This inspector handles the parsing of Java source files using the JavaParser
 * library and provides the resulting AST to subclasses for analysis. It
 * includes
 * comprehensive error handling for parse failures and malformed source files.
 * </p>
 * 
 * <p>
 * Subclasses must implement getName() and analyzeCompilationUnit() methods.
 * </p>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_DETECTED }, produces = {})
public abstract class AbstractJavaParserInspector extends AbstractSourceFileInspector {

    private final JavaParser javaParser;

    /**
     * Creates a AbstractJavaParserInspector with a default JavaParser
     * configuration.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     */
    protected AbstractJavaParserInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
        this.javaParser = new JavaParser();
    }

    /**
     * Creates a AbstractJavaParserInspector with a custom JavaParser configuration.
     * This allows subclasses to customize parsing behavior, symbol resolution, etc.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     * @param customParser     the customized JavaParser instance to use
     */
    protected AbstractJavaParserInspector(ResourceResolver resourceResolver, JavaParser customParser) {
        super(resourceResolver);
        if (customParser == null) {
            throw new IllegalArgumentException("JavaParser cannot be null");
        }
        this.javaParser = customParser;
    }

    @Override
    protected final void analyzeSourceFile(ProjectFile clazz, ResourceLocation sourceLocation,
            NodeDecorator<ProjectFile> decorator)
            throws IOException {
        try {
            String content = readFileContent(sourceLocation);
            ParseResult<CompilationUnit> parseResult = javaParser.parse(content);

            // Check for parse errors
            if (!parseResult.isSuccessful()) {
                String problems = parseResult.getProblems().toString();
                decorator.error("Parse errors: " + problems);
                return;
            }

            // Get the parsed compilation unit
            CompilationUnit cu = parseResult.getResult().orElse(null);
            if (cu == null) {
                decorator.error("Failed to parse compilation unit");
                return;
            }

            analyzeCompilationUnit(cu, clazz, decorator);

        } catch (IOException e) {
            decorator.error("Error reading source file: " + e.getMessage());
        } catch (Exception e) {
            decorator.error("JavaParser error: " + e.getMessage());
        }
    }

    /**
     * Analyzes the parsed compilation unit using the provided NodeDecorator.
     * Subclasses implement specific AST analysis logic here.
     * <p>
     * The CompilationUnit provides access to the complete AST of the parsed Java
     * file,
     * allowing for sophisticated analysis of classes, methods, fields, annotations,
     * imports, and all other Java language constructs.
     * </p>
     * <p>
     * Subclasses should use decorator methods to store analysis results
     * and decorator.error() to report errors.
     * </p>
     *
     * @param cu          the parsed CompilationUnit representing the source file's
     *                    AST
     * @param projectFile the project file being analyzed
     * @param decorator   the decorator for setting properties and tags
     */
    protected abstract void analyzeCompilationUnit(CompilationUnit cu, ProjectFile projectFile,
            NodeDecorator<ProjectFile> decorator);

    /**
     * Gets the JavaParser instance used by this inspector.
     * Subclasses can use this to access parser configuration or perform additional
     * parsing.
     * 
     * @return the JavaParser instance
     */
    protected JavaParser getJavaParser() {
        return javaParser;
    }

}
