package com.analyzer.cli;

import com.analyzer.api.graph.GraphRepository;
import com.analyzer.core.db.GraphDatabase;
import com.analyzer.core.db.H2GraphDatabase;
import com.analyzer.core.db.loader.LoadOptions;
import com.analyzer.core.serialization.JsonSerializationService;
import com.analyzer.migration.context.MigrationContext;
import com.analyzer.migration.engine.ExecutionResult;
import com.analyzer.migration.engine.MigrationEngine;
import com.analyzer.migration.engine.listeners.ConsoleProgressListener;
import com.analyzer.migration.loader.IncludeResolver;
import com.analyzer.migration.loader.MigrationPlanConverter;
import com.analyzer.migration.loader.YamlMigrationPlanLoader;
import com.analyzer.migration.loader.dto.MigrationPlanDTO;
import com.analyzer.migration.plan.MigrationPlan;
import com.analyzer.migration.plan.Phase;
import com.analyzer.migration.state.MigrationStateManager;
import com.analyzer.migration.state.StateFileListener;
import com.analyzer.migration.state.model.MigrationExecutionState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
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
@CommandLine.Command(name = "apply", description = "Apply migration plan to transform EJB project to Spring Boot")
public class ApplyMigrationCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(ApplyMigrationCommand.class);
    private static final Pattern ENV_VAR_PATTERN = Pattern
            .compile("\\$\\{env\\.([A-Za-z_][A-Za-z0-9_]*)(?::(-[^}]*))?\\}");

    // ========================================================================
    // Required Parameters
    // ========================================================================

    @CommandLine.Option(names = "--project", description = "Path to the EJB project directory to migrate", required = true)
    private String projectPath;

    @CommandLine.Option(names = "--plan", description = "Path to migration plan YAML file or classpath resource (e.g., migration-plans/jboss-to-springboot-phase0-1.yaml)", required = true)
    private String planPath;

    // ========================================================================
    // Variable Parameters
    // ========================================================================

    @CommandLine.Option(names = "-D", description = "Define variables (Maven-style): -Dkey=value. Highest priority.", mapFallbackValue = "")
    private final Map<String, String> systemProperties = new LinkedHashMap<>();

    @CommandLine.Option(names = "--variable", description = "Define variables: --variable key=value", mapFallbackValue = "")
    private final Map<String, String> cliVariables = new LinkedHashMap<>();

    @CommandLine.Option(names = "--variables", description = "Path to properties file containing variables")
    private String variablesFile;

    @CommandLine.Option(names = "--list-variables", description = "List all variables required by the migration plan and exit")
    private boolean listVariables;

    // ========================================================================
    // Execution Control Parameters
    // ========================================================================

    @CommandLine.Option(names = "--task", description = "Execute only specific task by ID (e.g., task-000)")
    private String taskId;

    @CommandLine.Option(names = "--phase", description = "Execute only specific phase by ID (e.g., phase-0)")
    private String phaseId;

    @CommandLine.Option(names = "--resume", description = "Resume execution from last checkpoint")
    private boolean resume;

    @CommandLine.Option(names = "--dry-run", description = "Validate plan and variables without executing")
    private boolean dryRun;

    @CommandLine.Option(names = "--interactive", description = "Enable interactive mode with validation prompts", defaultValue = "false")
    private boolean interactive;

    @CommandLine.Option(names = "--step-by-step", description = "Enable step-by-step mode - press Enter to proceed between each block")
    private boolean stepByStep;

    @CommandLine.Option(names = "--status", description = "Show migration progress status and exit")
    private boolean statusOnly;

    // ========================================================================
    // Optional Parameters
    // ========================================================================

    @CommandLine.Option(names = "--database", description = "Path to H2 database for progress tracking (default: project/.analysis/migration.db)")
    private String databasePath;

    @CommandLine.Option(names = "--verbose", description = "Enable verbose output")
    private boolean verbose;

    @CommandLine.Option(names = "--ai-provider", description = "AI provider to use: amazonq (default) or gemini", defaultValue = "amazonq")
    private String aiProvider;

    // ========================================================================
    // Cached Infrastructure (initialized once, reused)
    // ========================================================================

    private GraphDatabase cachedGraphDatabase;
    private YamlMigrationPlanLoader cachedLoader;

    // ========================================================================
    // AI Backend Support
    // ========================================================================

    /**
     * Initialize and validate the AI backend based on CLI parameter.
     *
     * @return The initialized AI backend
     * @throws IllegalArgumentException if the provider is invalid or unavailable
     */
    private com.analyzer.ai.AiBackend initializeAiBackend() {
        logger.info("Initializing AI backend: {}", aiProvider);

        try {
            com.analyzer.ai.AiBackend backend = com.analyzer.ai.AiBackendFactory.createFromString(aiProvider);

            // Check if the backend is available
            if (!backend.isAvailable()) {
                logger.error("AI provider '{}' is not available on this system", aiProvider);
                logger.error("Please ensure the {} CLI is installed and accessible", backend.getCliCommand());
                throw new IllegalStateException("AI provider not available: " + aiProvider);
            }

            logger.info("AI backend initialized successfully: {} ({})", backend.getType().getName(),
                    backend.getCliCommand());
            return backend;

        } catch (IllegalArgumentException e) {
            logger.error("Invalid AI provider: {}", aiProvider);
            logger.error("Supported providers: amazonq, gemini");
            throw e;
        }
    }

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
            final MigrationPlan plan = loadMigrationPlan();
            if (null == plan) {
                return 1;
            }

            // 5. Build variable map with priority resolution
            final Map<String, String> variables = buildVariableMap(plan);

            // 6. Validate variables (check for unresolved placeholders)
            if (!areVariablesValid(variables)) {
                return 1;
            }

            // 7. Display execution configuration
            displayExecutionConfiguration(plan, variables);

            // 8. Execute migration (including dry-run mode)
            return executeMigration(plan, variables);

        } catch (final Exception e) {
            logger.error("Migration execution failed: {}", e.getMessage(), e);
            return 1;
        }
    }

    // ========================================================================
    // List Variables Mode
    // ========================================================================

    private Integer handleListVariables() throws Exception {
        logger.info("Listing variables required by migration plan: {}", planPath);

        final MigrationPlan plan = loadMigrationPlan();
        if (null == plan) {
            return 1;
        }

        logger.info("\n=== Migration Plan Variables ===");
        logger.info("Plan: {}", plan.getName());
        logger.info("Version: {}", plan.getVersion());
        System.out.println();

        // Get variables from YAML plan
        final Map<String, String> planVariables = loadPlanVariables();

        if (null == planVariables || planVariables.isEmpty()) {
            logger.info("No variables defined in migration plan.");
        } else {
            logger.info("Defined Variables:");
            logger.info("-----------------");

            // Sort variables alphabetically
            final List<String> sortedKeys = new ArrayList<>(planVariables.keySet());
            Collections.sort(sortedKeys);

            for (final String key : sortedKeys) {
                final String value = planVariables.get(key);
                System.out.printf("  %-30s = %s%n", key, value);
            }

            System.out.println();
            logger.info("Auto-Derived Variables:");
            logger.info("----------------------");
            logger.info("  project.root                   = <project path>");
            logger.info("  project.name                   = <project directory name>");
            logger.info("  plan.name                      = <migration plan name>");
            logger.info("  current_date                   = <current date/time>");
            logger.info("  current_datetime               = <current datetime ISO format>");
            logger.info("  user.name                      = <system user name>");
            logger.info("  user.home                      = <user home directory>");

            System.out.println();
            logger.info("Variable Override Options:");
            logger.info("-------------------------");
            logger.info("  1. Command-line -D flags:        -Dkey=value (highest priority)");
            logger.info("  2. Command-line --variable:      --variable key=value");
            logger.info("  3. Properties file:              --variables /path/to/file.properties");
            logger.info("  4. Plan defaults:                (values shown above)");
            logger.info("  5. Environment variables:        ${env.VAR_NAME} or ${env.VAR_NAME:-default}");
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
        final Path projectDir = Paths.get(projectPath);
        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            logger.error("Project directory does not exist or is not a directory: {}", projectPath);
            return 1;
        }

        // Check state file
        final MigrationStateManager stateManager = new MigrationStateManager(projectDir);
        if (!stateManager.exists()) {
            logger.info("\n=== Migration Status ===");
            logger.info("Status: No migration executed yet");
            logger.info("State File: Not found at {}", stateManager.getStateFilePath());
            logger.info("\nUse 'apply --project {} --plan <plan>' to start migration", projectPath);
            System.out.println();
            return 0;
        }

        // Load and display status
        try {
            final com.analyzer.migration.state.model.MigrationState state = stateManager.loadState();

            logger.info("\n=== Migration Status ===");
            logger.info("Project:      {}", state.getProjectRoot());
            logger.info("Last Updated: {}", state.getLastUpdated());
            logger.info("Migrations:   {}", state.getMigrations().size());
            System.out.println();

            if (state.getMigrations().isEmpty()) {
                logger.info("No migrations found in state file.");
            } else {
                logger.info("Active Migrations:");
                logger.info("─────────────────────────────────────────────");
                for (final String planKey : state.getMigrations().keySet()) {
                    final MigrationExecutionState migState = state.getMigration(planKey);
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

        } catch (final Exception e) {
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
        final Path projectDir = Paths.get(projectPath);
        if (!Files.exists(projectDir)) {
            logger.error("Project directory does not exist: {}", projectPath);
            valid = false;
        } else if (!Files.isDirectory(projectDir)) {
            logger.error("Project path is not a directory: {}", projectPath);
            valid = false;
        }

        // Validate variables file if provided
        if (null != variablesFile && !variablesFile.isEmpty()) {
            final Path varsFile = Paths.get(variablesFile);
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
        if (null != taskId)
            executionModes++;
        if (null != phaseId)
            executionModes++;
        if (resume)
            executionModes++;

        if (1 < executionModes) {
            logger.error(
                    "Cannot specify multiple execution modes: --task, --phase, and --resume are mutually exclusive");
            valid = false;
        }

        return valid;
    }

    // ========================================================================
    // Migration Plan Loading
    // ========================================================================

    private @Nullable MigrationPlan loadMigrationPlan() {
        logger.info("Loading migration plan from: {}", planPath);

        try {
            // Get or initialize loader infrastructure (cached to avoid expensive
            // re-initialization)
            final YamlMigrationPlanLoader loader = getOrCreateLoader();

            // Try to load from file system first (supports includes)
            final File planFile = new File(planPath);

            if (planFile.exists() && planFile.isFile()) {
                logger.debug("Loading plan from file system: {}", planFile.getAbsolutePath());
                final MigrationPlan plan = loader.loadFromFile(planFile);
                logger.info("Successfully loaded migration plan: {} (version: {})",
                        plan.getName(), plan.getVersion());
                return plan;
            }

            // Fall back to classpath loading
            logger.debug("File not found on file system, trying classpath: {}", planPath);
            final InputStream planStream = getClass().getClassLoader().getResourceAsStream(planPath);

            if (null == planStream) {
                logger.error("Migration plan not found: {} (searched file system and classpath)", planPath);
                return null;
            }

            // Use try-with-resources to ensure stream is closed
            try (final InputStream stream = planStream) {
                // For classpath resources, try to determine a base path for includes
                // by looking for the plan in known locations
                final Path basePath = detectClasspathBasePath(planPath);

                final MigrationPlan plan = loader.loadFromInputStream(stream, basePath);

                logger.info("Successfully loaded migration plan: {} (version: {})",
                        plan.getName(), plan.getVersion());

                return plan;
            }

        } catch (final IOException e) {
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
    private @Nullable Path detectClasspathBasePath(final String resourcePath) {
        try {
            // Try to find the resource on the file system via the classpath
            final java.net.URL resourceUrl = getClass().getClassLoader().getResource(resourcePath);

            if (null != resourceUrl && "file".equals(resourceUrl.getProtocol())) {
                final Path resourceFilePath = Path.of(resourceUrl.toURI());
                logger.debug("Detected file system path for classpath resource: {}", resourceFilePath);
                return resourceFilePath;
            }

            // If resource is inside a JAR or not accessible, return null
            logger.debug("Could not detect file system path for classpath resource: {}", resourcePath);
            return null;

        } catch (URISyntaxException e) {
            logger.error("Could not detect file system path for classpath resource: {}", resourcePath, e);
            return null;
        } catch (final Exception e) {
            logger.warn("Failed to detect base path for resource: {}", resourcePath, e);
            return null;
        }
    }

    // ========================================================================
    // Graph Repository Initialization
    // ========================================================================

    /**
     * Gets or creates the YamlMigrationPlanLoader, caching it for reuse.
     * This avoids expensive database initialization on every call.
     *
     * @return Cached or newly created YamlMigrationPlanLoader instance
     * @throws RuntimeException if initialization fails
     */
    private YamlMigrationPlanLoader getOrCreateLoader() {
        if (null != cachedLoader) {
            return cachedLoader;
        }

        try {
            // Initialize H2 database and repository for GRAPH_QUERY blocks
            final GraphDatabase graphDB = initializeGraphDatabase();

            // Load and convert plan
            final GraphRepository repository = graphDB.snapshot();
            final MigrationPlanConverter converter = new MigrationPlanConverter(repository);
            cachedLoader = new YamlMigrationPlanLoader(converter);

            return cachedLoader;

        } catch (final Exception e) {
            logger.error("Failed to initialize loader: {}", e.getMessage(), e);
            throw new RuntimeException("Loader initialization failed", e);
        }
    }

    /**
     * Initializes the H2 graph database and repository for GRAPH_QUERY blocks.
     * Creates an empty database if one doesn't exist at the project location.
     * Caches the result to avoid expensive re-initialization.
     *
     * @return Initialized H2GraphDatabase instance
     * @throws RuntimeException if initialization fails
     */
    private GraphDatabase initializeGraphDatabase() {
        if (null != cachedGraphDatabase) {
            return cachedGraphDatabase;
        }

        try {
            final Path projectDir = Paths.get(projectPath).toAbsolutePath();

            // Create LoadOptions with project root (automatically sets database path)
            final LoadOptions options = LoadOptions.builder()
                    .withProjectRoot(projectDir)
                    .loadAllNodes()
                    .loadAllEdges()
                    .build();

            // Create JSON serialization service
            final JsonSerializationService jsonSerializer = new JsonSerializationService();

            // Create and initialize H2 database
            final H2GraphDatabase database = new H2GraphDatabase(options, jsonSerializer);
            database.load(); // Initializes/creates database if needed

            logger.info("Graph database initialized at: {}", options.getDatabasePath());

            cachedGraphDatabase = database;
            return database;

        } catch (final Exception e) {
            logger.error("Failed to initialize graph repository: {}", e.getMessage(), e);
            throw new RuntimeException("Graph repository initialization failed", e);
        }
    }

    // ========================================================================
    // Variable Resolution
    // ========================================================================

    private Map<String, String> buildVariableMap(final MigrationPlan plan) throws Exception {
        Map<String, String> variables = new LinkedHashMap<>();

        // Priority 4: Load plan variables from YAML (lowest priority)
        // Note: Variables are stored in the DTO during loading, not in the plan object
        // We need to reload to get the variables
        final Map<String, String> planVariables = loadPlanVariables();
        if (null != planVariables && !planVariables.isEmpty()) {
            variables.putAll(planVariables);
        }

        // Priority 3: Properties file
        if (null != variablesFile && !variablesFile.isEmpty()) {
            final Map<String, String> fileVars = loadPropertiesFile(variablesFile);
            variables.putAll(fileVars);
        }

        // Priority 2: CLI --variable flags
        if (!cliVariables.isEmpty()) {
            variables.putAll(cliVariables);
        }

        // Priority 1: CLI -D flags (highest priority)
        if (!systemProperties.isEmpty()) {
            variables.putAll(systemProperties);
        }

        // Add auto-derived variables (always set, can't be overridden)
        addAutoDerivedVariables(variables, plan);

        // Resolve environment variables
        variables = resolveEnvironmentVariables(variables);

        return variables;
    }

    /**
     * Load variables from the migration plan using YamlMigrationPlanLoader.
     * This method reuses the cached loader infrastructure from loadMigrationPlan()
     * to avoid expensive database re-initialization.
     */
    private Map<String, String> loadPlanVariables() {
        try {
            // Reuse cached loader infrastructure (same as loadMigrationPlan)
            final YamlMigrationPlanLoader loader = getOrCreateLoader();

            // Try to load from file system first (supports includes)
            final File planFile = new File(planPath);

            if (planFile.exists() && planFile.isFile()) {
                logger.debug("Loading plan variables from file system: {}", planFile.getAbsolutePath());
                // Use the loader's ObjectMapper to parse DTO (same as loader does internally)
                MigrationPlanDTO dto = loader.getYamlMapper().readValue(planFile, MigrationPlanDTO.class);

                // Process includes using IncludeResolver (same as loader does)
                if (null != dto.getPlanRoot().getIncludes() && !dto.getPlanRoot().getIncludes().isEmpty()) {
                    final IncludeResolver resolver = new IncludeResolver(planFile.toPath());
                    dto = resolver.mergeIncludes(dto);
                }

                return dto.getPlanRoot().getVariables();
            }

            // Fall back to classpath loading
            logger.debug("File not found on file system, trying classpath: {}", planPath);
            final InputStream planStream = getClass().getClassLoader().getResourceAsStream(planPath);

            if (null == planStream) {
                logger.warn("Migration plan not found: {} (searched file system and classpath)", planPath);
                return new HashMap<>();
            }

            // Use try-with-resources to ensure stream is closed
            try (final InputStream stream = planStream) {
                // Parse DTO using loader's ObjectMapper
                MigrationPlanDTO dto = loader.getYamlMapper().readValue(stream, MigrationPlanDTO.class);

                // For classpath resources, try to determine a base path for includes
                final Path basePath = detectClasspathBasePath(planPath);

                // Process includes if basePath is available
                if (null != basePath && null != dto.getPlanRoot().getIncludes()
                        && !dto.getPlanRoot().getIncludes().isEmpty()) {
                    final IncludeResolver resolver = new IncludeResolver(basePath);
                    dto = resolver.mergeIncludes(dto);
                } else if (null != dto.getPlanRoot().getIncludes() && !dto.getPlanRoot().getIncludes().isEmpty()) {
                    logger.warn(
                            "Migration plan contains {} include(s) but no base path available - includes will not be resolved",
                            dto.getPlanRoot().getIncludes().size());
                }

                return dto.getPlanRoot().getVariables();
            }

        } catch (final Exception e) {
            logger.warn("Failed to load plan variables: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private void addAutoDerivedVariables(final Map<String, String> variables, final MigrationPlan plan) {
        final Path projectDir = Paths.get(projectPath).toAbsolutePath();

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

    private static Map<String, String> resolveEnvironmentVariables(final Map<String, String> variables) {
        final Map<String, String> resolved = new LinkedHashMap<>();

        for (final Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue();
            if (null != value) {
                value = substituteEnvironmentVariables(value);
            }
            resolved.put(entry.getKey(), value);
        }

        return resolved;
    }

    private static String substituteEnvironmentVariables(final String value) {
        if (null == value || !value.contains("${env.")) {
            return value;
        }

        final Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        final StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            final String envVarName = matcher.group(1);
            final String defaultValue = matcher.group(2);

            final String envValue = System.getenv(envVarName);

            if (null != envValue) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(envValue));
            } else if (null != defaultValue) {
                // Remove leading ":-" from default value
                final String actualDefault = defaultValue.substring(2);
                matcher.appendReplacement(result, Matcher.quoteReplacement(actualDefault));
            } else {
                // Leave unresolved if no default provided
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static Map<String, String> loadPropertiesFile(final String filePath) throws IOException {
        final Map<String, String> props = new LinkedHashMap<>();

        try (final FileInputStream fis = new FileInputStream(filePath)) {
            final Properties properties = new Properties();
            properties.load(fis);

            for (final String key : properties.stringPropertyNames()) {
                props.put(key, properties.getProperty(key));
            }

            logger.info("Loaded {} variables from properties file: {}", props.size(), filePath);
        }

        return props;
    }

    // ========================================================================
    // Variable Validation
    // ========================================================================

    private static boolean areVariablesValid(final Map<String, String> variables) {
        boolean valid = true;
        final List<String> unresolvedVars = new ArrayList<>();

        // Check for unresolved placeholders in variable values
        for (final Map.Entry<String, String> entry : variables.entrySet()) {
            final String value = entry.getValue();
            if (null != value && containsUnresolvedPlaceholder(value)) {
                unresolvedVars.add(entry.getKey() + " = " + value);
                valid = false;
            }
        }

        if (!unresolvedVars.isEmpty()) {
            logger.error("Found {} unresolved variable placeholders:", unresolvedVars.size());
            for (final String variable : unresolvedVars) {
                logger.error("  {}", variable);
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

    private static boolean containsUnresolvedPlaceholder(final String value) {
        // Check for ${...} patterns that weren't resolved
        return value.contains("${") && !value.contains("${project.") && !value.contains("${plan.");
    }

    // ========================================================================
    // Execution Configuration Display
    // ========================================================================

    private void displayExecutionConfiguration(final MigrationPlan plan, final Map<String, String> variables) {
        logger.info("\n=== Migration Execution Configuration ===");
        logger.info("Migration Plan:    {}", plan.getName());
        logger.info("Version:           {}", plan.getVersion());
        logger.info("Project Path:      {}", projectPath);
        logger.info("Database Path:     {}", null != databasePath ? databasePath : "default");
        logger.info("Interactive Mode:  {}", interactive ? "enabled" : "disabled");
        logger.info("Step-by-Step Mode: {}", stepByStep ? "enabled" : "disabled");

        if (null != taskId) {
            logger.info("Execution Mode:    Single Task ({})", taskId);
        } else if (null != phaseId) {
            logger.info("Execution Mode:    Single Phase ({})", phaseId);
        } else if (resume) {
            logger.info("Execution Mode:    Resume from checkpoint");
        } else {
            logger.info("Execution Mode:    Full plan");
        }

        if (verbose) {
            logger.info("\nVariables ({}):", variables.size());
            final List<String> sortedKeys = new ArrayList<>(variables.keySet());
            Collections.sort(sortedKeys);
            for (final String key : sortedKeys) {
                logger.info("  %-30s = %s", key, variables.get(key));
            }
        } else {
            logger.info("\nVariables:         {} defined (use --verbose to see all)", variables.size());
        }

        System.out.println();
    }

    // ========================================================================
    // Migration Execution
    // ========================================================================

    private Integer executeMigration(final MigrationPlan plan, final Map<String, String> variables) throws Exception {
        if (dryRun) {
            logger.info("DRY-RUN MODE: Simulating migration plan execution: {}", plan.getName());
            logger.info("\n=== DRY-RUN MODE ===");
            logger.info("Simulating execution without making actual changes");
            System.out.println();
        } else {
            logger.info("Executing migration plan: {}", plan.getName());
        }

        // Create migration context with all variables
        final Path projectDir = Paths.get(projectPath).toAbsolutePath();
        final MigrationContext context = new MigrationContext(projectDir, dryRun);

        // Initialize and set AI backend
        final com.analyzer.ai.AiBackend aiBackend = initializeAiBackend();
        context.setAiBackend(aiBackend);

        // Set execution modes
        context.setInteractiveMode(interactive);
        context.setStepByStepMode(stepByStep);

        for (final Map.Entry<String, String> entry : variables.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }

        // Determine database path for progress tracking
        final Path dbPath = determineDatabasePath(projectDir);
        logger.info("Progress tracking database: {}", dbPath);

        // Create migration engine
        final MigrationEngine engine = new MigrationEngine(plan.getName());

        // Add console progress listener
        final ConsoleProgressListener progressListener = new ConsoleProgressListener();
        engine.addListener(progressListener);

        // Add state file listener for persistent tracking
        final String planKey = Paths.get(planPath).getFileName().toString();
        final StateFileListener stateListener = new StateFileListener(projectDir, planKey, verbose);
        engine.addListener(stateListener);
        logger.info("State tracking enabled at: {}", stateListener.getStateManager().getStateFilePath());

        // Execute based on mode
        final ExecutionResult result;

        if (null != taskId) {
            logger.info("Executing single task: {}", taskId);
            result = engine.executeTaskById(plan, taskId, context);
        } else if (null != phaseId) {
            logger.info("Executing single phase: {}", phaseId);

            // Validate phase exists (search by ID first, then by name)
            final boolean phaseExists = plan.getPhases().stream()
                    .anyMatch(p -> (null != p.getId() && p.getId().equalsIgnoreCase(phaseId))
                            || p.getName().equalsIgnoreCase(phaseId));

            if (!phaseExists) {
                logger.error("\n❌ Phase not found: {}", phaseId);
                logger.error("\nAvailable phases in plan:");
                for (int i = 0; i < plan.getPhases().size(); i++) {
                    final Phase p = plan.getPhases().get(i);
                    logger.error("  {}. [{}] {}", i + 1, null != p.getId() ? p.getId() : "no-id", p.getName());
                }
                logger.error("\nTip: Use 'analyzer list-phases --plan {}' to see all phases", planPath);
                return 1;
            }

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
            logger.info("\n=== DRY-RUN COMPLETED ===");
            logger.info("No actual changes were made to the system");
            logger.info("Remove --dry-run flag to execute for real");
            System.out.println();
        }

        return result.isSuccess() ? 0 : 1;
    }

    private Path determineDatabasePath(final Path projectDir) {
        if (null != databasePath && !databasePath.isEmpty()) {
            return Paths.get(databasePath);
        } else {
            // Default: project/.analysis/migration.db
            return projectDir.resolve(".analysis").resolve("migration.db");
        }
    }

    private void displayExecutionResults(final ExecutionResult result) {
        logger.info("\n=== Migration Execution Results ===");
        logger.info("Status:            {}", result.isSuccess() ? "SUCCESS" : "FAILED");
        logger.info("Total Duration:    {}ms", result.getDuration().toMillis());

        if (!result.isSuccess() && null != result.getFailureReason()) {
            logger.info("Error:             {}", result.getFailureReason());
            if (null != result.getFailurePhase()) {
                logger.info("Failed at Phase:   {}", result.getFailurePhase());
            }
        }

        logger.info("\nPhase Results:");
        result.getPhaseResults().forEach(phase -> {
            logger.info("  {}: {} ({}ms)", phase.getPhaseName(), phase.isSuccess() ? "SUCCESS" : "FAILED",
                    phase.getDuration().toMillis());
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

    public String getAiProvider() {
        return aiProvider;
    }
}
