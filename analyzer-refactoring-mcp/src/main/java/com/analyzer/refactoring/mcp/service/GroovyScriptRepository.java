package com.analyzer.refactoring.mcp.service;

import com.analyzer.refactoring.mcp.model.OpenRewriteVisitorScript;
import com.analyzer.refactoring.mcp.model.ScriptMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.script.CompiledScript;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Repository for persisting Groovy visitor scripts to disk.
 * Provides storage, retrieval, and lifecycle management of generated scripts.
 */
@Service
public class GroovyScriptRepository {

    private static final Logger logger = LoggerFactory.getLogger(GroovyScriptRepository.class);
    private static final String SCRIPT_EXTENSION = ".groovy";
    private static final String METADATA_EXTENSION = ".json";

    private final boolean enabled;
    private final Path storagePath;
    private final int maxScripts;
    private final boolean cleanupEnabled;
    private final int cleanupUnusedDays;
    private final ObjectMapper objectMapper;
    private final GroovyScriptExecutionService executionService;

    public GroovyScriptRepository(
            @Value("${groovy.script.storage.enabled:true}") final boolean enabled,
            @Value("${groovy.script.storage.path:${user.home}/.java-refactoring-mcp/scripts}") final String storagePath,
            @Value("${groovy.script.storage.max-scripts:500}") final int maxScripts,
            @Value("${groovy.script.storage.cleanup.enabled:true}") final boolean cleanupEnabled,
            @Value("${groovy.script.storage.cleanup.unused-days:90}") final int cleanupUnusedDays,
            final GroovyScriptExecutionService executionService) {

        this.enabled = enabled;
        this.storagePath = Paths.get(storagePath);
        this.maxScripts = maxScripts;
        this.cleanupEnabled = cleanupEnabled;
        this.cleanupUnusedDays = cleanupUnusedDays;
        this.executionService = executionService;
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        logger.info("Groovy script repository initialized: enabled={}, path={}, maxScripts={}",
                enabled, storagePath, maxScripts);
    }

    /**
     * Initialize storage directory on startup.
     */
    @PostConstruct
    public void initialize() {
        if (!enabled) {
            logger.info("Script repository disabled");
            return;
        }

        try {
            Files.createDirectories(storagePath);
            logger.info("Script storage directory ready: {}", storagePath);

            // Perform cleanup on startup if enabled
            if (cleanupEnabled) {
                cleanupOldScripts();
            }
        } catch (final IOException e) {
            logger.error("Failed to create script storage directory: {}", storagePath, e);
        }
    }

    /**
     * Save a script and its metadata to disk.
     *
     * @param scriptHash   the SHA-256 hash identifying the script
     * @param scriptSource the Groovy source code
     * @param metadata     the script metadata
     * @return true if save was successful
     */
    public boolean saveScript(final String scriptHash, final String scriptSource, final ScriptMetadata metadata) {
        if (!enabled) {
            return false;
        }

        try {
            final Path scriptFile = storagePath.resolve(scriptHash + SCRIPT_EXTENSION);
            final Path metadataFile = storagePath.resolve(scriptHash + METADATA_EXTENSION);

            // Write script source
            Files.writeString(scriptFile, scriptSource, StandardCharsets.UTF_8);

            // Write metadata
            objectMapper.writeValue(metadataFile.toFile(), metadata);

            logger.info("Saved script to disk: hash={}, pattern='{}'",
                    scriptHash, metadata.getPatternDescription());
            return true;

        } catch (final IOException e) {
            logger.error("Failed to save script: hash={}", scriptHash, e);
            return false;
        }
    }

    /**
     * Load a script from disk.
     *
     * @param scriptHash the SHA-256 hash identifying the script
     * @return the visitor script wrapper, or empty if not found
     */
    public Optional<OpenRewriteVisitorScript> loadScript(final String scriptHash) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            final Path scriptFile = storagePath.resolve(scriptHash + SCRIPT_EXTENSION);
            final Path metadataFile = storagePath.resolve(scriptHash + METADATA_EXTENSION);

            if (!Files.exists(scriptFile) || !Files.exists(metadataFile)) {
                return Optional.empty();
            }

            // Read script source
            final String scriptSource = Files.readString(scriptFile, StandardCharsets.UTF_8);

            // Read metadata
            final ScriptMetadata metadata = objectMapper.readValue(metadataFile.toFile(), ScriptMetadata.class);

            // Compile script
            final CompiledScript compiled = executionService.compileScript(scriptSource);

            // Create visitor script wrapper using builder
            final OpenRewriteVisitorScript visitorScript = OpenRewriteVisitorScript.builder()
                                                                                   .sourceCode(scriptSource)
                                                                                   .compiledScript(compiled)
                                                                                   .patternDescription(metadata.getPatternDescription())
                                                                                   .nodeType(metadata.getNodeType())
                                                                                   .projectPath(metadata.getProjectPath())
                                                                                   .generationAttempts(metadata.getGenerationAttempts())
                                                                                   .createdAt(metadata.getGeneratedAt())
                                                                                   .build();

