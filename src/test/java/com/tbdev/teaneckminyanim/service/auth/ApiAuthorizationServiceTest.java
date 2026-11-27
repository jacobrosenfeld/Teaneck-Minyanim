package com.tbdev.teaneckminyanim.service.auth;

import com.tbdev.teaneckminyanim.api.ApiException;
import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.security.ApiAuthentication;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Uses the real TNMUserService over a mocked repository, so shul access is the production logic. */
class ApiAuthorizationServiceTest {
    private final TNMUserRepository repository = mock(TNMUserRepository.class);
    private final TNMUserService users = new TNMUserService(repository);
    private final ApiAuthorizationService authorization = new ApiAuthorizationService(users);

    private TNMUser superAdmin;
    private TNMUser manager;

    @BeforeEach
    void setUp() {
        superAdmin = ApiKeyServiceTest.user("SUPER", "super", null, Role.ADMIN);
        manager = ApiKeyServiceTest.user("MGR", "manager", "ORGA", Role.ADMIN);
        manager.getAdditionalOrganizationIds().add("ORGB");
        for (TNMUser user : List.of(superAdmin, manager)) {
            when(repository.findById(user.getId())).thenReturn(Optional.of(user));
            when(repository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        }
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noBearerAuthenticationIsUnauthenticated() {
        assertApiError(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                () -> authorization.require(ApiScope.OVERRIDES_WRITE, "ORGA"));
        assertApiError(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", authorization::requireAuthenticated);

        // A browser session principal is not an API credential.
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "manager", null, List.of(new SimpleGrantedAuthority(Role.ADMIN.getName()))));
        assertApiError(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", authorization::requireAuthenticated);
    }

    @Test
    void keyNeedsTheScope() {
        key(manager, Set.of(ApiScope.SCHEDULE_READ), false, Set.of());
        assertApiError(HttpStatus.FORBIDDEN, "INSUFFICIENT_SCOPE",
                () -> authorization.require(ApiScope.OVERRIDES_WRITE, "ORGA"));
        assertSame(manager, authorization.require(ApiScope.SCHEDULE_READ, "ORGA"));
    }

    @Test
    void sameAsOwnerKeysAndAppTokensHoldEveryScope() {
        key(manager, Set.of(), true, Set.of());
        for (ApiScope scope : ApiScope.values()) {
            assertSame(manager, authorization.require(scope, "ORGA"));
        }
        SecurityContextHolder.getContext().setAuthentication(
                ApiAuthentication.forAppSession("MGR", "manager", Role.ADMIN.getName(), "S1"));
        for (ApiScope scope : ApiScope.values()) {
            assertSame(manager, authorization.require(scope, "ORGB"));
        }
    }

    @Test
    void scopeNeverExceedsTheOwnersShulAccess() {
        key(manager, Set.of(ApiScope.OVERRIDES_WRITE), false, Set.of());
        assertSame(manager, authorization.require(ApiScope.OVERRIDES_WRITE, "ORGB"));
        assertApiError(HttpStatus.FORBIDDEN, "FORBIDDEN", () -> authorization.require(ApiScope.OVERRIDES_WRITE, "ORGC"));
        assertApiError(HttpStatus.FORBIDDEN, "FORBIDDEN", () -> authorization.require(ApiScope.OVERRIDES_WRITE, null));
    }

    @Test
    void shulRestrictionNarrowsTheKey() {
        key(superAdmin, Set.of(), true, Set.of("ORGA"));
        assertSame(superAdmin, authorization.require(ApiScope.OVERRIDES_WRITE, "ORGA"));
        assertApiError(HttpStatus.FORBIDDEN, "FORBIDDEN", () -> authorization.require(ApiScope.OVERRIDES_WRITE, "ORGB"));
        // TNMUserService.canAccessOrganization is narrowed too, even for a super-admin owner.
        assertTrue(users.canAccessOrganization("ORGA"));
        assertFalse(users.canAccessOrganization("ORGB"));
        assertTrue(users.isSuperAdmin());
    }

    @Test
    void keyIsDeniedWhenItsOwnerLosesAccessToTheShul() {
        key(manager, Set.of(ApiScope.MINYANIM_WRITE), false, Set.of("ORGB"));
        assertSame(manager, authorization.require(ApiScope.MINYANIM_WRITE, "ORGB"));

        manager.getAdditionalOrganizationIds().remove("ORGB");

        assertApiError(HttpStatus.FORBIDDEN, "FORBIDDEN", () -> authorization.require(ApiScope.MINYANIM_WRITE, "ORGB"));
    }

    @Test
    void disabledOrMissingOwnerIsAnInvalidToken() {
        key(manager, Set.of(), true, Set.of());
        manager.setEnabled(false);
        assertApiError(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", authorization::requireAuthenticated);
        when(repository.findById("MGR")).thenReturn(Optional.empty());
        assertApiError(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", authorization::requireAuthenticated);
    }

    @Test
    void siteWideActionsNeedAnUnrestrictedSuperAdminCredential() {
        key(manager, Set.of(), true, Set.of());
        assertApiError(HttpStatus.FORBIDDEN, "FORBIDDEN", () -> authorization.requireSuperAdmin(ApiScope.NOTIFICATIONS_WRITE));

        key(superAdmin, Set.of(ApiScope.SCHEDULE_READ), false, Set.of());
        assertApiError(HttpStatus.FORBIDDEN, "INSUFFICIENT_SCOPE",
                () -> authorization.requireSuperAdmin(ApiScope.NOTIFICATIONS_WRITE));

        key(superAdmin, Set.of(ApiScope.NOTIFICATIONS_WRITE), false, Set.of("ORGA"));
        assertApiError(HttpStatus.FORBIDDEN, "FORBIDDEN", () -> authorization.requireSuperAdmin(ApiScope.NOTIFICATIONS_WRITE));

        key(superAdmin, Set.of(ApiScope.NOTIFICATIONS_WRITE), false, Set.of());
        assertSame(superAdmin, authorization.requireSuperAdmin(ApiScope.NOTIFICATIONS_WRITE));
    }

    @Test
    void requireAuthenticatedAcceptsAnyValidCredential() {
        key(manager, Set.of(ApiScope.SCHEDULE_READ), false, Set.of("ORGA"));
        assertSame(manager, authorization.requireAuthenticated());
        assertTrue(authorization.currentAuthentication().isPresent());
        assertSame(manager, authorization.requireScope(ApiScope.SCHEDULE_READ));
        assertApiError(HttpStatus.FORBIDDEN, "INSUFFICIENT_SCOPE", () -> authorization.requireScope(ApiScope.SHUL_WRITE));
    }

    private void key(TNMUser owner, Set<ApiScope> scopes, boolean sameAsOwner, Set<String> restriction) {
        SecurityContextHolder.getContext().setAuthentication(new ApiAuthentication(ApiAuthentication.Kind.KEY,
                owner.getId(), owner.getUsername(), owner.role().getName(), scopes, sameAsOwner, restriction, "K1"));
    }

    private static void assertApiError(HttpStatus status, String code, Runnable call) {
        ApiException error = assertThrows(ApiException.class, call::run);
        assertEquals(status, error.getStatus());
        assertEquals(code, error.getCode());
    }
}
