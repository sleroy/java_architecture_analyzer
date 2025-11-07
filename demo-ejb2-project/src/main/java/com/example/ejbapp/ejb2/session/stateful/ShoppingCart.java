package com.example.ejbapp.ejb2.session.stateful;

import javax.ejb.EJBObject;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote interface for ShoppingCart Stateful Session Bean
 * 
 * @ejb.interface remote-class="com.example.ejbapp.ejb2.session.stateful.ShoppingCart"
 */
public interface ShoppingCart extends EJBObject {
    
    /**
     * Add item to cart
     * @ejb.interface-method
     */
    void addItem(Integer productId, String productName, 
                int quantity, double unitPrice) throws RemoteException;
    
    /**
     * Remove item from cart
     * @ejb.interface-method
     */
    void removeItem(Integer productId) throws RemoteException;
    
    /**
     * Update item quantity
     * @ejb.interface-method
     */
    void updateQuantity(Integer productId, int newQuantity) throws RemoteException;
    
    /**
     * Get all cart items
     * @ejb.interface-method
     */
    List getCartItems() throws RemoteException;
    
    /**
     * Get total amount
     * @ejb.interface-method
     */
    double getTotalAmount() throws RemoteException;
    
    /**
     * Get item count
     * @ejb.interface-method
     */
    int getItemCount() throws RemoteException;
    
    /**
     * Check if cart is empty
     * @ejb.interface-method
     */
    boolean isEmpty() throws RemoteException;
    
    /**
     * Clear cart
     * @ejb.interface-method
     */
    void clearCart() throws RemoteException;
    
    /**
     * Checkout and process order
     * @ejb.interface-method
     */
    String checkout() throws RemoteException;
    
    /**
     * Get customer ID
     * @ejb.interface-method
     */
    String getCustomerId() throws RemoteException;
    
    /**
     * Check if checked out
     * @ejb.interface-method
     */
    boolean isCheckedOut() throws RemoteException;
}
