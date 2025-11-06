package br.com.semeru.ejb2.cmp;

import javax.ejb.EJBLocalObject;
import java.math.BigDecimal;

/**
 * EJB 2.0 CMP Entity Bean - Local Interface
 * 
 * This is the local interface for the Product CMP Entity Bean.
 * Used for local access within the same JVM (no remote exceptions).
 * Local interfaces are more efficient than remote interfaces.
 * 
 * Note: This is legacy EJB 2.0 technology. Modern applications should use JPA.
 */
public interface ProductLocal extends EJBLocalObject {
    
    /**
     * Get product ID
     */
    String getProductId();
    
    /**
     * Get product name
     */
    String getProductName();
    
    /**
     * Set product name
     */
    void setProductName(String name);
    
    /**
     * Get product description
     */
    String getDescription();
    
    /**
     * Set product description
     */
    void setDescription(String description);
    
    /**
     * Get product price
     */
    BigDecimal getPrice();
    
    /**
     * Set product price
     */
    void setPrice(BigDecimal price);
    
    /**
     * Get quantity in stock
     */
    Integer getQuantityInStock();
    
    /**
     * Set quantity in stock
     */
    void setQuantityInStock(Integer quantity);
    
    /**
     * Get category
     */
    String getCategory();
    
    /**
     * Set category
     */
    void setCategory(String category);
    
    /**
     * Business method: Check if product is in stock
     */
    boolean isInStock();
    
    /**
     * Business method: Update stock quantity
     */
    void updateStock(int quantity);
}
