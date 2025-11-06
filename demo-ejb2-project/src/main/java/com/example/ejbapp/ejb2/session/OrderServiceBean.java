package com.example.ejbapp.ejb2.session;

import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * EJB 2.0 Stateless Session Bean - Bean Class
 * This is the bean implementation for the OrderService Stateless Session Bean.
 * It provides business logic for order management.
 */
public class OrderServiceBean implements SessionBean {

    private SessionContext sessionContext;
    private static final Logger logger = Logger.getLogger(OrderServiceBean.class.getName());

    // In a real application, this would interact with a database
    private static final Map<String, Order> orders = new HashMap<>();

    /**
     * EJB Create method.
     * Called by the container to create a new instance of the session bean.
     */
    public void ejbCreate() {
        logger.info("OrderServiceBean: ejbCreate() called.");
    }

    /**
     * Places a new order.
     * @param customerId The ID of the customer placing the order.
     * @param productId The ID of the product being ordered.
     * @param quantity The quantity of the product.
     * @return The ID of the newly placed order.
     */
    public String placeOrder(String customerId, String productId, int quantity) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Order newOrder = new Order(orderId, customerId, productId, quantity, "PENDING");
        orders.put(orderId, newOrder);
        logger.info("OrderServiceBean: Placed new order: " + newOrder);
        return orderId;
    }

    /**
     * Gets the details of a specific order.
     * @param orderId The ID of the order to retrieve.
     * @return A string representation of the order details.
     */
    public String getOrderDetails(String orderId) {
        Order order = orders.get(orderId);
        if (order != null) {
            logger.info("OrderServiceBean: Retrieved order details for " + orderId + ": " + order);
            return order.toString();
        }
 else {
            logger.warning("OrderServiceBean: Order " + orderId + " not found.");
            return "Order not found.";
        }
    }

    /**
     * Lists all orders for a given customer.
     * @param customerId The ID of the customer.
     * @return A list of order IDs.
     */
    public List<String> listOrdersByCustomer(String customerId) {
        List<String> customerOrderIds = new ArrayList<>();
        for (Order order : orders.values()) {
            if (order.getCustomerId().equals(customerId)) {
                customerOrderIds.add(order.getOrderId());
            }
        }
        logger.info("OrderServiceBean: Listed " + customerOrderIds.size() + " orders for customer " + customerId);
        return customerOrderIds;
    }

    /**
     * Updates the status of an existing order.
     * @param orderId The ID of the order to update.
     * @param newStatus The new status for the order.
     * @return True if the update was successful, false otherwise.
     */
    public boolean updateOrderStatus(String orderId, String newStatus) {
        Order order = orders.get(orderId);
        if (order != null) {
            String oldStatus = order.getStatus();
            order.setStatus(newStatus);
            logger.info("OrderServiceBean: Updated order " + orderId + " status from " + oldStatus + " to " + newStatus);
            return true;
        }
 else {
            logger.warning("OrderServiceBean: Failed to update status for order " + orderId + ": not found.");
            return false;
        }
    }

    /**
     * Cancels an existing order.
     * @param orderId The ID of the order to cancel.
     * @return True if the cancellation was successful, false otherwise.
     */
    public boolean cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order != null) {
            if (!"CANCELLED".equals(order.getStatus())) {
                order.setStatus("CANCELLED");
                logger.info("OrderServiceBean: Cancelled order " + orderId);
                return true;
            }
 else {
                logger.info("OrderServiceBean: Order " + orderId + " was already cancelled.");
                return false;
            }
        }
 else {
            logger.warning("OrderServiceBean: Failed to cancel order " + orderId + ": not found.");
            return false;
        }
    }

    // ==================== SessionBean Lifecycle Methods ====================

    @Override
    public void setSessionContext(SessionContext sessionContext) {
        this.sessionContext = sessionContext;
        logger.info("OrderServiceBean: setSessionContext() called.");
    }

    @Override
    public void ejbActivate() {
        logger.info("OrderServiceBean: ejbActivate() called.");
    }

    @Override
    public void ejbPassivate() {
        logger.info("OrderServiceBean: ejbPassivate() called.");
    }

    @Override
    public void ejbRemove() {
        logger.info("OrderServiceBean: ejbRemove() called.");
    }

    /**
     * Inner class to represent an Order (for demonstration purposes).
     * In a real application, this would likely be a JPA Entity or a DTO.
     */
    private static class Order {
        private String orderId;
        private String customerId;
        private String productId;
        private int quantity;
        private String status;

        public Order(String orderId, String customerId, String productId, int quantity, String status) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.productId = productId;
            this.quantity = quantity;
            this.status = status;
        }

        public String getOrderId() {
            return orderId;
        }

        public String getCustomerId() {
            return customerId;
        }

        public String getProductId() {
            return productId;
        }

        public int getQuantity() {
            return quantity;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return "Order{"
                    + "orderId='" + orderId + '\''
                    + ", customerId='" + customerId + '\''
                    + ", productId='" + productId + '\''
                    + ", quantity=" + quantity
                    + ", status='" + status + '\''
                    + '}';
        }
    }
}
