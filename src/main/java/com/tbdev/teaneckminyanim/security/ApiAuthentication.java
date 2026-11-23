package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.enums.ApiScope;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Authentication set by {@link BearerTokenAuthenticationFilter} for a request carrying a valid bearer token.
 * {@link #getName()} is the account's username, so {@code TNMUserService.getCurrentUser()} and
 * {@code isSuperAdmin()} work unchanged. It never holds the raw token.
 */
public class ApiAuthentication extends AbstractAuthenticationToken {
    public enum Kind {
        /** Mobile app access token ({@code tnmapp_...}); holds every scope. */
        APP,
        /** Personal API key ({@code tnm_...}). */
        KEY
    }

    private final Kind kind;
    private final String accountId;
    private final String username;
    private final Set<ApiScope> scopes;
    private final boolean sameAsOwner;
    private final Set<String> restrictedOrganizationIds;
    private final String credentialId;

    public ApiAuthentication(Kind kind, String accountId, String username, String roleAuthority,
                             Set<ApiScope> scopes, boolean sameAsOwner, Set<String> restrictedOrganizationIds,
                             String credentialId) {
        super(authorities(roleAuthority));
        this.kind = kind;
        this.accountId = accountId;
        this.username = username;
        this.scopes = scopes == null || scopes.isEmpty()
                ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(scopes));
        this.sameAsOwner = sameAsOwner;
        this.restrictedOrganizationIds = restrictedOrganizationIds == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(restrictedOrganizationIds));
        this.credentialId = credentialId;
        super.setAuthenticated(true);
    }

    /** An app-token authentication: every scope, no shul restriction. */
    public static ApiAuthentication forAppSession(String accountId, String username, String roleAuthority,
                                                  String sessionId) {
        return new ApiAuthentication(Kind.APP, accountId, username, roleAuthority,
                EnumSet.allOf(ApiScope.class), true, Set.of(), sessionId);
    }

    private static Collection<? extends GrantedAuthority> authorities(String roleAuthority) {
        return roleAuthority == null ? List.of() : List.of(new SimpleGrantedAuthority(roleAuthority));
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isAppToken() {
        return kind == Kind.APP;
    }

    public boolean isApiKey() {
        return kind == Kind.KEY;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    /** Explicit key scopes. App tokens report every scope. */
    public Set<ApiScope> getScopes() {
        return scopes;
    }

    public boolean isSameAsOwner() {
        return sameAsOwner;
    }

    /** True when this credential may use {@code scope}: app tokens and same-as-owner keys always may. */
    public boolean hasScope(ApiScope scope) {
        return kind == Kind.APP || sameAsOwner || scopes.contains(scope);
    }

    /** Shuls an API key is limited to. Empty means no restriction (all the owner's shuls). */
    public Set<String> getRestrictedOrganizationIds() {
        return restrictedOrganizationIds;
    }

    public boolean isOrganizationRestricted() {
        return !restrictedOrganizationIds.isEmpty();
    }

    /** False only when this key has a shul restriction that does not include {@code organizationId}. */
    public boolean allowsOrganization(String organizationId) {
        return restrictedOrganizationIds.isEmpty()
                || (organizationId != null && restrictedOrganizationIds.contains(organizationId));
    }

    /** The AppSession id (APP) or ApiKey id (KEY). */
    public String getCredentialId() {
        return credentialId;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    /** Always null: the raw token is never kept. */
    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return username;
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) {
            throw new IllegalArgumentException("ApiAuthentication is created authenticated; it cannot be re-authenticated.");
        }
        super.setAuthenticated(false);
    }

    @Override
    public String toString() {
        return "ApiAuthentication[kind=" + kind + ", accountId=" + accountId + ", username=" + username
                + ", credentialId=" + credentialId + ", scopes=" + scopes + ", sameAsOwner=" + sameAsOwner
                + ", restrictedOrganizationIds=" + restrictedOrganizationIds + ", authorities=" + getAuthorities() + "]";
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
