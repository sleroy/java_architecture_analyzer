package com.example.ejbapp.ejb2.mdb;

import com.example.ejbapp.ejb2.mdb.NotificationMDB.NotificationData;
import com.example.ejbapp.ejb2.mdb.NotificationMDB.NotificationData.NotificationType;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Example JMS Message Sender
 * 
 * This class demonstrates how to send messages to JMS destinations
 * that will be processed by Message-Driven Beans.
 * 
 * It shows:
 * - Sending to a Queue (point-to-point)
 * - Sending to a Topic (publish-subscribe)
 * - Different message types (TextMessage, ObjectMessage)
 * 
 * Note: This is legacy JMS 1.1 code. Modern applications should use:
 * - Spring JmsTemplate
 * - Spring @JmsListener
 * - JMS 2.0 simplified API
 */
public class MessageSenderExample {
    
    private static final Logger logger = Logger.getLogger(MessageSenderExample.class.getName());
    
    // JNDI names - these should match your server configuration
    private static final String QUEUE_CONNECTION_FACTORY = "java:/ConnectionFactory";
    private static final String ORDER_QUEUE = "java:/jms/queue/OrderQueue";
    private static final String NOTIFICATION_TOPIC = "java:/jms/topic/NotificationTopic";
    
    /**
     * Send an order message to the order processing queue
     * 
     * @param orderId Order identifier
     * @param customerId Customer identifier
     * @param productId Product identifier
     */
    public void sendOrderMessage(String orderId, String customerId, String productId) {
        
        Connection connection = null;
        Session session = null;
        
        try {
            // Lookup JMS resources via JNDI
            Context ctx = new InitialContext();
            ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup(QUEUE_CONNECTION_FACTORY);
            Queue queue = (Queue) ctx.lookup(ORDER_QUEUE);
            
            // Create connection and session
            connection = connectionFactory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            
            // Create message producer
            MessageProducer producer = session.createProducer(queue);
            
            // Create text message
            String orderData = orderId + "," + customerId + "," + productId;
            TextMessage message = session.createTextMessage(orderData);
            
            // Set message properties (optional)
            message.setStringProperty("orderId", orderId);
            message.setStringProperty("customerId", customerId);
            message.setLongProperty("timestamp", System.currentTimeMillis());
            
            // Send message
            producer.send(message);
            
            logger.info("Order message sent successfully: " + orderData);
            
            // Clean up
            producer.close();
            
        } catch (NamingException e) {
            logger.log(Level.SEVERE, "JNDI lookup failed", e);
        } catch (JMSException e) {
            logger.log(Level.SEVERE, "JMS error sending message", e);
        } finally {
            // Close resources
            closeResources(session, connection);
        }
    }
    
    /**
     * Send a notification message to the notification topic
     * Multiple subscribers can receive this message
     * 
     * @param type Notification type
     * @param recipient Recipient address (email, phone, etc.)
     * @param subject Notification subject
     * @param message Notification message
     */
    public void sendNotificationMessage(NotificationType type, String recipient, 
                                       String subject, String message) {
        
        Connection connection = null;
        Session session = null;
        
        try {
            // Lookup JMS resources via JNDI
            Context ctx = new InitialContext();
            ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup(QUEUE_CONNECTION_FACTORY);
            Topic topic = (Topic) ctx.lookup(NOTIFICATION_TOPIC);
            
            // Create connection and session
            connection = connectionFactory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            
            // Create message producer for topic
            MessageProducer producer = session.createProducer(topic);
            
            // Create notification data object
            NotificationData notification = new NotificationData(type, recipient, subject, message);
            
            // Create object message
            ObjectMessage objectMessage = session.createObjectMessage(notification);
            
            // Set message properties
            objectMessage.setStringProperty("notificationType", type.name());
            objectMessage.setStringProperty("recipient", recipient);
            
            // Send message to topic (all subscribers will receive it)
            producer.send(objectMessage);
            
            logger.info("Notification message published to topic: " + notification);
            
            // Clean up
            producer.close();
            
        } catch (NamingException e) {
            logger.log(Level.SEVERE, "JNDI lookup failed", e);
        } catch (JMSException e) {
            logger.log(Level.SEVERE, "JMS error sending message", e);
        } finally {
            closeResources(session, connection);
        }
    }
    
    /**
     * Send multiple order messages for testing
     */
    public void sendBatchOrders(int count) {
        logger.info("Sending " + count + " order messages...");
        
        for (int i = 1; i <= count; i++) {
            String orderId = "ORD-" + String.format("%05d", i);
            String customerId = "CUST-" + (1000 + i);
            String productId = "PROD-" + (i % 10 + 1);
            
            sendOrderMessage(orderId, customerId, productId);
            
            // Small delay between messages
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        logger.info("Batch sending complete");
    }
    
    /**
     * Close JMS resources properly
     */
    private void closeResources(Session session, Connection connection) {
        try {
            if (session != null) {
                session.close();
            }
        } catch (JMSException e) {
            logger.log(Level.WARNING, "Error closing session", e);
        }
        
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (JMSException e) {
            logger.log(Level.WARNING, "Error closing connection", e);
        }
    }
    
    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        MessageSenderExample sender = new MessageSenderExample();
        
        System.out.println("========================================");
        System.out.println("JMS Message Sender Example");
        System.out.println("========================================\n");
        
        // Test 1: Send single order message
        System.out.println("Test 1: Sending single order message...");
        sender.sendOrderMessage("ORD-00001", "CUST-1001", "PROD-001");
        
        // Test 2: Send notification to topic
        System.out.println("\nTest 2: Sending email notification...");
        sender.sendNotificationMessage(
            NotificationType.EMAIL,
            "user@example.com",
            "Order Confirmation",
            "Your order has been received and is being processed."
        );
        
        // Test 3: Send multiple notifications
        System.out.println("\nTest 3: Sending multiple notifications...");
        sender.sendNotificationMessage(
            NotificationType.SMS,
            "+1234567890",
            "Alert",
            "Your order will arrive tomorrow"
        );
        
        sender.sendNotificationMessage(
            NotificationType.PUSH,
            "user-device-token",
            "Promotion",
            "Special offer: 20% off your next order!"
        );
        
        // Test 4: Send batch of orders
        System.out.println("\nTest 4: Sending batch of 5 orders...");
        sender.sendBatchOrders(5);
        
        System.out.println("\n========================================");
        System.out.println("All messages sent successfully!");
        System.out.println("Check MDB logs for processing results");
        System.out.println("========================================");
    }
}
