package com.example.ejbapp.jdbc.rest;

import com.example.ejbapp.jdbc.model.Customer;
import com.example.ejbapp.jdbc.service.CustomerManager;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer REST Service - JAX-RS
 * 
 * REST endpoint that uses the generic AbstractEntityManager interface.
 * Demonstrates how the Map-based API was exposed to web services.
 * 
 * Typical EJB 2.0 era patterns:
 * - Direct instantiation of service (no DI)
 * - Map-based request/response
 * - Manual JSON-like structure with Maps
 * - Poor error handling
 * 
 * @author EJB 2.0 Era Developer
 */
@Path("/jdbc/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CustomerRestService {
    
    // Direct instantiation - no dependency injection
    private CustomerManager manager = new CustomerManager();
    
    /**
     * Get all customers
     * Uses the generic listAll() method
     * 
     * @return Response with list of customers
     */
    @GET
    public Response getAllCustomers() {
        try {
            // Call generic listAll() method
            List customers = manager.listAll();
            
            // Return as-is (untyped List)
            return Response.ok(customers).build();
            
        } catch (Exception e) {
            return Response.serverError()
                    .entity(createErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Get customer by ID
     * Uses the generic getRecord() method
     * 
     * @param id customer ID
     * @return Response with customer data
     */
    @GET
    @Path("/{id}")
    public Response getCustomer(@PathParam("id") int id) {
        try {
            // Call generic getRecord() method (returns Object)
            Object customer = manager.getRecord(id);
            
            if (customer == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(createErrorResponse("Customer not found"))
                        .build();
            }
            
            return Response.ok(customer).build();
            
        } catch (Exception e) {
            return Response.serverError()
                    .entity(createErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Add a new customer
     * Uses the generic addRecord() method with Map
     * 
     * Expects JSON like:
     * {
     *   "name": "John Doe",
     *   "email": "john@example.com",
     *   "phone": "555-0100"
     * }
     * 
     * @param data Map containing customer data
     * @return Response with created customer
     */
    @POST
    public Response addCustomer(Map data) {
        try {
            // Call generic addRecord() method
            Object customer = manager.addRecord(data);
            
            return Response.status(Response.Status.CREATED)
                    .entity(customer)
                    .build();
            
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(createErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Update a customer
     * Uses the generic updateRecord() method with Map
     * 
     * @param id customer ID
     * @param data Map containing customer data to update
     * @return Response with status
     */
    @PUT
    @Path("/{id}")
    public Response updateCustomer(@PathParam("id") int id, Map data) {
        try {
            // Call generic updateRecord() method
            boolean updated = manager.updateRecord(id, data);
            
            if (!updated) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(createErrorResponse("Customer not found"))
                        .build();
            }
            
            // Return updated customer
            Object customer = manager.getRecord(id);
            return Response.ok(customer).build();
            
        } catch (Exception e) {
            return Response.serverError()
                    .entity(createErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Delete a customer
     * Uses the generic deleteRecord() method
     * 
     * @param id customer ID
     * @return Response with status
     */
    @DELETE
    @Path("/{id}")
    public Response deleteCustomer(@PathParam("id") int id) {
        try {
            // Call generic deleteRecord() method
            boolean deleted = manager.deleteRecord(id);
            
            if (!deleted) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(createErrorResponse("Customer not found"))
                        .build();
            }
            
            Map response = new HashMap();
            response.put("message", "Customer deleted successfully");
            return Response.ok(response).build();
            
        } catch (Exception e) {
            return Response.serverError()
                    .entity(createErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Count customers
     * Uses the generic countRecords() method
     * 
     * @return Response with count
     */
    @GET
    @Path("/count")
    public Response countCustomers() {
        try {
            int count = manager.countRecords();
            
            Map response = new HashMap();
            response.put("count", count);
            return Response.ok(response).build();
            
        } catch (Exception e) {
            return Response.serverError()
                    .entity(createErrorResponse(e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Create error response as Map
     * Typical pattern from EJB 2.0 era
     */
    private Map createErrorResponse(String message) {
        Map error = new HashMap();
        error.put("error", true);
        error.put("message", message);
        return error;
    }
}
