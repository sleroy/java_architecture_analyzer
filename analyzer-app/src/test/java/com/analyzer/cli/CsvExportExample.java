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
// https://github.com/leandrocgsi/semeru-ejb-maven/tree/master
public class CsvExportExample {

    private static final Logger logger = LoggerFactory.getLogger(CsvExportExample.class);

    public static void main(String[] args) throws IOException {

        String folder = "/home/sleroy/git/semeru-ejb-maven";

        int exitCode3 = new CommandLine(new AnalyzerCLI()).execute("csv_export", "--project", folder, "--output-dir", "/tmp/example");

    }
}