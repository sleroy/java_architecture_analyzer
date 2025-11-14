package com.analyzer.refactoring.mcp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for loading and executing Groovy script templates with parameters.
 * 
 * Instead of string substitution, this service uses Groovy's Binding mechanism
 * to pass parameters to scripts, which is more type-safe and follows Groovy best practices.
 * 
 * Templates can access parameters as variables directly:
 * <pre>
 * // In template:
 * String targetAnnotation = annotationName  // Parameter passed via binding
 * </pre>
 */
@Service
public class GroovyTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(GroovyTemplateService.class);

    private final ResourceLoader resourceLoader;
    private final GroovyScriptExecutionService executionService;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public GroovyTemplateService(
            ResourceLoader resourceLoader,
            GroovyScriptExecutionService executionService) {
        this.resourceLoader = resourceLoader;
        this.executionService = executionService;
    }

    @PostConstruct
    public void initialize() {
        logger.info("Groovy template service initialized with parameter binding support");
    }

    /**
     * Load a template and prepare it for execution with parameters.
     * 
     * The template will have access to parameters as variables through Groovy's binding.
     * Also ensures the compilationUnit is available in the binding.
     * 
     * @param templateName name of the template file (e.g., "annotation-class-finder.groovy")
     * @return CompiledScript ready for execution with parameters
     * @throws TemplateException if template cannot be loaded or compiled
     */
    public CompiledScript loadTemplate(String templateName) throws TemplateException {
        try {
            String templateSource = loadTemplateSource(templateName);
            return executionService.compileScript(templateSource);
        } catch (IOException e) {
            throw new TemplateException("Failed to load template: " + templateName, e);
        } catch (GroovyScriptExecutionService.ScriptCompilationException e) {
            throw new TemplateException("Failed to compile template: " + templateName, e);
        }
    }

    /**
     * Execute a compiled template with the given parameters and compilation unit.
     * 
     * Parameters are passed to the script via Groovy's Binding mechanism:
     * - Parameters from the params map are available as variables
     * - compilationUnit is automatically available
     * 
     * @param compiledTemplate the compiled template script
     * @param params map of parameter names to values
     * @param compilationUnit the OpenRewrite compilation unit to analyze
     * @return the result returned by the template script
     * @throws TemplateException if execution fails
     */
    @SuppressWarnings("unchecked")
    public <T> T executeTemplate(
            CompiledScript compiledTemplate,
            Map<String, Object> params,
            Object compilationUnit) throws TemplateException {
        
        try {
            // Create bindings for parameters
            Bindings bindings = compiledTemplate.getEngine().createBindings();
            
            // Add all user parameters
            if (params != null) {
                bindings.putAll(params);
            }
            
            // Add compilation unit (required for all templates)
            bindings.put("compilationUnit", compilationUnit);
            
            // Execute with bindings
            compiledTemplate.getEngine().setBindings(bindings, ScriptContext.ENGINE_SCOPE);
            Object result = compiledTemplate.eval(bindings);
            
            logger.debug("Template executed successfully, result type: {}", 
                        result != null ? result.getClass().getSimpleName() : "null");
            
            return (T) result;
            
        } catch (Exception e) {
            throw new TemplateException("Failed to execute template: " + e.getMessage(), e);
        }
    }

    /**
     * Load a template source from the classpath.
     * Templates are cached after first load.
     * 
     * @param templateName name of the template file
     * @return the template source code
     * @throws IOException if template cannot be loaded
     */
    private String loadTemplateSource(String templateName) throws IOException {
        return templateCache.computeIfAbsent(templateName, name -> {
            try {
                String resourcePath = "classpath:groovy-templates/" + name;
                Resource resource = resourceLoader.getResource(resourcePath);
                
                if (!resource.exists()) {
                    throw new IOException("Template not found: " + resourcePath);
                }
                
                String content = new String(
                    resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
                );
                
                logger.debug("Loaded template: {} ({} chars)", name, content.length());
                return content;
                
            } catch (IOException e) {
                throw new RuntimeException("Failed to load template: " + name, e);
            }
        });
    }

    /**
     * Load and execute a template in one call (convenience method).
     * 
     * @param templateName name of the template file
     * @param params parameters to pass to the template
     * @param compilationUnit the compilation unit to analyze
     * @return the result from the template
     * @throws TemplateException if loading or execution fails
     */
    public <T> T loadAndExecute(
            String templateName,
            Map<String, Object> params,
            Object compilationUnit) throws TemplateException {
        
        CompiledScript compiled = loadTemplate(templateName);
        return executeTemplate(compiled, params, compilationUnit);
    }

    /**
     * Clear the template cache.
     * Useful for development/testing when templates are being modified.
     */
    public void clearCache() {
        templateCache.clear();
        logger.info("Template cache cleared");
    }

    /**
     * Get cache statistics.
     */
    public Map<String, Object> getCacheStats() {
        return Map.of(
            "cachedTemplates", templateCache.size(),
            "templateNames", templateCache.keySet()
        );
    }

    /**
     * Exception thrown when template operations fail.
     */
    public static class TemplateException extends Exception {
        public TemplateException(String message) {
            super(message);
        }

        public TemplateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
