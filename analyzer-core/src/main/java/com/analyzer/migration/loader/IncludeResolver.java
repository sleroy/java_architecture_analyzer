package com.analyzer.migration.loader;

import com.analyzer.migration.loader.dto.MigrationPlanDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Resolves and merges included YAML files for migration plans.
 * Supports single-level includes only (no nested includes).
 */
public class IncludeResolver {

    private static final Logger logger = LoggerFactory.getLogger(IncludeResolver.class);

    private final Path basePath;
    private final ObjectMapper yamlMapper;

    /**
     * Creates a new include resolver.
     *
     * @param basePath the base path for resolving relative include paths
     */
    public IncludeResolver(Path basePath) {
        this.basePath = basePath;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Validates that all included files exist.
     *
     * @param includes list of include paths to validate
     * @throws IOException if any include file does not exist
     */
    public void validateIncludes(List<String> includes) throws IOException {
        if (includes == null || includes.isEmpty()) {
            return;
        }

        List<String> missingFiles = new ArrayList<>();

        for (String include : includes) {
            Path includePath = resolveIncludePath(include);
            if (!Files.exists(includePath)) {
                missingFiles.add(include + " (resolved to: " + includePath + ")");
            } else if (!Files.isRegularFile(includePath)) {
                missingFiles.add(include + " (not a regular file: " + includePath + ")");
            }
        }

        if (!missingFiles.isEmpty()) {
            StringBuilder error = new StringBuilder("Include file(s) not found:\n");
            for (String missing : missingFiles) {
                error.append("  - ").append(missing).append("\n");
            }
            throw new FileNotFoundException(error.toString());
        }

        logger.debug("All {} include files validated successfully", includes.size());
    }

    /**
     * Loads and merges included files with the main plan.
     * Merge order: includes (in order) → main file (main file takes precedence)
     *
     * @param mainPlan the main migration plan DTO
     * @return merged migration plan DTO
     * @throws IOException if loading or merging fails
     */
    public MigrationPlanDTO mergeIncludes(MigrationPlanDTO mainPlan) throws IOException {
        List<String> includes = mainPlan.getPlanRoot().getIncludes();

        if (includes == null || includes.isEmpty()) {
            logger.debug("No includes found, returning main plan as-is");
            return mainPlan;
        }

        logger.info("Processing {} include(s)", includes.size());

        // Validate all includes exist before loading
        validateIncludes(includes);

        // Load each included file
        List<MigrationPlanDTO> includedPlans = new ArrayList<>();
        for (String include : includes) {
            Path includePath = resolveIncludePath(include);
            logger.debug("Loading include: {}", include);

            // Check for nested includes (not allowed)
            MigrationPlanDTO includedPlan = loadYaml(includePath);
            validateNoNestedIncludes(includedPlan, include);

            includedPlans.add(includedPlan);
        }

        // Merge all plans
        return merge(mainPlan, includedPlans);
    }

    /**
     * Resolves an include path relative to the base path.
     *
     * @param include the include path (relative or absolute)
     * @return resolved absolute path
     */
    private Path resolveIncludePath(String include) {
        Path includePath = Path.of(include);

        if (includePath.isAbsolute()) {
            return includePath;
        }

        // Resolve relative to the parent directory of the base path
        return basePath.getParent().resolve(include).normalize();
    }

    /**
     * Loads a YAML file into a MigrationPlanDTO.
     *
     * @param path the path to the YAML file
     * @return loaded DTO
     * @throws IOException if loading fails
     */
    private MigrationPlanDTO loadYaml(Path path) throws IOException {
        return yamlMapper.readValue(path.toFile(), MigrationPlanDTO.class);
    }

    /**
     * Validates that an included file does not contain nested includes.
     *
     * @param plan        the included plan to validate
     * @param includePath the path of the include for error reporting
     * @throws IllegalArgumentException if nested includes are found
     */
    private void validateNoNestedIncludes(MigrationPlanDTO plan, String includePath) {
        List<String> nestedIncludes = plan.getPlanRoot().getIncludes();
        if (nestedIncludes != null && !nestedIncludes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nested includes are not allowed. Include file '" + includePath +
                            "' contains " + nestedIncludes.size() + " include(s): " + nestedIncludes);
        }
    }

