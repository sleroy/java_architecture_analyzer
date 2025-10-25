package com.analyzer;

import com.analyzer.cli.AnalyzerCLI;
import com.analyzer.core.model.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class InventoryExample {
    public static void main(String[] args) throws IOException {

        String folder = "/home/sleroy/git/sample.ejb2";
        Path configFile = Paths.get(folder).resolve(Project.DEFAULT_FILE_NAME);
        if (Files.exists(configFile)) {
            Files.delete(configFile);
        }

        AnalyzerCLI.main(new String[]{
              "inventory",  "--project" , folder, "--java_version", "17", "--output", "ejb.csv"
        });
    }
}