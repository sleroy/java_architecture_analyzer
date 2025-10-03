package com.analyzer.inspectors.core.source;

import com.analyzer.core.Clazz;
import com.analyzer.core.InspectorResult;
import com.analyzer.resource.ResourceLocation;
import com.analyzer.resource.ResourceResolver;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;

/**
 * Abstract base class for source file inspectors that use JavaParser for AST-based analysis.
 * Provides parsed CompilationUnit to subclasses for sophisticated source code analysis.
 * 
 * This inspector handles the parsing of Java source files using the JavaParser library
 * and provides the resulting AST to subclasses for analysis. It includes comprehensive
 * error handling for parse failures and malformed source files.
 * 
 * Subclasses must implement getName(), getColumnName(), and analyzeCompilationUnit() methods.
 */
public abstract class JavaParserInspector extends SourceFileInspector {

    private final JavaParser javaParser;

    /**
     * Creates a JavaParserInspector with a default JavaParser configuration.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     */
    protected JavaParserInspector(ResourceResolver resourceResolver) {
        super(resourceResolver);
        this.javaParser = new JavaParser();
    }

    /**
     * Creates a JavaParserInspector with a custom JavaParser configuration.
     * This allows subclasses to customize parsing behavior, symbol resolution, etc.
     * 
     * @param resourceResolver the resolver for accessing source file resources
     * @param customParser the customized JavaParser instance to use
     */
    protected JavaParserInspector(ResourceResolver resourceResolver, JavaParser customParser) {
        super(resourceResolver);
        if (customParser == null) {
            throw new IllegalArgumentException("JavaParser cannot be null");
        }
        this.javaParser = customParser;
    }

    @Override
    protected final InspectorResult analyzeSourceFile(Clazz clazz, ResourceLocation sourceLocation) 
            throws IOException {
        try {
            String content = readFileContent(sourceLocation);
            ParseResult<CompilationUnit> parseResult = javaParser.parse(content);
            
            // Check for parse errors
            if (!parseResult.isSuccessful()) {
                String problems = parseResult.getProblems().toString();
                return InspectorResult.error(getName(), "Parse errors: " + problems);
            }
            
            // Get the parsed compilation unit
            CompilationUnit cu = parseResult.getResult().orElse(null);
            if (cu == null) {
                return InspectorResult.error(getName(), "Failed to parse compilation unit");
            }
            
            return analyzeCompilationUnit(cu, clazz);
            
        } catch (IOException e) {
            return InspectorResult.error(getName(), "Error reading source file: " + e.getMessage());
        } catch (Exception e) {
            return InspectorResult.error(getName(), "JavaParser error: " + e.getMessage());
        }
    }

    /**
     * Analyzes the parsed compilation unit and returns the analysis result.
     * Subclasses implement specific AST analysis logic here.
     * 
     * The CompilationUnit provides access to the complete AST of the parsed Java file,
     * allowing for sophisticated analysis of classes, methods, fields, annotations,
     * imports, and all other Java language constructs.
     * 
     * @param cu the parsed CompilationUnit representing the source file's AST
     * @param clazz the class being analyzed
     * @return the result of AST analysis
     */
    protected abstract InspectorResult analyzeCompilationUnit(CompilationUnit cu, Clazz clazz);

    /**
     * Gets the JavaParser instance used by this inspector.
     * Subclasses can use this to access parser configuration or perform additional parsing.
     * 
     * @return the JavaParser instance
     */
    protected JavaParser getJavaParser() {
        return javaParser;
    }
}
