package com.tbdev.teaneckminyanim.model;

import com.tbdev.teaneckminyanim.enums.Role;
import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashSet;

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

    @Column(name = "EMAIL_NORMALIZED", unique = true)
    private String emailNormalized;

    @Column(name = "ENCRYPTED_PASSWORD", nullable = false)
    private String encryptedPassword;

    @Column(name = "ORGANIZATION_ID")
    private String organizationId;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ACCOUNT_ORGANIZATION", joinColumns = @JoinColumn(name = "ACCOUNT_ID"))
    @Column(name = "ORGANIZATION_ID", nullable = false)
    private Set<String> additionalOrganizationIds = new LinkedHashSet<>();

    /** Primary organization is retained for legacy links and the default landing page. */
    public Set<String> getAccessibleOrganizationIds() {
        Set<String> ids = new LinkedHashSet<>();
        if (organizationId != null) ids.add(organizationId);
        if (additionalOrganizationIds != null) ids.addAll(additionalOrganizationIds);
        return ids;
    }

    public boolean canAccessOrganization(String id) {
        return isEnabled() && id != null && (isSuperAdmin() || getAccessibleOrganizationIds().contains(id));
    }

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

    @Builder.Default
    @Column(name = "WEEKLY_MINYAN_REVIEW_EMAILS_ENABLED", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean weeklyMinyanReviewEmailsEnabled = true;

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
        if (weeklyMinyanReviewEmailsEnabled == null) {
            weeklyMinyanReviewEmailsEnabled = true;
        }
    }

    public static String normalizeEmail(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    /** Validates the normalized account address without regex backtracking. */
    public static boolean isValidAccountEmail(String email) {
        if (email == null || email.length() > 254) {
            return false;
        }

        int at = email.indexOf('@');
        if (at <= 0 || email.indexOf('@', at + 1) >= 0) {
            return false;
        }

        // The domain needs a dot with at least one character on either side.
        int domainDot = email.indexOf('.', at + 2);
        if (domainDot < 0 || domainDot == email.length() - 1) {
            return false;
        }

        for (int i = 0; i < email.length(); i++) {
            if (" \t\n\u000B\f\r".indexOf(email.charAt(i)) >= 0) {
                return false;
            }
        }
        return true;
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

    public boolean isWeeklyMinyanReviewEmailsEnabled() {
        return weeklyMinyanReviewEmailsEnabled == null || weeklyMinyanReviewEmailsEnabled;
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
