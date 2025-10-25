package com.example.config;

/**
 * Application configuration constants class.
 * This should be detected by ConfigurationConstantsInspector.
 */
public class AppConfig {

    // Private constructor to prevent instantiation
    private AppConfig() {
        // Utility class should not be instantiated
    }

    // API endpoint constants
    public static final String API_BASE_URL = "https://api.example.com/v1";
    public static final String AUTH_ENDPOINT = API_BASE_URL + "/auth";
    public static final String USERS_ENDPOINT = API_BASE_URL + "/users";
    public static final String PRODUCTS_ENDPOINT = API_BASE_URL + "/products";

    // Database configuration
    public static final String DB_HOST = "db.example.com";
    public static final int DB_PORT = 5432;
    public static final String DB_NAME = "myapp_db";
    public static final String DB_USER = "app_user";
    public static final String DB_CONNECTION_URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;

    // Cache settings
    public static final boolean CACHE_ENABLED = true;
    public static final int CACHE_TTL_SECONDS = 3600;
    public static final int CACHE_MAX_ENTRIES = 10000;

    // Timeout settings (in milliseconds)
    public static final int CONNECTION_TIMEOUT = 5000;
    public static final int READ_TIMEOUT = 10000;
    public static final int WRITE_TIMEOUT = 10000;

    // File upload limits
    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    public static final String[] ALLOWED_FILE_EXTENSIONS = { ".jpg", ".png", ".pdf", ".docx" };

    // Feature flags
    public static final boolean FEATURE_NEW_UI_ENABLED = true;
    public static final boolean FEATURE_ANALYTICS_ENABLED = true;
    public static final boolean FEATURE_NOTIFICATIONS_ENABLED = false;

    // Email settings
    public static final String SMTP_HOST = "smtp.example.com";
    public static final int SMTP_PORT = 587;
    public static final boolean SMTP_TLS_ENABLED = true;
    public static final String EMAIL_FROM_ADDRESS = "no-reply@example.com";
    public static final String EMAIL_FROM_NAME = "Example App";
}
