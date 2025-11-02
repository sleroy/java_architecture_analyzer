package com.analyzer.migration.state;

import com.analyzer.migration.state.model.MigrationExecutionState;
import com.analyzer.migration.state.model.MigrationState;
import com.analyzer.migration.state.model.PhaseExecutionRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe manager for migration state file operations.
 * Handles reading, writing, and updating the migration state file with atomic
 * operations.
 */
public class MigrationStateManager {
    private static final Logger logger = LoggerFactory.getLogger(MigrationStateManager.class);
    private static final String STATE_DIRECTORY = ".analysis";
    private static final String STATE_FILENAME = "migration-state.json";
    private static final String BACKUP_SUFFIX = ".backup";
    private static final String CURRENT_SCHEMA_VERSION = "1.0";

    private final Path stateFilePath;
    private final Path backupFilePath;
    private final ObjectMapper objectMapper;
    private final ReadWriteLock lock;

    /**
     * Create a state manager for the given project root.
     *
     * @param projectRoot Root directory of the project
     */
    public MigrationStateManager(Path projectRoot) {
        this.stateFilePath = projectRoot.resolve(STATE_DIRECTORY).resolve(STATE_FILENAME);
        this.backupFilePath = projectRoot.resolve(STATE_DIRECTORY).resolve(STATE_FILENAME + BACKUP_SUFFIX);
        this.objectMapper = createObjectMapper();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Create configured ObjectMapper for JSON serialization.
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    /**
     * Initialize state file if it doesn't exist.
     *
     * @param projectRoot Project root path
     * @throws IOException If file creation fails
     */
    public void initializeStateFile(Path projectRoot) throws IOException {
        lock.writeLock().lock();
        try {
            if (!Files.exists(stateFilePath)) {
                // Create .analysis directory if needed
                Files.createDirectories(stateFilePath.getParent());

                // Create new state
                MigrationState state = new MigrationState(projectRoot.toAbsolutePath().toString());

                // Write to file
                objectMapper.writeValue(stateFilePath.toFile(), state);
                logger.info("Initialized migration state file: {}", stateFilePath);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Load state from file. Returns empty state if file doesn't exist.
     *
     * @return Migration state
     * @throws IOException If reading fails
     */
    public MigrationState loadState() throws IOException {
        lock.readLock().lock();
        try {
            if (!Files.exists(stateFilePath)) {
                logger.debug("State file does not exist: {}", stateFilePath);
                return new MigrationState();
            }

            MigrationState state = objectMapper.readValue(stateFilePath.toFile(), MigrationState.class);
            validateSchemaVersion(state);
            logger.debug("Loaded migration state from: {}", stateFilePath);
            return state;
        } catch (IOException e) {
            logger.error("Failed to load state file: {}", stateFilePath, e);
            // Try to recover from backup
            if (Files.exists(backupFilePath)) {
                logger.info("Attempting to recover from backup: {}", backupFilePath);
                return recoverFromBackup();
            }
            throw e;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Save complete state to file with atomic write and backup.
     *
     * @param state Migration state to save
     * @throws IOException If writing fails
     */
    public void saveState(MigrationState state) throws IOException {
        lock.writeLock().lock();
        try {
            // Create backup if state file exists
            if (Files.exists(stateFilePath)) {
                backupStateFile();
            } else {
                // Ensure directory exists
                Files.createDirectories(stateFilePath.getParent());
            }

            // Update timestamp
            state.touch();

            // Write to temporary file first (atomic write)
            Path tempFile = stateFilePath.getParent().resolve(STATE_FILENAME + ".tmp");
            objectMapper.writeValue(tempFile.toFile(), state);

            // Move to final location atomically
            Files.move(tempFile, stateFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            logger.debug("Saved migration state to: {}", stateFilePath);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update state for a specific migration.
     *
     * @param planKey        Unique key for the migration plan
     * @param migrationState Migration execution state
     * @throws IOException If update fails
     */
    public void updateMigrationState(String planKey, MigrationExecutionState migrationState) throws IOException {
        lock.writeLock().lock();
        try {
            MigrationState state = loadStateWithoutLock();
            state.putMigration(planKey, migrationState);
            saveStateWithoutLock(state);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Add execution history record for a migration.
     *
     * @param planKey Unique key for the migration plan
     * @param record  Phase execution record
     * @throws IOException If update fails
     */
    public void addExecutionHistory(String planKey, PhaseExecutionRecord record) throws IOException {
        lock.writeLock().lock();
        try {
            MigrationState state = loadStateWithoutLock();
            MigrationExecutionState migrationState = state.getMigration(planKey);
            if (migrationState != null) {
                migrationState.addExecutionRecord(record);
                state.putMigration(planKey, migrationState);
                saveStateWithoutLock(state);
            } else {
                logger.warn("Migration not found in state: {}", planKey);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get migration state for a specific plan.
     *
     * @param planKey Unique key for the migration plan
     * @return Migration execution state or null if not found
     * @throws IOException If reading fails
     */
    public MigrationExecutionState getMigrationState(String planKey) throws IOException {
        MigrationState state = loadState();
        return state.getMigration(planKey);
    }

    /**
     * Check if state file exists.
     *
     * @return true if state file exists
     */
    public boolean exists() {
        return Files.exists(stateFilePath);
    }

    /**
     * Get the path to the state file.
     *
     * @return State file path
     */
    public Path getStateFilePath() {
        return stateFilePath;
    }

    /**
     * Create backup of current state file.
     */
    private void backupStateFile() throws IOException {
        if (Files.exists(stateFilePath)) {
            Files.copy(stateFilePath, backupFilePath, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Created backup: {}", backupFilePath);
        }
    }

    /**
     * Recover state from backup file.
     */
    private MigrationState recoverFromBackup() throws IOException {
        MigrationState state = objectMapper.readValue(backupFilePath.toFile(), MigrationState.class);
        logger.info("Recovered state from backup");
        // Restore the backup to main file
        Files.copy(backupFilePath, stateFilePath, StandardCopyOption.REPLACE_EXISTING);
        return state;
    }

    /**
     * Load state without acquiring lock (for internal use when lock already held).
     */
    private MigrationState loadStateWithoutLock() throws IOException {
        if (!Files.exists(stateFilePath)) {
            return new MigrationState();
        }
        MigrationState state = objectMapper.readValue(stateFilePath.toFile(), MigrationState.class);
        validateSchemaVersion(state);
        return state;
    }

    /**
     * Validate the schema version of the loaded state.
     * Logs a warning for unknown versions but doesn't fail.
     * This allows for forward compatibility while alerting about potential issues.
     *
     * @param state The migration state to validate
     */
    private void validateSchemaVersion(MigrationState state) {
        String schemaVersion = state.getSchemaVersion();

        if (schemaVersion == null) {
            logger.warn("State file has no schema version. This may indicate an old or corrupted file.");
            // Set to current version for backward compatibility
            state.setSchemaVersion(CURRENT_SCHEMA_VERSION);
            return;
        }

        if (!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
            logger.warn("State file schema version '{}' differs from current version '{}'. " +
                    "The application may not fully support this version.",
                    schemaVersion, CURRENT_SCHEMA_VERSION);

            // Parse versions to check compatibility
            if (isCompatibleVersion(schemaVersion)) {
                logger.info("Schema version '{}' is compatible with current version '{}'",
                        schemaVersion, CURRENT_SCHEMA_VERSION);
            } else {
                logger.error("Schema version '{}' is not compatible with current version '{}'. " +
                        "Please update the application or migrate the state file.",
                        schemaVersion, CURRENT_SCHEMA_VERSION);
            }
        }
    }

    /**
     * Check if a schema version is compatible with the current version.
     * Currently supports only version 1.x (minor version differences are
     * compatible).
     *
     * @param version Schema version to check
     * @return true if compatible, false otherwise
     */
    private boolean isCompatibleVersion(String version) {
        if (version == null) {
            return false;
        }

        try {
            String[] parts = version.split("\\.");
            String[] currentParts = CURRENT_SCHEMA_VERSION.split("\\.");

            if (parts.length < 1 || currentParts.length < 1) {
                return false;
            }

            // Major version must match
            int majorVersion = Integer.parseInt(parts[0]);
            int currentMajor = Integer.parseInt(currentParts[0]);

            return majorVersion == currentMajor;
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse schema version: {}", version, e);
            return false;
        }
    }

    /**
     * Save state without acquiring lock (for internal use when lock already held).
     */
    private void saveStateWithoutLock(MigrationState state) throws IOException {
        if (Files.exists(stateFilePath)) {
            backupStateFile();
        } else {
            Files.createDirectories(stateFilePath.getParent());
        }

        state.touch();
        Path tempFile = stateFilePath.getParent().resolve(STATE_FILENAME + ".tmp");
        objectMapper.writeValue(tempFile.toFile(), state);
        Files.move(tempFile, stateFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
