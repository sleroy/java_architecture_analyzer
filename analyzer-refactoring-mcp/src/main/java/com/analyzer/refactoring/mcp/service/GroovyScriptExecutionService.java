package com.analyzer.refactoring.mcp.service;

import com.analyzer.refactoring.mcp.model.OpenRewriteVisitorScript;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.script.*;
import java.util.concurrent.*;

/**
 * Service for compiling and executing Groovy scripts with timeout protection.
 * Uses JSR-223 ScriptEngine API for Groovy script execution.
 */
@Service
public class GroovyScriptExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(GroovyScriptExecutionService.class);

    private final ScriptEngine groovyEngine;
    private final Compilable compilableEngine;
    private final int timeoutSeconds;
    private final ExecutorService executorService;

    public GroovyScriptExecutionService(
            @Value("${groovy.script.timeout.seconds:30}") int timeoutSeconds) {

        this.timeoutSeconds = timeoutSeconds;

        // Initialize Groovy script engine
        ScriptEngineManager manager = new ScriptEngineManager();
        this.groovyEngine = manager.getEngineByName("groovy");

        if (this.groovyEngine == null) {
            throw new IllegalStateException(
                    "Groovy ScriptEngine not available. Ensure groovy-jsr223 is on the classpath.");
        }

        if (!(this.groovyEngine instanceof Compilable)) {
            throw new IllegalStateException("Groovy ScriptEngine does not support compilation");
        }

        this.compilableEngine = (Compilable) this.groovyEngine;

        // Create thread pool for script execution
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("groovy-script-executor-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });

        logger.info("Groovy script execution service initialized with timeout: {}s", timeoutSeconds);
    }

    /**
     * Compile a Groovy script.
     *
     * @param scriptSource the Groovy source code
     * @return the compiled script
     * @throws ScriptCompilationException if compilation fails
     */
    public CompiledScript compileScript(String scriptSource) throws ScriptCompilationException {
        try {
            logger.debug("Compiling Groovy script ({} characters)", scriptSource.length());
            CompiledScript compiled = compilableEngine.compile(scriptSource);
            logger.debug("Script compiled successfully");
            return compiled;
        } catch (ScriptException e) {
            logger.error("Failed to compile Groovy script", e);
            throw new ScriptCompilationException("Script compilation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validate a script by executing it on a test class.
     *
     * @param script the compiled script to validate
     * @return true if validation succeeds
     * @throws ScriptExecutionException if validation fails
     */
    public boolean validateScript(CompiledScript script) throws ScriptExecutionException {
        try {
            // Create a simple test context
            ScriptContext testContext = new SimpleScriptContext();
            testContext.setBindings(createTestBindings(), ScriptContext.ENGINE_SCOPE);

            // Execute with timeout
            executeWithTimeout(() -> {
                try {
                    script.eval(testContext);
                    return null;
                } catch (ScriptException e) {
                    throw new RuntimeException("Script validation failed", e);
                }
            });

            logger.debug("Script validation successful");
            return true;

        } catch (TimeoutException e) {
            throw new ScriptExecutionException("Script validation timed out after " + timeoutSeconds + " seconds", e);
        } catch (Exception e) {
            throw new ScriptExecutionException("Script validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a compiled script with the given bindings.
     *
     * @param script   the compiled script
     * @param bindings the bindings to use during execution
     * @return the result of script execution
     * @throws ScriptExecutionException if execution fails
     */
    public Object executeScript(CompiledScript script, Bindings bindings) throws ScriptExecutionException {
        try {
            return executeWithTimeout(() -> {
                try {
                    return script.eval(bindings);
                } catch (ScriptException e) {
                    throw new RuntimeException("Script execution failed", e);
                }
            });
        } catch (TimeoutException e) {
            throw new ScriptExecutionException(
                    "Script execution timed out after " + timeoutSeconds + " seconds", e);
        } catch (Exception e) {
            throw new ScriptExecutionException("Script execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a script with the given visitor script wrapper.
     *
     * @param visitorScript the visitor script wrapper
     * @param bindings      the bindings for execution
     * @return the execution result
     * @throws ScriptExecutionException if execution fails
     */
    public Object executeVisitorScript(
            OpenRewriteVisitorScript visitorScript,
            Bindings bindings) throws ScriptExecutionException {

        logger.debug("Executing visitor script for pattern: {}", visitorScript.getPatternDescription());
        return executeScript(visitorScript.getCompiledScript(), bindings);
    }

    /**
     * Create test bindings for script validation.
     * Provides a minimal test compilation unit for validation purposes.
     */
    private Bindings createTestBindings() {
        Bindings bindings = groovyEngine.createBindings();

        try {
            // Create a simple test Java source for validation
            String testSource = """
                    package test;

                    public class TestClass {
                        public void testMethod() {
                            // Test method
                        }
                    }
                    """;

            // Parse into a compilation unit
            JavaParser javaParser = JavaParser.fromJavaVersion().build();
            J.CompilationUnit compilationUnit = javaParser.parse(testSource)
                    .findFirst()
                    .map(cu -> (J.CompilationUnit) cu)
                    .orElse(null);

            // Add required bindings for script validation
            bindings.put("compilationUnit", compilationUnit);
            bindings.put("executionContext", new InMemoryExecutionContext());

            logger.debug("Created test bindings with mock compilation unit");

        } catch (Exception e) {
            logger.warn("Failed to create test compilation unit, using null: {}", e.getMessage());
            // Provide null as fallback - some scripts might handle this
            bindings.put("compilationUnit", null);
            bindings.put("executionContext", new InMemoryExecutionContext());
        }

        return bindings;
    }

    /**
     * Execute a callable with timeout.
     */
    private <T> T executeWithTimeout(Callable<T> task)
            throws TimeoutException, ExecutionException, InterruptedException {
        Future<T> future = executorService.submit(task);

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }

    /**
     * Create script bindings with the given parameters.
     *
     * @return new bindings instance
     */
    public Bindings createBindings() {
        return groovyEngine.createBindings();
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Exception thrown when script compilation fails.
     */
    public static class ScriptCompilationException extends Exception {
        public ScriptCompilationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when script execution fails.
     */
    public static class ScriptExecutionException extends Exception {
        public ScriptExecutionException(String message, Throwable cause) {
            super(message, cause);
        }

        public ScriptExecutionException(String message) {
            super(message);
        }
    }
}
