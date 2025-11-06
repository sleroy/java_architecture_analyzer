package com.example.ejbapp.ejb2.mdb;

import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * EJB 2.0 Message-Driven Bean Example - Notification Processor
 * 
 * This MDB processes notification messages from a JMS topic.
 * It demonstrates publish/subscribe pattern with topics.
 * Multiple instances can receive the same message from a topic.
 * 
 * Configuration in ejb-jar.xml:
 * - Destination type: Topic
 * - Transaction type: Container-managed
 * - Subscription durability: NonDurable
 * 
 * Use cases:
 * - Email notifications
 * - SMS alerts
 * - Push notifications
 * - Event broadcasting
 * 
 * Note: This is legacy EJB 2.0 technology. Modern applications should use:
 * - Spring @JmsListener with topics
 * - Spring Cloud Stream
 * - Apache Kafka with Spring
 * - WebSockets for real-time notifications
 */
public class NotificationMDB implements MessageDrivenBean, MessageListener {
    
    private static final Logger logger = Logger.getLogger(NotificationMDB.class.getName());
    
    private MessageDrivenContext mdbContext;
    
    /**
     * Called by the container when a notification message arrives
     * 
     * @param message The JMS message to process
     */
    @Override
    public void onMessage(Message message) {
        logger.info("NotificationMDB: Received notification message");
        
        try {
            if (message instanceof ObjectMessage) {
                ObjectMessage objectMessage = (ObjectMessage) message;
                Serializable payload = objectMessage.getObject();
                
                if (payload instanceof NotificationData) {
                    NotificationData notification = (NotificationData) payload;
                    processNotification(notification);
                } else {
                    logger.warning("Received unsupported object type: " + 
                                 payload.getClass().getName());
                }
                
            } else {
                logger.warning("Received non-ObjectMessage: " + 
                             message.getClass().getName());
            }
            
        } catch (JMSException e) {
            logger.log(Level.SEVERE, "Error reading message", e);
            mdbContext.setRollbackOnly();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing notification", e);
            mdbContext.setRollbackOnly();
        }
    }
    
    /**
     * Process the notification
     * 
     * @param notification The notification data
     */
    private void processNotification(NotificationData notification) {
        logger.info("Processing notification:");
        logger.info("  Type: " + notification.getType());
        logger.info("  Recipient: " + notification.getRecipient());
        logger.info("  Subject: " + notification.getSubject());
        logger.info("  Message: " + notification.getMessage());
        
        // Route to appropriate handler based on type
        switch (notification.getType()) {
            case EMAIL:
                sendEmail(notification);
                break;
            case SMS:
                sendSMS(notification);
                break;
            case PUSH:
                sendPushNotification(notification);
                break;
            default:
                logger.warning("Unknown notification type: " + notification.getType());
        }
    }
    
    /**
     * Send email notification
     */
    private void sendEmail(NotificationData notification) {
        logger.info("Sending email to: " + notification.getRecipient());
        // In real application, integrate with JavaMail API
        // or external email service
        try {
            Thread.sleep(50); // Simulate email sending
            logger.info("Email sent successfully");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Send SMS notification
     */
    private void sendSMS(NotificationData notification) {
        logger.info("Sending SMS to: " + notification.getRecipient());
        // In real application, integrate with SMS gateway
        try {
            Thread.sleep(30); // Simulate SMS sending
            logger.info("SMS sent successfully");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Send push notification
     */
    private void sendPushNotification(NotificationData notification) {
        logger.info("Sending push notification to: " + notification.getRecipient());
        // In real application, integrate with push notification service
        // (Firebase, Apple Push Notification, etc.)
        try {
            Thread.sleep(20); // Simulate push notification
            logger.info("Push notification sent successfully");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    // ==================== MessageDrivenBean Lifecycle Methods ====================
    
    @Override
    public void setMessageDrivenContext(MessageDrivenContext mdbContext) {
        logger.info("setMessageDrivenContext called");
        this.mdbContext = mdbContext;
    }
    
    public void ejbCreate() {
        logger.info("ejbCreate called - NotificationMDB initialized");
    }
    
    @Override
    public void ejbRemove() {
        logger.info("ejbRemove called - NotificationMDB destroyed");
    }
    
    // ==================== Inner Class for Notification Data ====================
    
    /**
     * Notification data object (must be Serializable for JMS ObjectMessage)
     */
    public static class NotificationData implements Serializable {
        
        private static final long serialVersionUID = 1L;
        
        public enum NotificationType {
            EMAIL, SMS, PUSH
        }
        
        private NotificationType type;
        private String recipient;
        private String subject;
        private String message;
        private String priority;
        
        public NotificationData() {
        }
        
        public NotificationData(NotificationType type, String recipient, 
                               String subject, String message) {
            this.type = type;
            this.recipient = recipient;
            this.subject = subject;
            this.message = message;
            this.priority = "NORMAL";
        }
        
        // Getters and Setters
        
        public NotificationType getType() {
            return type;
        }
        
        public void setType(NotificationType type) {
            this.type = type;
        }
        
        public String getRecipient() {
            return recipient;
        }
        
        public void setRecipient(String recipient) {
            this.recipient = recipient;
        }
        
        public String getSubject() {
            return subject;
        }
        
        public void setSubject(String subject) {
            this.subject = subject;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public String getPriority() {
            return priority;
        }
        
        public void setPriority(String priority) {
            this.priority = priority;
        }
        
        @Override
        public String toString() {
            return "NotificationData{" +
                    "type=" + type +
                    ", recipient='" + recipient + '\'' +
                    ", subject='" + subject + '\'' +
                    ", priority='" + priority + '\'' +
                    '}';
        }
    }
}
