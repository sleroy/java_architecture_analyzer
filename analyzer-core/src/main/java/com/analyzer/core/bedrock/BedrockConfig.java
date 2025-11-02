package com.analyzer.core.bedrock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration management for AWS Bedrock integration.
 * Handles loading configuration from multiple sources with priority order:
 * 1. CLI parameters (highest priority)
 * 2. System properties
 * 3. Environment variables
 * 4. Default classpath file (lowest priority)
 */
public class BedrockConfig {

    private static final Logger logger = LoggerFactory.getLogger(BedrockConfig.class);
    private static final String DEFAULT_CONFIG_FILE = "bedrock.properties";

    // AWS Authentication
    private final String awsAccessKeyId;
    private final String awsSecretAccessKey;
    private final String awsRegion;

    // Model Configuration
    private final String modelId;
    private final int maxTokens;
    private final double temperature;
    private final double topP;

    // API Control
    private final int rateLimitRpm;
    private final int timeoutSeconds;
    private final int retryAttempts;
    private final boolean enabled;
    private final int batchSize;
    private final boolean cacheResults;
    private final boolean logRequests;
    private final boolean logResponses;

    private BedrockConfig(Properties properties) {
        // AWS Authentication
        this.awsAccessKeyId = getProperty(properties, "bedrock.aws.access.key.id", null);
        this.awsSecretAccessKey = getProperty(properties, "bedrock.aws.secret.access.key", null);
        this.awsRegion = getProperty(properties, "bedrock.aws.region", "us-east-1");

        // Model Configuration
        this.modelId = getProperty(properties, "bedrock.model.id", "anthropic.claude-3-sonnet-20240229-v1:0");
        this.maxTokens = getIntProperty(properties, "bedrock.max.tokens", 1000);
        this.temperature = getDoubleProperty(properties, "bedrock.temperature", 0.1);
        this.topP = getDoubleProperty(properties, "bedrock.top.p", 0.9);

        // API Control
        this.rateLimitRpm = getIntProperty(properties, "bedrock.rate.limit.rpm", 60);
        this.timeoutSeconds = getIntProperty(properties, "bedrock.timeout.seconds", 30);
        this.retryAttempts = getIntProperty(properties, "bedrock.retry.attempts", 3);
        this.enabled = getBooleanProperty(properties, "bedrock.enabled", true);
        this.batchSize = getIntProperty(properties, "bedrock.batch.size", 10);
        this.cacheResults = getBooleanProperty(properties, "bedrock.cache.results", false);
        this.logRequests = getBooleanProperty(properties, "bedrock.log.requests", false);
        this.logResponses = getBooleanProperty(properties, "bedrock.log.responses", false);
    }

    /**
     * Load configuration with default settings from classpath.
     */
    public static BedrockConfig load() {
        return load(null);
    }

    /**
     * Load configuration with optional custom configuration file.
     * 
     * @param customConfigPath Path to custom configuration file (can be null)
     * @return Configured BedrockConfig instance
     */
    public static BedrockConfig load(String customConfigPath) {
        Properties properties = new Properties();

        // 1. Load default configuration from classpath
        loadDefaultConfig(properties);

        // 2. Override with custom file if provided
        if (customConfigPath != null && !customConfigPath.trim().isEmpty()) {
            loadCustomConfig(properties, customConfigPath);
        }

        // 3. Override with system properties
        loadSystemProperties(properties);

        // 4. Override with environment variables
        loadEnvironmentVariables(properties);

        return new BedrockConfig(properties);
    }

    /**
     * Create configuration with explicit parameters (for CLI overrides).
     */
    public static BedrockConfig create(String customConfigPath, String apiToken, String modelId) {
        BedrockConfig baseConfig = load(customConfigPath);

        // Create new properties with overrides
        Properties properties = new Properties();
        copyToProperties(baseConfig, properties);

        // Apply CLI overrides
        if (apiToken != null && !apiToken.trim().isEmpty()) {
            properties.setProperty("bedrock.api.token", apiToken);
        }
        if (modelId != null && !modelId.trim().isEmpty()) {
            properties.setProperty("bedrock.model.id", modelId);
        }

        return new BedrockConfig(properties);
    }

