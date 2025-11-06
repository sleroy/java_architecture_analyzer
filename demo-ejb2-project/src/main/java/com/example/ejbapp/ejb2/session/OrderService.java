package com.example.ejbapp.ejb2.session;

import javax.ejb.EJBObject;
import java.rmi.RemoteException;
import java.util.List;

/**
 * EJB 2.0 Stateless Session Bean - Remote Interface
 * This is the remote interface for the OrderService Stateless Session Bean.
 * Clients use this interface to interact with the session bean remotely.
 *
 * It provides business methods for order management.
 */
public interface OrderService extends EJBObject {

    /**
     * Places a new order.
     * @param customerId The ID of the customer placing the order.
     * @param productId The ID of the product being ordered.
     * @param quantity The quantity of the product.
     * @return The ID of the newly placed order.
     * @throws RemoteException
     */
    String placeOrder(String customerId, String productId, int quantity) throws RemoteException;

    /**
     * Gets the details of a specific order.
     * @param orderId The ID of the order to retrieve.
     * @return A string representation of the order details.
     * @throws RemoteException
     */
    String getOrderDetails(String orderId) throws RemoteException;

    /**
     * Lists all orders for a given customer.
     * @param customerId The ID of the customer.
     * @return A list of order IDs.
     * @throws RemoteException
     */
    List<String> listOrdersByCustomer(String customerId) throws RemoteException;

    /**
     * Updates the status of an existing order.
     * @param orderId The ID of the order to update.
     * @param newStatus The new status for the order.
     * @return True if the update was successful, false otherwise.
     * @throws RemoteException
     */
    boolean updateOrderStatus(String orderId, String newStatus) throws RemoteException;

    /**
     * Cancels an existing order.
     * @param orderId The ID of the order to cancel.
     * @return True if the cancellation was successful, false otherwise.
     * @throws RemoteException
     */
    boolean cancelOrder(String orderId) throws RemoteException;
}
