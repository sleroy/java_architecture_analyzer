package com.analyzer.migration.template;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.plan.MigrationBlock;

/**
 * Interface for migration blocks that contain template properties requiring
 * resolution before execution.
 * 
 * Blocks implementing this interface can store template strings during plan
 * loading and resolve them to concrete values when MigrationContext becomes
 * available during execution.
 */
public interface TemplateAwareBlock extends MigrationBlock {

    /**
     * Resolves all template properties using the provided migration context.
     * This method is called by the TaskExecutor before block execution.
     * 
     * @param context The migration context containing variables for resolution
     * @throws RuntimeException if template resolution fails
     */
    void resolveTemplates(MigrationContext context);

    /**
     * Validates template syntax and structure without performing resolution.
     * This method can be called during plan loading to catch template
     * syntax errors early.
     * 
     * @return true if all templates have valid syntax, false otherwise
     */
    boolean validateTemplates();

    /**
     * Checks if this block has any unresolved template properties.
     * 
     * @return true if there are templates that need resolution
     */
    boolean hasUnresolvedTemplates();
}
