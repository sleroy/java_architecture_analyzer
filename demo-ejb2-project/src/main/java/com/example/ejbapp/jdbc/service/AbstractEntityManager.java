package com.example.ejbapp.jdbc.service;

import java.util.List;
import java.util.Map;

/**
 * Abstract Entity Manager - Generic Base Class
 * 
 * This is a typical "generic CRUD" pattern from the EJB 2.0 era.
 * Provides common methods that work with Maps instead of typed objects.
 * 
 * Anti-patterns demonstrated:
 * - Loss of type safety (everything is Object/Map)
 * - One-size-fits-all approach
 * - Requires casting everywhere
 * - No generics (or primitive generics usage)
 * - Forces all entities to follow same pattern
 * 
 * This pattern was very popular in the 2000s before modern frameworks
 * and was seen as a way to reduce code duplication. In reality, it often
 * caused more problems than it solved.
 * 
 * @author EJB 2.0 Era Developer
 */
public abstract class AbstractEntityManager {
    
    /**
     * List all records
     * Returns untyped List requiring casting
     * 
     * @return List of entity objects (requires casting)
     * @throws Exception if operation fails
     */
    public abstract List listAll() throws Exception;
    
    /**
     * Add a new record using a Map of field values
     * Typical pattern: lose type safety for "flexibility"
     * 
     * @param data Map containing field names and values
     * @return the created entity (as Object, requires casting)
     * @throws Exception if operation fails
     */
    public abstract Object addRecord(Map data) throws Exception;
    
    /**
     * Get a single record by ID
     * 
     * @param id the record ID
     * @return the entity (as Object, requires casting)
     * @throws Exception if operation fails
     */
    public abstract Object getRecord(int id) throws Exception;
    
    /**
     * Update a record using a Map of field values
     * 
     * @param id the record ID
     * @param data Map containing field names and values
     * @return true if updated successfully
     * @throws Exception if operation fails
     */
    public abstract boolean updateRecord(int id, Map data) throws Exception;
    
    /**
     * Delete a record by ID
     * 
     * @param id the record ID
     * @return true if deleted successfully
     * @throws Exception if operation fails
     */
    public abstract boolean deleteRecord(int id) throws Exception;
    
    /**
     * Count all records
     * 
     * @return number of records
     * @throws Exception if operation fails
     */
    public abstract int countRecords() throws Exception;
}
