package com.analyzer.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;

/**
 * Manages the lifecycle of JAR/WAR/EAR extraction with MD5-based change
 * detection.
 * Coordinates extraction operations, cleanup, and caching to avoid unnecessary
 * work.
 */
public class ExtractionManager {

    private static final Logger logger = LoggerFactory.getLogger(ExtractionManager.class);
    private static final String MD5_CACHE_FILE = ".md5_cache";
    private static final String EXTRACTION_DIR = ".analysis";
    private static final String BINARIES_DIR = "binaries";

    private final JarExtractor jarExtractor;
    private final Map<String, String> md5Cache = new HashMap<>();

    /**
     * Constructor with default JarExtractor
     */
    public ExtractionManager(FileIgnoreFilter fileIgnoreFilter) {
        this(new JarExtractor(fileIgnoreFilter));
    }

    /**
     * Constructor with custom JarExtractor
     * 
     * @param jarExtractor the extractor to use for archive processing
     */
    public ExtractionManager(JarExtractor jarExtractor) {
        this.jarExtractor = jarExtractor;
    }

    /**
     * Main entry point - cleans and extracts all archives that have changed.
     * 
     * @param archives    the list of archive ProjectFiles to process
     * @param projectRoot the root directory of the project
     * @return extraction statistics
     */
    public ExtractionManagerResult cleanAndExtractAll(List<ProjectFile> archives, Path projectRoot) {
        Path extractionRoot = getExtractionRoot(projectRoot);

        logger.info("Starting extraction management for {} archives", archives.size());

        try {
            // Load existing MD5 cache
            loadMD5Cache(extractionRoot);

            // Clean extraction directory (preserve MD5 cache)
            //cleanExtractionDirectory(extractionRoot);

            // Extract archives that need extraction
            return extractChangedArchives(archives, extractionRoot);

        } catch (IOException e) {
            logger.error("Failed to manage extractions: {}", e.getMessage());
            return ExtractionManagerResult.error(e.getMessage());
        }
    }

    /**
     * Extracts only archives that have changed (based on MD5 comparison).
     */
    private ExtractionManagerResult extractChangedArchives(List<ProjectFile> archives, Path extractionRoot)
            throws IOException {

        int totalArchives = archives.size();
        int extractedArchives = 0;
        int skippedArchives = 0;
        int errorArchives = 0;
        Set<String> usedNames = new HashSet<>();
        List<Path> extractedPaths = new ArrayList<>();

        Map<String, String> newMD5Cache = new HashMap<>();

        for (ProjectFile archive : archives) {
            try {
                String archivePath = archive.getFilePath().toString();
                String currentMD5 = calculateMD5(archive.getFilePath());
                String cachedMD5 = md5Cache.get(archivePath);

                if (needsExtraction(currentMD5, cachedMD5)) {
                    logger.info("Extracting changed archive: {}", archive.getRelativePath());

                    JarExtractor.ExtractionResult result = jarExtractor.extractArchive(archive, extractionRoot,
                            usedNames);

                    if (result.isSuccessful()) {
                        extractedArchives++;
                        extractedPaths.add(result.getExtractedPath());
                        newMD5Cache.put(archivePath, currentMD5);

                        logger.debug("Extraction completed: {} -> {}",
                                archive.getRelativePath(), result.getExtractedPath());
                    } else {
                        logger.warn("Extraction failed for {}: {}",
                                archive.getRelativePath(), result.getErrorMessage());
                        errorArchives++;
                        // Keep old MD5 to retry next time
                        if (cachedMD5 != null) {
                            newMD5Cache.put(archivePath, cachedMD5);
                        }
                    }
                } else {
                    logger.debug("Archive unchanged, skipping: {}", archive.getRelativePath());
                    skippedArchives++;
                    newMD5Cache.put(archivePath, currentMD5);
                }

            } catch (Exception e) {
                logger.error("Error processing archive {}: {}", archive.getRelativePath(), e.getMessage());
                errorArchives++;
            }
        }

        // Save updated MD5 cache
        saveMD5Cache(extractionRoot, newMD5Cache);

        logger.info("Extraction management completed: {} total, {} extracted, {} skipped, {} errors",
                totalArchives, extractedArchives, skippedArchives, errorArchives);

        return ExtractionManagerResult.success(totalArchives, extractedArchives, skippedArchives,
                errorArchives, extractedPaths);
    }

    /**
     * Determines if an archive needs extraction based on MD5 comparison.
     */
    private boolean needsExtraction(String currentMD5, String cachedMD5) {
        if (cachedMD5 == null) {
            return true; // Never extracted before
        }

        return !currentMD5.equals(cachedMD5); // Extract if MD5 changed
    }

