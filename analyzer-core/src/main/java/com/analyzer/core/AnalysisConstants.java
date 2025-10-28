package com.analyzer.core;

import java.nio.file.Path;

/**
 * Shared constants for analysis operations.
 */
public final class AnalysisConstants {

    public static final String ANALYSIS_DIR = ".analysis";
    public static final String BINARIES_DIR = "binaries";
    public static final String PROJECT_DATA_DIR = "project-data";

    /** Base name for the H2 graph database file (without .mv.db extension) */
    public static final String GRAPH_DB_NAME = "graph";

    private AnalysisConstants() {
        // Utility class
    }

    public static String getCompleteDatabaseName() {
        return GRAPH_DB_NAME + ".mv.db";
    }
}
