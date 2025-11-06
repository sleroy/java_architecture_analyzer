package br.com.semeru.ejb2.mdb;

import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * EJB 2.0 Message-Driven Bean Example - Order Processor
 * 
 * This MDB processes order messages from a JMS queue asynchronously.
 * It demonstrates the EJB 2.0 MDB pattern for asynchronous message processing.
 * 
 * Configuration in ejb-jar.xml:
 * - Destination type: Queue
 * - Transaction type: Container-managed
 * - Acknowledge mode: Auto-acknowledge
 * 
 * Note: This is legacy EJB 2.0 technology. Modern applications should use:
 * - Spring JMS with @JmsListener
 * - Spring Boot with @EnableJms
 * - Or message-driven POJOs with Spring Integration
 */
public class OrderProcessorMDB implements MessageDrivenBean, MessageListener {
    
    private static final Logger logger = Logger.getLogger(OrderProcessorMDB.class.getName());
    
    private MessageDrivenContext mdbContext;
    
    /**
     * Called by the container when a message arrives.
     * This is the main processing method.
     * 
     * @param message The JMS message to process
     */
    @Override
    public void onMessage(Message message) {
        logger.info("OrderProcessorMDB: Received message");
        
        try {
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                String orderData = textMessage.getText();
                
                logger.info("Processing order: " + orderData);
                
                // Parse order data (simplified example)
                processOrder(orderData);
                
                logger.info("Order processed successfully: " + orderData);
                
            } else {
                logger.warning("Received non-TextMessage: " + message.getClass().getName());
            }
            
        } catch (JMSException e) {
            logger.log(Level.SEVERE, "Error reading message", e);
            // Container will handle transaction rollback
            mdbContext.setRollbackOnly();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing order", e);
            // Container will handle transaction rollback
            mdbContext.setRollbackOnly();
        }
    }
    
    /**
     * Business logic to process the order
     * 
     * @param orderData Order data from the message
     */
    private void processOrder(String orderData) throws Exception {
        // Simulate order processing logic
        logger.info("Validating order...");
        
        // Parse order (simplified - in real app would parse JSON/XML)
        String[] parts = orderData.split(",");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid order format");
        }
        
        String orderId = parts[0].trim();
        String customerId = parts[1].trim();
        String productId = parts[2].trim();
        
        logger.info("Order ID: " + orderId);
        logger.info("Customer ID: " + customerId);
        logger.info("Product ID: " + productId);
        
        // In a real application, you would:
        // 1. Validate the order
        // 2. Check inventory
        // 3. Update database
        // 4. Send confirmation
        // 5. Possibly send to another queue for fulfillment
        
        // Simulate processing time
        Thread.sleep(100);
        
        logger.info("Order validation complete");
        
        // Here you might use JNDI to lookup and call other EJBs
        // For example, to update inventory or send notifications
    }
    
    // ==================== MessageDrivenBean Lifecycle Methods ====================
    
    /**
     * Set the message-driven context
     * Called by container before ejbCreate
     */
    @Override
    public void setMessageDrivenContext(MessageDrivenContext mdbContext) {
        logger.info("setMessageDrivenContext called");
        this.mdbContext = mdbContext;
    }
    
    /**
     * Create method called by container after setMessageDrivenContext
     * Initialize any resources here
     */
    public void ejbCreate() {
        logger.info("ejbCreate called - OrderProcessorMDB initialized");
        // Initialize resources if needed
    }
    
    /**
     * Remove method called by container before destroying the bean
     * Clean up resources here
     */
    @Override
    public void ejbRemove() {
        logger.info("ejbRemove called - OrderProcessorMDB destroyed");
        // Clean up resources if needed
    }
}
