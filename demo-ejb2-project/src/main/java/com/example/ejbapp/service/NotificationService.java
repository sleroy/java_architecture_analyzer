package com.example.ejbapp.service;

import com.example.ejbapp.model.Member;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring service for notification management.
 * Demonstrates event-driven architecture and business logic without external dependencies.
 */
@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Sends a welcome notification to a new member
     */
    public void sendWelcomeNotification(Member member) {
        log.info("Sending welcome notification to: " + member.getName());
        
        NotificationEvent event = new NotificationEvent();
        event.setRecipient(member.getEmail());
        event.setSubject("Welcome to Unicorn!");
        event.setMessage(buildWelcomeMessage(member.getName()));
        event.setType(NotificationType.WELCOME);
        event.setTimestamp(LocalDateTime.now());
        
        eventPublisher.publishEvent(event);
        log.info("Welcome notification event fired for: " + member.getEmail());
    }

    /**
     * Sends a generic notification
     */
    public void sendNotification(String recipient, String subject, String message) {
        log.info("Sending notification to: " + recipient);
        
        NotificationEvent event = new NotificationEvent();
        event.setRecipient(recipient);
        event.setSubject(subject);
        event.setMessage(message);
        event.setType(NotificationType.GENERIC);
        event.setTimestamp(LocalDateTime.now());
        
        eventPublisher.publishEvent(event);
        log.info("Notification event fired successfully");
    }

    /**
     * Sends alert notifications to multiple recipients
     */
    public void sendAlertNotification(List<String> recipients, String alertMessage) {
        log.info("Sending alert notification to " + recipients.size() + " recipients");
        
        for (String recipient : recipients) {
            NotificationEvent event = new NotificationEvent();
            event.setRecipient(recipient);
            event.setSubject("ALERT: Important Notification");
            event.setMessage(alertMessage);
            event.setType(NotificationType.ALERT);
            event.setPriority(NotificationPriority.HIGH);
            event.setTimestamp(LocalDateTime.now());
            
            eventPublisher.publishEvent(event);
            log.info("Alert sent to: " + recipient);
        }
    }

    /**
     * Sends a reminder notification
     */
    public void sendReminderNotification(String recipient, String reminderText, LocalDateTime scheduledFor) {
        log.info("Scheduling reminder for: " + recipient);
        
        NotificationEvent event = new NotificationEvent();
        event.setRecipient(recipient);
        event.setSubject("Reminder");
        event.setMessage(reminderText);
        event.setType(NotificationType.REMINDER);
        event.setScheduledFor(scheduledFor);
        event.setTimestamp(LocalDateTime.now());
        
        eventPublisher.publishEvent(event);
        log.info("Reminder notification scheduled");
    }

    /**
     * Validates email format
     */
    @Transactional(readOnly = true)
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }

    /**
     * Validates notification content
     */
    @Transactional(readOnly = true)
    public boolean validateNotification(String recipient, String subject, String message) {
        if (!isValidEmail(recipient)) {
            log.warn("Invalid recipient email: " + recipient);
            return false;
        }
        
        if (subject == null || subject.trim().isEmpty()) {
            log.warn("Empty subject");
            return false;
        }
        
        if (message == null || message.trim().isEmpty()) {
            log.warn("Empty message");
            return false;
        }
        
        return true;
    }

    /**
     * Formats notification history
     */
    @Transactional(readOnly = true)
    public List<String> formatNotificationHistory(List<NotificationEvent> events) {
        log.info("Formatting " + events.size() + " notification events");
        
        List<String> formattedList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        for (NotificationEvent event : events) {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(event.getTimestamp().format(formatter)).append("] ");
            sb.append(event.getType()).append(" - ");
            sb.append(event.getSubject()).append(" -> ");
            sb.append(event.getRecipient());
            
            if (event.getPriority() == NotificationPriority.HIGH) {
                sb.append(" [HIGH PRIORITY]");
            }
            
            formattedList.add(sb.toString());
        }
        
        return formattedList;
    }

    /**
     * Builds a welcome message
     */
    private String buildWelcomeMessage(String memberName) {
        return "Hello " + memberName + ",\n\n" +
               "Welcome to Unicorn! We're excited to have you as a new member.\n\n" +
               "Get started by:\n" +
               "- Exploring your dashboard\n" +
               "- Updating your profile\n" +
               "- Connecting with other members\n\n" +
               "If you have any questions, please don't hesitate to contact us.\n\n" +
               "Best regards,\n" +
               "The Unicorn Team";
    }

    /**
     * Notification event class
     */
    public static class NotificationEvent {
        private String recipient;
        private String subject;
        private String message;
        private NotificationType type;
        private NotificationPriority priority = NotificationPriority.NORMAL;
        private LocalDateTime timestamp;
        private LocalDateTime scheduledFor;

        // Getters and setters
        public String getRecipient() { return recipient; }
        public void setRecipient(String recipient) { this.recipient = recipient; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public NotificationType getType() { return type; }
        public void setType(NotificationType type) { this.type = type; }

        public NotificationPriority getPriority() { return priority; }
        public void setPriority(NotificationPriority priority) { this.priority = priority; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public LocalDateTime getScheduledFor() { return scheduledFor; }
        public void setScheduledFor(LocalDateTime scheduledFor) { this.scheduledFor = scheduledFor; }
    }

    /**
     * Notification type enumeration
     */
    public enum NotificationType {
        WELCOME,
        ALERT,
        REMINDER,
        GENERIC,
        SYSTEM
    }

    /**
     * Notification priority enumeration
     */
    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
}
