package com.analyzer.core.resource;

import com.analyzer.core.filter.FileIgnoreFilter;
import com.analyzer.core.model.ProjectFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Core component for physically extracting JAR/WAR/EAR files to the filesystem.
 * Replaces the virtual file-based JarContentScanner with physical extraction
 * to .analysis/binaries/ directory structure.
 */
public class JarExtractor {

    private static final Logger logger = LoggerFactory.getLogger(JarExtractor.class);

    private final FileIgnoreFilter ignoreFilter;

    /**
     * Constructor with default ignore filter
     */
    public JarExtractor(Collection<String> ignoreFilter) {
        this(new FileIgnoreFilter(ignoreFilter));
    }

    /**
     * Constructor with custom ignore filter
     * 
     * @param ignoreFilter filter to apply to extracted contents
     */
    public JarExtractor(FileIgnoreFilter ignoreFilter) {
        this.ignoreFilter = ignoreFilter;
    }

    /**
     * Extracts a JAR/WAR/EAR archive to the specified extraction root directory.
     * 
     * @param archive        the ProjectFile representing the archive to extract
     * @param extractionRoot the root directory for extraction (.analysis/binaries/)
     * @param usedNames      set of already used directory names for duplicate
     *                       handling
     * @return extraction result with statistics and extracted path
     */
    public ExtractionResult extractArchive(ProjectFile archive, Path extractionRoot, Set<String> usedNames) {
        if (!isArchiveFile(archive)) {
            return ExtractionResult.notArchive();
        }

        Path archivePath = archive.getFilePath();
        String baseName = getArchiveBaseName(archive.getFileName());
        String uniqueName = generateUniqueDirectoryName(baseName, usedNames);
        Path targetDirectory = extractionRoot.resolve(uniqueName);

        logger.debug("Extracting archive {} to {}", archive.getRelativePath(), targetDirectory);

        try {
            // Create target directory
            Files.createDirectories(targetDirectory);

            // Extract archive contents
            ExtractionStats stats = extractArchiveContents(archivePath, targetDirectory, 0);

            // Track used name
            usedNames.add(uniqueName);

            logger.info("Successfully extracted {}: {} files, {} directories, {} nested archives",
                    archive.getRelativePath(), stats.filesExtracted, stats.directoriesCreated, stats.nestedArchives);

            return ExtractionResult.success(targetDirectory, stats);

        } catch (IOException e) {
            logger.error("Failed to extract archive {}: {}", archive.getRelativePath(), e.getMessage());
            return ExtractionResult.error(e.getMessage());
        }
    }

    /**
     * Extracts the contents of an archive recursively.
     */
    private ExtractionStats extractArchiveContents(Path archivePath, Path targetDirectory, int depth)
            throws IOException {
        ExtractionStats stats = new ExtractionStats();
        Set<String> nestedUsedNames = new HashSet<>();

        try (JarFile jarFile = new JarFile(archivePath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // Apply ignore filtering
                Path entryPath = Path.of(entryName);
            /*    if (ignoreFilter.shouldIgnore(entryPath, Path.of(""))) {
                    logger.trace("Filtered out entry: {}", entryName);
                    continue;
                }*/

                Path entryTargetPath = targetDirectory.resolve(entryName);

                if (entry.isDirectory()) {
                    // Create directory
                    Files.createDirectories(entryTargetPath);
                    stats.directoriesCreated++;
                    logger.trace("Created directory: {}", entryTargetPath);
                } else {
                    // Extract file
                    extractFileEntry(jarFile, entry, entryTargetPath);
                    stats.filesExtracted++;
                    logger.trace("Extracted file: {}", entryTargetPath);

                    // If this is a nested archive, extract it recursively (no depth limit)
                    if (isArchiveFileName(entryName)) {
                        logger.debug("Found nested archive at depth {}: {}", depth, entryName);

                        String nestedBaseName = getArchiveBaseName(Path.of(entryName).getFileName().toString());
                        String nestedUniqueName = generateUniqueDirectoryName(nestedBaseName, nestedUsedNames);
                        Path nestedTargetDir = targetDirectory.resolve(nestedUniqueName);

                        try {
                            ExtractionStats nestedStats = extractArchiveContents(entryTargetPath, nestedTargetDir,
                                    depth + 1);
                            stats.merge(nestedStats);
                            stats.nestedArchives++;
                            nestedUsedNames.add(nestedUniqueName);

                            logger.debug("Completed nested extraction: {} -> {}", entryName, nestedTargetDir);
                        } catch (IOException e) {
                            logger.warn("Failed to extract nested archive {}: {}", entryName, e.getMessage());
                        }
                    }
                }
            }
        }

        return stats;
    }

