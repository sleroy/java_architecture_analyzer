package com.analyzer.migration.loader;

import com.analyzer.core.db.H2GraphStorageRepository;
import com.analyzer.migration.blocks.analysis.GraphQueryBlock;
import com.analyzer.migration.blocks.automated.CommandBlock;
import com.analyzer.migration.blocks.validation.InteractiveValidationBlock;
import com.analyzer.migration.plan.MigrationPlan;
import com.analyzer.migration.plan.Phase;
import com.analyzer.migration.plan.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for loading the complete JBoss to Spring Boot migration
 * plan.
 */
class MigrationPlanLoaderIntegrationTest {

    @Mock
    private H2GraphStorageRepository mockRepository;

    private YamlMigrationPlanLoader loader;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        MigrationPlanConverter converter = new MigrationPlanConverter(mockRepository);
        loader = new YamlMigrationPlanLoader(converter);
    }

    @Test
    void testLoadCompleteJBossMigrationPlan() throws IOException {
        // Load the complete JBoss to Spring Boot migration plan
        MigrationPlan plan = loader.loadFromResource("/migration-plans/examplejboss-migration-plan.yaml");

        // Verify plan metadata
        assertNotNull(plan, "Migration plan should not be null");
        assertEquals("JBoss EJB 2 to Spring Boot Migration - Phase 0 & 1", plan.getName());
        assertEquals("1.0.0", plan.getVersion());
        assertNotNull(plan.getDescription());

        // Verify phases
        assertFalse(plan.getPhases().isEmpty(), "Plan should have phases");

        // Verify we have both Phase 0 and Phase 1
        assertTrue(plan.getPhases().size() >= 2, "Should have at least 2 phases");

        Phase phase0 = plan.getPhases().get(0);
        assertEquals("Pre-Migration Assessment and Preparation", phase0.getName());

        Phase phase1 = plan.getPhases().get(1);
        assertEquals("Spring Boot Project Initialization", phase1.getName());

        // Verify Phase 0 has tasks
        assertFalse(phase0.getTasks().isEmpty(), "Phase 0 should have tasks");

        // Verify TASK-000: Baseline Branch Creation
        Task task000 = phase0.getTasks().stream()
                .filter(t -> "task-000".equals(t.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(task000, "TASK-000 should exist");
        assertEquals("Project Baseline Documentation", task000.getName());
        assertFalse(task000.getBlocks().isEmpty(), "TASK-000 should have blocks");

        // Verify TASK-000 has a command block for git checkout
        assertTrue(task000.getBlocks().stream()
                .anyMatch(b -> b instanceof CommandBlock),
                "TASK-000 should have at least one command block");

        // Verify TASK-001: Create Migration Branch Structure
        Task task001 = phase0.getTasks().stream()
                .filter(t -> "task-001".equals(t.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(task001, "TASK-001 should exist");
        assertEquals("Create Migration Branch Structure", task001.getName());

        // Verify TASK-001 has command blocks
        assertTrue(task001.getBlocks().stream()
                .anyMatch(b -> b instanceof CommandBlock),
                "TASK-001 should have command blocks");

        // Verify TASK-100: Repository Structure Review
        Task task100 = phase1.getTasks().stream()
                .filter(t -> "task-100".equals(t.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(task100, "TASK-100 should exist");
        assertEquals("Create Spring Boot Parent POM", task100.getName());

        // Verify TASK-100 has an InteractiveValidationBlock
        assertTrue(task100.getBlocks().stream()
                .anyMatch(b -> b instanceof InteractiveValidationBlock),
                "TASK-100 should have an interactive validation block");

        // Verify all tasks have valid dependencies
        for (Phase phase : plan.getPhases()) {
            for (Task task : phase.getTasks()) {
                assertNotNull(task.getId(), "Task ID should not be null");
                assertNotNull(task.getName(), "Task name should not be null");
                assertFalse(task.getBlocks().isEmpty(), "Task should have at least one block");

                // Verify each block is valid
                task.getBlocks().forEach(block -> {
                    assertTrue(block.validate(), "Block " + block.getName() + " should be valid");
                });
            }
        }
    }

    @Test
    void testPlanHasCorrectTaskCount() throws IOException {
        MigrationPlan plan = loader.loadFromResource("/migration-plans/examplejboss-migration-plan.yaml");

        // Count total tasks across all phases
        int totalTasks = plan.getTotalTaskCount();

        // The plan should have at least 5 tasks (TASK-000 through TASK-101)
        assertTrue(totalTasks >= 5, "Plan should have at least 5 tasks, but has " + totalTasks);
    }

    @Test
    void testAllBlockTypesConvert() throws IOException {
        MigrationPlan plan = loader.loadFromResource("/migration-plans/examplejboss-migration-plan.yaml");

        // Verify that we have multiple block types
        boolean hasCommandBlock = false;
        boolean hasGraphQueryBlock = false;
        boolean hasValidationBlock = false;

        for (Phase phase : plan.getPhases()) {
            for (Task task : phase.getTasks()) {
                for (var block : task.getBlocks()) {
                    if (block instanceof CommandBlock)
                        hasCommandBlock = true;
                    if (block instanceof GraphQueryBlock)
                        hasGraphQueryBlock = true;
                    if (block instanceof InteractiveValidationBlock)
                        hasValidationBlock = true;
                }
            }
        }

        assertTrue(hasCommandBlock, "Plan should have at least one CommandBlock");
        assertTrue(hasGraphQueryBlock, "Plan should have at least one GraphQueryBlock");
        assertTrue(hasValidationBlock, "Plan should have at least one InteractiveValidationBlock");
    }
}
