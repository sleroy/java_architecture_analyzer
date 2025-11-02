package com.analyzer.migration.loader;

import com.analyzer.migration.loader.dto.MigrationPlanDTO;
import com.analyzer.migration.plan.MigrationPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Loads migration plans from YAML files and converts them to domain objects.
 * Supports loading from file paths, classpath resources, and input streams.
 */
public class YamlMigrationPlanLoader {

    private static final Logger logger = LoggerFactory.getLogger(YamlMigrationPlanLoader.class);

    private final ObjectMapper yamlMapper;
    private final MigrationPlanConverter converter;
    private final Validator validator;

    /**
     * Creates a new YAML migration plan loader with the specified converter.
     *
     * @param converter the converter to transform DTOs to domain objects
     */
    public YamlMigrationPlanLoader(MigrationPlanConverter converter) {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        // Configure to ignore unknown properties for compatibility
        /*
         * this.yamlMapper.configure(com.fasterxml.jackson.databind.
         * DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
         * false);
         */
        this.converter = converter;

        // Initialize validator for bean validation
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    /**
     * Loads a migration plan from a file path.
     *
     * @param filePath the path to the YAML file
     * @return the loaded migration plan
     * @throws IOException              if file cannot be read
     * @throws IllegalArgumentException if YAML is invalid or fails validation
     */
    public MigrationPlan loadFromFile(String filePath) throws IOException {
        logger.info("Loading migration plan from file: {}", filePath);
        return loadFromFile(new File(filePath));
    }

    /**
     * Loads a migration plan from a File object.
     *
     * @param file the YAML file
     * @return the loaded migration plan
     * @throws IOException              if file cannot be read
     * @throws IllegalArgumentException if YAML is invalid or fails validation
     */
    public MigrationPlan loadFromFile(File file) throws IOException {
        logger.info("Loading migration plan from file: {}", file.getAbsolutePath());

        if (!file.exists()) {
            throw new IOException("File not found: " + file.getAbsolutePath());
        }

        if (!file.canRead()) {
            throw new IOException("Cannot read file: " + file.getAbsolutePath());
        }

        MigrationPlanDTO dto = yamlMapper.readValue(file, MigrationPlanDTO.class);

        // Process includes if present
        dto = processIncludes(dto, file.toPath());

        validateDTO(dto);
        return converter.convert(dto);
    }

    /**
     * Loads a migration plan from a Path object.
     *
     * @param path the path to the YAML file
     * @return the loaded migration plan
     * @throws IOException              if file cannot be read
     * @throws IllegalArgumentException if YAML is invalid or fails validation
     */
    public MigrationPlan loadFromPath(Path path) throws IOException {
        logger.info("Loading migration plan from path: {}", path);

        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }

        if (!Files.isReadable(path)) {
            throw new IOException("Cannot read file: " + path);
        }

        MigrationPlanDTO dto = yamlMapper.readValue(path.toFile(), MigrationPlanDTO.class);

        // Process includes if present
        dto = processIncludes(dto, path);

        validateDTO(dto);
        return converter.convert(dto);
    }

    /**
     * Loads a migration plan from a classpath resource.
     *
     * @param resourcePath the classpath resource path (e.g.,
     *                     "/migration-plans/my-plan.yaml")
     * @return the loaded migration plan
     * @throws IOException              if resource cannot be read
     * @throws IllegalArgumentException if YAML is invalid or fails validation
     */
    public MigrationPlan loadFromResource(String resourcePath) throws IOException {
        logger.info("Loading migration plan from resource: {}", resourcePath);

        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return loadFromInputStream(is);
        }
    }

    /**
     * Loads a migration plan from an input stream.
     * Note: This method cannot resolve includes since no base path is available.
     * Use loadFromInputStream(InputStream, Path) if your plan contains includes.
     *
     * @param inputStream the input stream containing YAML data
     * @return the loaded migration plan
     * @throws IOException              if stream cannot be read
     * @throws IllegalArgumentException if YAML is invalid or fails validation
     */
    public MigrationPlan loadFromInputStream(InputStream inputStream) throws IOException {
        return loadFromInputStream(inputStream, null);
    }

    /**
     * Loads a migration plan from an input stream with optional base path for
     * include resolution.
     *
     * @param inputStream the input stream containing YAML data
     * @param basePath    the base path for resolving includes (null to skip include
     *                    resolution)
     * @return the loaded migration plan
     * @throws IOException              if stream cannot be read
     * @throws IllegalArgumentException if YAML is invalid or fails validation
     */
    public MigrationPlan loadFromInputStream(InputStream inputStream, Path basePath) throws IOException {
        logger.debug("Loading migration plan from input stream (basePath: {})", basePath);

        MigrationPlanDTO dto = yamlMapper.readValue(inputStream, MigrationPlanDTO.class);

        // Process includes if basePath is provided
        if (basePath != null) {
            dto = processIncludes(dto, basePath);
        } else {
            // Warn if plan has includes but no base path provided
            if (dto.getPlanRoot().getIncludes() != null && !dto.getPlanRoot().getIncludes().isEmpty()) {
                logger.warn(
                        "Migration plan contains {} include(s) but no base path provided - includes will not be resolved",
                        dto.getPlanRoot().getIncludes().size());
            }
        }

        // Validate AFTER includes are resolved
        validateDTO(dto);
        return converter.convert(dto);
    }

    /**
     * Validates a migration plan DTO using bean validation.
     *
     * @param dto the DTO to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateDTO(MigrationPlanDTO dto) {
        Set<ConstraintViolation<MigrationPlanDTO>> violations = validator.validate(dto);

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Migration plan validation failed:\n");
            for (ConstraintViolation<MigrationPlanDTO> violation : violations) {
                sb.append("  - ").append(violation.getPropertyPath())
                        .append(": ").append(violation.getMessage()).append("\n");
            }
            logger.error(sb.toString());
            throw new IllegalArgumentException(sb.toString());
        }

        logger.debug("Migration plan DTO validation passed");
    }

    /**
     * Processes includes in a migration plan DTO.
     * If the plan contains includes, they will be resolved and merged.
     *
     * @param dto      the migration plan DTO
     * @param basePath the base path for resolving relative includes
     * @return the processed (merged) DTO, or original if no includes
     * @throws IOException if include processing fails
     */
    private MigrationPlanDTO processIncludes(MigrationPlanDTO dto, Path basePath) throws IOException {
        List<String> includes = dto.getPlanRoot().getIncludes();

        if (includes == null || includes.isEmpty()) {
            logger.debug("No includes found in migration plan");
            return dto;
        }

        logger.info("Processing {} include(s) from plan", includes.size());

        IncludeResolver resolver = new IncludeResolver(basePath);
        return resolver.mergeIncludes(dto);
    }

    /**
     * Gets the underlying Jackson ObjectMapper for advanced usage.
     *
     * @return the YAML object mapper
     */
    public ObjectMapper getYamlMapper() {
        return yamlMapper;
    }
}