    /**
     * Extracts a single file entry from the JAR.
     */
    private void extractFileEntry(JarFile jarFile, JarEntry entry, Path targetPath) throws IOException {
        // Create parent directories if needed
        Path parentDir = targetPath.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        // Extract file content
        try (InputStream inputStream = jarFile.getInputStream(entry);
                OutputStream outputStream = Files.newOutputStream(targetPath)) {

            inputStream.transferTo(outputStream);
        }

        // Preserve last modified time if available
        if (entry.getLastModifiedTime() != null) {
            Files.setLastModifiedTime(targetPath, entry.getLastModifiedTime());
        }
    }

    /**
     * Generates a unique directory name for extraction, handling duplicates with
     * hash suffixes.
     */
    private String generateUniqueDirectoryName(String baseName, Set<String> usedNames) {
        if (!usedNames.contains(baseName)) {
            return baseName;
        }

        // Generate hash suffix for duplicates
        String hash = Integer.toHexString(baseName.hashCode() + usedNames.size()).substring(0, 6);
        String uniqueName = baseName + "_" + hash;

        // Ensure uniqueness (very unlikely collision, but handle it)
        int counter = 1;
        while (usedNames.contains(uniqueName)) {
            uniqueName = baseName + "_" + hash + "_" + counter;
            counter++;
        }

        return uniqueName;
    }

    /**
     * Gets the base name for an archive (without extension).
     */
    private String getArchiveBaseName(String fileName) {
        if (fileName == null) {
            return "unknown";
        }

        int lastDot = fileName.lastIndexOf('.');
        return lastDot != -1 ? fileName.substring(0, lastDot) : fileName;
    }

    /**
     * Checks if a ProjectFile represents an archive.
     */
    private boolean isArchiveFile(ProjectFile file) {
        if (file == null || file.getFileName() == null) {
            return false;
        }

        return isArchiveFileName(file.getFileName());
    }

    /**
     * Checks if a file name represents an archive.
     */
    private boolean isArchiveFileName(String fileName) {
        if (fileName == null) {
            return false;
        }

        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".jar") ||
                lowerName.endsWith(".war") ||
                lowerName.endsWith(".ear") ||
                lowerName.endsWith(".zip");
    }

    /**
     * Result of an archive extraction operation.
     */
    public static class ExtractionResult {
        private final boolean successful;
        private final String errorMessage;
        private final Path extractedPath;
        private final ExtractionStats stats;

        private ExtractionResult(boolean successful, String errorMessage, Path extractedPath, ExtractionStats stats) {
            this.successful = successful;
            this.errorMessage = errorMessage;
            this.extractedPath = extractedPath;
            this.stats = stats;
        }

        public static ExtractionResult success(Path extractedPath, ExtractionStats stats) {
            return new ExtractionResult(true, null, extractedPath, stats);
        }

        public static ExtractionResult error(String errorMessage) {
            return new ExtractionResult(false, errorMessage, null, new ExtractionStats());
        }

        public static ExtractionResult notArchive() {
            return new ExtractionResult(false, "Not an archive file", null, new ExtractionStats());
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Path getExtractedPath() {
            return extractedPath;
        }

        public ExtractionStats getStats() {
            return stats;
        }

        @Override
        public String toString() {
            if (successful) {
                return String.format("ExtractionResult{successful=true, path=%s, stats=%s}",
                        extractedPath, stats);
            } else {
                return String.format("ExtractionResult{successful=false, error='%s'}", errorMessage);
            }
        }
    }

    /**
     * Statistics about an extraction operation.
     */
    public static class ExtractionStats {
        private int filesExtracted = 0;
        private int directoriesCreated = 0;
        private int nestedArchives = 0;

        public void merge(ExtractionStats other) {
            this.filesExtracted += other.filesExtracted;
            this.directoriesCreated += other.directoriesCreated;
            this.nestedArchives += other.nestedArchives;
        }

        public int getFilesExtracted() {
            return filesExtracted;
        }

        public int getDirectoriesCreated() {
            return directoriesCreated;
        }

        public int getNestedArchives() {
            return nestedArchives;
        }

        @Override
        public String toString() {
            return String.format("ExtractionStats{files=%d, dirs=%d, nested=%d}",
                    filesExtracted, directoriesCreated, nestedArchives);
        }
    }
}
