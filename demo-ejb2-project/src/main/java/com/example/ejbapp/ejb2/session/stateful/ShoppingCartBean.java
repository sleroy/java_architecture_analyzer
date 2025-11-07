package com.example.ejbapp.ejb2.session.stateful;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Stateful Session Bean for Shopping Cart
 * Demonstrates legacy EJB 2.0 stateful session bean pattern
 * 
 * This bean maintains conversational state between method calls
 * 
 * @ejb.bean name="ShoppingCart"
 *           type="Stateful"
 *           jndi-name="ejb/ShoppingCart"
 *           
 * @ejb.transaction type="Required"
 */
public class ShoppingCartBean implements SessionBean {
    
    private SessionContext context;
    
    // Stateful session bean maintains client-specific state
    private String customerId;
    private Map cartItems; // Map<Integer, CartItem>
    private double totalAmount;
    private boolean checkedOut;
    
    /**
     * Shopping cart item - inner class (antipattern: should be separate)
     */
    public static class CartItem implements Serializable {
        private Integer productId;
        private String productName;
        private int quantity;
        private double unitPrice;
        private double subtotal;
        
        public CartItem(Integer productId, String productName, 
                       int quantity, double unitPrice) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.subtotal = quantity * unitPrice;
        }
        
        public Integer getProductId() {
            return productId;
        }
        
        public String getProductName() {
            return productName;
        }
        
        public int getQuantity() {
            return quantity;
        }
        
        public void setQuantity(int quantity) {
            this.quantity = quantity;
            this.subtotal = quantity * unitPrice;
        }
        
        public double getUnitPrice() {
            return unitPrice;
        }
        
        public double getSubtotal() {
            return subtotal;
        }
    }
    
    /**
     * ejbCreate - Initialize shopping cart for customer
     */
    public void ejbCreate(String customerId) throws CreateException {
        this.customerId = customerId;
        this.cartItems = new HashMap();
        this.totalAmount = 0.0;
        this.checkedOut = false;
        
        logInfo("Shopping cart created for customer: " + customerId);
    }
    
    /**
     * Add item to cart - Business method
     * @ejb.interface-method
     */
    public void addItem(Integer productId, String productName, 
                       int quantity, double unitPrice) {
        if (checkedOut) {
            throw new EJBException("Cannot modify cart after checkout");
        }
        
        CartItem existingItem = (CartItem) cartItems.get(productId);
        
        if (existingItem != null) {
            // Update quantity if item already exists
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
        } else {
            // Add new item
            CartItem newItem = new CartItem(productId, productName, 
                                           quantity, unitPrice);
            cartItems.put(productId, newItem);
        }
        
        recalculateTotal();
        logInfo("Added item to cart: " + productName + " x " + quantity);
    }
    
    /**
     * Remove item from cart
     * @ejb.interface-method
     */
    public void removeItem(Integer productId) {
        if (checkedOut) {
            throw new EJBException("Cannot modify cart after checkout");
        }
        
        CartItem removed = (CartItem) cartItems.remove(productId);
        if (removed != null) {
            recalculateTotal();
            logInfo("Removed item from cart: " + removed.getProductName());
        }
    }
    
    /**
     * Update item quantity
     * @ejb.interface-method
     */
    public void updateQuantity(Integer productId, int newQuantity) {
        if (checkedOut) {
            throw new EJBException("Cannot modify cart after checkout");
        }
        
        if (newQuantity <= 0) {
            removeItem(productId);
            return;
        }
        
        CartItem item = (CartItem) cartItems.get(productId);
        if (item != null) {
            item.setQuantity(newQuantity);
            recalculateTotal();
            logInfo("Updated quantity for: " + item.getProductName() + 
                   " to " + newQuantity);
        }
    }
    
    /**
     * Get all cart items
     * @ejb.interface-method
     */
    public List getCartItems() {
        return new ArrayList(cartItems.values());
    }
    
    /**
     * Get total amount
     * @ejb.interface-method
     */
    public double getTotalAmount() {
        return totalAmount;
    }
    
    /**
     * Get item count
     * @ejb.interface-method
     */
    public int getItemCount() {
        return cartItems.size();
    }
    
    /**
     * Check if cart is empty
     * @ejb.interface-method
     */
    public boolean isEmpty() {
        return cartItems.isEmpty();
    }
    
    /**
     * Clear cart
     * @ejb.interface-method
     */
    public void clearCart() {
        if (checkedOut) {
            throw new EJBException("Cannot clear cart after checkout");
        }
        
        cartItems.clear();
        totalAmount = 0.0;
        logInfo("Cart cleared for customer: " + customerId);
    }
    
    /**
     * Checkout - Process order
     * @ejb.interface-method
     */
    public String checkout() {
        if (checkedOut) {
            throw new EJBException("Cart already checked out");
        }
        
        if (cartItems.isEmpty()) {
            throw new EJBException("Cannot checkout empty cart");
        }
        
        // Legacy pattern: Direct business logic in session bean (antipattern)
        // Should delegate to separate service layer
        
        try {
            // Simulate order processing
            String orderId = generateOrderId();
            
            // In real implementation, would:
            // 1. Create order record
            // 2. Update inventory
            // 3. Process payment
            // 4. Send confirmation
            
            checkedOut = true;
            logInfo("Checkout completed for customer: " + customerId + 
                   ", Order ID: " + orderId);
            
            return orderId;
            
        } catch (Exception e) {
            throw new EJBException("Checkout failed: " + e.getMessage());
        }
    }
    
    /**
     * Get customer ID
     * @ejb.interface-method
     */
    public String getCustomerId() {
        return customerId;
    }
    
    /**
     * Check if checked out
     * @ejb.interface-method
     */
    public boolean isCheckedOut() {
        return checkedOut;
    }
    
    // Private helper methods
    
    /**
     * Recalculate cart total
     */
    private void recalculateTotal() {
        totalAmount = 0.0;
        Iterator iter = cartItems.values().iterator();
        while (iter.hasNext()) {
            CartItem item = (CartItem) iter.next();
            totalAmount += item.getSubtotal();
        }
    }
    
    /**
     * Generate order ID - Antipattern: Business logic in bean
     */
    private String generateOrderId() {
        // Legacy approach using timestamp
        long timestamp = System.currentTimeMillis();
        return "ORD-" + customerId + "-" + timestamp;
    }
    
    /**
     * Logging helper - Antipattern: Direct System.out usage
     */
    private void logInfo(String message) {
        System.out.println("[ShoppingCart] " + message);
    }
    
    // SessionBean lifecycle methods
    
    public void ejbActivate() {
        // Called when bean is activated from passivation
        logInfo("Shopping cart activated for customer: " + customerId);
    }
    
    public void ejbPassivate() {
        // Called before bean is passivated
        logInfo("Shopping cart passivated for customer: " + customerId);
    }
    
    public void ejbRemove() {
        // Called before bean is removed
        logInfo("Shopping cart removed for customer: " + customerId);
        
        // Cleanup resources
        if (cartItems != null) {
            cartItems.clear();
            cartItems = null;
        }
    }
    
    public void setSessionContext(SessionContext context) {
        this.context = context;
    }
}
