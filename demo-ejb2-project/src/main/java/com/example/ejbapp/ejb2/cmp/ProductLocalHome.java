package com.example.ejbapp.ejb2.cmp;

import javax.ejb.CreateException;
import javax.ejb.EJBLocalHome;
import javax.ejb.FinderException;
import java.math.BigDecimal;
import java.util.Collection;

/**
 * EJB 2.0 CMP Entity Bean - Local Home Interface
 * 
 * This is the local home interface for the Product CMP Entity Bean.
 * Provides factory methods (create) and finder methods for local access.
 * Local home interfaces don't throw RemoteException.
 * 
 * Note: This is legacy EJB 2.0 technology. Modern applications should use JPA.
 */
public interface ProductLocalHome extends EJBLocalHome {
    
    /**
     * Create a new product (local version)
     * 
     * @param productId Unique product identifier
     * @param productName Name of the product
     * @param description Product description
     * @param price Product price
     * @param quantityInStock Initial quantity in stock
     * @param category Product category
     * @return ProductLocal interface
     * @throws CreateException if creation fails
     */
    ProductLocal create(String productId, String productName, String description,
                       BigDecimal price, Integer quantityInStock, String category)
            throws CreateException;
    
    /**
     * Find product by primary key (productId)
     * 
     * @param productId The product ID to find
     * @return ProductLocal interface
     * @throws FinderException if product not found
     */
    ProductLocal findByPrimaryKey(String productId)
            throws FinderException;
    
    /**
     * Find all products
     * 
     * @return Collection of ProductLocal interfaces
     * @throws FinderException if query fails
     */
    Collection findAll()
            throws FinderException;
    
    /**
     * Find products by category
     * 
     * @param category The category to search for
     * @return Collection of ProductLocal interfaces
     * @throws FinderException if query fails
     */
    Collection findByCategory(String category)
            throws FinderException;
    
    /**
     * Find products with price less than specified amount
     * 
     * @param maxPrice Maximum price
     * @return Collection of ProductLocal interfaces
     * @throws FinderException if query fails
     */
    Collection findByPriceLessThan(BigDecimal maxPrice)
            throws FinderException;
    
    /**
     * Find products in stock
     * 
     * @return Collection of ProductLocal interfaces
     * @throws FinderException if query fails
     */
    Collection findInStock()
            throws FinderException;
}
