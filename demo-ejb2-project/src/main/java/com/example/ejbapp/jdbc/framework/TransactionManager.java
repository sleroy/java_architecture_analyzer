package com.example.ejbapp.jdbc.framework;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Transaction Manager - ThreadLocal-based
 * 
 * Manual transaction management using ThreadLocal.
 * This was a common pattern in the EJB 2.0 era before Spring's @Transactional.
 * 
 * Anti-patterns demonstrated:
 * - ThreadLocal usage (can cause memory leaks if not cleaned up)
 * - Manual transaction management
 * - Static methods everywhere
 * - No declarative transaction management
 * 
 * Usage:
 * <pre>
 * try {
 *     TransactionManager.beginTransaction();
 *     // ... do database operations
 *     TransactionManager.commit();
 * } catch (Exception e) {
 *     TransactionManager.rollback();
 *     throw e;
 * }
 * </pre>
 * 
 * @author EJB 2.0 Era Developer
 */
public class TransactionManager {
    
    // ThreadLocal to store connection per thread
    private static final ThreadLocal<Connection> threadConnection = new ThreadLocal<Connection>();
    
    /**
     * Begin a transaction
     * Gets a connection and stores it in ThreadLocal
     * 
     * @throws Exception if connection cannot be obtained
     */
    public static void beginTransaction() throws Exception {
        Connection conn = threadConnection.get();
        if (conn != null) {
            throw new IllegalStateException("Transaction already started for this thread");
        }
        
        conn = ConnectionManager.getInstance().getConnection();
        conn.setAutoCommit(false);
        threadConnection.set(conn);
    }
    
    /**
     * Commit the current transaction
     * 
     * @throws Exception if commit fails
     */
    public static void commit() throws Exception {
        Connection conn = threadConnection.get();
        if (conn == null) {
            throw new IllegalStateException("No transaction started for this thread");
        }
        
        try {
            conn.commit();
        } finally {
            cleanup();
        }
    }
    
    /**
     * Rollback the current transaction
     */
    public static void rollback() {
        Connection conn = threadConnection.get();
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                System.err.println("Error during rollback: " + e.getMessage());
            } finally {
                cleanup();
            }
        }
    }
    
    /**
     * Get the current connection for this thread
     * Used by DAO classes to participate in the transaction
     * 
     * @return current connection, or null if no transaction
     */
    public static Connection getCurrentConnection() {
        return threadConnection.get();
    }
    
    /**
     * Check if a transaction is active
     * 
     * @return true if transaction is active
     */
    public static boolean isTransactionActive() {
        return threadConnection.get() != null;
    }
    
    /**
     * Clean up - close connection and remove from ThreadLocal
     * IMPORTANT: Always call this to avoid memory leaks!
     */
    private static void cleanup() {
        Connection conn = threadConnection.get();
        if (conn != null) {
            try {
                conn.setAutoCommit(true); // Reset to default
            } catch (SQLException e) {
                // Ignore
            }
            ConnectionManager.closeQuietly(conn);
            threadConnection.remove(); // Important to prevent memory leak!
        }
    }
}
