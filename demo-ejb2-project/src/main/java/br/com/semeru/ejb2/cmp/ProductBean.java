package br.com.semeru.ejb2.cmp;

import javax.ejb.CreateException;
import javax.ejb.EntityBean;
import javax.ejb.EntityContext;
import java.math.BigDecimal;

/**
 * EJB 2.0 CMP Entity Bean - Bean Class
 * 
 * This is the bean implementation for the Product CMP Entity Bean.
 * The container manages the persistence of CMP fields automatically.
 * 
 * CMP Fields (Container-Managed Persistence):
 * - productId (Primary Key)
 * - productName
 * - description
 * - price
 * - quantityInStock
 * - category
 * 
 * Note: This is legacy EJB 2.0 technology. Modern applications should use JPA.
 * 
 * Deployment descriptor (ejb-jar.xml) must define:
 * - CMP fields
 * - Primary key field
 * - Finder queries (EJB-QL)
 */
public abstract class ProductBean implements EntityBean {
    
    private EntityContext context;
    
    // ==================== CMP Field Accessors (Abstract) ====================
    // These methods are implemented by the container at deployment time
    
    /**
     * Get product ID (Primary Key)
     */
    public abstract String getProductId();
    
    /**
     * Set product ID (Primary Key)
     */
    public abstract void setProductId(String productId);
    
    /**
     * Get product name
     */
    public abstract String getProductName();
    
    /**
     * Set product name
     */
    public abstract void setProductName(String productName);
    
    /**
     * Get description
     */
    public abstract String getDescription();
    
    /**
     * Set description
     */
    public abstract void setDescription(String description);
    
    /**
     * Get price
     */
    public abstract BigDecimal getPrice();
    
    /**
     * Set price
     */
    public abstract void setPrice(BigDecimal price);
    
    /**
     * Get quantity in stock
     */
    public abstract Integer getQuantityInStock();
    
    /**
     * Set quantity in stock
     */
    public abstract void setQuantityInStock(Integer quantity);
    
    /**
     * Get category
     */
    public abstract String getCategory();
    
    /**
     * Set category
     */
    public abstract void setCategory(String category);
    
    // ==================== Business Methods ====================
    
    /**
     * Check if product is in stock
     */
    public boolean isInStock() {
        Integer quantity = getQuantityInStock();
        return quantity != null && quantity > 0;
    }
    
    /**
     * Update stock quantity
     * This method modifies the CMP field, which will be automatically persisted
     */
    public void updateStock(int quantity) {
        Integer currentStock = getQuantityInStock();
        if (currentStock == null) {
            currentStock = 0;
        }
        setQuantityInStock(currentStock + quantity);
    }
    
    // ==================== EJB Create Methods ====================
    
    /**
     * ejbCreate method corresponding to create() in home interface
     * Initializes CMP fields
     */
    public String ejbCreate(String productId, String productName, String description,
                           BigDecimal price, Integer quantityInStock, String category)
            throws CreateException {
        
        // Validation
        if (productId == null || productId.trim().isEmpty()) {
            throw new CreateException("Product ID is required");
        }
        if (productName == null || productName.trim().isEmpty()) {
            throw new CreateException("Product name is required");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new CreateException("Price must be a positive value");
        }
        
        // Set CMP fields
        setProductId(productId);
        setProductName(productName);
        setDescription(description);
        setPrice(price);
        setQuantityInStock(quantityInStock != null ? quantityInStock : 0);
        setCategory(category);
        
        // Return primary key
        return productId;
    }
    
    /**
     * ejbPostCreate method - called after ejbCreate
     * Used for operations that require the bean's identity
     */
    public void ejbPostCreate(String productId, String productName, String description,
                             BigDecimal price, Integer quantityInStock, String category) {
        // Post-creation operations if needed
        // Can be used to establish relationships with other beans
    }
    
    // ==================== EntityBean Lifecycle Methods ====================
    
    /**
     * Set the entity context
     */
    public void setEntityContext(EntityContext context) {
        this.context = context;
    }
    
    /**
     * Unset the entity context
     */
    public void unsetEntityContext() {
        this.context = null;
    }
    
    /**
     * Remove the entity bean
     */
    public void ejbRemove() {
        // Cleanup operations before removal
        // The container will handle the actual database deletion
    }
    
    /**
     * Activate the bean instance
     * Called when the bean is retrieved from the pool
     */
    public void ejbActivate() {
        // Acquire resources if needed
    }
    
    /**
     * Passivate the bean instance
     * Called before the bean is returned to the pool
     */
    public void ejbPassivate() {
        // Release resources if needed
    }
    
    /**
     * Load the bean state from the database
     * Called by the container before business methods
     */
    public void ejbLoad() {
        // The container automatically loads CMP fields
        // This method can be used for derived attributes or caching
    }
    
    /**
     * Store the bean state to the database
     * Called by the container after business methods
     */
    public void ejbStore() {
        // The container automatically stores CMP fields
        // This method can be used to update derived attributes
    }
}
