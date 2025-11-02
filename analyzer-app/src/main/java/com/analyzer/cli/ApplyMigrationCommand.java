package com.analyzer.cli;

import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.engine.Checkpoint;
import com.analyzer.migration.engine.ExecutionResult;
import com.analyzer.migration.engine.MigrationEngine;
import com.analyzer.migration.engine.ProgressInfo;
import com.analyzer.migration.engine.ProgressTracker;
import com.analyzer.migration.engine.listeners.ConsoleProgressListener;
import com.analyzer.migration.loader.IncludeResolver;
import com.analyzer.migration.loader.MigrationPlanConverter;
import com.analyzer.migration.loader.YamlMigrationPlanLoader;
import com.analyzer.migration.loader.dto.MigrationPlanDTO;
import com.analyzer.migration.plan.MigrationPlan;
import com.analyzer.migration.state.MigrationStateManager;
import com.analyzer.migration.state.StateFileListener;
import com.analyzer.migration.state.model.MigrationExecutionState;
import com.analyzer.core.db.H2GraphDatabase;
import com.analyzer.core.db.H2GraphStorageRepository;
import com.analyzer.core.db.loader.LoadOptions;
import com.analyzer.core.serialization.JsonSerializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Apply migration command implementation.
 * Executes EJB-to-Spring Boot migration plans from YAML files with full
 * variable support.
 */
