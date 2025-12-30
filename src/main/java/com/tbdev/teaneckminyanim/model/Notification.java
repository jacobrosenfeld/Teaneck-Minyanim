package com.tbdev.teaneckminyanim.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Enhanced notification system with unique IDs for cookie-based view tracking.
 * Supports homepage announcements and popup notifications with expiration and max view limits.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "NOTIFICATION")
public class Notification {

    @Id
    @Column(name="ID", nullable = false, unique = true, length = 36)
    private String id;

    @Column(name = "TITLE", nullable = false, length = 255)
    private String title;

    @Column(name = "MESSAGE", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "TYPE", nullable = false, length = 50)
    private String type; // "BANNER" or "POPUP"

    @Column(name = "ENABLED", nullable = false)
    private Boolean enabled;

    @Column(name = "EXPIRATION_DATE", nullable = true)
    private LocalDateTime expirationDate;

    @Column(name = "MAX_DISPLAYS", nullable = true)
    private Integer maxDisplays;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = true)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if this notification is currently active
     */
    public boolean isActive() {
        if (!enabled) {
            return false;
        }
        
        if (expirationDate != null && LocalDateTime.now().isAfter(expirationDate)) {
            return false;
        }
        
        return true;
    }
}
