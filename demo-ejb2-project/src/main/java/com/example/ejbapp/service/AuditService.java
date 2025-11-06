package com.example.ejbapp.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Spring service for audit logging.
 * Demonstrates database operations, querying, and business logic.
 */
@Service
@Transactional
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    @PersistenceContext
    private EntityManager em;

    /**
     * Logs a user action to the audit trail
     */
    public void logAction(String username, String action, String entityType, Long entityId) {
        log.info("Logging action: " + action + " by " + username + " on " + entityType + " [" + entityId + "]");
        
        AuditEntry entry = new AuditEntry();
        entry.setUsername(username);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setTimestamp(LocalDateTime.now());
        entry.setIpAddress(getCurrentUserIp());
        
        em.persist(entry);
        log.info("Audit entry created with ID: " + entry.getId());
    }

    /**
     * Logs a login event
     */
    public void logLogin(String username, boolean success) {
        log.info("Logging login attempt for user: " + username + " - Success: " + success);
        
        AuditEntry entry = new AuditEntry();
        entry.setUsername(username);
        entry.setAction(success ? "LOGIN_SUCCESS" : "LOGIN_FAILED");
        entry.setEntityType("USER");
        entry.setTimestamp(LocalDateTime.now());
        entry.setIpAddress(getCurrentUserIp());
        
        em.persist(entry);
    }

    /**
     * Logs a logout event
     */
    public void logLogout(String username) {
        log.info("Logging logout for user: " + username);
        
        AuditEntry entry = new AuditEntry();
        entry.setUsername(username);
        entry.setAction("LOGOUT");
        entry.setEntityType("USER");
        entry.setTimestamp(LocalDateTime.now());
        entry.setIpAddress(getCurrentUserIp());
        
        em.persist(entry);
    }

    /**
     * Retrieves audit entries for a specific user
     */
    @Transactional(readOnly = true)
    public List<AuditEntry> getAuditEntriesForUser(String username, int maxResults) {
        log.info("Retrieving audit entries for user: " + username);
        
        TypedQuery<AuditEntry> query = em.createQuery(
            "SELECT a FROM AuditEntry a WHERE a.username = :username ORDER BY a.timestamp DESC",
            AuditEntry.class
        );
        query.setParameter("username", username);
        query.setMaxResults(maxResults);
        
        return query.getResultList();
    }

    /**
     * Retrieves audit entries for a specific entity
     */
    @Transactional(readOnly = true)
    public List<AuditEntry> getAuditEntriesForEntity(String entityType, Long entityId) {
        log.info("Retrieving audit entries for entity: " + entityType + " [" + entityId + "]");
        
        TypedQuery<AuditEntry> query = em.createQuery(
            "SELECT a FROM AuditEntry a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.timestamp DESC",
            AuditEntry.class
        );
        query.setParameter("entityType", entityType);
        query.setParameter("entityId", entityId);
        
        return query.getResultList();
    }

    /**
     * Retrieves recent audit entries
     */
    @Transactional(readOnly = true)
    public List<AuditEntry> getRecentAuditEntries(int maxResults) {
        log.info("Retrieving recent audit entries");
        
        TypedQuery<AuditEntry> query = em.createQuery(
            "SELECT a FROM AuditEntry a ORDER BY a.timestamp DESC",
            AuditEntry.class
        );
        query.setMaxResults(maxResults);
        
        return query.getResultList();
    }

    /**
     * Counts audit entries for a specific action
     */
    @Transactional(readOnly = true)
    public long countActionsByType(String action) {
        log.info("Counting audit entries for action: " + action);
        
        TypedQuery<Long> query = em.createQuery(
            "SELECT COUNT(a) FROM AuditEntry a WHERE a.action = :action",
            Long.class
        );
        query.setParameter("action", action);
        
        return query.getSingleResult();
    }

    /**
     * Generates an audit report summary
     */
    @Transactional(readOnly = true)
    public String generateAuditReport(String username, LocalDateTime fromDate, LocalDateTime toDate) {
        log.info("Generating audit report for user: " + username);
        
        TypedQuery<AuditEntry> query = em.createQuery(
            "SELECT a FROM AuditEntry a WHERE a.username = :username " +
            "AND a.timestamp BETWEEN :fromDate AND :toDate ORDER BY a.timestamp DESC",
            AuditEntry.class
        );
        query.setParameter("username", username);
        query.setParameter("fromDate", fromDate);
        query.setParameter("toDate", toDate);
        
        List<AuditEntry> entries = query.getResultList();
        
        StringBuilder report = new StringBuilder();
        report.append("=== AUDIT REPORT ===\n");
        report.append("User: ").append(username).append("\n");
        report.append("Period: ").append(formatDateTime(fromDate)).append(" to ").append(formatDateTime(toDate)).append("\n");
        report.append("Total Actions: ").append(entries.size()).append("\n\n");
        
        for (AuditEntry entry : entries) {
            report.append(formatDateTime(entry.getTimestamp()))
                  .append(" - ")
                  .append(entry.getAction())
                  .append(" on ")
                  .append(entry.getEntityType());
            
            if (entry.getEntityId() != null) {
                report.append(" [").append(entry.getEntityId()).append("]");
            }
            
            report.append("\n");
        }
        
        return report.toString();
    }

    /**
     * Gets the current user's IP address (placeholder implementation)
     */
    private String getCurrentUserIp() {
        // In a real application, this would extract the IP from the request context
        return "127.0.0.1";
    }

    /**
     * Formats a LocalDateTime for display
     */
    private String formatDateTime(LocalDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return dateTime.format(formatter);
    }

    /**
     * Simple inner class representing an audit entry
     * In a real application, this would be a separate @Entity class
     */
    public static class AuditEntry {
        private Long id;
        private String username;
        private String action;
        private String entityType;
        private Long entityId;
        private LocalDateTime timestamp;
        private String ipAddress;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }

        public Long getEntityId() { return entityId; }
        public void setEntityId(Long entityId) { this.entityId = entityId; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    }
}
