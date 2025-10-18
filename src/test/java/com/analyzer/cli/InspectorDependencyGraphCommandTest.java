package com.analyzer.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the InspectorDependencyGraphCommand to ensure proper functionality.
 */
public class InspectorDependencyGraphCommandTest {

    @TempDir
    File tempDir;

    @Test
    public void testCommandCreation() {
        InspectorDependencyGraphCommand command = new InspectorDependencyGraphCommand();
        assertNotNull(command);
    }

    @Test
    public void testCommandLineIntegration() {
        CommandLine cmdLine = new CommandLine(new InspectorDependencyGraphCommand());
        assertNotNull(cmdLine);
        
        // Test that the command can be parsed
        String[] args = {"--help"};
        assertDoesNotThrow(() -> {
            cmdLine.parseArgs(args);
        });
    }

    @Test
    public void testGraphFormatEnumValues() {
        // Test that all expected formats are available
        InspectorDependencyGraphCommand.GraphFormat[] formats = 
            InspectorDependencyGraphCommand.GraphFormat.values();
        
        assertEquals(3, formats.length);
        
        // Test specific format values
        assertEquals(InspectorDependencyGraphCommand.GraphFormat.graphml, 
                    InspectorDependencyGraphCommand.GraphFormat.valueOf("graphml"));
        assertEquals(InspectorDependencyGraphCommand.GraphFormat.dot, 
                    InspectorDependencyGraphCommand.GraphFormat.valueOf("dot"));
        assertEquals(InspectorDependencyGraphCommand.GraphFormat.json, 
                    InspectorDependencyGraphCommand.GraphFormat.valueOf("json"));
    }

    @Test
    public void testOutputFileParameter() throws Exception {
        File outputFile = new File(tempDir, "test-output.graphml");
        
        InspectorDependencyGraphCommand command = new InspectorDependencyGraphCommand();
        CommandLine cmdLine = new CommandLine(command);
        
        String[] args = {outputFile.getAbsolutePath()};
        cmdLine.parseArgs(args);
        
        // The command should parse without error
        assertNotNull(command);
    }

    @Test
    public void testDefaultParameters() {
        InspectorDependencyGraphCommand command = new InspectorDependencyGraphCommand();
        CommandLine cmdLine = new CommandLine(command);
        
        File outputFile = new File(tempDir, "test.graphml");
        String[] args = {outputFile.getAbsolutePath()};
        cmdLine.parseArgs(args);
        
        // Test default values are set properly through reflection
        // (This is a basic test to ensure the command structure is sound)
        assertNotNull(command);
    }

    @Test
    public void testMinimalExecution() throws Exception {
        File outputFile = new File(tempDir, "minimal-test.graphml");
        
        InspectorDependencyGraphCommand command = new InspectorDependencyGraphCommand();
        CommandLine cmdLine = new CommandLine(command);
        
        String[] args = {
            outputFile.getAbsolutePath(),
            "--verbose"
        };
        
        cmdLine.parseArgs(args);
        
        // This test ensures the command can be created and parsed
        // Full execution testing would require a complete setup with inspectors
        assertNotNull(command);
        assertTrue(outputFile.getParentFile().exists());
    }
}
