package com.example.ejbapp.jdbc.framework;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC Helper - Static Utility Class
 * 
 * Simplified JDBC framework with static methods.
 * Inspired by early frameworks like Spring JdbcTemplate but much simpler.
 * Typical of the EJB 2.0 era (2000-2010).
 * 
 * Anti-patterns demonstrated:
 * - All static methods (no OOP)
 * - Map-based return types (no type safety)
 * - Generic Exception throwing
 * - No proper generics usage
 * 
 * @author EJB 2.0 Era Developer
 */
public class JdbcHelper {
    
    /**
     * Execute a SELECT query and return results as List of Maps
     * Each Map represents one row with column name as key
     * 
     * @param sql SQL query with ? placeholders
     * @param params parameters for the query
     * @return List of Maps, each Map is one row
     * @throws Exception if query fails
     */
    public static List queryForList(String sql, Object[] params) throws Exception {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        List results = new ArrayList();
        
        try {
            conn = ConnectionManager.getInstance().getConnection();
            pstmt = conn.prepareStatement(sql);
            
            // Set parameters
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    pstmt.setObject(i + 1, params[i]);
                }
            }
            
            rs = pstmt.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            
            // Convert each row to a Map
            while (rs.next()) {
                Map row = new HashMap();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = meta.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                results.add(row);
            }
            
            return results;
            
        } finally {
            closeQuietly(rs);
            closeQuietly(pstmt);
            ConnectionManager.closeQuietly(conn);
        }
    }
    
    /**
     * Execute a SELECT query and return a single Map
     * 
     * @param sql SQL query with ? placeholders
     * @param params parameters for the query
     * @return Map representing one row, or null if not found
     * @throws Exception if query fails
     */
    public static Map queryForMap(String sql, Object[] params) throws Exception {
        List results = queryForList(sql, params);
        if (results == null || results.isEmpty()) {
            return null;
        }
        return (Map) results.get(0);
    }
    
    /**
     * Execute an UPDATE/INSERT/DELETE statement
     * 
     * @param sql SQL statement with ? placeholders
     * @param params parameters for the statement
     * @return number of rows affected
     * @throws Exception if execution fails
     */
    public static int update(String sql, Object[] params) throws Exception {
        Connection conn = null;
        PreparedStatement pstmt = null;
        
        try {
            conn = ConnectionManager.getInstance().getConnection();
            pstmt = conn.prepareStatement(sql);
            
            // Set parameters
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    pstmt.setObject(i + 1, params[i]);
                }
            }
            
            return pstmt.executeUpdate();
            
        } finally {
            closeQuietly(pstmt);
            ConnectionManager.closeQuietly(conn);
        }
    }
    
    /**
     * Execute an INSERT and return the generated key
     * 
     * @param sql INSERT statement with ? placeholders
     * @param params parameters for the statement
     * @return generated key (usually the ID)
     * @throws Exception if execution fails
     */
    public static int insert(String sql, Object[] params) throws Exception {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            conn = ConnectionManager.getInstance().getConnection();
            pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            
            // Set parameters
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    pstmt.setObject(i + 1, params[i]);
                }
            }
            
            pstmt.executeUpdate();
            
            // Get generated key
            rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
            
            throw new SQLException("Failed to retrieve generated key");
            
        } finally {
            closeQuietly(rs);
            closeQuietly(pstmt);
            ConnectionManager.closeQuietly(conn);
        }
    }
    
    /**
     * Execute a query and return a single integer value
     * Useful for COUNT queries
     * 
     * @param sql SQL query
     * @param params parameters
     * @return integer value
     * @throws Exception if query fails
     */
    public static int queryForInt(String sql, Object[] params) throws Exception {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            conn = ConnectionManager.getInstance().getConnection();
            pstmt = conn.prepareStatement(sql);
            
            // Set parameters
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    pstmt.setObject(i + 1, params[i]);
                }
            }
            
            rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
            
            return 0;
            
        } finally {
            closeQuietly(rs);
            closeQuietly(pstmt);
            ConnectionManager.closeQuietly(conn);
        }
    }
    
    /**
     * Close ResultSet quietly (no exception thrown)
     */
    private static void closeQuietly(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                // Swallow exception
                System.err.println("Error closing ResultSet: " + e.getMessage());
            }
        }
    }
    
    /**
     * Close PreparedStatement quietly
     */
    private static void closeQuietly(PreparedStatement pstmt) {
        if (pstmt != null) {
            try {
                pstmt.close();
            } catch (SQLException e) {
                System.err.println("Error closing PreparedStatement: " + e.getMessage());
            }
        }
    }
}
