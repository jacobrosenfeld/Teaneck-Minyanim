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
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Entity
@Table(name = "MAGIC_LINK_TOKEN")
public class MagicLinkToken {

    @Id
    @Column(name = "ID", nullable = false, unique = true)
    private String id;

    @Column(name = "ACCOUNT_ID")
    private String accountId;

    @Column(name = "TOKEN_HASH", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "PURPOSE", nullable = false)
    private String purpose;

    @Column(name = "EXPIRES_AT", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "USED_AT")
    private LocalDateTime usedAt;

    @Column(name = "REQUESTED_EMAIL_HASH")
    private String requestedEmailHash;

    @Column(name = "REQUEST_IP_HASH")
    private String requestIpHash;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY_ACCOUNT_ID")
    private String createdByAccountId;

    @Column(name = "USER_AGENT")
    private String userAgent;

    @PrePersist
    void setDefaults() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}
