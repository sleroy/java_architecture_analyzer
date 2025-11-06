package com.example.ejbapp.jdbc.framework;

import org.apache.commons.dbcp.BasicDataSource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Static Singleton Connection Manager - EJB 2.0 Era Pattern
 * 
 * This is a typical "God Object" anti-pattern from the 2000s era.
 * Handles all database connection management using static methods.
 * Uses Apache Commons DBCP for connection pooling (very common in that period).
 * 
 * Anti-patterns demonstrated:
 * - Static singleton with eager initialization
 * - God object with too many responsibilities
 * - Tight coupling throughout the application
 * - No dependency injection
 * 
 * @author EJB 2.0 Era Developer
 */
public class ConnectionManager {
    
    // Static singleton instance (eager initialization)
    private static final ConnectionManager INSTANCE = new ConnectionManager();
    
    // Connection pool
    private BasicDataSource dataSource;
    
    // H2 database configuration
    private static final String DB_DRIVER = "org.h2.Driver";
    private static final String DB_URL = "jdbc:h2:mem:customerdb;DB_CLOSE_DELAY=-1";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    
    /**
     * Private constructor - initializes connection pool
     */
    private ConnectionManager() {
        try {
            initializeDataSource();
            initializeSchema();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ConnectionManager", e);
        }
    }
    
    /**
     * Get singleton instance
     */
    public static ConnectionManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initialize Apache Commons DBCP connection pool
     */
    private void initializeDataSource() {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName(DB_DRIVER);
        dataSource.setUrl(DB_URL);
        dataSource.setUsername(DB_USER);
        dataSource.setPassword(DB_PASSWORD);
        
        // Connection pool settings (typical for 2000s era)
        dataSource.setInitialSize(5);
        dataSource.setMaxActive(20);
        dataSource.setMaxIdle(10);
        dataSource.setMinIdle(5);
        dataSource.setMaxWait(10000);
        
        // Validation query
        dataSource.setValidationQuery("SELECT 1");
        dataSource.setTestOnBorrow(true);
    }
    
    /**
     * Initialize database schema from SQL file
     */
    private void initializeSchema() {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = getConnection();
            stmt = conn.createStatement();
            
            // Read schema.sql from classpath
            InputStream is = getClass().getClassLoader().getResourceAsStream("schema.sql");
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    // Skip comments and empty lines
                    line = line.trim();
                    if (!line.startsWith("--") && !line.isEmpty()) {
                        sb.append(line).append(" ");
                    }
                }
                reader.close();
                
                // Execute schema creation
                String sql = sb.toString();
                if (!sql.isEmpty()) {
                    stmt.execute(sql);
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not initialize schema: " + e.getMessage());
        } finally {
            closeQuietly(stmt);
            closeQuietly(conn);
        }
    }
    
    /**
     * Get a connection from the pool
     * 
     * @return database connection
     * @throws SQLException if connection cannot be obtained
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    /**
     * Close connection quietly (no exception thrown)
     * Typical pattern from pre-try-with-resources era
     */
    public static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                // Swallow exception - typical anti-pattern
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }
    
    /**
     * Close statement quietly
     */
    public static void closeQuietly(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                System.err.println("Error closing statement: " + e.getMessage());
            }
        }
    }
    
    /**
     * Shutdown connection pool (call on application shutdown)
     */
    public void shutdown() {
        try {
            if (dataSource != null) {
                dataSource.close();
            }
        } catch (SQLException e) {
            System.err.println("Error shutting down connection pool: " + e.getMessage());
        }
    }
}
