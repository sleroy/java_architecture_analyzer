package com.analyzer.rules.metrics;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@InspectorDependencies(requires = { InspectorTags.TAG_SOURCE_FILE }, produces = {})
public class FileMetricsInspector implements Inspector<ProjectFile> {

    @Override
    public void inspect(ProjectFile objectToAnalyze, NodeDecorator<ProjectFile> decorator) {
        Path filePath = objectToAnalyze.getFilePath();
        if (filePath == null || !Files.isRegularFile(filePath)) {
            return;
        }

        try {
            long fileSize = Files.size(filePath);
            decorator.setMetric(InspectorTags.TAG_METRIC_FILE_SIZE, fileSize);

            List<String> lines = Files.readAllLines(filePath);
            int lineCount = lines.size();
            int commentLineCount = 0;
            int blankLineCount = 0;

            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    blankLineCount++;
                } else if (line.trim().startsWith("//") || line.trim().startsWith("/*")
                        || line.trim().startsWith("*")) {
                    commentLineCount++;
                }
            }

            decorator.setMetric(InspectorTags.TAG_METRIC_LINES_OF_CODE, lineCount);
            decorator.setMetric(InspectorTags.TAG_METRIC_COMMENT_LINES, commentLineCount);
            decorator.setMetric(InspectorTags.TAG_METRIC_BLANK_LINES, blankLineCount);

        } catch (IOException e) {
            decorator.error("Error reading file for metrics: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "File Metrics Inspector";
    }

    @Override
    public boolean supports(ProjectFile objectToAnalyze) {
        return objectToAnalyze != null && !objectToAnalyze.isVirtual();
    }
}