    private static void loadDefaultConfig(Properties properties) {
        try (InputStream inputStream = BedrockConfig.class.getClassLoader()
                .getResourceAsStream(DEFAULT_CONFIG_FILE)) {
            if (inputStream != null) {
                properties.load(inputStream);
                logger.debug("Loaded default Bedrock configuration from classpath: {}", DEFAULT_CONFIG_FILE);
            } else {
                logger.warn("Default Bedrock configuration file not found in classpath: {}", DEFAULT_CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.warn("Failed to load default Bedrock configuration: {}", e.getMessage());
        }
    }

    private static void loadCustomConfig(Properties properties, String customConfigPath) {
        try {
            Path configPath = Paths.get(customConfigPath);
            if (Files.exists(configPath)) {
                try (InputStream inputStream = Files.newInputStream(configPath)) {
                    properties.load(inputStream);
                    logger.info("Loaded custom Bedrock configuration from: {}", customConfigPath);
                }
            } else {
                logger.warn("Custom Bedrock configuration file not found: {}", customConfigPath);
            }
        } catch (IOException e) {
            logger.warn("Failed to load custom Bedrock configuration from {}: {}", customConfigPath, e.getMessage());
        }
    }

    private static void loadSystemProperties(Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            String systemValue = System.getProperty(key);
            if (systemValue != null) {
                properties.setProperty(key, systemValue);
                logger.debug("Override from system property: {} = {}", key,
                        key.contains("token") ? "***" : systemValue);
            }
        }
    }

    private static void loadEnvironmentVariables(Properties properties) {
        // Special handling for BEDROCK_API_TOKEN environment variable
        String envToken = System.getenv("BEDROCK_API_TOKEN");
        if (envToken != null && !envToken.trim().isEmpty()) {
            properties.setProperty("bedrock.api.token", envToken);
            logger.debug("Override from environment variable: BEDROCK_API_TOKEN = ***");
        }

        // Handle other environment variables that might be set
        for (String key : properties.stringPropertyNames()) {
            String envKey = key.toUpperCase().replace('.', '_');
            String envValue = System.getenv(envKey);
            if (envValue != null) {
                properties.setProperty(key, envValue);
                logger.debug("Override from environment variable: {} = {}", envKey,
                        key.contains("token") ? "***" : envValue);
            }
        }
    }

    private static void copyToProperties(BedrockConfig config, Properties properties) {
        properties.setProperty("bedrock.aws.access.key.id", config.awsAccessKeyId != null ? config.awsAccessKeyId : "");
        properties.setProperty("bedrock.aws.secret.access.key",
                config.awsSecretAccessKey != null ? config.awsSecretAccessKey : "");
        properties.setProperty("bedrock.aws.region", config.awsRegion);
        properties.setProperty("bedrock.model.id", config.modelId);
        properties.setProperty("bedrock.max.tokens", String.valueOf(config.maxTokens));
        properties.setProperty("bedrock.temperature", String.valueOf(config.temperature));
        properties.setProperty("bedrock.top.p", String.valueOf(config.topP));
        properties.setProperty("bedrock.rate.limit.rpm", String.valueOf(config.rateLimitRpm));
        properties.setProperty("bedrock.timeout.seconds", String.valueOf(config.timeoutSeconds));
        properties.setProperty("bedrock.retry.attempts", String.valueOf(config.retryAttempts));
        properties.setProperty("bedrock.enabled", String.valueOf(config.enabled));
        properties.setProperty("bedrock.batch.size", String.valueOf(config.batchSize));
        properties.setProperty("bedrock.cache.results", String.valueOf(config.cacheResults));
        properties.setProperty("bedrock.log.requests", String.valueOf(config.logRequests));
        properties.setProperty("bedrock.log.responses", String.valueOf(config.logResponses));
    }

    private static String getProperty(Properties properties, String key, String defaultValue) {
        String value = properties.getProperty(key, defaultValue);
        // Handle environment variable substitution like ${BEDROCK_API_TOKEN:}
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String envVar = value.substring(2, value.length() - 1);
            String envDefault = "";

            int colonIndex = envVar.indexOf(':');
            if (colonIndex > 0) {
                envDefault = envVar.substring(colonIndex + 1);
                envVar = envVar.substring(0, colonIndex);
            }

            String envValue = System.getenv(envVar);
            value = envValue != null ? envValue : envDefault;
        }
        return value;
    }

