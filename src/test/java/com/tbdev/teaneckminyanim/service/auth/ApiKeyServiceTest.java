package com.tbdev.teaneckminyanim.service.auth;

import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.ApiKey;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.ApiKeyRepository;
import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.security.ApiAuthentication;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class ApiKeyServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneId.of("America/New_York"));

    private final ApiKeyRepository repository = mock(ApiKeyRepository.class);
    private final TNMUserService users = mock(TNMUserService.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final AuthAuditService audit = mock(AuthAuditService.class);
    private final ApiKeyService service = new ApiKeyService(repository, users, organizations, audit, CLOCK);

    private TNMUser superAdmin;
    private TNMUser manager;

    @BeforeEach
    void setUp() {
        superAdmin = user("SUPER", "super", null, Role.ADMIN);
        manager = user("MGR", "manager", "ORGA", Role.ADMIN);
        manager.getAdditionalOrganizationIds().add("ORGB");
        when(users.isSuperAdmin()).thenReturn(true);
        when(users.getCurrentUser()).thenReturn(superAdmin);
        when(users.findById("SUPER")).thenReturn(superAdmin);
        when(users.findById("MGR")).thenReturn(manager);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReturnsRawKeyOnceAndStoresOnlyItsHash() {
        ApiKeyService.CreatedApiKey created = service.create("MGR", "  Shul sync  ", false,
                Set.of(ApiScope.OVERRIDES_WRITE), Set.of("ORGA"));

        String raw = created.rawKey();
        assertTrue(raw.matches("tnm_[0-9A-Za-z]{40}"), raw);
        ApiKey key = created.key();
        assertEquals(AuthAuditService.hash(raw), key.getKeyHash());
        assertEquals(raw.substring(0, 12), key.getDisplayPrefix());
        assertEquals("Shul sync", key.getName());
        assertEquals("MGR", key.getAccountId());
        assertEquals("SUPER", key.getCreatedByAccountId());
        assertEquals(Set.of(ApiScope.OVERRIDES_WRITE), key.getScopes());
        assertEquals(Set.of("ORGA"), key.getRestrictedOrganizationIds());
        assertFalse(key.isSameAsOwner());
        assertEquals(LocalDateTime.now(CLOCK), key.getCreatedAt());

        // The raw key never reaches the entity, its toString, the result's toString or the audit trail.
        assertFalse(key.toString().contains(raw));
        assertFalse(key.toString().contains(key.getKeyHash()));
        assertFalse(created.toString().contains(raw));
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq("API_KEY_CREATED"), eq("MGR"), any(), details.capture());
        assertFalse(details.getValue().contains(raw));
        assertTrue(details.getValue().contains(key.getId()));
    }

    @Test
    void eachKeyIsDifferent() {
        String first = service.create("MGR", "a", true, Set.of(), Set.of()).rawKey();
        String second = service.create("MGR", "b", true, Set.of(), Set.of()).rawKey();
        assertNotEquals(first, second);
    }

    @Test
    void sameAsOwnerIgnoresScopesAndNeedsNone() {
        ApiKey key = service.create("MGR", "full", true, Set.of(ApiScope.SHUL_WRITE), null).key();
        assertTrue(key.isSameAsOwner());
        assertTrue(key.getScopes().isEmpty());
        assertTrue(key.getRestrictedOrganizationIds().isEmpty());
    }

    @Test
    void createValidatesInput() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("MGR", "  ", false, Set.of(ApiScope.SCHEDULE_READ), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> service.create("MGR", "x".repeat(101), false, Set.of(ApiScope.SCHEDULE_READ), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> service.create("MGR", "no scopes", false, Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> service.create("MISSING", "owner", true, Set.of(), Set.of()));
        manager.setEnabled(false);
        assertThrows(IllegalArgumentException.class,
                () -> service.create("MGR", "disabled owner", true, Set.of(), Set.of()));
        verify(repository, never()).save(any());
    }

    @Test
    void restrictionMustBeShulsTheOwnerCanAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("MGR", "other shul", true, Set.of(), Set.of("ORGA", "ORGC")));
        assertEquals(Set.of("ORGA", "ORGB"),
                service.create("MGR", "both", true, Set.of(), Set.of("ORGA", "ORGB")).key().getRestrictedOrganizationIds());

        // A super-admin owner can be limited to any existing shul, but not to an unknown one.
        when(organizations.existsById("ORGC")).thenReturn(true);
        when(organizations.existsById("NOPE")).thenReturn(false);
        assertEquals(Set.of("ORGC"),
                service.create("SUPER", "super key", true, Set.of(), Set.of("ORGC")).key().getRestrictedOrganizationIds());
        assertThrows(IllegalArgumentException.class,
                () -> service.create("SUPER", "unknown", true, Set.of(), Set.of("NOPE")));
    }

    @Test
    void onlySuperAdminsManageKeys() {
        when(users.isSuperAdmin()).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.create("MGR", "k", true, Set.of(), Set.of()));
        assertThrows(AccessDeniedException.class, () -> service.revoke("K1"));
        assertThrows(AccessDeniedException.class, service::listAll);
        assertThrows(AccessDeniedException.class, () -> service.listForAccount("MGR"));
        verify(repository, never()).save(any());
    }

    @Test
    void anApiKeyCannotManageKeysEvenForASuperAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new ApiAuthentication(ApiAuthentication.Kind.KEY,
                "SUPER", "super", Role.ADMIN.getName(), Set.of(), true, Set.of(), "K1"));
        assertThrows(AccessDeniedException.class, () -> service.create("MGR", "k", true, Set.of(), Set.of()));
        assertThrows(AccessDeniedException.class, () -> service.revoke("K1"));
    }

    @Test
    void resolveFindsActiveKeyByHashAndRecordsUse() {
        String raw = SecureTokens.newApiKey();
        ApiKey key = ApiKey.builder().id("K1").accountId("MGR").keyHash(AuthAuditService.hash(raw)).build();
        when(repository.findByKeyHash(AuthAuditService.hash(raw))).thenReturn(Optional.of(key));

        assertEquals(Optional.of(key), service.resolve(raw, null));
        verify(repository).markUsed(eq("K1"), eq(LocalDateTime.now(CLOCK)), isNull());
        assertEquals(LocalDateTime.now(CLOCK), key.getLastUsedAt());

        // A second use within a minute does not write again.
        service.resolve(raw, null);
        verify(repository, times(1)).markUsed(anyString(), any(), any());
    }

    @Test
    void resolveRejectsMalformedUnknownAndRevokedKeys() {
        assertTrue(service.resolve(null, null).isEmpty());
        assertTrue(service.resolve("tnm_short", null).isEmpty());
        assertTrue(service.resolve("tnmapp_" + "a".repeat(43), null).isEmpty());
        verify(repository, never()).findByKeyHash(any());

        String unknown = SecureTokens.newApiKey();
        when(repository.findByKeyHash(any())).thenReturn(Optional.empty());
        assertTrue(service.resolve(unknown, null).isEmpty());

        String revoked = SecureTokens.newApiKey();
        when(repository.findByKeyHash(AuthAuditService.hash(revoked))).thenReturn(Optional.of(
                ApiKey.builder().id("K2").keyHash(AuthAuditService.hash(revoked)).revokedAt(LocalDateTime.now(CLOCK)).build()));
        assertTrue(service.resolve(revoked, null).isEmpty());
        verify(repository, never()).markUsed(anyString(), any(), any());
    }

    @Test
    void revokeMarksKeyAndAudits() {
        ApiKey key = ApiKey.builder().id("K1").accountId("MGR").build();
        when(repository.findById("K1")).thenReturn(Optional.of(key));

        ApiKey revoked = service.revoke("K1");

        assertTrue(revoked.isRevoked());
        assertEquals("SUPER", revoked.getRevokedByAccountId());
        assertEquals("MANUAL", revoked.getRevokedReason());
        verify(audit).record(eq("API_KEY_REVOKED"), eq("MGR"), any(), contains("key=K1"));

        // Revoking again is a no-op; an unknown id is an error.
        service.revoke("K1");
        verify(repository, times(1)).save(any());
        assertThrows(IllegalArgumentException.class, () -> service.revoke("missing"));
    }

    @Test
    void revokeAllForAccountAuditsOnlyWhenSomethingWasRevoked() {
        when(repository.revokeAllActiveForAccount(eq("MGR"), any(), eq("SUPER"), eq("USER_DISABLED"))).thenReturn(2);
        assertEquals(2, service.revokeAllForAccount("MGR", "USER_DISABLED"));
        verify(audit).record(eq("API_KEY_REVOKED"), eq("MGR"), any(), contains("count=2"));

        when(repository.revokeAllActiveForAccount(eq("OTHER"), any(), any(), any())).thenReturn(0);
        assertEquals(0, service.revokeAllForAccount("OTHER", "USER_DISABLED"));
        verify(audit, never()).record(any(), eq("OTHER"), any(), any());
    }

    @Test
    void scopesParseFromWireValuesAndNames() {
        assertEquals(ApiScope.OVERRIDES_WRITE, ApiScope.fromValue("overrides:write"));
        assertEquals(ApiScope.SHUL_WRITE, ApiScope.fromValue("SHUL_WRITE"));
        assertEquals("schedule:read", ApiScope.SCHEDULE_READ.value());
        assertThrows(IllegalArgumentException.class, () -> ApiScope.fromValue("admin:all"));
        assertEquals(5, EnumSet.allOf(ApiScope.class).size());
    }

    static TNMUser user(String id, String username, String organizationId, Role role) {
        return TNMUser.builder().id(id).username(username).email(username + "@example.com")
                .encryptedPassword("!PASSWORDLESS").organizationId(organizationId).roleId(role.getId())
                .enabled(true).build();
    }
}
