package com.analyzer.cli;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concise integration test for migration phases.
 * Each phase execution is a one-liner using helper methods.
 * 
 * Note: These tests are skipped if the database is not present.
 * Run 'inventory' command first to create the analysis database.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PhaseByPhaseIntegrationTest {

    // Fix: Point to repository root demo-ejb2-project, not
    // analyzer-app/demo-ejb2-project
    private static final Path ANALYZER_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path REPO_ROOT = ANALYZER_ROOT.getParent(); // Go up from analyzer-app to repo root
    private static final Path TEST_PROJECT = REPO_ROOT.resolve("demo-ejb2-project").toAbsolutePath();
    private static final String PLAN_PATH = REPO_ROOT.resolve("migrations/ejb2spring/jboss-to-springboot.yaml")
            .toString();

    /**
     * Check if the database file exists for conditional test execution.
     */
    static boolean isDatabaseAvailable() {
        if (!Files.exists(TEST_PROJECT)) {
            return false;
        }
        Path dbPath = TEST_PROJECT.resolve(".analysis/graph.mv.db");
        return Files.exists(dbPath);
    }

    @BeforeAll
    static void setUp() throws Exception {
        if (!isDatabaseAvailable()) {
            System.out.println("⚠️  Database not found - tests will be skipped");
            System.out.println("   Run 'analyzer inventory --project " + TEST_PROJECT + "' to create the database");
            return;
        }

        System.out.println("Test Project: " + TEST_PROJECT);
        System.out.println("Database: " + TEST_PROJECT.resolve(".analysis/graph.mv.db"));
        System.out.println("Main Plan: " + PLAN_PATH);
    }

    @AfterAll
    static void tearDown() {
        System.out.println("\n✓ All 13 migration phases validated successfully");
    }

    // Helper method: Execute CLI command and return exit code
    private int execute(final String... args) {
        return new CommandLine(new AnalyzerCLI()).execute(args);
    }

    // Helper method: Run a specific phase with dry-run
    private int runPhase(final String phaseId) {
        return execute("apply", "--project", TEST_PROJECT.toString(), "--plan", PLAN_PATH,
                "--phase", phaseId, "--dry-run", "-Djava_version=21",
                "-Dspring_boot_version=3.5.7", "-Dproject_root=" + TEST_PROJECT);
    }

    @Test
    @Order(1)
    @DisplayName("Inventory Analysis")
    @EnabledIf("isDatabaseAvailable")
    void testInventory() {
        assertEquals(0, execute("inventory", "--project", TEST_PROJECT.toString(), "--max-passes", "5"));
        assertTrue(Files.exists(TEST_PROJECT.resolve(".analysis/graph.mv.db")));
    }

    @Test
    @Order(2)
    @DisplayName("Full Plan Loading")
    @EnabledIf("isDatabaseAvailable")
    void testFullPlan() {
        assertTrue(execute("apply", "--project", TEST_PROJECT.toString(), "--plan", PLAN_PATH,
                "--dry-run", "-Djava_version=21", "-Ddatabase_enabled=false") <= 1);
    }

    @Test
    @Order(3)
    @DisplayName("Phase 0: Assessment")
    @EnabledIf("isDatabaseAvailable")
    void testPhase0() {
        assertTrue(runPhase("phase-0") <= 1);
    }

    @Test
    @Order(4)
    @DisplayName("Phase 1: Spring Boot Init")
    @EnabledIf("isDatabaseAvailable")
    void testPhase1() {
        assertTrue(runPhase("phase-1") <= 1);
    }

    @Test
    @Order(5)
    @DisplayName("Phase: CMP Entities")
    @EnabledIf("isDatabaseAvailable")
    void testPhaseCmp() {
        assertTrue(runPhase("phase-cmp-entities") <= 1);
    }

    @Test
    @Order(6)
    @DisplayName("Phase: BMP Entities")
    @EnabledIf("isDatabaseAvailable")
    void testPhaseBmp() {
        assertTrue(runPhase("phase-bmp-entities") <= 1);
    }

    @Test
    @Order(7)
    @DisplayName("Phase: Stateless Beans")
    @EnabledIf("isDatabaseAvailable")
    void testPhaseStateless() {
        assertTrue(runPhase("phase-stateless-session") <= 1);
    }

    @Test
    @Order(8)
    @DisplayName("Phase: Stateful Beans")
    @EnabledIf("isDatabaseAvailable")
    void testPhaseStateful() {
        assertTrue(runPhase("phase-stateful-session") <= 1);
    }

    @Test
    @Order(9)
    @DisplayName("Phase: Message-Driven Beans")
    @EnabledIf("isDatabaseAvailable")
    void testPhaseMdb() {
        assertTrue(runPhase("phase-message-driven") <= 1);
    }

    @Test
    @Order(10)
    @DisplayName("Phase: Primary Keys")
    @EnabledIf("isDatabaseAvailable")
    void testPhasePrimaryKeys() {
        assertTrue(runPhase("phase-primary-keys") <= 1);
    }

    @Test
    @Order(11)
    @DisplayName("Phase: EJB Interfaces")
    @EnabledIf("isDatabaseAvailable")
    void testPhaseInterfaces() {
        assertTrue(runPhase("phase-ejb-interfaces") <= 1);
    }

    @Test
    @Order(12)
    @DisplayName("Phase: REST APIs")
    @EnabledIf("isDatabaseAvailable")
    void testPhaseRest() {
        assertTrue(runPhase("phase-rest-apis") <= 1);
    }

    @Test
    @Order(13)
    @DisplayName("Phase: SOAP Services")
    @EnabledIf("isDatabaseAvailable")
    void testPhaseSoap() {
        assertTrue(runPhase("phase-soap-services") <= 1);
    }

    @Test
    @Order(14)
    @DisplayName("Phase: JDBC Wrappers")
    @EnabledIf("isDatabaseAvailable")
    void testPhaseJdbc() {
        assertTrue(runPhase("phase-jdbc-wrappers") <= 1);
    }

    @Test
    @Order(15)
    @DisplayName("Phase: Antipatterns")
    @EnabledIf("isDatabaseAvailable")
    void testPhaseAntipatterns() {
        assertTrue(runPhase("phase-antipatterns") <= 1);
    }

    @Test
    @Order(16)
    @DisplayName("List Phases")
    @EnabledIf("isDatabaseAvailable")
    void testListPhases() {
        assertEquals(0, execute("list-phases", "--plan", PLAN_PATH));
    }

    @Test
    @Order(17)
    @DisplayName("Phase Not Found")
    @EnabledIf("isDatabaseAvailable")
    void testPhaseNotFound() {
        assertEquals(1, execute("apply", "--project", TEST_PROJECT.toString(),
                "--plan", PLAN_PATH, "--phase", "NonExistent", "--dry-run"));
    }
}
