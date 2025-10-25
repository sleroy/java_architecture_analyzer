package com.analyzer.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

/**
 * Main CLI application for the Java Architecture Analyzer.
 * Provides command-line interface for static analysis of Java applications.
 */
@Command(name = "java-architecture-analyzer", description = "Static analysis tool for Java applications", version = "1.0.0-SNAPSHOT", mixinStandardHelpOptions = true, subcommands = {
        InventoryCommand.class,
        InspectorDependencyGraphCommand.class,
        HelpCommand.class
})
public class AnalyzerCLI implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AnalyzerCLI()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // When no subcommand is specified, show help
        CommandLine.usage(this, System.out);
    }
}
