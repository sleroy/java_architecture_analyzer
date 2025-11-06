package com.example.ejbapp.jdbc.demo;

import com.example.ejbapp.jdbc.model.Customer;
import com.example.ejbapp.jdbc.service.CustomerManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer Demo Application
 * 
 * Demonstrates the EJB 2.0 era JDBC CRUD functionality.
 * Shows both the generic Map-based API and the typed convenience methods.
 * 
 * To run:
 * 1. Compile the project: mvn clean compile
 * 2. Run this class
 * 
 * @author EJB 2.0 Era Developer
 */
public class CustomerDemo {
    
    public static void main(String[] args) {
        System.out.println("=== EJB 2.0 Era JDBC CRUD Demo ===\n");
        
        // Create manager instance
        CustomerManager manager = new CustomerManager();
        
        try {
            // Demo 1: Add customers using Map-based API (generic interface)
            System.out.println("1. Adding customers using generic Map-based API...");
            
            Map customer1Data = new HashMap();
            customer1Data.put("name", "John Doe");
            customer1Data.put("email", "john.doe@example.com");
            customer1Data.put("phone", "555-0100");
            
            Object result1 = manager.addRecord(customer1Data);
            Customer customer1 = (Customer) result1; // Requires casting!
            System.out.println("   Added: " + customer1);
            
            Map customer2Data = new HashMap();
            customer2Data.put("name", "Jane Smith");
            customer2Data.put("email", "jane.smith@example.com");
            customer2Data.put("phone", "555-0101");
            
            Object result2 = manager.addRecord(customer2Data);
            Customer customer2 = (Customer) result2;
            System.out.println("   Added: " + customer2);
            
            Map customer3Data = new HashMap();
            customer3Data.put("name", "Bob Johnson");
            customer3Data.put("email", "bob.johnson@example.com");
            customer3Data.put("phone", "555-0102");
            
            Object result3 = manager.addRecord(customer3Data);
            Customer customer3 = (Customer) result3;
            System.out.println("   Added: " + customer3);
            
            // Demo 2: List all customers using generic interface
            System.out.println("\n2. Listing all customers using generic listAll()...");
            List customers = manager.listAll(); // Returns untyped List
            System.out.println("   Total customers: " + customers.size());
            for (int i = 0; i < customers.size(); i++) {
                Customer c = (Customer) customers.get(i); // Requires casting!
                System.out.println("   - " + c);
            }
            
            // Demo 3: Get customer by ID using generic interface
            System.out.println("\n3. Getting customer by ID using generic getRecord()...");
            Object customerObj = manager.getRecord(customer1.getId());
            Customer retrieved = (Customer) customerObj; // Requires casting!
            System.out.println("   Retrieved: " + retrieved);
            
            // Demo 4: Update customer using Map-based API
            System.out.println("\n4. Updating customer using generic updateRecord()...");
            Map updateData = new HashMap();
            updateData.put("phone", "555-9999");
            updateData.put("email", "john.doe.updated@example.com");
            
            boolean updated = manager.updateRecord(customer1.getId(), updateData);
            System.out.println("   Update successful: " + updated);
            
            // Retrieve updated customer
            Object updatedObj = manager.getRecord(customer1.getId());
            Customer updatedCustomer = (Customer) updatedObj;
            System.out.println("   Updated customer: " + updatedCustomer);
            
            // Demo 5: Count customers
            System.out.println("\n5. Counting customers using generic countRecords()...");
            int count = manager.countRecords();
            System.out.println("   Total count: " + count);
            
            // Demo 6: Delete customer
            System.out.println("\n6. Deleting customer using generic deleteRecord()...");
            boolean deleted = manager.deleteRecord(customer3.getId());
            System.out.println("   Delete successful: " + deleted);
            
            // List customers after delete
            List remainingCustomers = manager.listAll();
            System.out.println("   Remaining customers: " + remainingCustomers.size());
            for (int i = 0; i < remainingCustomers.size(); i++) {
                Customer c = (Customer) remainingCustomers.get(i);
                System.out.println("   - " + c);
            }
            
            // Demo 7: Demonstrate typed convenience methods
            System.out.println("\n7. Using typed convenience methods...");
            List typedCustomers = manager.getAllCustomers();
            System.out.println("   Total customers (typed method): " + typedCustomers.size());
            
            Customer typedCustomer = manager.getCustomerById(customer1.getId());
            System.out.println("   Retrieved by typed method: " + typedCustomer);
            
            System.out.println("\n=== Demo Complete ===");
            System.out.println("\nKey Observations:");
            System.out.println("- All generic methods return Object/List requiring casting");
            System.out.println("- Map-based API uses string keys (error-prone)");
            System.out.println("- No type safety at compile time");
            System.out.println("- Typical patterns from EJB 2.0 / early Java EE era (2000-2010)");
            System.out.println("\nREST API available at: /rest/jdbc/customers");
            System.out.println("SOAP API available at: CustomerSoapService");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
