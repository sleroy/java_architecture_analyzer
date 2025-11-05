package com.analyzer.cli;

import com.analyzer.api.graph.GraphRepository;
import com.analyzer.core.db.H2GraphDatabase;
import com.analyzer.core.db.loader.LoadOptions;
import com.analyzer.core.serialization.JsonSerializationService;
import com.analyzer.migration.loader.MigrationPlanConverter;
import com.analyzer.migration.loader.YamlMigrationPlanLoader;
import com.analyzer.migration.plan.MigrationPlan;
import com.analyzer.migration.plan.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * CLI command to list all phases in a migration plan.
 * Provides an overview of the plan structure and available phases for
 * execution.
 */
@CommandLine.Command(name = "list-phases", description = "List all phases in a migration plan")
public class ListPhasesCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(ListPhasesCommand.class);

    @CommandLine.Option(names = "--plan", description = "Path to migration plan YAML file", required = true)
    private String planPath;

    @CommandLine.Option(names = {"--verbose", "-v"}, description = "Show detailed phase information including tasks")
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        logger.info("Listing phases for migration plan: {}", planPath);

        try {
            // Load migration plan
            final MigrationPlan plan = loadMigrationPlan();
            if (null == plan) {
                return 1;
            }

            displayPhases(plan);
            return 0;

        } catch (final Exception e) {
            logger.error("Failed to list phases: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Load the migration plan from the specified file.
     */
    private MigrationPlan loadMigrationPlan() throws Exception {
        // Initialize H2 database for GRAPH_QUERY blocks
        final Path projectDir = Paths.get(System.getProperty("user.dir"));
        final LoadOptions options = LoadOptions.builder()
                                               .withProjectRoot(projectDir)
                                               .loadAllNodes()
                                               .loadAllEdges()
                                               .build();

        final JsonSerializationService jsonSerializer = new JsonSerializationService();
        final H2GraphDatabase database = new H2GraphDatabase(options, jsonSerializer);
        database.load();

        final GraphRepository repository = database.snapshot();

        // Load plan
        final MigrationPlanConverter converter = new MigrationPlanConverter(repository);
        final YamlMigrationPlanLoader loader = new YamlMigrationPlanLoader(converter);

        final File planFile = new File(planPath);
        if (!planFile.exists()) {
            logger.error("Plan file not found: {}", planPath);
            logger.error("Error: Plan file not found: {}", planPath);
            return null;
        }

        return loader.loadFromFile(planFile);
    }

    /**
     * Display the phases in a formatted output.
     */
    private void displayPhases(final MigrationPlan plan) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Migration Plan: " + plan.getName());
        System.out.println("Version: " + plan.getVersion());
        if (null != plan.getDescription() && !plan.getDescription().isEmpty()) {
            System.out.println("Description: " + plan.getDescription());
        }
        System.out.println("=".repeat(80));
        System.out.println();

        if (plan.getPhases().isEmpty()) {
            System.out.println("No phases defined in plan.");
            System.out.println();
            return;
        }

        System.out.printf("Total Phases: %d%n%n", plan.getPhases().size());

        for (int i = 0; i < plan.getPhases().size(); i++) {
            final Phase phase = plan.getPhases().get(i);

            System.out.printf("%2d. [%s] %s%n", i + 1, null != phase.getId() ? phase.getId() : "no-id",
                    phase.getName());
            System.out.printf("    Tasks: %d%n", phase.getTasks().size());

            if (null != phase.getDescription() && !phase.getDescription().isEmpty()) {
                System.out.println("    Description: " + phase.getDescription());
            }

            if (verbose && !phase.getTasks().isEmpty()) {
                System.out.println("    Task List:");
                phase.getTasks().forEach(task -> System.out.printf("      - %s (%s)%n", task.getName(), task.getId()));
            }

            System.out.println();
        }

        System.out.println("Usage Examples:");
        System.out.println("-".repeat(80));
        System.out.println("Execute full plan:");
        System.out.println("  analyzer apply --project <path> --plan " + planPath);
        System.out.println();
        System.out.println("Execute specific phase (by ID - easier to type):");
        final Phase firstPhase = plan.getPhases().get(0);
        final String phaseId = null != firstPhase.getId() ? firstPhase.getId() : firstPhase.getName();
        System.out.println("  analyzer apply --project <path> --plan " + planPath + " \\");
        System.out.println("    --phase " + phaseId);
        System.out.println();
    }
}
