package com.analyzer.migration.loader;

import com.analyzer.core.db.H2GraphStorageRepository;
import com.analyzer.migration.plan.MigrationPlan;
import com.analyzer.migration.plan.Phase;
import com.analyzer.migration.plan.Task;
import com.analyzer.migration.blocks.automated.CommandBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for YamlMigrationPlanLoader.
 */
class YamlMigrationPlanLoaderTest {

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
    void testLoadFromResource_SimplePlan() throws IOException {
        // Load the simple test plan
        MigrationPlan plan = loader.loadFromResource("/test-plans/simple-plan.yaml");

        // Verify plan metadata
        assertNotNull(plan);
        assertEquals("Test Migration Plan", plan.getName());
        assertEquals("1.0.0", plan.getVersion());
        assertEquals("A simple test plan for unit testing", plan.getDescription());

        // Verify phases
        assertEquals(1, plan.getPhases().size());
        Phase phase = plan.getPhases().get(0);
        assertEquals("Test Phase", phase.getName());

        // Verify tasks
        assertEquals(1, phase.getTasks().size());
        Task task = phase.getTasks().get(0);
        assertEquals("task-001", task.getId());
        assertEquals("Test Task", task.getName());

        // Verify blocks
        assertEquals(1, task.getBlocks().size());
        assertTrue(task.getBlocks().get(0) instanceof CommandBlock);
        CommandBlock cmdBlock = (CommandBlock) task.getBlocks().get(0);
        assertEquals("test-command", cmdBlock.getName());
    }

    @Test
    void testLoadFromResource_NonExistent() {
        // Test loading non-existent resource
        assertThrows(IOException.class, () -> {
            loader.loadFromResource("/test-plans/non-existent.yaml");
        });
    }

    @Test
    void testLoadFromFile_InvalidPath() {
        // Test loading from invalid file path
        assertThrows(IOException.class, () -> {
            loader.loadFromFile("/invalid/path/plan.yaml");
        });
    }
}
