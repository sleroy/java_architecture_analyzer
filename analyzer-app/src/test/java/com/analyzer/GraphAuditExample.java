package com.analyzer;

import com.analyzer.cli.AnalyzerCLI;

public class GraphAuditExample {
    public static void main(String[] args) {

        AnalyzerCLI.main(new String[]{
              "inspector-graph",  "dependencies.graphml"
        });
    }
}