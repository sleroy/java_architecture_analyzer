package com.example.ejbapp.jdbc.soap;

import com.example.ejbapp.jdbc.model.Customer;
import com.example.ejbapp.jdbc.service.CustomerManager;

import javax.jws.WebMethod;
import javax.jws.WebService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer SOAP Web Service
 * 
 * SOAP endpoint that uses the generic AbstractEntityManager interface.
 * Demonstrates how the Map-based API was exposed to SOAP services.
 * 
 * Typical EJB 2.0 era patterns:
 * - Direct instantiation of service (no DI)
 * - Map-based parameters (converted to/from XML)
 * - Untyped return values
 * - Poor error handling (throws Exception)
 * 
 * @author EJB 2.0 Era Developer
 */
@WebService(serviceName = "CustomerSoapService")
public class CustomerSoapService {
    
    // Direct instantiation - no dependency injection
    private CustomerManager manager = new CustomerManager();
    
    /**
     * List all customers
     * Uses the generic listAll() method
     * 
     * @return array of Customer objects
     * @throws Exception if operation fails
     */
    @WebMethod
    public Customer[] listAllCustomers() throws Exception {
        // Call generic listAll() method
        List customers = manager.listAll();
        
        // Convert to array (typical SOAP pattern)
        if (customers == null || customers.isEmpty()) {
            return new Customer[0];
        }
        
        return (Customer[]) customers.toArray(new Customer[customers.size()]);
    }
    
    /**
     * Get customer by ID
     * Uses the generic getRecord() method
     * 
     * @param id customer ID
     * @return Customer object or null if not found
     * @throws Exception if operation fails
     */
    @WebMethod
    public Customer getCustomerById(int id) throws Exception {
        // Call generic getRecord() method (returns Object)
        Object result = manager.getRecord(id);
        
        // Cast to Customer (required because getRecord returns Object)
        return (Customer) result;
    }
    
    /**
     * Add a new customer
     * Uses the generic addRecord() method with Map
     * 
     * In SOAP, Map parameters are typically sent as separate fields
     * 
     * @param name customer name
     * @param email customer email
     * @param phone customer phone
     * @return the created Customer object
     * @throws Exception if operation fails
     */
    @WebMethod
    public Customer addCustomer(String name, String email, String phone) throws Exception {
        // Create Map from parameters (typical pattern)
        Map data = new HashMap();
        data.put("name", name);
        data.put("email", email);
        data.put("phone", phone);
        
        // Call generic addRecord() method
        Object result = manager.addRecord(data);
        
        // Cast to Customer (required because addRecord returns Object)
        return (Customer) result;
    }
    
    /**
     * Update a customer
     * Uses the generic updateRecord() method with Map
     * 
     * @param id customer ID
     * @param name new name (optional)
     * @param email new email (optional)
     * @param phone new phone (optional)
     * @return true if updated successfully
     * @throws Exception if operation fails
     */
    @WebMethod
    public boolean updateCustomer(int id, String name, String email, String phone) throws Exception {
        // Create Map from parameters
        Map data = new HashMap();
        if (name != null && !name.trim().isEmpty()) {
            data.put("name", name);
        }
        if (email != null && !email.trim().isEmpty()) {
            data.put("email", email);
        }
        if (phone != null && !phone.trim().isEmpty()) {
            data.put("phone", phone);
        }
        
        // Call generic updateRecord() method
        return manager.updateRecord(id, data);
    }
    
    /**
     * Delete a customer
     * Uses the generic deleteRecord() method
     * 
     * @param id customer ID
     * @return true if deleted successfully
     * @throws Exception if operation fails
     */
    @WebMethod
    public boolean deleteCustomer(int id) throws Exception {
        // Call generic deleteRecord() method
        return manager.deleteRecord(id);
    }
    
    /**
     * Count all customers
     * Uses the generic countRecords() method
     * 
     * @return number of customers
     * @throws Exception if operation fails
     */
    @WebMethod
    public int countCustomers() throws Exception {
        // Call generic countRecords() method
        return manager.countRecords();
    }
}
