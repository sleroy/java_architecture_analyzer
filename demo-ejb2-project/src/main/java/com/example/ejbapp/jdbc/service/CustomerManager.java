package com.example.ejbapp.jdbc.service;

import com.example.ejbapp.jdbc.dao.CustomerDAO;
import com.example.ejbapp.jdbc.framework.TransactionManager;
import com.example.ejbapp.jdbc.model.Customer;

import java.util.List;
import java.util.Map;

/**
 * Customer Manager - Service Layer
 * 
 * Extends AbstractEntityManager to provide generic CRUD operations.
 * Uses CustomerDAO for database access.
 * Demonstrates the Map-based API pattern from the EJB 2.0 era.
 * 
 * Anti-patterns demonstrated:
 * - Implements generic interface losing type safety
 * - Map-based parameters require string keys
 * - Returns Object instead of typed Customer
 * - Manual transaction management
 * - Tight coupling to DAO singleton
 * 
 * @author EJB 2.0 Era Developer
 */
public class CustomerManager extends AbstractEntityManager {
    
    // Reference to DAO singleton
    private CustomerDAO dao = CustomerDAO.getInstance();
    
    /**
     * List all customers
     * 
     * @return List of Customer objects (untyped, requires casting)
     * @throws Exception if operation fails
     */
    @Override
    public List listAll() throws Exception {
        return dao.findAll();
    }
    
    /**
     * Add a new customer using a Map
     * 
     * Expected Map keys: "name", "email", "phone"
     * 
     * @param data Map containing customer data
     * @return the created Customer object (as Object, requires casting)
     * @throws Exception if operation fails or required fields missing
     */
    @Override
    public Object addRecord(Map data) throws Exception {
        // Validate required fields
        if (data == null) {
            throw new IllegalArgumentException("Customer data cannot be null");
        }
        
        String name = (String) data.get("name");
        String email = (String) data.get("email");
        String phone = (String) data.get("phone");
        
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer email is required");
        }
        
        // Create Customer object
        Customer customer = new Customer();
        customer.setName(name);
        customer.setEmail(email);
        customer.setPhone(phone);
        
        // Insert using DAO
        dao.insert(customer);
        
        return customer; // Return as Object (typical anti-pattern)
    }
    
    /**
     * Get a customer by ID
     * 
     * @param id customer ID
     * @return Customer object (as Object, requires casting), or null if not found
     * @throws Exception if operation fails
     */
    @Override
    public Object getRecord(int id) throws Exception {
        return dao.findById(id);
    }
    
    /**
     * Update a customer using a Map
     * 
     * Expected Map keys: "name", "email", "phone"
     * 
     * @param id customer ID
     * @param data Map containing customer data
     * @return true if updated successfully
     * @throws Exception if operation fails
     */
    @Override
    public boolean updateRecord(int id, Map data) throws Exception {
        // Validate required fields
        if (data == null) {
            throw new IllegalArgumentException("Customer data cannot be null");
        }
        
        // Get existing customer
        Customer customer = dao.findById(id);
        if (customer == null) {
            return false;
        }
        
        // Update fields if provided
        if (data.containsKey("name")) {
            customer.setName((String) data.get("name"));
        }
        if (data.containsKey("email")) {
            customer.setEmail((String) data.get("email"));
        }
        if (data.containsKey("phone")) {
            customer.setPhone((String) data.get("phone"));
        }
        
        return dao.update(customer);
    }
    
    /**
     * Delete a customer by ID
     * 
     * @param id customer ID
     * @return true if deleted successfully
     * @throws Exception if operation fails
     */
    @Override
    public boolean deleteRecord(int id) throws Exception {
        return dao.delete(id);
    }
    
    /**
     * Count all customers
     * 
     * @return number of customers
     * @throws Exception if operation fails
     */
    @Override
    public int countRecords() throws Exception {
        return dao.count();
    }
    
    /**
     * Add customer with transaction management
     * This method demonstrates manual transaction handling
     * 
     * @param data Map containing customer data
     * @return the created Customer object
     * @throws Exception if operation fails
     */
    public Customer addCustomerWithTransaction(Map data) throws Exception {
        try {
            TransactionManager.beginTransaction();
            
            // Use addRecord which returns Object
            Object result = addRecord(data);
            
            TransactionManager.commit();
            
            return (Customer) result; // Requires casting
            
        } catch (Exception e) {
            TransactionManager.rollback();
            throw e;
        }
    }
    
    /**
     * Get all customers (typed version for convenience)
     * This is a helper method that returns typed List
     * 
     * @return List of Customer objects
     * @throws Exception if operation fails
     */
    public List getAllCustomers() throws Exception {
        return listAll(); // Returns untyped List
    }
    
    /**
     * Get customer by ID (typed version for convenience)
     * 
     * @param id customer ID
     * @return Customer object or null if not found
     * @throws Exception if operation fails
     */
    public Customer getCustomerById(int id) throws Exception {
        return (Customer) getRecord(id); // Requires casting
    }
}