    private static int getIntProperty(Properties properties, String key, int defaultValue) {
        String value = getProperty(properties, key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}: {}. Using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private static double getDoubleProperty(Properties properties, String key, double defaultValue) {
        String value = getProperty(properties, key, String.valueOf(defaultValue));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid double value for {}: {}. Using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private static boolean getBooleanProperty(Properties properties, String key, boolean defaultValue) {
        String value = getProperty(properties, key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    // Getters
    public String getAwsAccessKeyId() {
        return awsAccessKeyId;
    }

    public String getAwsSecretAccessKey() {
        return awsSecretAccessKey;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public String getModelId() {
        return modelId;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getTopP() {
        return topP;
    }

    public int getRateLimitRpm() {
        return rateLimitRpm;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public boolean isEnabled() {
        return enabled && hasAwsCredentials();
    }

    private boolean hasAwsCredentials() {
        return (awsAccessKeyId != null && !awsAccessKeyId.trim().isEmpty() &&
                awsSecretAccessKey != null && !awsSecretAccessKey.trim().isEmpty());
    }

    public int getBatchSize() {
        return batchSize;
    }

    public boolean isCacheResults() {
        return cacheResults;
    }

    public boolean isLogRequests() {
        return logRequests;
    }

    public boolean isLogResponses() {
        return logResponses;
    }

    /**
     * Validate the configuration and throw exceptions if invalid.
     */
    public void validate() throws BedrockConfigurationException {
        if (!hasAwsCredentials()) {
            throw new BedrockConfigurationException(
                    "AWS credentials are required. Set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables "
                            +
                            "or configure bedrock.aws.access.key.id and bedrock.aws.secret.access.key in properties.");
        }

        if (awsRegion == null || awsRegion.trim().isEmpty()) {
            throw new BedrockConfigurationException("AWS region cannot be empty");
        }

        if (modelId == null || modelId.trim().isEmpty()) {
            throw new BedrockConfigurationException("Bedrock model ID cannot be empty");
        }

        if (maxTokens <= 0) {
            throw new BedrockConfigurationException("Max tokens must be positive: " + maxTokens);
        }

        if (temperature < 0.0 || temperature > 1.0) {
            throw new BedrockConfigurationException("Temperature must be between 0.0 and 1.0: " + temperature);
        }

        if (topP < 0.0 || topP > 1.0) {
            throw new BedrockConfigurationException("Top-p must be between 0.0 and 1.0: " + topP);
        }

        if (rateLimitRpm <= 0) {
            throw new BedrockConfigurationException("Rate limit RPM must be positive: " + rateLimitRpm);
        }

        if (timeoutSeconds <= 0) {
            throw new BedrockConfigurationException("Timeout seconds must be positive: " + timeoutSeconds);
        }

        if (retryAttempts < 0) {
            throw new BedrockConfigurationException("Retry attempts cannot be negative: " + retryAttempts);
        }
    }

    @Override
    public String toString() {
        return "BedrockConfig{" +
                "awsRegion='" + awsRegion + '\'' +
                ", modelId='" + modelId + '\'' +
                ", maxTokens=" + maxTokens +
                ", temperature=" + temperature +
                ", topP=" + topP +
                ", rateLimitRpm=" + rateLimitRpm +
                ", timeoutSeconds=" + timeoutSeconds +
                ", retryAttempts=" + retryAttempts +
                ", enabled=" + enabled +
                ", batchSize=" + batchSize +
                ", cacheResults=" + cacheResults +
                ", awsCredentialsSet=" + hasAwsCredentials() +
                '}';
    }
}
