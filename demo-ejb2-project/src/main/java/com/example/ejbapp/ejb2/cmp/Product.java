package com.example.ejbapp.ejb2.cmp;

import javax.ejb.EJBObject;
import java.rmi.RemoteException;
import java.math.BigDecimal;

/**
 * EJB 2.0 CMP Entity Bean - Remote Interface
 * 
 * This is the remote interface for the Product CMP Entity Bean.
 * Clients use this interface to interact with the entity bean remotely.
 * 
 * Note: This is legacy EJB 2.0 technology. Modern applications should use JPA.
 */
public interface Product extends EJBObject {
    
    /**
     * Get product ID
     */
    String getProductId() throws RemoteException;
    
    /**
     * Get product name
     */
    String getProductName() throws RemoteException;
    
    /**
     * Set product name
     */
    void setProductName(String name) throws RemoteException;
    
    /**
     * Get product description
     */
    String getDescription() throws RemoteException;
    
    /**
     * Set product description
     */
    void setDescription(String description) throws RemoteException;
    
    /**
     * Get product price
     */
    BigDecimal getPrice() throws RemoteException;
    
    /**
     * Set product price
     */
    void setPrice(BigDecimal price) throws RemoteException;
    
    /**
     * Get quantity in stock
     */
    Integer getQuantityInStock() throws RemoteException;
    
    /**
     * Set quantity in stock
     */
    void setQuantityInStock(Integer quantity) throws RemoteException;
    
    /**
     * Get category
     */
    String getCategory() throws RemoteException;
    
    /**
     * Set category
     */
    void setCategory(String category) throws RemoteException;
    
    /**
     * Business method: Check if product is in stock
     */
    boolean isInStock() throws RemoteException;
    
    /**
     * Business method: Update stock quantity
     */
    void updateStock(int quantity) throws RemoteException;
}
