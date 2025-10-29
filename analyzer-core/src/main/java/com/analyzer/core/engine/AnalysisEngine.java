package com.analyzer.core.engine;

import com.analyzer.api.analysis.Analysis;
import com.analyzer.api.analysis.AnalysisResult;
import com.analyzer.api.collector.ClassNodeCollector;
import com.analyzer.api.graph.*;
import com.analyzer.core.collector.CollectionContext;
import com.analyzer.api.detector.FileDetector;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.filter.FileIgnoreFilter;
import com.analyzer.core.inspector.InspectorProgressTracker;
import com.analyzer.core.inspector.InspectorRegistry;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.inspector.InspectorTargetType;
import com.analyzer.core.model.Project;
import com.analyzer.core.model.ProjectDeserializer;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.model.ProjectHolder;
import com.analyzer.core.resource.JARClassLoaderService;
import com.analyzer.api.inspector.Inspector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Core analysis engine responsible for the new Project/ProjectFile-based
 * architecture.
 * <p>
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
    private final List<Analysis> availableAnalyses;
    private final GraphRepository graphRepository;
    private final ProjectFileRepository projectFileRepository;
    private final ClassNodeRepository classNodeRepository;
    private final InspectorProgressTracker progressTracker;
    private final ProjectHolder projectHolder;

    /**
     * Primary constructor used by PicoContainer for dependency injection.
     * All dependencies are injected automatically by the container.
     *
     * @param inspectorRegistry     the inspector registry
     * @param graphRepository       the graph repository
     * @param projectFileRepository the project file repository
     * @param classNodeRepository   the class node repository
     * @param progressTracker       the progress tracker
     * @param projectHolder         the project holder for DI into inspectors
     */
    public AnalysisEngine(InspectorRegistry inspectorRegistry,
            GraphRepository graphRepository,
            ProjectFileRepository projectFileRepository,
            ClassNodeRepository classNodeRepository,
            InspectorProgressTracker progressTracker,
            ProjectHolder projectHolder) {
        this.inspectorRegistry = inspectorRegistry;
        this.availableAnalyses = new ArrayList<>();
        this.graphRepository = graphRepository;
        this.projectFileRepository = projectFileRepository;
        this.classNodeRepository = classNodeRepository;
        this.progressTracker = progressTracker != null ? progressTracker : new InspectorProgressTracker();
        this.projectHolder = projectHolder;
    }

    /**
     * Sets the analyses to execute on the project.
     * This should be called after getting the AnalysisEngine from PicoContainer
     * and before calling analyzeProject().
     *
     * @param analyses list of analyses to execute
     */
    public void setAvailableAnalyses(List<Analysis> analyses) {
        this.availableAnalyses.clear();
        if (analyses != null) {
            this.availableAnalyses.addAll(analyses);
        }
    }

    /**
     * Analyzes a project using the new ProjectFile-based workflow with multi-pass
     * algorithm.
     * Supports incremental analysis by automatically reloading existing project
     * data if available.
     *
     * @param projectPath         the path to the project directory
     * @param requestedInspectors list of inspector names to use (null = all)
     * @param maxPasses           maximum number of analysis passes for convergence
     * @return the analyzed Project object
     */
    public Project analyzeProject(Path projectPath, List<String> requestedInspectors, int maxPasses)
            throws IOException {
        return analyzeProject(projectPath, requestedInspectors, maxPasses, null);
    }

    /**
     * Analyzes a project using the new ProjectFile-based workflow with multi-pass
     * algorithm.
     * Supports incremental analysis by automatically reloading existing project
     * data if available.
     *
     * @param projectPath         the path to the project directory
     * @param requestedInspectors list of inspector names to use (null = all)
     * @param maxPasses           maximum number of analysis passes for convergence
     * @param packageFilters      list of application package prefixes (e.g.,
     *                            "com.example", "com.myapp")
     * @return the analyzed Project object
     */
    public Project analyzeProject(Path projectPath, List<String> requestedInspectors, int maxPasses,
            List<String> packageFilters) throws IOException {
        logger.info("Starting multi-pass project analysis for: {} (max passes: {})", projectPath, maxPasses);

        // Step 1: Try to reload existing project data for incremental analysis
        Project project = loadExistingProjectOrCreate(projectPath);

        // Store package filters in Project metadata for inspectors to access
        if (packageFilters != null && !packageFilters.isEmpty()) {
            project.setProjectData("application.packages", packageFilters);
            logger.info("Application package filters configured: {}", packageFilters);
        }

        // Inject Project into ProjectHolder so inspectors can access it via DI
        projectHolder.setProject(project);
        logger.debug("Project injected into ProjectHolder for inspector access");

        // PHASE 1: File Discovery with Ignore Filtering
        logger.info("=== PHASE 1: File Discovery with Filtering ===");
        JARClassLoaderService jarClassLoaderService = inspectorRegistry.getJarClassLoaderService();
        // Disabled by default: .m2 repository contains many JARs that can slow down
        // analysis
        jarClassLoaderService.scanProjectJars(project, false);

        scanProjectFilesWithFiltering(project);

        // PHASE 2: ClassNode Collection
        collectClassNodesFromFiles(project);

        // Step 4: List the analyses available
        logger.info("Found {} available analyses", availableAnalyses.size());
        for (Analysis analysis : availableAnalyses) {
            logger.debug("Available analysis: {} - {}", analysis.getName(), analysis.getDescription());
        }

        // Step 5: Execute all the analyses
        executeAnalyses(project);

        // PHASE 3: Multi-pass ProjectFile Analysis with Convergence Detection
        logger.info("=== PHASE 3: Multi-pass ProjectFile Analysis ===");
        executeMultiPassInspectors(project, requestedInspectors, maxPasses);

        // PHASE 4: Multi-pass ClassNode Analysis with Convergence Detection
        logger.info("=== PHASE 4: Multi-pass ClassNode Analysis ===");
        executeMultiPassOnClassNodes(project, maxPasses);

        // Step 7: Store the project data in JSON format
        saveProjectAnalysisToJson(project);
        logger.info("Project analysis completed. Found {} files", project.getProjectFiles().size());

        return project;
    }

    /**
     * PHASE 1: File Discovery with Physical JAR Extraction
     * Scans the project directory for files using Inspector-based file detection
     * with ignore filtering and physical extraction of archives.
     */
    private void scanProjectFilesWithFiltering(Project project) throws IOException {
        Path projectPath = project.getProjectPath();
        logger.info("Phase 1: Discovering files in project directory with physical extraction");

        // Initialize ExecutionProfile for Phase 1 tracking
        ExecutionProfile phase1Profile = new ExecutionProfile(
                this.inspectorRegistry.getFileDetectors().stream()
                        .map(FileDetector::getName)
                        .toList());

        // Initialize file ignore filter
        FileIgnoreFilter ignoreFilter = FileIgnoreFilter.fromApplicationProperties();
        logger.info("Initialized file filtering with {} ignore patterns", ignoreFilter.getPatternCount());

        try (Stream<Path> paths = Files.walk(projectPath)) {
            List<Path> allFiles = paths
                    .filter(Files::isRegularFile)
                    .toList();

            logger.info("Found {} files in project directory before filtering", allFiles.size());

            // Apply ignore filtering
            List<Path> filteredFiles = allFiles.stream()
                    .filter(filePath -> {
                        boolean shouldIgnore = ignoreFilter.shouldIgnore(filePath, projectPath);
                        return !shouldIgnore;
                    })
                    .toList();

            logger.info("After filtering: {} files remaining (filtered out: {})",
                    filteredFiles.size(), allFiles.size() - filteredFiles.size());

            // Phase 1a: Scan filesystem files and collect archives
            logger.info("Phase 1a: Scanning filesystem files");
            List<ProjectFile> archiveFiles = new ArrayList<>();

            try (ProgressBar pb = new ProgressBar("Phase 1a: Filesystem Files", filteredFiles.size())) {
                logger.info("Using {} file detection inspectors", inspectorRegistry.getFileDetectors().size());
                for (Path filePath : filteredFiles) {
                    ProjectFile projectFile = scanFile(project, filePath, phase1Profile,
                            ExecutionProfile.ExecutionPhase.PHASE_1A_FILESYSTEM_SCAN);

                    // Collect archive files for physical extraction
                    if (projectFile != null && isArchiveFile(projectFile)) {
                        archiveFiles.add(projectFile);
                    }

                    pb.step();
                }
            }

            // Phase 1b: Physical extraction of archives
            if (!archiveFiles.isEmpty()) {
                logger.info("Phase 1b: Physically extracting {} archive files", archiveFiles.size());
                extractArchivesPhysically(project, archiveFiles);
            }

            // Phase 1c: Re-scan to include extracted content
            logger.info("Phase 1c: Re-scanning to include extracted content");
            rescanForExtractedContent(project, phase1Profile);
        }

        // Log Phase 1 execution profile
        phase1Profile.setAnalysisMetrics(1, project.getProjectFiles().size());
        phase1Profile.markAnalysisComplete();
        logger.info("=== Phase 1 Execution Summary ===");
        phase1Profile.logReport();
        logger.info("=== End Phase 1 Summary ===");

        logger.info("Phase 1 completed. File discovery finished with {} project files created",
                project.getProjectFiles().size());
    }

    /**
     * Scans a single file with all available file detection inspectors.
     *
     * @param project          the project context
     * @param filePath         the file path to scan
     * @param executionProfile the execution profile for tracking (optional)
     * @param phase            the execution phase for tracking
     * @return the created ProjectFile
     */
    private ProjectFile scanFile(Project project, Path filePath, ExecutionProfile executionProfile,
            ExecutionProfile.ExecutionPhase phase) {
        // Create ProjectFile for this path (all files get a ProjectFile)
        String relativePath = project.getProjectPath().relativize(filePath).toString();
        ProjectFile projectFile = project.getOrCreateProjectFile(relativePath, filePath);

        // Apply file detection inspectors to identify and tag the file
        for (FileDetector detector : inspectorRegistry.getFileDetectors()) {
            try {
                if (detector.supports(projectFile)) {
                    // Record execution start time for performance tracking
                    long startTime = System.nanoTime();

                    executeDetector(projectFile, detector);

                    // Record execution timing in ExecutionProfile
                    if (executionProfile != null) {
                        long executionTimeNanos = System.nanoTime() - startTime;
                        executionProfile.recordInspectorExecution(detector.getName(), phase, executionTimeNanos);
                    }

                    logger.debug("File detection detector {} matched file: {}",
                            detector.getName(), relativePath);
                }
            } catch (Exception e) {
                logger.warn("Error applying file detection detector {} to file {}: {}",
                        detector.getName(), filePath, e.getMessage());

                // Record failed execution in ExecutionProfile
                if (executionProfile != null) {
                    executionProfile.recordInspectorExecution(detector.getName(), phase, 0); // 0 time for failed
                                                                                             // execution
                }
            }
        }

        return projectFile;
    }

    /**
     * Checks if a ProjectFile represents an archive (JAR/WAR/EAR/ZIP).
     */
    private boolean isArchiveFile(ProjectFile file) {
        if (file == null || file.getFileName() == null) {
            return false;
        }
        String fileName = file.getFileName().toLowerCase();
        return fileName.endsWith(".jar") || fileName.endsWith(".war") ||
                fileName.endsWith(".ear") || fileName.endsWith(".zip");
    }

    /**
     * Physically extracts archives using the ExtractionManager.
     */
    private void extractArchivesPhysically(Project project, List<ProjectFile> archiveFiles) {
        ExtractionManager extractionManager = new ExtractionManager(FileIgnoreFilter.fromApplicationProperties());

        try {
            ExtractionManager.ExtractionManagerResult result = extractionManager.cleanAndExtractAll(
                    archiveFiles, project.getProjectPath());

            if (result.isSuccessful()) {
                logger.info("Physical extraction completed: {} archives processed, {} extracted, {} skipped",
                        result.getTotalArchives(), result.getExtractedArchives(), result.getSkippedArchives());

                if (result.getErrorArchives() > 0) {
                    logger.warn("Physical extraction had {} errors", result.getErrorArchives());
                }
            } else {
                logger.error("Physical extraction failed: {}", result.getErrorMessage());
            }

        } catch (Exception e) {
            logger.error("Error during physical extraction: {}", e.getMessage());
        }
    }

    /**
     * Re-scans the project directory to include extracted content from
     * .analysis/binaries/
     * <p>
     * NOTE: This method creates ProjectFiles directly without applying file
     * detection
     * inspectors to avoid re-extracting JAR files that are already extracted.
     */
    private void rescanForExtractedContent(Project project, ExecutionProfile executionProfile) throws IOException {
        Path projectPath = project.getProjectPath();
        Path extractionRoot = projectPath.resolve(".analysis").resolve("binaries");

        if (!Files.exists(extractionRoot)) {
            logger.debug("No extraction directory found, skipping re-scan");
            return;
        }

        // Initialize file ignore filter for extracted content
        FileIgnoreFilter ignoreFilter = FileIgnoreFilter.fromApplicationProperties();

        try (Stream<Path> paths = Files.walk(extractionRoot)) {
            List<Path> extractedFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(filePath -> !ignoreFilter.shouldIgnore(filePath, projectPath))
                    .toList();

            logger.info("Found {} files in extracted content", extractedFiles.size());

            try (ProgressBar pb = new ProgressBar("Phase 1c: Extracted Content", extractedFiles.size())) {
                for (Path filePath : extractedFiles) {
                    // Create ProjectFile for extracted content WITHOUT applying file detection
                    // inspectors
                    // This prevents extracted JAR files from being marked for re-extraction
                    String relativePath = projectPath.relativize(filePath).toString();
                    ProjectFile projectFile = project.getOrCreateProjectFile(relativePath, filePath);

                    logger.debug("Added extracted file to project: {}", relativePath);
                    pb.step();
                }
            }

            logger.info("Re-scan completed: {} extracted files added to project", extractedFiles.size());

        } catch (IOException e) {
            logger.warn("Failed to re-scan extracted content: {}", e.getMessage());
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
     * Gets ClassNode collectors from the inspector registry.
     *
     * @return list of ClassNode collectors
     */
    private List<ClassNodeCollector> getClassNodeCollectors() {
        return inspectorRegistry.getClassNodeCollectors();
    }

    /**
     * PHASE 2: ClassNode Collection
     * Creates JavaClassNode objects from ProjectFiles using registered collectors.
     */
    private void collectClassNodesFromFiles(Project project) {
        logger.info("=== PHASE 2: ClassNode Collection ===");

        List<ClassNodeCollector> collectors = getClassNodeCollectors();

        logger.info("Using {} ClassNode collectors", collectors.stream().map(ClassNodeCollector::getName).toList());

        // Check if repositories are configured
        if (projectFileRepository == null || classNodeRepository == null) {
            logger.warn("Repositories not configured, skipping ClassNode collection");
            return;
        }

        CollectionContext context = new CollectionContext(
                projectFileRepository,
                classNodeRepository);

        try (ProgressBar pb = new ProgressBar("Phase 2: Collecting Classes",
                project.getProjectFiles().size())) {
            for (ProjectFile projectFile : project.getProjectFiles().values()) {
                for (ClassNodeCollector collector : collectors) {
                    try {
                        if (collector.canCollect(projectFile)) {
                            collector.collect(projectFile, context);
                            logger.debug("Collector {} created nodes from {}",
                                    collector.getName(),
                                    projectFile.getRelativePath());
                        }
                    } catch (Exception e) {
                        logger.error("Error in collector {} on file {}: {}",
                                collector.getName(),
                                projectFile.getRelativePath(),
                                e.getMessage());
                    }
                }
                pb.step();
            }
        }

        int classCount = classNodeRepository.findAll().size();
        logger.info("Phase 2 completed: {} JavaClassNode objects created", classCount);
    }

    /**
     * Gets ClassNode inspectors from the inspector registry.
     *
     * @return list of ClassNode inspectors
     */
    @SuppressWarnings("unchecked")
    private List<Inspector<JavaClassNode>> getClassNodeInspectors() {
        return (List<Inspector<JavaClassNode>>) (List<?>) inspectorRegistry
                .getClassNodeInspectors();
    }

    /**
     * PHASE 4: Multi-pass ClassNode Analysis with Convergence Detection
     * Executes inspectors on JavaClassNode objects with multiple passes until
     * convergence.
     */
    private void executeMultiPassOnClassNodes(Project project, int maxPasses) {
        List<Inspector<JavaClassNode>> inspectors = getClassNodeInspectors();

        if (inspectors.isEmpty()) {
            logger.info("No ClassNode inspectors found, skipping Phase 4");
            return;
        }

        // Check if repositories are configured
        if (classNodeRepository == null) {
            logger.warn("ClassNodeRepository not configured, skipping Phase 4");
            return;
        }

        List<JavaClassNode> classNodes = classNodeRepository.findAll();

        if (classNodes.isEmpty()) {
            logger.info("No JavaClassNode objects to analyze, skipping Phase 4");
            return;
        }

        logger.info("Phase 4: Executing {} inspectors on {} class nodes (max passes: {})",
                inspectors.size(), classNodes.size(), maxPasses);

        // Create multi-pass executor configuration
        MultiPassExecutor<JavaClassNode> executor = new MultiPassExecutor<>();
        MultiPassExecutor.ExecutionConfig<JavaClassNode> config = new MultiPassExecutor.ExecutionConfig<>(
                "Phase 4",
                maxPasses,
                ExecutionProfile.ExecutionPhase.PHASE_4_CLASSNODE_ANALYSIS,
                classNodeRepository::findAll,
                inspectors,
                this::analyzeClassNodeWithTracking);

        // Execute multi-pass analysis
        MultiPassExecutor.ExecutionResult result = executor.execute(config);

        logger.info("Phase 4 completed: {} passes executed, converged: {}",
                result.getPassesExecuted(), result.isConverged());
    }

    /**
     * Analyzes a single JavaClassNode with execution tracking.
     * Uses JavaClassNode's execution tracking to determine if inspectors need to
     * run.
     *
     * @param classNode        the class node to analyze
     * @param inspectors       the inspectors to run
     * @param passStartTime    the start time of the current pass
     * @param executionProfile the execution profile for tracking
     * @param pass             the current pass number
     * @return set of inspector names that were triggered for this node
     */
    private Set<String> analyzeClassNodeWithTracking(JavaClassNode classNode,
            List<Inspector<JavaClassNode>> inspectors,
            LocalDateTime passStartTime,
            ExecutionProfile executionProfile,
            int pass) {
        Set<String> triggeredInspectors = new HashSet<>();
        ExecutionProfile.ExecutionPhase phase = ExecutionProfile.ExecutionPhase.PHASE_4_CLASSNODE_ANALYSIS;

        for (Inspector<JavaClassNode> inspector : inspectors) {
            try {
                String inspectorName = inspector.getName();

                logger.debug("Inspector '{}' needs to run on class: {}", inspectorName,
                        classNode.getFullyQualifiedName());

                if (inspector.canProcess(classNode)) {
                    // Record execution start time for performance tracking
                    long startTime = System.nanoTime();

                    // Execute inspector
                    NodeDecorator<JavaClassNode> decorator = new NodeDecorator<>(classNode);
                    inspector.inspect(classNode, decorator);

                    // Record execution timing in ExecutionProfile with pass number
                    long executionTimeNanos = System.nanoTime() - startTime;
                    executionProfile.recordInspectorExecution(inspectorName, phase, pass, executionTimeNanos);

                    logger.debug("Inspector '{}' executed on class: {}", inspectorName,
                            classNode.getFullyQualifiedName());

                    // Mark inspector as executed
                    classNode.markInspectorExecuted(inspectorName, passStartTime);
                    triggeredInspectors.add(inspectorName);
                } else {
                    logger.debug("Inspector '{}' not supported for class: {}", inspectorName,
                            classNode.getFullyQualifiedName());
                }
            } catch (Exception e) {
                logger.error("Error running inspector '{}' on class '{}': {}",
                        inspector.getName(), classNode.getFullyQualifiedName(), e.getMessage());

                // Record failed execution in ExecutionProfile
                executionProfile.recordInspectorExecution(inspector.getName(), phase, 0);

                // Mark as executed to prevent repeated errors
                classNode.markInspectorExecuted(inspector.getName(), passStartTime);
                triggeredInspectors.add(inspector.getName());
            }
        }

        return triggeredInspectors;
    }

    /**
     * PHASE 3: Multi-pass Analysis with Convergence Detection
     * Executes inspectors with multiple passes until convergence or max passes
     * reached.
     */
    private void executeMultiPassInspectors(Project project, List<String> requestedInspectors, int maxPasses) {
        List<Inspector<ProjectFile>> projectFileInspectors = getProjectFileInspectors(requestedInspectors);

        if (projectFileInspectors.isEmpty()) {
            logger.warn("No ProjectFile inspectors found");
            return;
        }

        logger.info("Phase 3: Executing {} inspectors on {} project files (max passes: {})",
                projectFileInspectors.size(), project.getProjectFiles().size(), maxPasses);

        // Print the specific inspectors that will be executed
        List<String> executingInspectorNames = projectFileInspectors.stream()
                .map(Inspector::getName)
                .toList();
        logger.debug("Inspectors selected for execution ({}): [{}]",
                executingInspectorNames.size(),
                String.join(", ", executingInspectorNames));

        // Create multi-pass executor configuration
        MultiPassExecutor<ProjectFile> executor = new MultiPassExecutor<>();
        MultiPassExecutor.ExecutionConfig<ProjectFile> config = new MultiPassExecutor.ExecutionConfig<>(
                "Phase 3",
                maxPasses,
                ExecutionProfile.ExecutionPhase.PHASE_3_PROJECTFILE_ANALYSIS,
                () -> project.getProjectFiles().values(),
                projectFileInspectors,
                this::analyzeProjectFileWithTrackingAndCollection);

        // Execute multi-pass analysis
        MultiPassExecutor.ExecutionResult result = executor.execute(config);

        logger.info("Phase 3 completed: {} passes executed, converged: {}",
                result.getPassesExecuted(), result.isConverged());

        // Mark progress tracking completed and log comprehensive report
        progressTracker.markTrackingCompleted();
        progressTracker.logProgressReport();
    }

    /**
     * Result of analyzing a ProjectFile - contains processing status and triggered
     * inspectors.
     */
    private record ProjectFileAnalysisResult(boolean wasProcessed, Set<String> triggeredInspectors) {
    }

    /**
     * Analyzes a single ProjectFile with execution tracking and collects triggered
     * inspectors.
     * Uses ProjectFile's execution tracking to determine if inspectors need to run.
     *
     * @param projectFile      the file to analyze
     * @param inspectors       the inspectors to run
     * @param passStartTime    the start time of the current pass
     * @param executionProfile the execution profile for tracking
     * @param pass             the current pass number
     * @return set of inspector names that were triggered for this file
     */
    private Set<String> analyzeProjectFileWithTrackingAndCollection(ProjectFile projectFile,
            List<Inspector<ProjectFile>> inspectors,
            LocalDateTime passStartTime,
            ExecutionProfile executionProfile,
            int pass) {
        ProjectFileAnalysisResult result = analyzeProjectFileInternal(projectFile, inspectors, passStartTime, true,
                executionProfile, pass);
        return result.triggeredInspectors();
    }

    /**
     * Internal method that contains the core logic for analyzing a ProjectFile.
     * This eliminates code duplication between the various analyzeProjectFile*
     * methods.
     *
     * @param projectFile      the file to analyze
     * @param inspectors       the inspectors to run
     * @param passStartTime    the start time of the current pass (null if not using
     *                         tracking)
     * @param useTracking      whether to use execution tracking
     * @param executionProfile the execution profile for tracking (null if not
     *                         using)
     * @param pass             the current pass number (0 if not using)
     * @return analysis result containing processing status and triggered inspectors
     */
    private ProjectFileAnalysisResult analyzeProjectFileInternal(ProjectFile projectFile,
            List<Inspector<ProjectFile>> inspectors,
            LocalDateTime passStartTime,
            boolean useTracking,
            ExecutionProfile executionProfile,
            int pass) {
        boolean fileProcessed = false;
        Set<String> triggeredInspectors = new HashSet<>();

        // Determine the execution phase based on pass number
        ExecutionProfile.ExecutionPhase phase = ExecutionProfile.ExecutionPhase.PHASE_3_PROJECTFILE_ANALYSIS;
        if (pass == 1) {
            // First pass could be mixed, but let's classify as Analysis Pass since it's
            // Phase 3
            phase = ExecutionProfile.ExecutionPhase.PHASE_3_PROJECTFILE_ANALYSIS;
        }

        for (Inspector<ProjectFile> inspector : inspectors) {
            try {
                String inspectorName = inspector.getName();
                boolean shouldRun = true;

                // Check if this inspector needs to run on this file (only if using tracking)
                if (useTracking && projectFile.isInspectorUpToDate(inspectorName)) {
                    logger.debug("Inspector '{}' up-to-date for file: {}", inspectorName,
                            projectFile.getRelativePath());
                    shouldRun = false;
                }

                if (shouldRun) {
                    logger.debug("Inspector '{}' needs to run on file: {}", inspectorName,
                            projectFile.getRelativePath());

                    if (inspector.canProcess(projectFile)) {
                        // Record execution start time for performance tracking
                        long startTime = System.nanoTime();

                        executeInspector(projectFile, inspector);

                        // Record execution timing in ExecutionProfile with pass number
                        if (executionProfile != null) {
                            long executionTimeNanos = System.nanoTime() - startTime;
                            executionProfile.recordInspectorExecution(inspectorName, phase, pass, executionTimeNanos);
                        }

                        logger.debug("Inspector '{}' executed on file: {}", inspectorName,
                                projectFile.getRelativePath());

                        // Mark inspector as executed (only if using tracking)
                        if (useTracking) {
                            projectFile.markInspectorExecuted(inspectorName, passStartTime);
                            // Add to triggered set since we processed it
                            triggeredInspectors.add(inspectorName);
                        }
                    } else {
                        logger.debug("Inspector '{}' not supported for file: {}", inspectorName,
                                projectFile.getRelativePath());
                    }

                    fileProcessed = true;
                }
            } catch (Exception e) {
                logger.error("Error running inspector '{}' on file '{}': {}",
                        inspector.getName(), projectFile.getRelativePath(), e.getMessage());
                projectFile.setProperty(InspectorTags.PROCESSING_ERROR, "ERROR: " + e.getMessage());

                // Record failed execution in ExecutionProfile
                if (executionProfile != null) {
                    executionProfile.recordInspectorExecution(inspector.getName(), phase, 0); // 0 time for failed
                                                                                              // execution
                }

                // Mark as executed to prevent repeated errors (only if using tracking)
                if (useTracking) {
                    projectFile.markInspectorExecuted(inspector.getName(), passStartTime);
                    triggeredInspectors.add(inspector.getName());
                }

                fileProcessed = true;
            }
        }

        return new ProjectFileAnalysisResult(fileProcessed, triggeredInspectors);
    }

    private void executeInspector(ProjectFile projectFile, Inspector<ProjectFile> inspector) {
        // Record inspector trigger for progress tracking
        progressTracker.recordInspectorTrigger(inspector.getName(), projectFile);

        // GraphRepository is now injected via @Inject in inspector constructors
        // No need for manual injection anymore

        NodeDecorator<ProjectFile> decorator = new NodeDecorator<>(projectFile);
        inspector.inspect(projectFile, decorator);
    }

    private void executeDetector(ProjectFile projectFile, FileDetector fileDetector) {
        // Record fileDetector trigger for progress tracking
        progressTracker.recordInspectorTrigger(fileDetector.getName(), projectFile);

        // GraphRepository is now injected via @Inject in fileDetector constructors
        // No need for manual injection anymore

        NodeDecorator<ProjectFile> decorator = new NodeDecorator<>(projectFile);
        fileDetector.detect(decorator);
    }

    /**
     * Gets ProjectFile inspectors to execute based on request.
     * Uses type-safe filtering with InspectorTargetType to separate inspectors by
     * phase.
     *
     * @param requestedInspectors list of specific inspectors to run, or null for
     *                            all
     * @return list of inspectors that target PROJECT_FILE
     */
    @SuppressWarnings("unchecked")
    private List<Inspector<ProjectFile>> getProjectFileInspectors(List<String> requestedInspectors) {
        List<Inspector> allInspectors = getInspectorsToExecute(requestedInspectors);
        List<Inspector<ProjectFile>> projectFileInspectors = new ArrayList<>();
        int excludedCount = 0;

        for (Inspector inspector : allInspectors) {
            // TYPE-SAFE FILTERING: Use getTargetType() instead of instanceof checks
            // This is explicit, maintainable, and avoids fragile reflection
            InspectorTargetType targetType = inspector.getTargetType();

            if (targetType == InspectorTargetType.PROJECT_FILE || targetType == InspectorTargetType.ANY) {
                // Include inspectors that target PROJECT_FILE or ANY
                projectFileInspectors.add((Inspector<ProjectFile>) inspector);
            } else {
                // Exclude inspectors that target other types (e.g., JAVA_CLASS_NODE)
                logger.debug("Excluding {} inspector '{}' from Phase 3 (ProjectFile analysis) - " +
                        "will execute in Phase 4 (target type: {})",
                        targetType, inspector.getName(), targetType);
                excludedCount++;
            }
        }

        if (excludedCount > 0) {
            logger.info("Phase 3: Filtered {} non-ProjectFile inspectors from ProjectFile analysis - " +
                    "they will execute in their appropriate phase", excludedCount);
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
     * Gets the GraphRepository used by this AnalysisEngine.
     *
     * @return the graph repository, or null if not configured
     */
    public GraphRepository getGraphRepository() {
        return graphRepository;
    }

    /**
     * Builds and returns a graph based on the specified criteria.
     *
     * @param nodeTypes the types of nodes to include (null = all)
     * @param edgeTypes the types of edges to include (null = all)
     * @return the built graph, or null if no GraphRepository is configured
     */
    public org.jgrapht.Graph<GraphNode, GraphEdge> buildGraph(Set<String> nodeTypes, Set<String> edgeTypes) {
        if (graphRepository == null) {
            logger.warn("No GraphRepository configured, cannot build graph");
            return null;
        }
        return graphRepository.buildGraph(nodeTypes, edgeTypes);
    }

    /**
     * Load existing project data if available, otherwise create a new project.
     * This enables incremental analysis by reusing previously computed results.
     */
    private Project loadExistingProjectOrCreate(Path projectPath) {
        Path existingAnalysisFile = projectPath.resolve(Project.DEFAULT_FILE_NAME);

        if (Files.exists(existingAnalysisFile)) {
            logger.info("Found existing project analysis file, attempting to load: {}", existingAnalysisFile);

            try {
                ProjectDeserializer deserializer = new ProjectDeserializer();

                // Validate the file first
                ProjectDeserializer.ValidationResult validation = deserializer
                        .validateProjectFile(existingAnalysisFile);
                if (!validation.isValid()) {
                    logger.warn("Existing project analysis file is invalid: {}", validation.getMessage());
                    logger.info("Creating new project analysis instead");
                    return new Project(projectPath, projectPath.getFileName().toString(), projectFileRepository);
                }

                // Load the project with graph repository and project file repository if
                // available
                Project existingProject = deserializer.loadProject(existingAnalysisFile, graphRepository,
                        projectFileRepository);

                logger.info("Successfully loaded existing project analysis with {} files",
                        existingProject.getProjectFiles().size());

                // Update last analyzed time to indicate this is a continuation
                existingProject.updateLastAnalyzed();

                return existingProject;

            } catch (Exception e) {
                logger.warn("Failed to load existing project analysis: {}", e.getMessage());
                logger.info("Creating new project analysis instead");
            }
        } else {
            logger.info("No existing project analysis found, creating new analysis for: {}", projectPath);
        }

        // Fallback: create new project with repository
        return new Project(projectPath, projectPath.getFileName().toString(), projectFileRepository);
    }

    /**
     * Save project analysis results to JSON format.
     */
    private void saveProjectAnalysisToJson(Project project) {
        try {
            GraphSerializer serializer = new GraphSerializer();
            Path outputPath = project.getProjectPath().resolve(Project.DEFAULT_FILE_NAME);
            serializer.saveProjectWithGraph(project, graphRepository, outputPath);

            // Also save graph-only data
            if (graphRepository != null && graphRepository.getNodeCount() > 0) {
                Path graphPath = project.getProjectPath().resolve("graph-data.json");
                serializer.saveGraphOnly(graphRepository, graphPath);
            }
        } catch (Exception e) {
            logger.warn("Failed to save project analysis to JSON: {}", e.getMessage());
        }
    }

    /**
     * Gets statistics about the analysis.
     */
    public AnalysisStatistics getStatistics() {
        return new AnalysisStatistics(
                inspectorRegistry.getInspectorCount(),
                availableAnalyses.size());
    }

    /**
     * Statistics about the analysis execution.
     */
    public record AnalysisStatistics(int totalInspectors, int totalAnalyses) {

        @Override
        public String toString() {
            return String.format("Analysis Statistics: %d total inspectors, %d analyses",
                    totalInspectors, totalAnalyses);
        }
    }

    /**
     * Utility for serializing project analysis results including graph data to
     * JSON.
     */
    public static class GraphSerializer {

        private final ObjectMapper objectMapper;

        public GraphSerializer() {
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
            this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }

        /**
         * Serialize complete project analysis to JSON including graph data.
         */
        public void saveProjectWithGraph(Project project, GraphRepository graphRepository, Path outputPath)
                throws IOException {
            ProjectAnalysisData data = new ProjectAnalysisData();

            // Project metadata
            data.projectName = project.getProjectName();
            data.projectPath = project.getProjectPath().toString();
            data.createdAt = project.getCreatedAt();
            data.lastAnalyzed = project.getLastAnalyzed();
            data.projectData = new HashMap<>(project.getAllProjectData());

            // Project files
            data.projectFiles = new HashMap<>();
            for (Map.Entry<String, ProjectFile> entry : project.getProjectFiles().entrySet()) {
                data.projectFiles.put(entry.getKey(), entry.getValue());
            }

            // Graph data
            if (graphRepository != null) {
                data.graphNodes = graphRepository.getNodes();
                data.graphEdges = graphRepository.getAllEdges();
                data.nodeCount = graphRepository.getNodeCount();
                data.edgeCount = graphRepository.getEdgeCount();
            }

            objectMapper.writeValue(outputPath.toFile(), data);
        }

        /**
         * Save only graph data to JSON.
         */
        public void saveGraphOnly(GraphRepository graphRepository, Path outputPath) throws IOException {
            GraphData graphData = new GraphData();
            graphData.nodes = graphRepository.getNodes();
            graphData.edges = graphRepository.getAllEdges();
            graphData.nodeCount = graphRepository.getNodeCount();
            graphData.edgeCount = graphRepository.getEdgeCount();
            graphData.exportedAt = new Date();

            objectMapper.writeValue(outputPath.toFile(), graphData);
        }

        /**
         * Data structure for complete project analysis serialization.
         */
        public static class ProjectAnalysisData {
            public String projectName;
            public String projectPath;
            public Date createdAt;
            public Date lastAnalyzed;
            public Map<String, Object> projectData;
            public Map<String, ProjectFile> projectFiles;

            // Graph data
            public Collection<GraphNode> graphNodes;
            public Collection<GraphEdge> graphEdges;
            public int nodeCount;
            public int edgeCount;
        }

        /**
         * Data structure for graph-only serialization.
         */
        public static class GraphData {
            public Collection<GraphNode> nodes;
            public Collection<GraphEdge> edges;
            public int nodeCount;
            public int edgeCount;
            public Date exportedAt;
        }
    }
}
