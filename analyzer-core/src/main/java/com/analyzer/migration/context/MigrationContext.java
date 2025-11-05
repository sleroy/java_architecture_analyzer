package com.analyzer.migration.context;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateScalarModel;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context for migration execution containing variables, project information,
 * and FreeMarker template engine integration for variable substitution.
 */
public class MigrationContext {
    private final Path projectRoot;
    private final Map<String, Object> variables;
    private final Configuration freemarkerConfig;
    private boolean dryRun;
    private boolean stepByStepMode;

    public MigrationContext(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.variables = new HashMap<>();
        this.freemarkerConfig = initializeFreeMarker();
        this.dryRun = false;
        initializeBuiltInVariables();
    }

    public MigrationContext(Path projectRoot, boolean dryRun) {
        this.projectRoot = projectRoot;
        this.variables = new HashMap<>();
        this.freemarkerConfig = initializeFreeMarker();
        this.dryRun = dryRun;
        initializeBuiltInVariables();
    }

    private Configuration initializeFreeMarker() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setNumberFormat("computer");
        // Add custom directives for better list handling
        cfg.setSharedVariable("list_to_string", new ListToStringMethod());
        return cfg;
    }

    private void initializeBuiltInVariables() {
        // Create nested structure for dot notation access
        Map<String, Object> project = new HashMap<>();
        project.put("root", projectRoot.toString());
        project.put("name", projectRoot.getFileName().toString());
        variables.put("project", project);

        // Also add flat versions for backward compatibility
        variables.put("project_root", projectRoot.toString());
        variables.put("project_name", projectRoot.getFileName().toString());
        variables.put("current_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        variables.put("current_datetime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // Add user info
        Map<String, Object> user = new HashMap<>();
        user.put("home", System.getProperty("user.home"));
        user.put("name", System.getProperty("user.name"));
        variables.put("user", user);
    }

    /**
     * Set a context variable that can be referenced in templates as
     * ${variable_name}.
     * If the value is a String containing variable references, they will be
     * resolved immediately using currently loaded variables.
     */
    public void setVariable(String name, Object value) {
        // Resolve string values that contain variable references
        if (value instanceof String) {
            String stringValue = (String) value;
            // Only attempt resolution if it looks like it contains variables
            if (stringValue.contains("${")) {
                try {
                    value = substituteVariables(stringValue);
                } catch (Exception e) {
                    // If resolution fails, store the original value
                    // This allows forward references to work, and preserves values
                    // that contain ${...} but aren't meant to be template variables
                    // (e.g., Maven properties in POM files)
                    // Debug log only to avoid noise from legitimate non-template uses
                }
            }
        }
        variables.put(name, value);
    }

    /**
     * Set multiple context variables at once.
     * Variables are processed in iteration order, allowing later variables
     * to reference earlier ones. Each variable value is resolved against
     * previously loaded variables before being stored.
     */
    public void setVariables(Map<String, Object> newVariables) {
        if (newVariables != null) {
            // Process variables one at a time to enable resolution
            for (Map.Entry<String, Object> entry : newVariables.entrySet()) {
                setVariable(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Get a context variable value
     */
    public Object getVariable(String name) {
        return variables.get(name);
    }

    /**
     * Get all context variables
     */
    public Map<String, Object> getAllVariables() {
        return new HashMap<>(variables);
    }

    /**
     * Substitute variables in a template string using FreeMarker.
     * Supports: ${variable}, ${object.property}, conditionals, loops, etc.
     *
     * @param template Template string with FreeMarker expressions
     * @return Processed string with variables substituted
     * @throws TemplateProcessingException if template processing fails
     */
    public String substituteVariables(String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        try {
            Template tmpl = new Template("inline", new StringReader(template), freemarkerConfig);
            StringWriter out = new StringWriter();
            tmpl.process(variables, out);
            return out.toString();
        } catch (IOException | TemplateException e) {
            throw new TemplateProcessingException("Failed to process template: " + template, e);
        }
    }

    /**
     * Resolves a variable name from YAML configuration.
     * <p>
     * This is a simple helper for batch blocks that need to extract variable names
     * from YAML fields like `input-nodes: "${stateless_beans}"`.
     * </p>
     * <p>
     * Behavior:
     * <ul>
     * <li>If the value contains `${}`, use FreeMarker's substituteVariables() to
     * resolve it</li>
     * <li>Otherwise, return the value as-is (already a variable name)</li>
     * </ul>
     * </p>
     * <p>
     * Examples:
     * <ul>
     * <li>`"${stateless_beans}"` → Uses substituteVariables() →
     * `"stateless_beans"`</li>
     * <li>`"stateless_beans"` → Returns as-is → `"stateless_beans"`</li>
     * <li>`"${my_var}_suffix"` → Uses substituteVariables() → `"value_suffix"`</li>
     * </ul>
     * </p>
     *
     * @param variableReference The variable reference from YAML (may contain ${}
     *                          syntax)
     * @return The resolved variable name to use with getVariable()
     */
    public String resolveVariableName(String variableReference) {
        if (variableReference == null || variableReference.trim().isEmpty()) {
            return variableReference;
        }

        // If it contains ${}, use FreeMarker to resolve it
        if (variableReference.contains("${")) {
            return substituteVariables(variableReference);
        }

        // Otherwise, use as-is
        return variableReference;
    }

    /**
     * Check if a variable exists in the context
     */
    public boolean hasVariable(String name) {
        return variables.containsKey(name);
    }

    /**
     * Remove a variable from the context
     */
    public void removeVariable(String name) {
        variables.remove(name);
    }

    /**
     * Clear all user-defined variables (preserves built-in variables)
     */
    public void clearUserVariables() {
        Map<String, Object> builtIns = Map.of(
                "project_root", projectRoot.toString(),
                "project_name", projectRoot.getFileName().toString(),
                "current_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "current_datetime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        variables.clear();
        variables.putAll(builtIns);
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * Check if context is in dry-run mode.
     * In dry-run mode, tasks simulate execution without making actual changes.
     *
     * @return true if dry-run mode is enabled
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * Set dry-run mode.
     *
     * @param dryRun true to enable dry-run mode
     */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Check if context is in step-by-step mode.
     * In step-by-step mode, execution pauses before each block for user
     * confirmation.
     *
     * @return true if step-by-step mode is enabled
     */
    public boolean isStepByStepMode() {
        return stepByStepMode;
    }

    /**
     * Set step-by-step mode.
     *
     * @param stepByStepMode true to enable step-by-step mode
     */
    public void setStepByStepMode(boolean stepByStepMode) {
        this.stepByStepMode = stepByStepMode;
    }

    /**
     * Exception thrown when template processing fails
     */
    public static class TemplateProcessingException extends RuntimeException {
        public TemplateProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * FreeMarker method to convert lists to strings for template rendering.
     * This handles the case where GraphQueryBlock returns ArrayList for _ids
     * variables
     * but templates expect strings.
     */
    public static class ListToStringMethod implements TemplateMethodModelEx {
        @Override
        public Object exec(List arguments) throws TemplateModelException {
            if (arguments.isEmpty()) {
                return "";
            }

            Object arg = arguments.get(0);
            if (arg == null) {
                return "";
            }

            // If it's already a scalar, return as string
            if (arg instanceof TemplateScalarModel) {
                return ((TemplateScalarModel) arg).getAsString();
            }

            // Convert list to string representation
            if (arg instanceof List) {
                List<?> list = (List<?>) arg;
                if (list.isEmpty()) {
                    return "No items found";
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        sb.append("\n");
                    }
                    sb.append("- ").append(list.get(i).toString());
                }
                return sb.toString();
            }

            // For other types, convert to string
            return arg.toString();
        }
    }
}