    /**
     * Calculates MD5 hash of a file.
     */
    private String calculateMD5(Path filePath) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = md5.digest(fileBytes);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 algorithm not available", e);
        }
    }

    /**
     * Gets the extraction root directory (.analysis/binaries/).
     */
    private Path getExtractionRoot(Path projectRoot) {
        return projectRoot.resolve(EXTRACTION_DIR).resolve(BINARIES_DIR);
    }

    /**
     * Gets the MD5 cache file path.
     */
    private Path getMD5CacheFile(Path extractionRoot) {
        return extractionRoot.resolve(MD5_CACHE_FILE);
    }

    /**
     * Loads the MD5 cache from the cache file.
     */
    private void loadMD5Cache(Path extractionRoot) throws IOException {
        Path cacheFile = getMD5CacheFile(extractionRoot);
        md5Cache.clear();

        if (!Files.exists(cacheFile)) {
            logger.debug("No MD5 cache file found, starting fresh");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(cacheFile);
            for (String line : lines) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    md5Cache.put(parts[0], parts[1]);
                }
            }
            logger.debug("Loaded MD5 cache with {} entries", md5Cache.size());

        } catch (IOException e) {
            logger.warn("Failed to load MD5 cache, will start fresh: {}", e.getMessage());
            md5Cache.clear();
        }
    }

    /**
     * Saves the MD5 cache to the cache file.
     */
    private void saveMD5Cache(Path extractionRoot, Map<String, String> newCache) throws IOException {
        Path cacheFile = getMD5CacheFile(extractionRoot);

        // Ensure extraction root exists
        Files.createDirectories(extractionRoot);

        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : newCache.entrySet()) {
            lines.add(entry.getKey() + "=" + entry.getValue());
        }

        Files.write(cacheFile, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        logger.debug("Saved MD5 cache with {} entries", newCache.size());
    }

    /**
     * Cleans the extraction directory while preserving the MD5 cache.
     */
    private void cleanExtractionDirectory(Path extractionRoot) throws IOException {
        if (!Files.exists(extractionRoot)) {
            logger.debug("Extraction directory does not exist, nothing to clean");
            return;
        }

        Path cacheFile = getMD5CacheFile(extractionRoot);
        byte[] cacheBackup = null;

        // Backup MD5 cache if it exists
        if (Files.exists(cacheFile)) {
            cacheBackup = Files.readAllBytes(cacheFile);
            logger.debug("Backed up MD5 cache ({} bytes)", cacheBackup.length);
        }

        // Clean all contents except .md5_cache
        try (Stream<Path> paths = Files.walk(extractionRoot)) {
            List<Path> toDelete = paths
                    .filter(path -> !path.equals(extractionRoot))
                    .filter(path -> !path.equals(cacheFile))
                    .sorted((a, b) -> -a.compareTo(b)) // Delete deeper paths first
                    .toList();

            int deletedCount = 0;
            for (Path path : toDelete) {
                try {
                    Files.delete(path);
                    deletedCount++;
                } catch (IOException e) {
                    logger.warn("Failed to delete {}: {}", path, e.getMessage());
                }
            }

            logger.debug("Cleaned extraction directory: {} items deleted", deletedCount);
        }

        // Restore MD5 cache if we had one
        if (cacheBackup != null) {
            Files.write(cacheFile, cacheBackup, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.debug("Restored MD5 cache");
        }
    }

    /**
     * Result of extraction management operation.
     */
    public static class ExtractionManagerResult {
        private final boolean successful;
        private final String errorMessage;
        private final int totalArchives;
        private final int extractedArchives;
        private final int skippedArchives;
        private final int errorArchives;
        private final List<Path> extractedPaths;

        private ExtractionManagerResult(boolean successful, String errorMessage, int totalArchives,
                int extractedArchives, int skippedArchives, int errorArchives, List<Path> extractedPaths) {
            this.successful = successful;
            this.errorMessage = errorMessage;
            this.totalArchives = totalArchives;
            this.extractedArchives = extractedArchives;
            this.skippedArchives = skippedArchives;
            this.errorArchives = errorArchives;
            this.extractedPaths = extractedPaths != null ? new ArrayList<>(extractedPaths) : new ArrayList<>();
        }

        public static ExtractionManagerResult success(int totalArchives, int extractedArchives,
                int skippedArchives, int errorArchives, List<Path> extractedPaths) {
            return new ExtractionManagerResult(true, null, totalArchives, extractedArchives,
                    skippedArchives, errorArchives, extractedPaths);
        }

        public static ExtractionManagerResult error(String errorMessage) {
            return new ExtractionManagerResult(false, errorMessage, 0, 0, 0, 0, new ArrayList<>());
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getTotalArchives() {
            return totalArchives;
        }

        public int getExtractedArchives() {
            return extractedArchives;
        }

        public int getSkippedArchives() {
            return skippedArchives;
        }

        public int getErrorArchives() {
            return errorArchives;
        }

        public List<Path> getExtractedPaths() {
            return new ArrayList<>(extractedPaths);
        }

        public boolean hasExtractedContent() {
            return !extractedPaths.isEmpty();
        }

        @Override
        public String toString() {
            if (successful) {
                return String.format("ExtractionManagerResult{total=%d, extracted=%d, skipped=%d, errors=%d, paths=%d}",
                        totalArchives, extractedArchives, skippedArchives, errorArchives, extractedPaths.size());
            } else {
                return String.format("ExtractionManagerResult{error='%s'}", errorMessage);
            }
        }
    }
}
