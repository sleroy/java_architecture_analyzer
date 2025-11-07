package com.example.ejbapp.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration Manager - Singleton Pattern (Antipattern in EJB environment)
 * 
 * This class demonstrates several antipatterns:
 * 1. Singleton in EJB environment (violates EJB spec)
 * 2. Static state management
 * 3. File I/O in business layer
 * 4. No thread safety considerations
 * 5. Hardcoded file paths
 * 
 * Legacy Java 1.6 style implementation
 */
public class ConfigurationManager {
    
    // Singleton instance - ANTIPATTERN in EJB environment
    private static ConfigurationManager instance;
    
    // Static configuration cache - ANTIPATTERN
    private static Map configCache;
    
    // Configuration properties
    private Properties properties;
    
    // Hardcoded configuration file path - ANTIPATTERN
    private static final String CONFIG_FILE = "/etc/app/config.properties";
    
    // Private constructor for singleton pattern
    private ConfigurationManager() {
        this.properties = new Properties();
        this.configCache = new HashMap();
        loadConfiguration();
    }
    
    /**
     * Get singleton instance - NOT thread-safe (antipattern)
     */
    public static ConfigurationManager getInstance() {
        if (instance == null) {
            // No synchronization - race condition possible
            instance = new ConfigurationManager();
        }
        return instance;
    }
    
    /**
     * Load configuration from file - ANTIPATTERN: File I/O in business code
     */
    private void loadConfiguration() {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(CONFIG_FILE);
            properties.load(fis);
            
            // Cache all properties - memory leak risk
            cacheAllProperties();
            
            logInfo("Configuration loaded successfully");
            
        } catch (IOException e) {
            // Swallowing exception - ANTIPATTERN
            System.err.println("Failed to load configuration: " + e.getMessage());
            
            // Load defaults - hardcoded fallback
            loadDefaultConfiguration();
            
        } finally {
            // Resource cleanup - pre-Java 7 style
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    // Ignored - ANTIPATTERN
                }
            }
        }
    }
    
    /**
     * Cache all properties - memory management antipattern
     */
    private void cacheAllProperties() {
        // Inefficient iteration - Java 1.6 style
        java.util.Enumeration keys = properties.propertyNames();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String value = properties.getProperty(key);
            configCache.put(key, value);
        }
    }
    
    /**
     * Load default configuration - hardcoded values antipattern
     */
    private void loadDefaultConfiguration() {
        properties.setProperty("db.url", "jdbc:mysql://localhost:3306/mydb");
        properties.setProperty("db.username", "root");
        properties.setProperty("db.password", "password"); // Security antipattern
        properties.setProperty("db.pool.size", "10");
        properties.setProperty("app.timeout", "30000");
        properties.setProperty("app.retry.count", "3");
        properties.setProperty("app.debug", "false");
        
        cacheAllProperties();
    }
    
    /**
     * Get configuration value
     */
    public String getValue(String key) {
        // Check cache first
        if (configCache.containsKey(key)) {
            return (String) configCache.get(key);
        }
        
        // Fallback to properties
        String value = properties.getProperty(key);
        if (value != null) {
            configCache.put(key, value);
        }
        
        return value;
    }
    
    /**
     * Get configuration value with default
     */
    public String getValue(String key, String defaultValue) {
        String value = getValue(key);
        return (value != null) ? value : defaultValue;
    }
    
    /**
     * Get integer value
     */
    public int getIntValue(String key, int defaultValue) {
        String value = getValue(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Return default on parse error
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    /**
     * Get boolean value
     */
    public boolean getBooleanValue(String key, boolean defaultValue) {
        String value = getValue(key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }
    
    /**
     * Set configuration value - Mutable singleton antipattern
     */
    public void setValue(String key, String value) {
        properties.setProperty(key, value);
        configCache.put(key, value);
    }
    
    /**
     * Reload configuration - Not thread-safe
     */
    public void reload() {
        properties.clear();
        configCache.clear();
        loadConfiguration();
    }
    
    /**
     * Get all configuration keys
     */
    public String[] getAllKeys() {
        return (String[]) configCache.keySet().toArray(new String[0]);
    }
    
    /**
     * Check if key exists
     */
    public boolean hasKey(String key) {
        return configCache.containsKey(key) || properties.containsKey(key);
    }
    
    /**
     * Get configuration count
     */
    public int getConfigurationCount() {
        return configCache.size();
    }
    
    /**
     * Logging utility - System.out antipattern
     */
    private void logInfo(String message) {
        System.out.println("[ConfigurationManager] " + message);
    }
    
    /**
     * Get database URL - Business logic in utility class antipattern
     */
    public String getDatabaseUrl() {
        return getValue("db.url", "jdbc:mysql://localhost:3306/defaultdb");
    }
    
    /**
     * Get database username
     */
    public String getDatabaseUsername() {
        return getValue("db.username", "admin");
    }
    
    /**
     * Get database password - Storing passwords in config antipattern
     */
    public String getDatabasePassword() {
        return getValue("db.password", "admin123");
    }
    
    /**
     * Get connection pool size
     */
    public int getConnectionPoolSize() {
        return getIntValue("db.pool.size", 10);
    }
    
    /**
     * Get application timeout in milliseconds
     */
    public long getApplicationTimeout() {
        return getIntValue("app.timeout", 30000);
    }
    
    /**
     * Get retry count
     */
    public int getRetryCount() {
        return getIntValue("app.retry.count", 3);
    }
    
    /**
     * Check if debug mode enabled
     */
    public boolean isDebugEnabled() {
        return getBooleanValue("app.debug", false);
    }
    
    /**
     * Dump configuration - For debugging (antipattern: exposing sensitive data)
     */
    public void dumpConfiguration() {
        System.out.println("=== Configuration Dump ===");
        String[] keys = getAllKeys();
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            String value = getValue(key);
            System.out.println(key + " = " + value);
        }
        System.out.println("=========================");
    }
}
