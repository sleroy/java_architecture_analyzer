package com.analyzer.core;

/**
 * Enumeration of different class types that can be discovered during analysis.
 * Used to distinguish between classes found only in source, only in binary, or
 * both.
 */
public enum ClassType {
    /**
     * Class found only in source files (.java)
     */
    SOURCE_ONLY,

    /**
     * Class found only in compiled class files (.class)
     */
    BINARY_ONLY,

    /**
     * Class found in both source and binary representations
     */
    BOTH
}