    /**
     * Merges the main plan with included plans.
     * Merge strategy:
     * - Variables: includes first (in order), then main (main overrides)
     * - Metadata: merge non-null fields, main takes precedence
     * - Phases: includes first (in order), then main phases
     *
     * @param mainPlan      the main migration plan
     * @param includedPlans list of included plans
     * @return merged plan
     */
    private MigrationPlanDTO merge(MigrationPlanDTO mainPlan, List<MigrationPlanDTO> includedPlans) {
        logger.debug("Merging main plan with {} included plan(s)", includedPlans.size());

        MigrationPlanDTO.PlanRootDTO mainRoot = mainPlan.getPlanRoot();

        // Create new merged plan root
        MigrationPlanDTO.PlanRootDTO mergedRoot = new MigrationPlanDTO.PlanRootDTO();

        // Copy basic properties from main plan (always from main)
        mergedRoot.setName(mainRoot.getName());
        mergedRoot.setVersion(mainRoot.getVersion());
        mergedRoot.setDescription(mainRoot.getDescription());

        // Merge variables: includes first, then main (main overrides)
        Map<String, String> mergedVariables = new LinkedHashMap<>();
        for (MigrationPlanDTO includedPlan : includedPlans) {
            Map<String, String> includeVars = includedPlan.getPlanRoot().getVariables();
            if (includeVars != null) {
                mergedVariables.putAll(includeVars);
            }
        }
        // Main plan variables override
        if (mainRoot.getVariables() != null) {
            mergedVariables.putAll(mainRoot.getVariables());
        }
        mergedRoot.setVariables(mergedVariables);

        // Merge metadata: merge non-null fields, main takes precedence
        MigrationPlanDTO.MetadataDTO mergedMetadata = mergeMetadata(mainRoot, includedPlans);
        mergedRoot.setMetadata(mergedMetadata);

        // Merge phases: includes first (in order), then main
        List<MigrationPlanDTO.PhaseDTO> mergedPhases = new ArrayList<>();
        for (MigrationPlanDTO includedPlan : includedPlans) {
            List<MigrationPlanDTO.PhaseDTO> includePhases = includedPlan.getPlanRoot().getPhases();
            if (includePhases != null) {
                mergedPhases.addAll(includePhases);
            }
        }
        // Add main plan phases
        if (mainRoot.getPhases() != null) {
            mergedPhases.addAll(mainRoot.getPhases());
        }
        mergedRoot.setPhases(mergedPhases);

        // Clear includes in merged plan (they're already processed)
        mergedRoot.setIncludes(new ArrayList<>());

        // Create merged plan
        MigrationPlanDTO mergedPlan = new MigrationPlanDTO();
        mergedPlan.setPlanRoot(mergedRoot);

        logger.info("Merge complete: {} variables, {} phases",
                mergedVariables.size(), mergedPhases.size());

        return mergedPlan;
    }

    /**
     * Merges metadata from main plan and included plans.
     * Strategy: merge non-null fields, main plan takes precedence.
     *
     * @param mainRoot      the main plan root
     * @param includedPlans list of included plans
     * @return merged metadata
     */
    private MigrationPlanDTO.MetadataDTO mergeMetadata(
            MigrationPlanDTO.PlanRootDTO mainRoot,
            List<MigrationPlanDTO> includedPlans) {

        MigrationPlanDTO.MetadataDTO mergedMetadata = new MigrationPlanDTO.MetadataDTO();

        // Start with included metadata
        for (MigrationPlanDTO includedPlan : includedPlans) {
            MigrationPlanDTO.MetadataDTO includeMeta = includedPlan.getPlanRoot().getMetadata();
            if (includeMeta != null) {
                copyNonNullMetadata(includeMeta, mergedMetadata);
            }
        }

        // Override with main plan metadata
        if (mainRoot.getMetadata() != null) {
            copyNonNullMetadata(mainRoot.getMetadata(), mergedMetadata);
        }

        return mergedMetadata;
    }

    /**
     * Copies non-null metadata fields from source to target.
     *
     * @param source source metadata
     * @param target target metadata
     */
    private void copyNonNullMetadata(
            MigrationPlanDTO.MetadataDTO source,
            MigrationPlanDTO.MetadataDTO target) {

        if (source.getAuthor() != null) {
            target.setAuthor(source.getAuthor());
        }
        if (source.getCreatedDate() != null) {
            target.setCreatedDate(source.getCreatedDate());
        }
        if (source.getLastModified() != null) {
            target.setLastModified(source.getLastModified());
        }
        if (source.getSourcePlatform() != null) {
            target.setSourcePlatform(source.getSourcePlatform());
        }
        if (source.getTargetPlatform() != null) {
            target.setTargetPlatform(source.getTargetPlatform());
        }
        if (source.getTags() != null && !source.getTags().isEmpty()) {
            // Merge tags (no duplicates)
            Set<String> mergedTags = new LinkedHashSet<>(target.getTags());
            mergedTags.addAll(source.getTags());
            target.setTags(new ArrayList<>(mergedTags));
        }
    }
}