            logger.debug("Loaded script from disk: hash={}, pattern='{}'",
                    scriptHash, metadata.getPatternDescription());
            return Optional.of(visitorScript);

        } catch (final Exception e) {
            logger.error("Failed to load script: hash={}", scriptHash, e);
            return Optional.empty();
        }
    }

    /**
     * Load metadata for a script.
     *
     * @param scriptHash the SHA-256 hash identifying the script
     * @return the metadata, or empty if not found
     */
    public Optional<ScriptMetadata> loadMetadata(final String scriptHash) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            final Path metadataFile = storagePath.resolve(scriptHash + METADATA_EXTENSION);

            if (!Files.exists(metadataFile)) {
                return Optional.empty();
            }

            final ScriptMetadata metadata = objectMapper.readValue(metadataFile.toFile(), ScriptMetadata.class);
            return Optional.of(metadata);

        } catch (final IOException e) {
            logger.error("Failed to load metadata: hash={}", scriptHash, e);
            return Optional.empty();
        }
    }

    /**
     * Update metadata for a script.
     *
     * @param scriptHash the SHA-256 hash identifying the script
     * @param metadata   the updated metadata
     * @return true if update was successful
     */
    public boolean updateMetadata(final String scriptHash, final ScriptMetadata metadata) {
        if (!enabled) {
            return false;
        }

        try {
            final Path metadataFile = storagePath.resolve(scriptHash + METADATA_EXTENSION);
            objectMapper.writeValue(metadataFile.toFile(), metadata);
            logger.debug("Updated metadata: hash={}", scriptHash);
            return true;

        } catch (final IOException e) {
            logger.error("Failed to update metadata: hash={}", scriptHash, e);
            return false;
        }
    }

    /**
     * List all script hashes stored on disk.
     *
     * @return list of script hashes
     */
    public List<String> listScriptHashes() {
        if (!enabled) {
            return Collections.emptyList();
        }

        try (final Stream<Path> paths = Files.list(storagePath)) {
            return paths
                    .filter(p -> p.toString().endsWith(SCRIPT_EXTENSION))
                    .map(p -> p.getFileName().toString())
                    .map(name -> name.substring(0, name.length() - SCRIPT_EXTENSION.length()))
                    .collect(Collectors.toList());
        } catch (final IOException e) {
            logger.error("Failed to list scripts", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get count of stored scripts.
     *
     * @return number of scripts on disk
     */
    public long getScriptCount() {
        if (!enabled) {
            return 0;
        }

        try (final Stream<Path> paths = Files.list(storagePath)) {
            return paths.filter(p -> p.toString().endsWith(SCRIPT_EXTENSION)).count();
        } catch (final IOException e) {
            logger.error("Failed to count scripts", e);
            return 0;
        }
    }

    /**
     * Delete a script and its metadata.
     *
     * @param scriptHash the SHA-256 hash identifying the script
     * @return true if deletion was successful
     */
    public boolean deleteScript(final String scriptHash) {
        if (!enabled) {
            return false;
        }

        try {
            final Path scriptFile = storagePath.resolve(scriptHash + SCRIPT_EXTENSION);
            final Path metadataFile = storagePath.resolve(scriptHash + METADATA_EXTENSION);

            final boolean scriptDeleted = Files.deleteIfExists(scriptFile);
            final boolean metadataDeleted = Files.deleteIfExists(metadataFile);

            if (scriptDeleted || metadataDeleted) {
                logger.info("Deleted script: hash={}", scriptHash);
                return true;
            }
            return false;

        } catch (final IOException e) {
            logger.error("Failed to delete script: hash={}", scriptHash, e);
            return false;
        }
    }

    /**
     * Cleanup old and unused scripts.
     * Removes scripts that haven't been used in the specified number of days.
     */
    public void cleanupOldScripts() {
        if (!enabled || !cleanupEnabled) {
            return;
        }

        try {
            final Instant cutoffDate = Instant.now().minus(cleanupUnusedDays, ChronoUnit.DAYS);
            final List<String> scriptsToDelete = new ArrayList<>();

            for (final String scriptHash : listScriptHashes()) {
                final Optional<ScriptMetadata> metadataOpt = loadMetadata(scriptHash);
                if (metadataOpt.isPresent()) {
                    final ScriptMetadata metadata = metadataOpt.get();
                    final Instant lastUsed = metadata.getLastUsed();

                    // Delete if never used or not used since cutoff
                    if (lastUsed == null || lastUsed.isBefore(cutoffDate)) {
                        scriptsToDelete.add(scriptHash);
                    }
                }
            }

            // Delete old scripts
            for (final String scriptHash : scriptsToDelete) {
                deleteScript(scriptHash);
            }

            if (!scriptsToDelete.isEmpty()) {
                logger.info("Cleaned up {} unused scripts (older than {} days)",
                        scriptsToDelete.size(), cleanupUnusedDays);
            }

            // Enforce max scripts limit if exceeded
            enforceMaxScriptsLimit();

        } catch (final Exception e) {
            logger.error("Failed to cleanup old scripts", e);
        }
    }

    /**
     * Enforce maximum scripts limit by removing least recently used scripts.
     */
    private void enforceMaxScriptsLimit() {
        final long currentCount = getScriptCount();
        if (currentCount <= maxScripts) {
            return;
        }

        try {
            // Load all metadata and sort by last used date
            final List<ScriptMetadata> allMetadata = listScriptHashes().stream()
                                                                       .map(this::loadMetadata)
                                                                       .filter(Optional::isPresent)
                                                                       .map(Optional::get)
                                                                       .sorted(Comparator.comparing(
                                                                         m -> m.getLastUsed() != null ? m.getLastUsed() : Instant.MIN))
                                                                       .collect(Collectors.toList());

            // Delete oldest scripts to get under limit
            final int toDelete = (int) (currentCount - maxScripts);
            for (int i = 0; i < toDelete && i < allMetadata.size(); i++) {
                deleteScript(allMetadata.get(i).getScriptHash());
            }

            logger.info("Enforced max scripts limit: deleted {} oldest scripts", toDelete);

        } catch (final Exception e) {
            logger.error("Failed to enforce max scripts limit", e);
        }
    }

    /**
     * Check if repository is enabled.
     *
     * @return true if repository is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
