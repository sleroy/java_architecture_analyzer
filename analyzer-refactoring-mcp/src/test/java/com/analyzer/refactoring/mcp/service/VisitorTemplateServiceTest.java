package com.analyzer.refactoring.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisitorTemplateServiceTest {

    private VisitorTemplateService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new VisitorTemplateService();

        // Mock the resource loading to avoid file system dependency
        ResourcePatternResolver mockResolver = mock(ResourcePatternResolver.class);

        // Create a mock template
        String templateContent = """
                /**
                 * Detects God Class anti-pattern in Java code.
                 *
                 * A God Class is identified by:
                 * - High number of methods (>20)
                 * - High number of fields (>15)
                 */
                class GodClassAntiPatternVisitor {
                    // Implementation
                }
                """;

        Resource mockResource = mock(Resource.class);
        when(mockResource.getFilename()).thenReturn("god-class-antipattern.groovy");
        when(mockResource.getInputStream()).thenReturn(
                new ByteArrayInputStream(templateContent.getBytes(StandardCharsets.UTF_8)));

        when(mockResolver.getResources(anyString())).thenReturn(new Resource[] { mockResource });

        // Use reflection to set the mock resolver and load templates
        try {
            var field = VisitorTemplateService.class.getDeclaredField("resourceResolver");
            field.setAccessible(true);
            field.set(service, mockResolver);
            service.loadTemplates();
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup test", e);
        }
    }

    @Test
    void testExactPhraseMatch() {
        // Should match when the exact phrase is in the description
        Optional<VisitorTemplateService.VisitorTemplate> result = service.findTemplate("find god class antipattern",
                "ClassDeclaration");

        assertTrue(result.isPresent(), "Should find template for exact phrase 'god class antipattern'");
        assertEquals("god-class-antipattern", result.get().getName());
    }

    @Test
    void testCaseInsensitiveMatch() {
        // Should match case-insensitively
        Optional<VisitorTemplateService.VisitorTemplate> result = service.findTemplate("Find God Class AntiPattern",
                "ClassDeclaration");

        assertTrue(result.isPresent(), "Should find template with different case");
        assertEquals("god-class-antipattern", result.get().getName());
    }

    @Test
    void testPartialPhraseMatch() {
        // Should match if the pattern description contains the phrase
        Optional<VisitorTemplateService.VisitorTemplate> result = service
                .findTemplate("I want to detect god class antipattern in my code", "ClassDeclaration");

        assertTrue(result.isPresent(), "Should find template when phrase is part of longer description");
        assertEquals("god-class-antipattern", result.get().getName());
    }

    @Test
    void testNoMatchOnSingleKeyword() {
        // This was the bug: matching on single keywords like "work" or "class"
        // Should NOT match just because "class" appears in pattern description
        Optional<VisitorTemplateService.VisitorTemplate> result = service.findTemplate("identify work",
                "ClassDeclaration");

        assertFalse(result.isPresent(),
                "Should NOT match on single keyword 'work' - requires full phrase");
    }

    @Test
    void testNoMatchOnDifferentPhrase() {
        // Should not match completely different patterns
        Optional<VisitorTemplateService.VisitorTemplate> result = service.findTemplate("find singleton pattern",
                "ClassDeclaration");

        assertFalse(result.isPresent(), "Should NOT match on different pattern");
    }

    @Test
    void testMatchOnExtractedDocPhrase() {
        // Should match on phrases extracted from documentation
        // "Detects God Class anti-pattern" -> "god class anti-pattern"
        Optional<VisitorTemplateService.VisitorTemplate> result = service.findTemplate("god class anti-pattern",
                "ClassDeclaration");

        assertTrue(result.isPresent(),
                "Should match on phrase extracted from 'Detects X' documentation");
    }

    @Test
    void testTemplateContainsMatchingPhrases() {
        Optional<VisitorTemplateService.VisitorTemplate> template = service.getTemplate("god-class-antipattern");

        assertTrue(template.isPresent(), "Template should be loaded");
        assertFalse(template.get().getMatchingPhrases().isEmpty(),
                "Template should have matching phrases");

        // Should contain at least the filename-based phrase
        assertTrue(template.get().getMatchingPhrases().stream()
                .anyMatch(phrase -> phrase.contains("god class")),
                "Should contain phrase derived from filename");
    }
}
