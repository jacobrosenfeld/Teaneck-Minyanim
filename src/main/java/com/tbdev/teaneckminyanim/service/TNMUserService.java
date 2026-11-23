package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.security.ApiAuthentication;
import com.tbdev.teaneckminyanim.service.auth.ApiKeyService;
import com.tbdev.teaneckminyanim.service.auth.AppTokenService;
import com.tbdev.teaneckminyanim.service.auth.AuthAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class TNMUserService {
    private final TNMUserRepository repository;
    // Resolved lazily: the token services depend on this service, so direct injection would be circular.
    private final ObjectProvider<AppTokenService> appTokens;
    private final ObjectProvider<ApiKeyService> apiKeys;
    private final ObjectProvider<AuthAuditService> audit;

    /** For unit tests that need no token revocation. */
    public TNMUserService(TNMUserRepository repository) {
        this(repository, null, null, null);
    }

    @Autowired
    public TNMUserService(TNMUserRepository repository,
                          ObjectProvider<AppTokenService> appTokens,
                          ObjectProvider<ApiKeyService> apiKeys,
                          ObjectProvider<AuthAuditService> audit) {
        this.repository = repository;
        this.appTokens = appTokens;
        this.apiKeys = apiKeys;
        this.audit = audit;
    }

    public TNMUser findByName(String username) {
        return repository.findByUsername(username).orElse(null);
    }

    public TNMUser findByNormalizedEmail(String email) {
        String normalizedEmail = TNMUser.normalizeEmail(email);
        if (normalizedEmail == null) {
            return null;
        }
        return repository.findByEmailNormalized(normalizedEmail)
                .or(() -> repository.findByEmailIgnoreCase(normalizedEmail))
                .orElse(null);
    }

    public TNMUser findByIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }

        String trimmedIdentifier = identifier.trim();
        if (trimmedIdentifier.isBlank()) {
            return null;
        }

        TNMUser user = findByName(trimmedIdentifier);
        if (user != null) {
            return user;
        }
        user = repository.findByUsernameIgnoreCase(trimmedIdentifier).orElse(null);
        if (user != null) {
            return user;
        }
        return findByNormalizedEmail(trimmedIdentifier);
    }

    public List<TNMUser> getAll() {
        return repository.findAll();
    }

    public List<TNMUser> getWeeklyMinyanReviewEmailRecipients() {
        return getAll().stream()
                .filter(Objects::nonNull)
                .filter(TNMUser::isEnabled)
                .filter(user -> user.getRoleId() != null)
                .filter(TNMUser::isAdmin)
                .filter(TNMUser::isWeeklyMinyanReviewEmailsEnabled)
                .toList();
    }

    public TNMUser findById(String id) {
        return repository.findById(id).orElse(null);
    }

    public boolean save(TNMUser tnmUser) {
        try {
            prepareForPersistence(tnmUser);
            repository.save(tnmUser);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /** Saves the account. Saving a disabled account revokes all of its app sessions and API keys. */
    public boolean update(TNMUser tnmUser) {
        try {
            prepareForPersistence(tnmUser);
            repository.save(tnmUser);
        } catch (Exception e) {
            return false;
        }
        if (tnmUser != null && tnmUser.isDisabled()) {
            try {
                onAccountDisabled(tnmUser.getId());
            } catch (RuntimeException e) {
                // The account is saved as disabled, and the bearer filter rejects disabled owners on every request.
                log.warn("Could not revoke tokens for disabled account {}: {}", tnmUser.getId(), e.getMessage());
            }
        }
        return true;
    }

    /** Deletes the account together with its app sessions and API keys. */
    public boolean delete(TNMUser tnmUser) {
        try {
            if (tnmUser != null) {
                onAccountDeleted(tnmUser.getId());
            }
            repository.delete(tnmUser);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Revokes every app session and API key of a disabled account and audits TOKENS_REVOKED_USER_DISABLED
     * when anything was revoked. Call this from every code path that disables an account. Idempotent.
     */
    public void onAccountDisabled(String accountId) {
        if (accountId == null) {
            return;
        }
        int sessions = 0;
        int keys = 0;
        AppTokenService tokenService = appTokens == null ? null : appTokens.getIfAvailable();
        ApiKeyService keyService = apiKeys == null ? null : apiKeys.getIfAvailable();
        if (tokenService != null) {
            sessions = tokenService.revokeAllForAccount(accountId, "USER_DISABLED");
        }
        if (keyService != null) {
            keys = keyService.revokeAllForAccount(accountId, "USER_DISABLED");
        }
        AuthAuditService auditService = audit == null ? null : audit.getIfAvailable();
        if (auditService != null && (sessions > 0 || keys > 0)) {
            auditService.record("TOKENS_REVOKED_USER_DISABLED", accountId,
                    com.tbdev.teaneckminyanim.service.auth.AuthRequests.currentClientIpHash(),
                    "sessions=" + sessions + " keys=" + keys);
        }
    }

    /** Deletes every app session and API key of an account that is being deleted. */
    public void onAccountDeleted(String accountId) {
        if (accountId == null) {
            return;
        }
        AppTokenService tokenService = appTokens == null ? null : appTokens.getIfAvailable();
        ApiKeyService keyService = apiKeys == null ? null : apiKeys.getIfAvailable();
        if (tokenService != null) {
            tokenService.deleteAllForAccount(accountId);
        }
        if (keyService != null) {
            keyService.deleteAllForAccount(accountId);
        }
    }

    /**
     * Get the currently authenticated user
     */
    public TNMUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken
                || authentication.getName() == null) {
            return null;
        }
        String username = authentication.getName();
        return findByName(username);
    }

    /**
     * Check if current user is a super admin (no organization, ADMIN role)
     */
    public boolean isSuperAdmin() {
        TNMUser user = getCurrentUser();
        return user != null && user.isEnabled() && user.isSuperAdmin();
    }

    /**
     * Check if current user can access a specific organization
     * Super admins can access all orgs, regular users only their own. When the request is authenticated
     * with a shul-restricted API key, only shuls in that restriction are accessible.
     */
    public boolean canAccessOrganization(String organizationId) {
        // A shul-restricted API key narrows whatever its owner could otherwise reach.
        if (SecurityContextHolder.getContext().getAuthentication() instanceof ApiAuthentication api
                && !api.allowsOrganization(organizationId)) {
            return false;
        }
        if (isSuperAdmin()) {
            return true;
        }
        TNMUser user = getCurrentUser();
        return user != null && user.canAccessOrganization(organizationId);
    }

    private void prepareForPersistence(TNMUser tnmUser) {
        if (tnmUser == null) {
            return;
        }
        tnmUser.normalizeAuthFields();
    }
}