@Command(name = "apply", description = "Apply migration plan to transform EJB project to Spring Boot")
public class ApplyMigrationCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(ApplyMigrationCommand.class);
    private static final Pattern ENV_VAR_PATTERN = Pattern
            .compile("\\$\\{env\\.([A-Za-z_][A-Za-z0-9_]*)(?::(-[^}]*))?\\}");

    // ========================================================================
    // Required Parameters
    // ========================================================================

    @Option(names = { "--project" }, description = "Path to the EJB project directory to migrate", required = true)
    private String projectPath;

    @Option(names = {
            "--plan" }, description = "Path to migration plan YAML file or classpath resource (e.g., migration-plans/jboss-to-springboot-phase0-1.yaml)", required = true)
    private String planPath;

    // ========================================================================
    // Variable Parameters
    // ========================================================================

    @Option(names = {
            "-D" }, description = "Define variables (Maven-style): -Dkey=value. Highest priority.", mapFallbackValue = "")
    private Map<String, String> systemProperties = new LinkedHashMap<>();

    @Option(names = { "--variable" }, description = "Define variables: --variable key=value", mapFallbackValue = "")
    private Map<String, String> cliVariables = new LinkedHashMap<>();

    @Option(names = { "--variables" }, description = "Path to properties file containing variables")
    private String variablesFile;

    @Option(names = { "--list-variables" }, description = "List all variables required by the migration plan and exit")
    private boolean listVariables;

    // ========================================================================
    // Execution Control Parameters
    // ========================================================================

    @Option(names = { "--task" }, description = "Execute only specific task by ID (e.g., task-000)")
    private String taskId;

    @Option(names = { "--phase" }, description = "Execute only specific phase by ID (e.g., phase-0)")
    private String phaseId;

    @Option(names = { "--resume" }, description = "Resume execution from last checkpoint")
    private boolean resume;

    @Option(names = { "--dry-run" }, description = "Validate plan and variables without executing")
    private boolean dryRun;

    @Option(names = {
            "--interactive" }, description = "Enable interactive mode with validation prompts", defaultValue = "true")
    private boolean interactive;

    @Option(names = {
            "--step-by-step" }, description = "Enable step-by-step mode - press Enter to proceed between each block")
    private boolean stepByStep;

    @Option(names = { "--status" }, description = "Show migration progress status and exit")
    private boolean statusOnly;

    // ========================================================================
    // Optional Parameters
    // ========================================================================

    @Option(names = {
            "--database" }, description = "Path to H2 database for progress tracking (default: project/.analysis/migration.db)")
    private String databasePath;

    @Option(names = { "--verbose" }, description = "Enable verbose output")
    private boolean verbose;

    // ========================================================================
    // Main Execution Method
    // ========================================================================

    @Override
    public Integer call() throws Exception {
        logger.info("Apply Migration Command - Starting execution");

        try {
            // 1. Handle --list-variables mode
            if (listVariables) {
                return handleListVariables();
            }

            // 2. Handle --status mode
            if (statusOnly) {
                return handleStatus();
            }

            // 3. Validate parameters
            if (!validateParameters()) {
                return 1;
            }

            // 4. Load migration plan
            MigrationPlan plan = loadMigrationPlan();
            if (plan == null) {
                return 1;
            }

            // 5. Build variable map with priority resolution
            Map<String, String> variables = buildVariableMap(plan);

            // 6. Validate variables (check for unresolved placeholders)
            if (!validateVariables(variables, plan)) {
                return 1;
            }

            // 7. Display execution configuration
            displayExecutionConfiguration(plan, variables);

            // 8. Execute migration (including dry-run mode)
            return executeMigration(plan, variables);

        } catch (Exception e) {
            logger.error("Migration execution failed: {}", e.getMessage(), e);
            return 1;
        }
    }

    // ========================================================================
    // List Variables Mode
    // ========================================================================

    private Integer handleListVariables() throws Exception {
        logger.info("Listing variables required by migration plan: {}", planPath);

        MigrationPlan plan = loadMigrationPlan();
        if (plan == null) {
            return 1;
        }

        System.out.println("\n=== Migration Plan Variables ===");
        System.out.println("Plan: " + plan.getName());
        System.out.println("Version: " + plan.getVersion());
        System.out.println();

        // Get variables from YAML plan
        Map<String, String> planVariables = loadPlanVariables();

        if (planVariables == null || planVariables.isEmpty()) {
            System.out.println("No variables defined in migration plan.");
        } else {
            System.out.println("Defined Variables:");
            System.out.println("-----------------");

            // Sort variables alphabetically
            List<String> sortedKeys = new ArrayList<>(planVariables.keySet());
            Collections.sort(sortedKeys);

            for (String key : sortedKeys) {
                String value = planVariables.get(key);
                System.out.printf("  %-30s = %s%n", key, value);
            }

            System.out.println();
            System.out.println("Auto-Derived Variables:");
            System.out.println("----------------------");
            System.out.println("  project.root                   = <project path>");
            System.out.println("  project.name                   = <project directory name>");
            System.out.println("  plan.name                      = <migration plan name>");
            System.out.println("  current_date                   = <current date ISO format>");
            System.out.println("  current_datetime               = <current datetime ISO format>");
            System.out.println("  user.name                      = <system user name>");
            System.out.println("  user.home                      = <user home directory>");

            System.out.println();
            System.out.println("Variable Override Options:");
            System.out.println("-------------------------");
            System.out.println("  1. Command-line -D flags:        -Dkey=value (highest priority)");
            System.out.println("  2. Command-line --variable:      --variable key=value");
            System.out.println("  3. Properties file:              --variables /path/to/file.properties");
            System.out.println("  4. Plan defaults:                (values shown above)");
            System.out.println("  5. Environment variables:        ${env.VAR_NAME} or ${env.VAR_NAME:-default}");
        }

        System.out.println();
        return 0;
    }

    // ========================================================================
    // Status Mode
    // ========================================================================

    private Integer handleStatus() throws Exception {
        logger.info("Checking migration status for project: {}", projectPath);

        // Validate project path
        Path projectDir = Paths.get(projectPath);
        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            logger.error("Project directory does not exist or is not a directory: {}", projectPath);
            return 1;
        }

        // Check state file
        MigrationStateManager stateManager = new MigrationStateManager(projectDir);
        if (!stateManager.exists()) {
            System.out.println("\n=== Migration Status ===");
            System.out.println("Status: No migration executed yet");
            System.out.println("State File: Not found at " + stateManager.getStateFilePath());
            System.out.println("\nUse 'apply --project " + projectPath + " --plan <plan>' to start migration");
            System.out.println();
            return 0;
        }

        // Load and display status
        try {
            com.analyzer.migration.state.model.MigrationState state = stateManager.loadState();

            System.out.println("\n=== Migration Status ===");
            System.out.println("Project:      " + state.getProjectRoot());
            System.out.println("Last Updated: " + state.getLastUpdated());
            System.out.println("Migrations:   " + state.getMigrations().size());
            System.out.println();

            if (state.getMigrations().isEmpty()) {
                System.out.println("No migrations found in state file.");
            } else {
                System.out.println("Active Migrations:");
                System.out.println("─────────────────────────────────────────────");
                for (String planKey : state.getMigrations().keySet()) {
                    MigrationExecutionState migState = state.getMigration(planKey);
                    System.out.printf("  %s%n", migState.getPlanName());
                    System.out.printf("    Status:       %s%n", migState.getStatus());
                    System.out.printf("    Started:      %s%n", migState.getStartedAt());
                    System.out.printf("    Completed:    %d phases%n", migState.getCompletedPhases().size());
                    if (!migState.getFailedPhases().isEmpty()) {
                        System.out.printf("    Failed:       %d phases%n", migState.getFailedPhases().size());
                    }
                    System.out.println();
                }
            }

            return 0;

        } catch (Exception e) {
            logger.error("Failed to read migration status: {}", e.getMessage());
            return 1;
        }
    }

    // ========================================================================
    // Parameter Validation
    // ========================================================================

    private boolean validateParameters() {
        boolean valid = true;

        // Validate project path
        Path projectDir = Paths.get(projectPath);
        if (!Files.exists(projectDir)) {
            logger.error("Project directory does not exist: {}", projectPath);
            valid = false;
        } else if (!Files.isDirectory(projectDir)) {
            logger.error("Project path is not a directory: {}", projectPath);
            valid = false;
        }

        // Validate variables file if provided
        if (variablesFile != null && !variablesFile.isEmpty()) {
            Path varsFile = Paths.get(variablesFile);
            if (!Files.exists(varsFile)) {
                logger.error("Variables file does not exist: {}", variablesFile);
                valid = false;
            } else if (!Files.isRegularFile(varsFile)) {
                logger.error("Variables path is not a file: {}", variablesFile);
                valid = false;
            }
        }

        // Validate mutually exclusive options
        int executionModes = 0;
        if (taskId != null)
            executionModes++;
        if (phaseId != null)
            executionModes++;
        if (resume)
            executionModes++;

        if (executionModes > 1) {
            logger.error(
                    "Cannot specify multiple execution modes: --task, --phase, and --resume are mutually exclusive");
            valid = false;
        }

        return valid;
    }

    // ========================================================================
    // Migration Plan Loading
    // ========================================================================

    private MigrationPlan loadMigrationPlan() throws Exception {
        logger.info("Loading migration plan from: {}", planPath);

        try {
            // Initialize H2 database and repository for GRAPH_QUERY blocks
            H2GraphStorageRepository repository = initializeGraphRepository();

            // Load and convert plan
            MigrationPlanConverter converter = new MigrationPlanConverter(repository);
            YamlMigrationPlanLoader loader = new YamlMigrationPlanLoader(converter);

            // Try to load from file system first (supports includes)
            File planFile = new File(planPath);

            if (planFile.exists() && planFile.isFile()) {
                logger.debug("Loading plan from file system: {}", planFile.getAbsolutePath());
                MigrationPlan plan = loader.loadFromFile(planFile);
                logger.info("Successfully loaded migration plan: {} (version: {})",
                        plan.getName(), plan.getVersion());
                return plan;
            }

            // Fall back to classpath loading
            logger.debug("File not found on file system, trying classpath: {}", planPath);
            InputStream planStream = getClass().getClassLoader().getResourceAsStream(planPath);

            if (planStream == null) {
                logger.error("Migration plan not found: {} (searched file system and classpath)", planPath);
                return null;
            }

            // Use try-with-resources to ensure stream is closed
            try (InputStream stream = planStream) {
                // For classpath resources, try to determine a base path for includes
                // by looking for the plan in known locations
                Path basePath = detectClasspathBasePath(planPath);

                MigrationPlan plan = loader.loadFromInputStream(stream, basePath);

                logger.info("Successfully loaded migration plan: {} (version: {})",
                        plan.getName(), plan.getVersion());

                return plan;
            }

        } catch (IOException e) {
            logger.error("Failed to load migration plan: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Attempts to detect a base path for resolving includes in classpath resources.
     * This enables include resolution for plans loaded from the classpath.
     *
     * @param resourcePath the classpath resource path
     * @return detected base path, or null if detection fails
     */
    private Path detectClasspathBasePath(String resourcePath) {
        try {
            // Try to find the resource on the file system via the classpath
            java.net.URL resourceUrl = getClass().getClassLoader().getResource(resourcePath);

            if (resourceUrl != null && "file".equals(resourceUrl.getProtocol())) {
                Path resourceFilePath = Path.of(resourceUrl.toURI());
                logger.debug("Detected file system path for classpath resource: {}", resourceFilePath);
                return resourceFilePath;
            }

            // If resource is inside a JAR or not accessible, return null
            logger.debug("Could not detect file system path for classpath resource: {}", resourcePath);
            return null;

        } catch (Exception e) {
            logger.warn("Failed to detect base path for resource: {}", resourcePath, e);
            return null;
        }
    }

    // ========================================================================
    // Graph Repository Initialization
    // ========================================================================

    /**
     * Initializes the H2 graph database and repository for GRAPH_QUERY blocks.
     * Creates an empty database if one doesn't exist at the project location.
     *
     * @return Initialized H2GraphStorageRepository instance
     * @throws RuntimeException if initialization fails
     */
    private H2GraphStorageRepository initializeGraphRepository() {
        try {
            Path projectDir = Paths.get(projectPath).toAbsolutePath();

            // Create LoadOptions with project root (automatically sets database path)
            LoadOptions options = LoadOptions.builder()
                    .withProjectRoot(projectDir)
                    .loadAllNodes()
                    .loadAllEdges()
                    .build();

            // Create JSON serialization service
            JsonSerializationService jsonSerializer = new JsonSerializationService();

            // Create and initialize H2 database
            H2GraphDatabase database = new H2GraphDatabase(options, jsonSerializer);
            database.load(); // Initializes/creates database if needed

            logger.info("Graph database initialized at: {}", options.getDatabasePath());

            return database.getRepository();

        } catch (Exception e) {
            logger.error("Failed to initialize graph repository: {}", e.getMessage(), e);
            throw new RuntimeException("Graph repository initialization failed", e);
        }
    }

    // ========================================================================
    // Variable Resolution
    // ========================================================================

    private Map<String, String> buildVariableMap(MigrationPlan plan) throws Exception {
        Map<String, String> variables = new LinkedHashMap<>();

        // Priority 4: Load plan variables from YAML (lowest priority)
        // Note: Variables are stored in the DTO during loading, not in the plan object
        // We need to reload to get the variables
        Map<String, String> planVariables = loadPlanVariables();
        if (planVariables != null && !planVariables.isEmpty()) {
            variables.putAll(planVariables);
        }

        // Priority 3: Properties file
        if (variablesFile != null && !variablesFile.isEmpty()) {
            Map<String, String> fileVars = loadPropertiesFile(variablesFile);
            variables.putAll(fileVars);
        }

        // Priority 2: CLI --variable flags
        if (cliVariables != null && !cliVariables.isEmpty()) {
            variables.putAll(cliVariables);
        }

        // Priority 1: CLI -D flags (highest priority)
        if (systemProperties != null && !systemProperties.isEmpty()) {
            variables.putAll(systemProperties);
        }

        // Add auto-derived variables (always set, can't be overridden)
        addAutoDerivedVariables(variables, plan);

        // Resolve environment variables
        variables = resolveEnvironmentVariables(variables);

        return variables;
    }

    /**
     * Load variables from the migration plan using proper include processing.
     * This method loads the plan with all includes resolved, then extracts
     * variables.
     */
    private Map<String, String> loadPlanVariables() throws Exception {
        try {
            // Initialize H2 database and repository for GRAPH_QUERY blocks
            H2GraphStorageRepository repository = initializeGraphRepository();

            // Load and convert plan using proper loader with include processing
            MigrationPlanConverter converter = new MigrationPlanConverter(repository);
            YamlMigrationPlanLoader loader = new YamlMigrationPlanLoader(converter);

            // Try to load from file system first (supports includes)
            File planFile = new File(planPath);

            if (planFile.exists() && planFile.isFile()) {
                logger.debug("Loading plan variables from file system: {}", planFile.getAbsolutePath());
                // Load the DTO with includes processed to get merged variables
                return loadVariablesFromFile(planFile);
            }

            // Fall back to classpath loading
            logger.debug("File not found on file system, trying classpath: {}", planPath);
            InputStream planStream = getClass().getClassLoader().getResourceAsStream(planPath);

            if (planStream == null) {
                logger.warn("Migration plan not found: {} (searched file system and classpath)", planPath);
                return new HashMap<>();
            }

            // Use try-with-resources to ensure stream is closed
            try (InputStream stream = planStream) {
                // For classpath resources, try to determine a base path for includes
                Path basePath = detectClasspathBasePath(planPath);
                return loadVariablesFromStream(stream, basePath);
            }

        } catch (Exception e) {
            logger.warn("Failed to load plan variables: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Load variables from a file using proper include processing.
     */
    private Map<String, String> loadVariablesFromFile(File planFile) throws Exception {
        // Use YamlMigrationPlanLoader's DTO loading logic with include processing
        com.fasterxml.jackson.databind.ObjectMapper yamlMapper = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());

        MigrationPlanDTO dto = yamlMapper.readValue(planFile, MigrationPlanDTO.class);

        // Process includes if present - this is the key fix!
        if (dto.getPlanRoot().getIncludes() != null && !dto.getPlanRoot().getIncludes().isEmpty()) {
            IncludeResolver resolver = new IncludeResolver(planFile.toPath());
            dto = resolver.mergeIncludes(dto);
        }

        return dto.getPlanRoot().getVariables();
    }

    /**
     * Load variables from a stream using proper include processing.
     */
    private Map<String, String> loadVariablesFromStream(InputStream stream, Path basePath) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper yamlMapper = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());

        MigrationPlanDTO dto = yamlMapper.readValue(stream, MigrationPlanDTO.class);

        // Process includes if present and basePath is available
        if (basePath != null && dto.getPlanRoot().getIncludes() != null && !dto.getPlanRoot().getIncludes().isEmpty()) {
            IncludeResolver resolver = new IncludeResolver(basePath);
            dto = resolver.mergeIncludes(dto);
        } else if (dto.getPlanRoot().getIncludes() != null && !dto.getPlanRoot().getIncludes().isEmpty()) {
            logger.warn(
                    "Migration plan contains {} include(s) but no base path available - includes will not be resolved",
                    dto.getPlanRoot().getIncludes().size());
        }

        return dto.getPlanRoot().getVariables();
    }

    private void addAutoDerivedVariables(Map<String, String> variables, MigrationPlan plan) {
        Path projectDir = Paths.get(projectPath).toAbsolutePath();

        // Auto-derived variables (these override any user-provided values)
        variables.put("project_root", projectDir.toString());
        variables.put("project_name", projectDir.getFileName().toString());
        variables.put("plan_name", plan.getName());
        variables.put("current_date", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        variables.put("current_datetime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        variables.put("user_name", System.getProperty("user.name"));
        variables.put("user_home", System.getProperty("user.home"));

        if (verbose) {
            logger.debug("Auto-derived variables:");
            variables.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("project.") || e.getKey().startsWith("plan.")
                            || e.getKey().startsWith("current_") || e.getKey().startsWith("user."))
                    .forEach(e -> logger.debug("  {} = {}", e.getKey(), e.getValue()));
        }
    }

    private Map<String, String> resolveEnvironmentVariables(Map<String, String> variables) {
        Map<String, String> resolved = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue();
            if (value != null) {
                value = substituteEnvironmentVariables(value);
            }
            resolved.put(entry.getKey(), value);
        }

        return resolved;
    }

    private String substituteEnvironmentVariables(String value) {
        if (value == null || !value.contains("${env.")) {
            return value;
        }

        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String envVarName = matcher.group(1);
            String defaultValue = matcher.group(2);

            String envValue = System.getenv(envVarName);

            if (envValue != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(envValue));
            } else if (defaultValue != null) {
                // Remove leading ":-" from default value
                String actualDefault = defaultValue.substring(2);
                matcher.appendReplacement(result, Matcher.quoteReplacement(actualDefault));
            } else {
                // Leave unresolved if no default provided
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private Map<String, String> loadPropertiesFile(String filePath) throws IOException {
        Map<String, String> props = new LinkedHashMap<>();

        try (FileInputStream fis = new FileInputStream(filePath)) {
            Properties properties = new Properties();
            properties.load(fis);

            for (String key : properties.stringPropertyNames()) {
                props.put(key, properties.getProperty(key));
            }

            logger.info("Loaded {} variables from properties file: {}", props.size(), filePath);
        }

        return props;
    }

    // ========================================================================
    // Variable Validation
    // ========================================================================

    private boolean validateVariables(Map<String, String> variables, MigrationPlan plan) {
        boolean valid = true;
        List<String> unresolvedVars = new ArrayList<>();

        // Check for unresolved placeholders in variable values
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue();
            if (value != null && containsUnresolvedPlaceholder(value)) {
                unresolvedVars.add(entry.getKey() + " = " + value);
                valid = false;
            }
        }

        if (!unresolvedVars.isEmpty()) {
            logger.error("Found {} unresolved variable placeholders:", unresolvedVars.size());
            for (String var : unresolvedVars) {
                logger.error("  {}", var);
            }
            logger.error("");
            logger.error("Solutions:");
            logger.error("  1. Set environment variables: export VAR_NAME=value");
            logger.error("  2. Use command line: -DVAR_NAME=value");
            logger.error("  3. Add to properties file: --variables /path/to/vars.properties");
            logger.error("  4. Use defaults in YAML plan variables section");
        }

        return valid;
    }

    private boolean containsUnresolvedPlaceholder(String value) {
        // Check for ${...} patterns that weren't resolved
        return value.contains("${") && !value.contains("${project.") && !value.contains("${plan.");
    }

    // ========================================================================
    // Execution Configuration Display
    // ========================================================================

    private void displayExecutionConfiguration(MigrationPlan plan, Map<String, String> variables) {
        System.out.println("\n=== Migration Execution Configuration ===");
        System.out.println("Migration Plan:    " + plan.getName());
        System.out.println("Version:           " + plan.getVersion());
        System.out.println("Project Path:      " + projectPath);
        System.out.println("Database Path:     " + (databasePath != null ? databasePath : "default"));
        System.out.println("Interactive Mode:  " + (interactive ? "enabled" : "disabled"));
        System.out.println("Step-by-Step Mode: " + (stepByStep ? "enabled" : "disabled"));

        if (taskId != null) {
            System.out.println("Execution Mode:    Single Task (" + taskId + ")");
        } else if (phaseId != null) {
            System.out.println("Execution Mode:    Single Phase (" + phaseId + ")");
        } else if (resume) {
            System.out.println("Execution Mode:    Resume from checkpoint");
        } else {
            System.out.println("Execution Mode:    Full plan");
        }

        if (verbose) {
            System.out.println("\nVariables (" + variables.size() + "):");
            List<String> sortedKeys = new ArrayList<>(variables.keySet());
            Collections.sort(sortedKeys);
            for (String key : sortedKeys) {
                System.out.printf("  %-30s = %s%n", key, variables.get(key));
            }
        } else {
            System.out.println("\nVariables:         " + variables.size() + " defined (use --verbose to see all)");
        }

        System.out.println();
    }

    // ========================================================================
    // Migration Execution
    // ========================================================================

    private Integer executeMigration(MigrationPlan plan, Map<String, String> variables) throws Exception {
        if (dryRun) {
            logger.info("DRY-RUN MODE: Simulating migration plan execution: {}", plan.getName());
            System.out.println("\n=== DRY-RUN MODE ===");
            System.out.println("Simulating execution without making actual changes");
            System.out.println();
        } else {
            logger.info("Executing migration plan: {}", plan.getName());
        }

        // Create migration context with all variables
        Path projectDir = Paths.get(projectPath).toAbsolutePath();
        MigrationContext context = new MigrationContext(projectDir, dryRun);

        // Set execution modes
        context.setStepByStepMode(stepByStep);

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }

        // Determine database path for progress tracking
        Path dbPath = determineDatabasePath(projectDir);
        logger.info("Progress tracking database: {}", dbPath);

        // Create migration engine
        MigrationEngine engine = new MigrationEngine(plan.getName());

        // Add console progress listener
        ConsoleProgressListener progressListener = new ConsoleProgressListener();
        engine.addListener(progressListener);

        // Add state file listener for persistent tracking
        String planKey = Paths.get(planPath).getFileName().toString();
        StateFileListener stateListener = new StateFileListener(projectDir, planKey, verbose);
        engine.addListener(stateListener);
        logger.info("State tracking enabled at: {}", stateListener.getStateManager().getStateFilePath());

        // Execute based on mode
        ExecutionResult result;

        if (taskId != null) {
            logger.info("Executing single task: {}", taskId);
            result = engine.executeTaskById(plan, taskId, context);
        } else if (phaseId != null) {
            logger.info("Executing single phase: {}", phaseId);
            result = engine.executePhaseById(plan, phaseId, context);
        } else if (resume) {
            logger.info("Resuming from checkpoint");
            result = engine.resumeFromCheckpoint(plan, context);
        } else {
            logger.info("Executing full migration plan");
            result = engine.executePlan(plan, context);
        }

        // Display results
        displayExecutionResults(result);

        if (dryRun) {
            System.out.println("\n=== DRY-RUN COMPLETED ===");
            System.out.println("No actual changes were made to the system");
            System.out.println("Remove --dry-run flag to execute for real");
            System.out.println();
        }

        return result.isSuccess() ? 0 : 1;
    }

    private Path determineDatabasePath(Path projectDir) {
        if (databasePath != null && !databasePath.isEmpty()) {
            return Paths.get(databasePath);
        } else {
            // Default: project/.analysis/migration.db
            return projectDir.resolve(".analysis").resolve("migration.db");
        }
    }

    private void displayExecutionResults(ExecutionResult result) {
        System.out.println("\n=== Migration Execution Results ===");
        System.out.println("Status:            " + (result.isSuccess() ? "SUCCESS" : "FAILED"));
        System.out.println("Total Duration:    " + result.getDuration().toMillis() + "ms");

        if (!result.isSuccess() && result.getFailureReason() != null) {
            System.out.println("Error:             " + result.getFailureReason());
            if (result.getFailurePhase() != null) {
                System.out.println("Failed at Phase:   " + result.getFailurePhase());
            }
        }

        System.out.println("\nPhase Results:");
        result.getPhaseResults().forEach(phase -> {
            System.out.println("  " + phase.getPhaseName() + ": " +
                    (phase.isSuccess() ? "SUCCESS" : "FAILED") +
                    " (" + phase.getDuration().toMillis() + "ms)");
        });

        System.out.println();
    }

    // ========================================================================
    // Getters for Testing
    // ========================================================================

    public String getProjectPath() {
        return projectPath;
    }

    public String getPlanPath() {
        return planPath;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    public Map<String, String> getCliVariables() {
        return cliVariables;
    }

    public String getVariablesFile() {
        return variablesFile;
    }

    public boolean isListVariables() {
        return listVariables;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getPhaseId() {
        return phaseId;
    }

    public boolean isResume() {
        return resume;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isInteractive() {
        return interactive;
    }

    public boolean isStatusOnly() {
        return statusOnly;
    }

    public String getDatabasePath() {
        return databasePath;
    }

    public boolean isVerbose() {
        return verbose;
    }
}
