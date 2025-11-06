package br.com.semeru.ejb2.cmp;

import javax.ejb.CreateException;
import javax.ejb.EJBHome;
import javax.ejb.FinderException;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.util.Collection;

/**
 * EJB 2.0 CMP Entity Bean - Home Interface
 * 
 * This is the home interface for the Product CMP Entity Bean.
 * It provides factory methods (create) and finder methods for the entity bean.
 * 
 * Note: This is legacy EJB 2.0 technology. Modern applications should use JPA.
 */
public interface ProductHome extends EJBHome {
    
    /**
     * Create a new product
     * 
     * @param productId Unique product identifier
     * @param productName Name of the product
     * @param description Product description
     * @param price Product price
     * @param quantityInStock Initial quantity in stock
     * @param category Product category
     * @return Product remote interface
     * @throws CreateException if creation fails
     * @throws RemoteException if remote communication fails
     */
    Product create(String productId, String productName, String description,
                   BigDecimal price, Integer quantityInStock, String category)
            throws CreateException, RemoteException;
    
    /**
     * Find product by primary key (productId)
     * 
     * @param productId The product ID to find
     * @return Product remote interface
     * @throws FinderException if product not found
     * @throws RemoteException if remote communication fails
     */
    Product findByPrimaryKey(String productId)
            throws FinderException, RemoteException;
    
    /**
     * Find all products
     * 
     * @return Collection of Product remote interfaces
     * @throws FinderException if query fails
     * @throws RemoteException if remote communication fails
     */
    Collection<Product> findAll()
            throws FinderException, RemoteException;
    
    /**
     * Find products by category
     * 
     * @param category The category to search for
     * @return Collection of Product remote interfaces
     * @throws FinderException if query fails
     * @throws RemoteException if remote communication fails
     */
    Collection<Product> findByCategory(String category)
            throws FinderException, RemoteException;
    
    /**
     * Find products with price less than specified amount
     * 
     * @param maxPrice Maximum price
     * @return Collection of Product remote interfaces
     * @throws FinderException if query fails
     * @throws RemoteException if remote communication fails
     */
    Collection<Product> findByPriceLessThan(BigDecimal maxPrice)
            throws FinderException, RemoteException;
    
    /**
     * Find products in stock
     * 
     * @return Collection of Product remote interfaces
     * @throws FinderException if query fails
     * @throws RemoteException if remote communication fails
     */
    Collection<Product> findInStock()
            throws FinderException, RemoteException;
}
