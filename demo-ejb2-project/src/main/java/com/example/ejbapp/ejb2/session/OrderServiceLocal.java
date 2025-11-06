package com.example.ejbapp.ejb2.session;

import javax.ejb.EJBLocalObject;
import java.util.List;

/**
 * EJB 2.0 Stateless Session Bean - Local Interface
 * This is the local interface for the OrderService Stateless Session Bean.
 * Used for local access within the same JVM (no remote exceptions).
 * Local interfaces are more efficient than remote interfaces.
 */
public interface OrderServiceLocal extends EJBLocalObject {

    /**
     * Places a new order.
     * @param customerId The ID of the customer placing the order.
     * @param productId The ID of the product being ordered.
     * @param quantity The quantity of the product.
     * @return The ID of the newly placed order.
     */
    String placeOrder(String customerId, String productId, int quantity);

    /**
     * Gets the details of a specific order.
     * @param orderId The ID of the order to retrieve.
     * @return A string representation of the order details.
     */
    String getOrderDetails(String orderId);

    /**
     * Lists all orders for a given customer.
     * @param customerId The ID of the customer.
     * @return A list of order IDs.
     */
    List<String> listOrdersByCustomer(String customerId);

    /**
     * Updates the status of an existing order.
     * @param orderId The ID of the order to update.
     * @param newStatus The new status for the order.
     * @return True if the update was successful, false otherwise.
     */
    boolean updateOrderStatus(String orderId, String newStatus);

    /**
     * Cancels an existing order.
     * @param orderId The ID of the order to cancel.
     * @return True if the cancellation was successful, false otherwise.
     */
    boolean cancelOrder(String orderId);
}
