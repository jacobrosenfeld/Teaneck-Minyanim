package com.tbdev.teaneckminyanim.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "AUTH_AUDIT_EVENT")
public class AuthAuditEvent {
    @Id
    @Column(name = "ID", nullable = false, length = 64)
    private String id;

    @Column(name = "ACCOUNT_ID", length = 64)
    private String accountId;

    @Column(name = "EVENT_TYPE", nullable = false, length = 64)
    private String eventType;

    @Column(name = "IP_HASH", length = 64)
    private String ipHash;

    @Column(name = "DETAILS", length = 1000)
    private String details;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void defaults() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
