package com.analyzer.cli;

import com.analyzer.core.model.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// https://download.jboss.org/jbossas/6.1/jboss-as-distribution-6.1.0.Final.zip

public class InventoryExample {

    private static final Logger logger = LoggerFactory.getLogger(InventoryExample.class);

    public static void main(String[] args) throws IOException {

        final Path projectFolder = Paths.get("demo-ejb2-project").toAbsolutePath();
        String folder = projectFolder.toString();
        Path configFile = Paths.get(folder).resolve(Project.DEFAULT_FILE_NAME);
        if (Files.exists(configFile)) {
            Files.delete(configFile);
        }

        int exitCode = new CommandLine(new AnalyzerCLI()).execute("inventory", "--project", folder, "--java_version", "17", "--packages", "com.example");


        logger.info("---------------------------------------------------------------");
        logger.info("");
        logger.info("");
        logger.info("");
        logger.info("");
        logger.info("");
        logger.info("");

        final String targetFolder = projectFolder.resolve("target").resolve("export").toString();
        int exitCode2 = new CommandLine(new AnalyzerCLI()).execute("json_export", "--project", folder, "--json-output", targetFolder);

        int exitCode3 = new CommandLine(new AnalyzerCLI()).execute("csv_export", "--project", folder, "--output-dir", targetFolder);

    }
}