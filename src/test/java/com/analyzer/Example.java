package com.analyzer;

import com.analyzer.cli.AnalyzerCLI;

public class Example {
    public static void main(String[] args) {

        AnalyzerCLI.main(new String[]{
              "inventory",  "--source" , "/home/sleroy/git/sample.ejb2", "--binary", "/home/sleroy/git/sample.ejb2", "--java_version", "17", "--output", "ejb.csv"
        });
    }
}