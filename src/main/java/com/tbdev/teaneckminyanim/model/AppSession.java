package com.tbdev.teaneckminyanim.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One mobile-app sign-in: an access token + refresh token pair. Each refresh replaces the row with a new
 * one in the same {@link #familyId}; the replaced row gets {@link #rotatedAt} and is revoked. Only SHA-256
 * hashes of the tokens are stored.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "APP_SESSION", indexes = {
        @Index(name = "idx_app_session_account", columnList = "ACCOUNT_ID"),
        @Index(name = "idx_app_session_family", columnList = "FAMILY_ID")
})
public class AppSession {
    public enum LoginMethod { MAGIC_LINK, PASSKEY }

    @Id
    @Column(name = "ID", nullable = false, length = 64)
    private String id;

    @Column(name = "ACCOUNT_ID", nullable = false, length = 64)
    private String accountId;

    /** Shared by every row produced from one sign-in through its refresh rotations. */
    @Column(name = "FAMILY_ID", nullable = false, length = 64)
    private String familyId;

    @ToString.Exclude
    @Column(name = "ACCESS_TOKEN_HASH", nullable = false, unique = true, length = 64)
    private String accessTokenHash;

    @Column(name = "ACCESS_EXPIRES_AT", nullable = false)
    private LocalDateTime accessExpiresAt;

    @ToString.Exclude
    @Column(name = "REFRESH_TOKEN_HASH", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    @Column(name = "REFRESH_EXPIRES_AT", nullable = false)
    private LocalDateTime refreshExpiresAt;

    @Column(name = "DEVICE_NAME", length = 100)
    private String deviceName;

    @Column(name = "USER_AGENT", length = 500)
    private String userAgent;

    // VARCHAR rather than a native MariaDB ENUM so new methods need no column migration.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "LOGIN_METHOD", nullable = false, length = 32)
    private LoginMethod loginMethod;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "LAST_USED_AT")
    private LocalDateTime lastUsedAt;

    /** Set when a refresh replaced this row with a new one. */
    @Column(name = "ROTATED_AT")
    private LocalDateTime rotatedAt;

    @Column(name = "REVOKED_AT")
    private LocalDateTime revokedAt;

    /** ROTATED, LOGOUT, REFRESH_REUSE, ADMIN_REVOKED, USER_DISABLED, ... */
    @Column(name = "REVOKED_REASON", length = 64)
    private String revokedReason;

    @PrePersist
    void defaults() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isRotated() {
        return rotatedAt != null;
    }
}
