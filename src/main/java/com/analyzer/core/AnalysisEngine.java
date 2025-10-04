package com.analyzer.core;

import com.analyzer.analysis.Analysis;
import com.analyzer.analysis.AnalysisResult;
import com.analyzer.detection.FileDetector;
import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Core analysis engine responsible for the new Project/ProjectFile-based
 * architecture.
 * 
 * Implements the refactored workflow:
 * 1. Creation of the Project object
 * 2. Reload data from the project folder (project, project files, ...)
 * 3. Scan for files by browsing the available detectors
 * 4. List the analyses available
 * 5. Execute all the analyses
 * 6. Iterate on the inspectors and apply them as before
 * 7. Store the project data in the project folder
 */
public class AnalysisEngine {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisEngine.class);
    private final InspectorRegistry inspectorRegistry;
    private final List<FileDetector> fileDetectors;
    private final List<Analysis> availableAnalyses;

    public AnalysisEngine(InspectorRegistry inspectorRegistry,
            List<FileDetector> fileDetectors,
            List<Analysis> availableAnalyses) {
        this.inspectorRegistry = inspectorRegistry;
        this.fileDetectors = fileDetectors != null ? fileDetectors : new ArrayList<>();
        this.availableAnalyses = availableAnalyses != null ? availableAnalyses : new ArrayList<>();
    }

    /**
     * Analyzes a project using the new ProjectFile-based workflow.
     * 
     * @param projectPath         the path to the project directory
     * @param requestedInspectors list of inspector names to use (null = all)
     * @return the analyzed Project object
     */
    public Project analyzeProject(Path projectPath, List<String> requestedInspectors) throws IOException {
        logger.info("Starting project analysis for: {}", projectPath);

        // Step 1: Create the Project object
        Project project = new Project(projectPath);

        // Step 2: Reload data from project folder (for now, create fresh - persistence
        // will come later)
        logger.info("Creating new project analysis for: {}", projectPath);

        // Step 3: Scan for files by browsing the available detectors
        scanProjectFiles(project);

        // Step 4: List the analyses available
        logger.info("Found {} available analyses", availableAnalyses.size());
        for (Analysis analysis : availableAnalyses) {
            logger.debug("Available analysis: {} - {}", analysis.getName(), analysis.getDescription());
        }

        // Step 5: Execute all the analyses
        executeAnalyses(project);

        // Step 6: Iterate on the inspectors and apply them on ProjectFiles
        executeInspectors(project, requestedInspectors);

        // Step 7: Store the project data in the project folder (TODO: implement
        // persistence)
        logger.info("Project analysis completed. Found {} files", project.getProjectFiles().size());

        return project;
    }

    /**
     * Scans the project directory for files using configured FileDetectors.
     */
    private void scanProjectFiles(Project project) throws IOException {
        Path projectPath = project.getProjectPath();
        logger.info("Scanning project files with {} detectors", fileDetectors.size());

        try (Stream<Path> paths = Files.walk(projectPath)) {
            List<Path> allFiles = paths
                    .filter(Files::isRegularFile)
                    .toList();

            logger.info("Found {} files in project directory", allFiles.size());

            try (ProgressBar pb = new ProgressBar("Scanning files", allFiles.size())) {
                for (Path filePath : allFiles) {
                    scanFile(project, filePath);
                    pb.step();
                }
            }
        }

        logger.info("File scanning completed. Detected {} project files", project.getProjectFiles().size());
    }

    /**
     * Scans a single file with all available FileDetectors.
     */
    private void scanFile(Project project, Path filePath) {
        for (FileDetector detector : fileDetectors) {
            try {
                if (detector.matches(filePath, project.getProjectPath())) {
                    // Create or get existing ProjectFile
                    String relativePath = project.getProjectPath().relativize(filePath).toString();
                    ProjectFile projectFile = project.getOrCreateProjectFile(relativePath, filePath);

                    // Apply detector-specific processing
                    detector.processFile(projectFile);

                    logger.debug("Detector {} matched file: {}", detector.getName(), relativePath);
                }
            } catch (Exception e) {
                logger.warn("Error applying detector {} to file {}: {}",
                        detector.getName(), filePath, e.getMessage());
            }
        }
    }

    /**
     * Executes all available analyses on the project.
     */
    private void executeAnalyses(Project project) {
        if (availableAnalyses.isEmpty()) {
            logger.info("No analyses configured, skipping analysis phase");
            return;
        }

        logger.info("Executing {} analyses", availableAnalyses.size());

        try (ProgressBar pb = new ProgressBar("Running analyses", availableAnalyses.size())) {
            for (Analysis analysis : availableAnalyses) {
                executeAnalysis(project, analysis);
                pb.step();
            }
        }
    }

    /**
     * Executes a single analysis on the project.
     */
    private void executeAnalysis(Project project, Analysis analysis) {
        try {
            if (!analysis.canAnalyze(project)) {
                logger.debug("Analysis {} cannot analyze this project, skipping", analysis.getName());
                return;
            }

            logger.debug("Executing analysis: {}", analysis.getName());
            AnalysisResult result = analysis.execute(project);

            if (result.isSuccessful()) {
                logger.debug("Analysis {} completed successfully", analysis.getName());
            } else {
                logger.warn("Analysis {} failed: {}", analysis.getName(), result.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error executing analysis '{}': {}", analysis.getName(), e.getMessage());
        }
    }

    /**
     * Executes inspectors on all ProjectFiles in the project.
     */
    private void executeInspectors(Project project, List<String> requestedInspectors) {
        List<Inspector<ProjectFile>> inspectors = getProjectFileInspectors(requestedInspectors);

        if (inspectors.isEmpty()) {
            logger.warn("No ProjectFile inspectors found");
            return;
        }

        logger.info("Executing {} inspectors on {} project files",
                inspectors.size(), project.getProjectFiles().size());

        try (ProgressBar pb = new ProgressBar("Running inspectors", project.getProjectFiles().size())) {
            for (ProjectFile projectFile : project.getProjectFiles().values()) {
                analyzeProjectFile(projectFile, inspectors);
                pb.step();
            }
        }
    }

    /**
     * Analyzes a single ProjectFile with all applicable inspectors.
     */
    private void analyzeProjectFile(ProjectFile projectFile, List<Inspector<ProjectFile>> inspectors) {
        for (Inspector<ProjectFile> inspector : inspectors) {
            try {
                if (inspector.supports(projectFile)) {
                    InspectorResult result = inspector.decorate(projectFile);

                    // Store the result using ProjectFile's addInspectorResult method
                    if (result.isSuccessful()) {
                        projectFile.addInspectorResult(inspector.getColumnName(), result.getValue());
                    } else if (result.isNotApplicable()) {
                        projectFile.addInspectorResult(inspector.getColumnName(), "N/A");
                    } else if (result.isError()) {
                        projectFile.addInspectorResult(inspector.getColumnName(), "ERROR: " + result.getErrorMessage());
                    }
                } else {
                    projectFile.addInspectorResult(inspector.getColumnName(), "N/A");
                }
            } catch (Exception e) {
                logger.error("Error running inspector '{}' on file '{}': {}",
                        inspector.getName(), projectFile.getRelativePath(), e.getMessage());
                projectFile.addInspectorResult(inspector.getColumnName(), "ERROR: " + e.getMessage());
            }
        }
    }

    /**
     * Gets ProjectFile inspectors to execute based on request.
     * Since all inspectors now work with ProjectFile by default, this simply casts
     * the inspectors.
     */
    @SuppressWarnings("unchecked")
    private List<Inspector<ProjectFile>> getProjectFileInspectors(List<String> requestedInspectors) {
        List<Inspector> allInspectors = getInspectorsToExecute(requestedInspectors);
        List<Inspector<ProjectFile>> projectFileInspectors = new ArrayList<>();

        for (Inspector inspector : allInspectors) {
            // All inspectors now work with ProjectFile by default
            projectFileInspectors.add((Inspector<ProjectFile>) inspector);
        }

        return projectFileInspectors;
    }

    /**
     * Gets the list of inspectors to execute based on user request.
     */
    private List<Inspector> getInspectorsToExecute(List<String> requestedInspectors) {
        if (requestedInspectors == null || requestedInspectors.isEmpty()) {
            return inspectorRegistry.getAllInspectors();
        } else {
            List<Inspector> inspectors = new ArrayList<>();
            for (String inspectorName : requestedInspectors) {
                Inspector inspector = inspectorRegistry.getInspector(inspectorName);
                if (inspector != null) {
                    inspectors.add(inspector);
                } else {
                    logger.warn("Inspector '{}' not found", inspectorName);
                }
            }
            return inspectors;
        }
    }

    /**
     * Gets the list of all inspector names for CSV headers.
     */
    public List<String> getInspectorNames(List<String> requestedInspectors) {
        List<Inspector> inspectors = getInspectorsToExecute(requestedInspectors);
        List<String> names = new ArrayList<>();
        for (Inspector inspector : inspectors) {
            names.add(inspector.getName());
        }
        return names;
    }

    /**
     * Gets the list of Inspector objects for CSV export.
     */
    public List<Inspector> getInspectors(List<String> requestedInspectors) {
        return getInspectorsToExecute(requestedInspectors);
    }

    /**
     * Gets statistics about the analysis.
     */
    public AnalysisStatistics getStatistics() {
        return new AnalysisStatistics(
                inspectorRegistry.getInspectorCount(),
                inspectorRegistry.getSourceInspectorCount(),
                inspectorRegistry.getBinaryInspectorCount(),
                availableAnalyses.size());
    }

    /**
     * Statistics about the analysis execution.
     */
    public static class AnalysisStatistics {
        private final int totalInspectors;
        private final int sourceInspectors;
        private final int binaryInspectors;
        private final int totalAnalyses;

        public AnalysisStatistics(int totalInspectors, int sourceInspectors, int binaryInspectors, int totalAnalyses) {
            this.totalInspectors = totalInspectors;
            this.sourceInspectors = sourceInspectors;
            this.binaryInspectors = binaryInspectors;
            this.totalAnalyses = totalAnalyses;
        }

        public int getTotalInspectors() {
            return totalInspectors;
        }

        public int getSourceInspectors() {
            return sourceInspectors;
        }

        public int getBinaryInspectors() {
            return binaryInspectors;
        }

        public int getTotalAnalyses() {
            return totalAnalyses;
        }

        @Override
        public String toString() {
            return String.format("Analysis Statistics: %d total inspectors (%d source, %d binary), %d analyses",
                    totalInspectors, sourceInspectors, binaryInspectors, totalAnalyses);
        }
    }
}
