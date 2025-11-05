package com.analyzer.migration.template;

import com.analyzer.migration.context.MigrationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemplateProperty template resolution functionality.
 */
class TemplatePropertyTest {

    @TempDir
    Path tempDir;

    private MigrationContext context;

    @BeforeEach
    void setUp() {
        context = new MigrationContext(tempDir);
        context.setVariable("project_root", "/home/user/project");
        context.setVariable("target_dir", "build/output");
    }

    @Test
    void testSimpleVariableResolution() {
        TemplateProperty<String> template = new TemplateProperty<>("${project_root}/src", s -> s);

        assertFalse(template.isResolved());
        assertTrue(template.hasVariables());

        String resolved = template.resolve(context);

        assertEquals("/home/user/project/src", resolved);
        assertTrue(template.isResolved());
        assertEquals(resolved, template.getResolvedValue());
    }

    @Test
    void testPathResolution() {
        TemplateProperty<Path> template = new TemplateProperty<>("${project_root}/target", Paths::get);

        Path resolved = template.resolve(context);

        assertEquals(Paths.get("/home/user/project/target"), resolved);
        assertTrue(template.isResolved());
    }

    @Test
    void testMultipleVariableResolution() {
        TemplateProperty<String> template = new TemplateProperty<>("${project_root}/${target_dir}/classes", s -> s);

        String resolved = template.resolve(context);

        assertEquals("/home/user/project/build/output/classes", resolved);
    }

    @Test
    void testNoVariablesTemplate() {
        TemplateProperty<String> template = new TemplateProperty<>("/fixed/path", s -> s);

        assertFalse(template.hasVariables());

        String resolved = template.resolve(context);

        assertEquals("/fixed/path", resolved);
    }

    @Test
    void testCachedResolution() {
        TemplateProperty<String> template = new TemplateProperty<>("${project_root}/src", s -> s);

        String resolved1 = template.resolve(context);
        String resolved2 = template.resolve(context);

        assertSame(resolved1, resolved2); // Should return cached value
        assertEquals("/home/user/project/src", resolved1);
    }

    @Test
    void testClearResolved() {
        TemplateProperty<String> template = new TemplateProperty<>("${project_root}/src", s -> s);

        String resolved1 = template.resolve(context);
        assertTrue(template.isResolved());

        template.clearResolved();
        assertFalse(template.isResolved());
        assertNull(template.getResolvedValue());

        String resolved2 = template.resolve(context);
        assertEquals(resolved1, resolved2);
    }

    @Test
    void testResolutionFailure() {
        TemplateProperty<String> template = new TemplateProperty<>("${undefined_variable}/src", s -> s);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            template.resolve(context);
        });

        assertTrue(exception.getMessage().contains("Failed to resolve template"));
        assertFalse(template.isResolved());
    }

    @Test
    void testResolverFailure() {
        TemplateProperty<Integer> template = new TemplateProperty<>("${project_root}", Integer::parseInt);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            template.resolve(context);
        });

        assertTrue(exception.getMessage().contains("Failed to resolve template"));
        assertFalse(template.isResolved());
    }

    @Test
    void testGetTemplate() {
        String templateStr = "${project_root}/src";
        TemplateProperty<String> template = new TemplateProperty<>(templateStr, s -> s);

        assertEquals(templateStr, template.getTemplate());
    }

    @Test
    void testToString() {
        TemplateProperty<String> template = new TemplateProperty<>("${project_root}/src", s -> s);

        String toString = template.toString();

        assertTrue(toString.contains("${project_root}/src"));
        assertTrue(toString.contains("resolved=false"));

        template.resolve(context);
        toString = template.toString();

        assertTrue(toString.contains("resolved=true"));
    }
}
