package com.analyzer.migration.export;

import com.analyzer.migration.plan.MigrationBlock;
import com.analyzer.migration.plan.MigrationPlan;
import com.analyzer.migration.plan.Phase;
import com.analyzer.migration.plan.Task;

import java.util.List;

/**
 * Generates Markdown documentation from migration plans.
 * Creates comprehensive documentation matching the format of existing migration
 * plans.
 */
public class MarkdownGenerator {

    /**
     * Generate complete Markdown documentation for a migration plan
     */
    public String generatePlanDocumentation(MigrationPlan plan) {
        StringBuilder md = new StringBuilder();

        // Header
        md.append("# ").append(plan.getName()).append("\n\n");

        if (plan.getDescription() != null && !plan.getDescription().isEmpty()) {
            md.append(plan.getDescription()).append("\n\n");
        }

        md.append("**Version:** ").append(plan.getVersion()).append("\n\n");
        md.append("**Total Tasks:** ").append(plan.getTotalTaskCount()).append("\n\n");

        // Table of Contents
        md.append("## Table of Contents\n\n");
        for (Phase phase : plan.getPhases()) {
            md.append("- [").append(phase.getName()).append("](#")
                    .append(toAnchor(phase.getName())).append(")\n");
        }
        md.append("\n");

        // Phases
        for (Phase phase : plan.getPhases()) {
            generatePhaseDocumentation(phase, md);
        }

        return md.toString();
    }

    /**
     * Generate Markdown documentation for a single phase
     */
    private void generatePhaseDocumentation(Phase phase, StringBuilder md) {
        md.append("## ").append(phase.getName()).append("\n\n");

        if (phase.getDescription() != null && !phase.getDescription().isEmpty()) {
            md.append(phase.getDescription()).append("\n\n");
        }

        md.append("**Number of Tasks:** ").append(phase.getTasks().size()).append("\n\n");

        // Tasks
        for (Task task : phase.getTasks()) {
            generateTaskDocumentation(task, md);
        }
    }

    /**
     * Generate Markdown documentation for a single task
     */
    private void generateTaskDocumentation(Task task, StringBuilder md) {
        md.append("### ").append(task.getId()).append(": ").append(task.getName()).append("\n\n");

        // Task metadata
        if (task.isManualReviewRequired()) {
            md.append("**⚠️ Manual Review Required**\n\n");
        }

        // Description
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            md.append(task.getDescription()).append("\n\n");
        }

        // Blocks
        if (!task.getBlocks().isEmpty()) {
            md.append("**Steps:**\n\n");
            int stepNumber = 1;
            for (MigrationBlock block : task.getBlocks()) {
                md.append(stepNumber++).append(". ");
                md.append(block.toMarkdownDescription()).append("\n");
            }
            md.append("\n");
        }

        // Success criteria
        if (task.getSuccessCriteria() != null && !task.getSuccessCriteria().isEmpty()) {
            md.append("**Success Criteria:**\n\n");
            md.append(task.getSuccessCriteria()).append("\n\n");
        }

        md.append("---\n\n");
    }

    /**
     * Generate summary statistics for a migration plan
     */
    public String generatePlanSummary(MigrationPlan plan) {
        StringBuilder md = new StringBuilder();

        md.append("# Migration Plan Summary: ").append(plan.getName()).append("\n\n");

        // Overview
        md.append("## Overview\n\n");
        md.append("| Metric | Count |\n");
        md.append("|--------|-------|\n");
        md.append("| Phases | ").append(plan.getPhases().size()).append(" |\n");
        md.append("| Total Tasks | ").append(plan.getTotalTaskCount()).append(" |\n");
        md.append("\n");

        // Phase breakdown
        md.append("## Phase Breakdown\n\n");
        for (Phase phase : plan.getPhases()) {
            md.append("- **").append(phase.getName()).append("**: ")
                    .append(phase.getTasks().size()).append(" tasks\n");
        }
        md.append("\n");

        return md.toString();
    }

    /**
     * Convert a heading to an anchor link
     */
    private String toAnchor(String heading) {
        return heading.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-");
    }
}
