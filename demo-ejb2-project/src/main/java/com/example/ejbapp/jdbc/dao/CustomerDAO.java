package com.example.ejbapp.jdbc.dao;

import com.example.ejbapp.jdbc.framework.ConnectionManager;
import com.example.ejbapp.jdbc.framework.JdbcHelper;
import com.example.ejbapp.jdbc.framework.TransactionManager;
import com.example.ejbapp.jdbc.model.Customer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Customer DAO - Static Singleton Pattern
 * 
 * Data Access Object for Customer entity.
 * Uses static singleton pattern typical of the EJB 2.0 era.
 * 
 * Anti-patterns demonstrated:
 * - Static singleton
 * - All static methods
 * - Manual JDBC code
 * - Tight coupling to ConnectionManager
 * - No interface/implementation separation
 * 
 * @author EJB 2.0 Era Developer
 */
public class CustomerDAO {
    
    // Static singleton instance
    private static final CustomerDAO INSTANCE = new CustomerDAO();
    
    // SQL queries
    private static final String SELECT_ALL = "SELECT * FROM CUSTOMER ORDER BY ID";
    private static final String SELECT_BY_ID = "SELECT * FROM CUSTOMER WHERE ID = ?";
    private static final String INSERT = "INSERT INTO CUSTOMER (NAME, EMAIL, PHONE) VALUES (?, ?, ?)";
    private static final String UPDATE = "UPDATE CUSTOMER SET NAME = ?, EMAIL = ?, PHONE = ? WHERE ID = ?";
    private static final String DELETE = "DELETE FROM CUSTOMER WHERE ID = ?";
    private static final String COUNT_ALL = "SELECT COUNT(*) FROM CUSTOMER";
    
    /**
     * Private constructor
     */
    private CustomerDAO() {
    }
    
    /**
     * Get singleton instance
     */
    public static CustomerDAO getInstance() {
        return INSTANCE;
    }
    
    /**
     * Find all customers
     * 
     * @return list of all customers
     * @throws Exception if query fails
     */
    public List findAll() throws Exception {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        List customers = new ArrayList();
        
        try {
            // Use transaction connection if available
            conn = getConnection();
            pstmt = conn.prepareStatement(SELECT_ALL);
            rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Customer customer = mapRowToCustomer(rs);
                customers.add(customer);
            }
            
            return customers;
            
        } finally {
            closeQuietly(rs);
            closeQuietly(pstmt);
            closeConnectionIfNotInTransaction(conn);
        }
    }
    
    /**
     * Find customer by ID
     * 
     * @param id customer ID
     * @return Customer object or null if not found
     * @throws Exception if query fails
     */
    public Customer findById(int id) throws Exception {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(SELECT_BY_ID);
            pstmt.setInt(1, id);
            rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapRowToCustomer(rs);
            }
            
            return null;
            
        } finally {
            closeQuietly(rs);
            closeQuietly(pstmt);
            closeConnectionIfNotInTransaction(conn);
        }
    }
    
    /**
     * Insert a new customer
     * 
     * @param customer customer to insert
     * @return generated ID
     * @throws Exception if insert fails
     */
    public int insert(Customer customer) throws Exception {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(INSERT, PreparedStatement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, customer.getName());
            pstmt.setString(2, customer.getEmail());
            pstmt.setString(3, customer.getPhone());
            
            pstmt.executeUpdate();
            
            // Get generated key
            rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                int id = rs.getInt(1);
                customer.setId(id);
                return id;
            }
            
            throw new Exception("Failed to retrieve generated key");
            
        } finally {
            closeQuietly(rs);
            closeQuietly(pstmt);
            closeConnectionIfNotInTransaction(conn);
        }
    }
    
    /**
     * Update an existing customer
     * 
     * @param customer customer to update
     * @return true if updated, false if not found
     * @throws Exception if update fails
     */
    public boolean update(Customer customer) throws Exception {
        Connection conn = null;
        PreparedStatement pstmt = null;
        
        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(UPDATE);
            pstmt.setString(1, customer.getName());
            pstmt.setString(2, customer.getEmail());
            pstmt.setString(3, customer.getPhone());
            pstmt.setInt(4, customer.getId());
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } finally {
            closeQuietly(pstmt);
            closeConnectionIfNotInTransaction(conn);
        }
    }
    
    /**
     * Delete a customer by ID
     * 
     * @param id customer ID
     * @return true if deleted, false if not found
     * @throws Exception if delete fails
     */
    public boolean delete(int id) throws Exception {
        Connection conn = null;
        PreparedStatement pstmt = null;
        
        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(DELETE);
            pstmt.setInt(1, id);
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } finally {
            closeQuietly(pstmt);
            closeConnectionIfNotInTransaction(conn);
        }
    }
    
    /**
     * Count all customers
     * 
     * @return number of customers
     * @throws Exception if query fails
     */
    public int count() throws Exception {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(COUNT_ALL);
            rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            
            return 0;
            
        } finally {
            closeQuietly(rs);
            closeQuietly(pstmt);
            closeConnectionIfNotInTransaction(conn);
        }
    }
    
    /**
     * Map ResultSet row to Customer object
     */
    private Customer mapRowToCustomer(ResultSet rs) throws Exception {
        Customer customer = new Customer();
        customer.setId(rs.getInt("ID"));
        customer.setName(rs.getString("NAME"));
        customer.setEmail(rs.getString("EMAIL"));
        customer.setPhone(rs.getString("PHONE"));
        customer.setCreatedAt(rs.getTimestamp("CREATED_AT"));
        return customer;
    }
    
    /**
     * Get connection - either from transaction or new connection
     */
    private Connection getConnection() throws Exception {
        // Check if we're in a transaction
        Connection conn = TransactionManager.getCurrentConnection();
        if (conn != null) {
            return conn;
        }
        // Get new connection
        return ConnectionManager.getInstance().getConnection();
    }
    
    /**
     * Close connection only if not in transaction
     */
    private void closeConnectionIfNotInTransaction(Connection conn) {
        if (conn != null && !TransactionManager.isTransactionActive()) {
            ConnectionManager.closeQuietly(conn);
        }
    }
    
    /**
     * Close ResultSet quietly
     */
    private void closeQuietly(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Exception e) {
                // Swallow exception
            }
        }
    }
    
    /**
     * Close PreparedStatement quietly
     */
    private void closeQuietly(PreparedStatement pstmt) {
        if (pstmt != null) {
            try {
                pstmt.close();
            } catch (Exception e) {
                // Swallow exception
            }
        }
    }
}
