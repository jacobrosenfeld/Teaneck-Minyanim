package com.tbdev.teaneckminyanim.service.auth;

import com.tbdev.teaneckminyanim.api.ApiException;
import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.security.ApiAuthentication;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Authorization for bearer-authenticated {@code /api/v1} requests. Admin API controllers call one of the
 * {@code require*} methods before doing anything; each returns the live {@link TNMUser} or throws an
 * {@link ApiException} (401/403) that {@code ApiExceptionHandler} renders.
 *
 * <p>Effective permission is the credential's scopes combined with what the owner can do right now:
 * the owner's role and shul access are checked live on every call.
 */
@Service
@RequiredArgsConstructor
public class ApiAuthorizationService {
    private final TNMUserService userService;

    /** The bearer authentication of the current request, if any. */
    public Optional<ApiAuthentication> currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof ApiAuthentication api && api.isAuthenticated()
                ? Optional.of(api) : Optional.empty();
    }

    /**
     * Any valid bearer token (for {@code /me}-style endpoints).
     *
     * @throws ApiException 401 UNAUTHENTICATED
     */
    public TNMUser requireAuthenticated() {
        return requireUser(requireApiAuthentication());
    }

    /**
     * Checks, in order: bearer authentication (401 UNAUTHENTICATED); the key's scope (403 INSUFFICIENT_SCOPE;
     * app tokens and same-as-owner keys hold every scope); the key's shul restriction (403 FORBIDDEN); and the
     * owner's live access to the shul (403 FORBIDDEN).
     *
     * @param organizationId the resolved shul id (not a slug); resolve and 404 unknown shuls first
     */
    public TNMUser require(ApiScope scope, String organizationId) {
        ApiAuthentication auth = requireApiAuthentication();
        TNMUser user = requireUser(auth);
        requireScope(auth, scope);
        if (organizationId == null || !auth.allowsOrganization(organizationId)) {
            throw ApiException.forbidden("This API key is not allowed to access this shul.");
        }
        if (!userService.canAccessOrganization(organizationId)) {
            throw ApiException.forbidden("You do not have access to this shul.");
        }
        return user;
    }

    /**
     * For site-wide actions (e.g. notifications): bearer authentication, the scope, no shul restriction on the
     * key, and a super-admin owner.
     *
     * @throws ApiException 401 UNAUTHENTICATED, 403 INSUFFICIENT_SCOPE or 403 FORBIDDEN
     */
    public TNMUser requireSuperAdmin(ApiScope scope) {
        ApiAuthentication auth = requireApiAuthentication();
        TNMUser user = requireUser(auth);
        requireScope(auth, scope);
        if (auth.isOrganizationRestricted()) {
            throw ApiException.forbidden("This API key is limited to specific shuls and cannot make site-wide changes.");
        }
        if (!user.isSuperAdmin()) {
            throw ApiException.forbidden("Only super admins can do this.");
        }
        return user;
    }

    /**
     * Bearer authentication plus the scope, with no shul check, for endpoints that filter their own results
     * by shul access.
     */
    public TNMUser requireScope(ApiScope scope) {
        ApiAuthentication auth = requireApiAuthentication();
        TNMUser user = requireUser(auth);
        requireScope(auth, scope);
        return user;
    }

    private ApiAuthentication requireApiAuthentication() {
        return currentAuthentication().orElseThrow(ApiException::unauthenticated);
    }

    private TNMUser requireUser(ApiAuthentication auth) {
        TNMUser user = userService.findById(auth.getAccountId());
        if (user == null || user.isDisabled() || user.getRoleId() == null || user.role() == null
                || !user.getUsername().equals(auth.getName())) {
            throw ApiException.invalidToken();
        }
        return user;
    }

    private static void requireScope(ApiAuthentication auth, ApiScope scope) {
        if (scope != null && !auth.hasScope(scope)) {
            throw ApiException.insufficientScope(scope.value());
        }
    }
}
