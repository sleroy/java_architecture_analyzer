package com.analyzer.refactoring.mcp.util;

import org.openrewrite.Cursor;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaPrinter;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.Space;

import java.util.*;

/**
 * Utility for tracking line and column positions of AST nodes in OpenRewrite.
 * Uses a custom printer approach similar to GoToLine in OpenRewrite.
 */
public class PositionTracker {

    /**
     * Get the line and column position of a specific AST node.
     * Line and column numbers are 1-based.
     *
     * @param sourceFile The source file containing the node
     * @param targetNode The node to find the position of
     * @return A map with "line" and "column" keys, or line=0, column=0 if not found
     */
    public static Map<String, Integer> getPosition(JavaSourceFile sourceFile, J targetNode) {
        PositionFinder finder = new PositionFinder(targetNode);
        finder.visit(sourceFile, finder.new PositionPrinter(0), new Cursor(null, "root"));

        Map<String, Integer> result = new HashMap<>();
        result.put("line", finder.foundLine);
        result.put("column", finder.foundColumn);
        return result;
    }

    private static class PositionFinder extends JavaPrinter<Integer> {
        private final J targetNode;
        private int currentLine = 1;
        private int currentColumn = 1;
        private int foundLine = 0;
        private int foundColumn = 0;
        private boolean found = false;

        public PositionFinder(J targetNode) {
            this.targetNode = targetNode;
        }

        @Override
        public Space visitSpace(Space space, Space.Location loc, PrintOutputCapture<Integer> p) {
            Space s = super.visitSpace(space, loc, p);

            // Check if we've found our target node at its prefix location
            if (!found && loc.toString().endsWith("_PREFIX")) {
                J currentNode = getCursor().getValue();
                if (currentNode == targetNode) {
                    foundLine = currentLine;
                    foundColumn = currentColumn;
                    found = true;
                    stopAfterPreVisit();
                }
            }

            return s;
        }

        class PositionPrinter extends PrintOutputCapture<Integer> {
            public PositionPrinter(Integer p) {
                super(p);
            }

            @Override
            public PrintOutputCapture<Integer> append(@Nullable String text) {
                if (text == null) {
                    return this;
                }
                for (int i = 0; i < text.length(); i++) {
                    append(text.charAt(i));
                }
                return this;
            }

            @Override
            public PrintOutputCapture<Integer> append(char c) {
                if (isNewLine(c)) {
                    currentColumn = 1;
                    currentLine++;
                } else {
                    currentColumn++;
                }
                // Don't actually need to append for position tracking
                return this;
            }
        }
    }

    private static boolean isNewLine(int c) {
        return c == '\n';
    }
}
