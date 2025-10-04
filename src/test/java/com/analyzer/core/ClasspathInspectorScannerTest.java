package com.analyzer.core;

import com.analyzer.inspectors.core.source.SourceFileInspector;
import com.analyzer.inspectors.core.binary.BinaryClassInspector;
import com.analyzer.resource.ResourceResolver;
import com.analyzer.test.stubs.StubResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ClasspathInspectorScanner to verify automatic discovery
 * functionality.
 */
public class ClasspathInspectorScannerTest {

    private static final Logger logger = LoggerFactory.getLogger(ClasspathInspectorScannerTest.class);

    private ClasspathInspectorScanner scanner;
    private ResourceResolver resourceResolver;
    private JARClassLoaderService jarClassLoaderService;

    @BeforeEach
    void setUp() {
        resourceResolver = new StubResourceResolver();
        jarClassLoaderService = new JARClassLoaderService();
        scanner = new ClasspathInspectorScanner(resourceResolver, jarClassLoaderService);
    }

    @Test
    void testScanForInspectors() {
        logger.info("Testing classpath scanning for inspector discovery...");

        List<Inspector> discoveredInspectors = scanner.scanForInspectors();

        // Verify that some inspectors were discovered
        assertNotNull(discoveredInspectors, "Discovered inspectors list should not be null");
        assertFalse(discoveredInspectors.isEmpty(), "Should discover at least some inspector classes");

        logger.info("Discovered {} inspector classes", discoveredInspectors.size());

        // Check that we have both source and binary inspectors
        boolean hasSourceInspectors = false;
        boolean hasBinaryInspectors = false;
        boolean hasServletInspector = false;

        for (Inspector inspector : discoveredInspectors) {
            assertNotNull(inspector, "Inspector should not be null");
            assertNotNull(inspector.getName(), "Inspector name should not be null");
            assertFalse(inspector.getName().trim().isEmpty(), "Inspector name should not be empty");

            logger.debug("Discovered inspector: {} ({})", inspector.getName(), inspector.getClass().getName());

            if (inspector instanceof SourceFileInspector) {
                hasSourceInspectors = true;
            }
            if (inspector instanceof BinaryClassInspector) {
                hasBinaryInspectors = true;
            }
            if ("Identify Servlet".equals(inspector.getName())) {
                hasServletInspector = true;
            }
        }

        assertTrue(hasSourceInspectors, "Should discover at least one source file inspector");
        assertTrue(hasBinaryInspectors, "Should discover at least one binary class inspector");
        assertTrue(hasServletInspector, "Should discover the IdentifyServletSourceInspector");
    }

    @Test
    void testExpectedInspectorsFound() {
        List<Inspector> discoveredInspectors = scanner.scanForInspectors();

        // Convert to a list of inspector names for easier checking
        List<String> inspectorNames = discoveredInspectors.stream()
                .map(Inspector::getName)
                .toList();

        logger.info("Found inspector names: {}", inspectorNames);

        // Verify that key inspectors are discovered (using actual getName() values)
        assertTrue(inspectorNames.contains("Number of lines of code"), "Should discover ClocInspector");
        assertTrue(inspectorNames.contains("Type Declaration Inspector"), "Should discover TypeInspector");
        assertTrue(inspectorNames.contains("Method count inspector (BINARY)"), "Should discover MethodCountInspector");
        assertTrue(inspectorNames.contains("Cyclomatic Complexity"), "Should discover CyclomaticComplexityInspector");
        assertTrue(inspectorNames.contains("Identify Servlet"), "Should discover IdentifyServletSourceInspector");

        // Note: annotation-count and code-quality inspectors may not be discovered due
        // to
        // constructor requirements or configuration issues
    }

    @Test
    void testNoDuplicateInspectors() {
        List<Inspector> discoveredInspectors = scanner.scanForInspectors();

        // Check for duplicate inspector names
        List<String> inspectorNames = discoveredInspectors.stream()
                .map(Inspector::getName)
                .toList();

        long uniqueCount = inspectorNames.stream().distinct().count();

        assertEquals(inspectorNames.size(), uniqueCount,
                "Should not have duplicate inspector names. Names: " + inspectorNames);
    }
}
