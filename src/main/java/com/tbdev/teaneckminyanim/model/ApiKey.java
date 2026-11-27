package com.tbdev.teaneckminyanim.model;

import com.tbdev.teaneckminyanim.enums.ApiScope;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A personal API key. Only the SHA-256 hash of the key is stored; the raw key is shown once at creation.
 * The key acts as its owner: its effective permission is its scopes (or "same as owner") combined with
 * whatever the owner can do at the time of each request.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "API_KEY", indexes = @Index(name = "idx_api_key_account", columnList = "ACCOUNT_ID"))
public class ApiKey {
    @Id
    @Column(name = "ID", nullable = false, length = 64)
    private String id;

    /** Owner account (TNMUser id). */
    @Column(name = "ACCOUNT_ID", nullable = false, length = 64)
    private String accountId;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    /** First 12 characters of the raw key, e.g. {@code tnm_AbCd1234}, for recognising a key. */
    @Column(name = "DISPLAY_PREFIX", nullable = false, length = 16)
    private String displayPrefix;

    @ToString.Exclude
    @Column(name = "KEY_HASH", nullable = false, unique = true, length = 64)
    private String keyHash;

    /** When true the key carries every scope; its permission is exactly the owner's. */
    @Builder.Default
    @Column(name = "SAME_AS_OWNER", nullable = false)
    private boolean sameAsOwner = false;

    // Stored as VARCHAR rather than a native MariaDB ENUM so adding a scope needs no column migration.
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "API_KEY_SCOPE", joinColumns = @JoinColumn(name = "API_KEY_ID"))
    @Column(name = "SCOPE", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private Set<ApiScope> scopes = new LinkedHashSet<>();

    /** Shuls this key is limited to. Empty means every shul the owner can access. */
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "API_KEY_ORGANIZATION", joinColumns = @JoinColumn(name = "API_KEY_ID"))
    @Column(name = "ORGANIZATION_ID", nullable = false, length = 64)
    private Set<String> restrictedOrganizationIds = new LinkedHashSet<>();

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY_ACCOUNT_ID", length = 64)
    private String createdByAccountId;

    @Column(name = "LAST_USED_AT")
    private LocalDateTime lastUsedAt;

    @ToString.Exclude
    @Column(name = "LAST_USED_IP_HASH", length = 64)
    private String lastUsedIpHash;

    @Column(name = "REVOKED_AT")
    private LocalDateTime revokedAt;

    @Column(name = "REVOKED_BY_ACCOUNT_ID", length = 64)
    private String revokedByAccountId;

    /** Why the key was revoked, e.g. MANUAL or USER_DISABLED. */
    @Column(name = "REVOKED_REASON", length = 64)
    private String revokedReason;

    @PrePersist
    void defaults() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public boolean isRestricted() {
        return restrictedOrganizationIds != null && !restrictedOrganizationIds.isEmpty();
    }

    public Set<ApiScope> getScopes() {
        return scopes == null ? Collections.emptySet() : scopes;
    }

    public Set<String> getRestrictedOrganizationIds() {
        return restrictedOrganizationIds == null ? Collections.emptySet() : restrictedOrganizationIds;
    }
}
