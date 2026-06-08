package com.tbdev.teaneckminyanim.model;

import com.tbdev.teaneckminyanim.enums.Role;
import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Locale;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Entity
@Table(name = "ACCOUNT")
public class TNMUser {

    @Id
    @Column(name="ID", nullable = false, unique = true)
    protected String id;

    @Column(name = "USERNAME", nullable = false, unique = true)
    private String username;

    @Column(name = "EMAIL", nullable = false)
    private String email;

    @Column(name = "EMAIL_NORMALIZED")
    private String emailNormalized;

    @Column(name = "ENCRYPTED_PASSWORD", nullable = false)
    private String encryptedPassword;

    @Column(name = "ORGANIZATION_ID")
    private String organizationId;

    @Column(name = "ROLE_ID")
    private Integer roleId;

    @Builder.Default
    @Column(name = "ENABLED", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean enabled = true;

    @Column(name = "LAST_LOGIN_AT")
    private LocalDateTime lastLoginAt;

    @Column(name = "LAST_LOGIN_METHOD")
    private String lastLoginMethod;

    @Column(name = "INVITED_AT")
    private LocalDateTime invitedAt;

    @Column(name = "INVITE_ACCEPTED_AT")
    private LocalDateTime inviteAcceptedAt;

    @Builder.Default
    @Column(name = "AUTH_MIGRATION_REQUIRED", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean authMigrationRequired = true;

    @PrePersist
    @PreUpdate
    public void normalizeAuthFields() {
        emailNormalized = normalizeEmail(email);
        if (enabled == null) {
            enabled = true;
        }
        if (authMigrationRequired == null) {
            authMigrationRequired = true;
        }
    }

    public static String normalizeEmail(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public Role role() {
        return Role.getRole(roleId);
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public boolean isDisabled() {
        return !isEnabled();
    }

    public boolean isAuthMigrationRequired() {
        return authMigrationRequired == null || authMigrationRequired;
    }

    public boolean isSuperAdmin() {
        return (this.role() == Role.ADMIN && this.getOrganizationId() == null);
    }

    public boolean isAdmin() {
        return (this.role() == Role.ADMIN);
    }

    public boolean isUser() {
        return (this.role() == Role.USER);
    }

    public String getRoleDisplayName() {
        return switch (this.role()) {
            case ADMIN -> (this.getOrganizationId() == null) ? "Admin" : "Manager";
            case USER -> "User";
        };
    }
}
