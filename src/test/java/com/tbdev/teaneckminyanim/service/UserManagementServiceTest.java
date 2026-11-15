package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.repo.MagicLinkTokenRepository;
import com.tbdev.teaneckminyanim.repo.AuthAuditEventRepository;
import com.tbdev.teaneckminyanim.service.auth.PasskeyCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserManagementServiceTest {
    private final TNMUserService users = mock(TNMUserService.class);
    private final TNMUserRepository repository = mock(TNMUserRepository.class);
    private final OrganizationService organizations = mock(OrganizationService.class);
    private final PasskeyCredentialService passkeys = mock(PasskeyCredentialService.class);
    private final MagicLinkTokenRepository magicLinkTokens = mock(MagicLinkTokenRepository.class);
    private final AuthAuditEventRepository authAuditEvents = mock(AuthAuditEventRepository.class);
    private final UserManagementService service = new UserManagementService(
            users, repository, organizations, passkeys, magicLinkTokens, authAuditEvents);
    private TNMUser actor;

    @BeforeEach
    void setup() {
        actor = user("super", null, Role.ADMIN);
        when(users.getCurrentUser()).thenReturn(actor);
        when(repository.findAll()).thenReturn(List.of(actor));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(organizations.findById("one")).thenReturn(Optional.of(new Organization()));
        when(organizations.findById("two")).thenReturn(Optional.of(new Organization()));
    }

    @Test
    void createsPasswordlessManagerWithMultipleShuls() {
        TNMUser created = service.create("Manager1", " PERSON@Example.com ", "MANAGER", Set.of("one", "two"));
        assertEquals("person@example.com", created.getEmailNormalized());
        assertEquals("!PASSWORDLESS", created.getEncryptedPassword());
        assertEquals(Set.of("one", "two"), created.getAccessibleOrganizationIds());
        assertTrue(created.canAccessOrganization("two"));
        assertFalse(created.canAccessOrganization("unassigned"));
        assertFalse(created.isSuperAdmin());
    }

    @Test
    void rejectsEmptyUnknownOrInvalidScope() {
        assertThrows(IllegalArgumentException.class, () -> service.create("manager", "a@example.com", "MANAGER", Set.of()));
        assertThrows(IllegalArgumentException.class, () -> service.create("manager", "a@example.com", "USER", Set.of("missing")));
        assertThrows(IllegalArgumentException.class, () -> service.create("manager", "a@example.com", "SUPER_ADMIN", Set.of("one")));
        assertThrows(IllegalArgumentException.class, () -> service.create("manager", "a@example.com", "OWNER", Set.of("one")));
        verify(repository, never()).save(any());
    }

    @Test
    void orgManagerAndDisabledSuperAdminCannotAssignPermissions() {
        actor.setOrganizationId("one");
        assertThrows(AccessDeniedException.class, () -> service.create("manager", "a@example.com", "SUPER_ADMIN", Set.of()));
        actor.setOrganizationId(null);
        actor.setEnabled(false);
        assertThrows(AccessDeniedException.class, service::requireSuperAdmin);
        verify(repository, never()).save(any());
    }

    @Test
    void cannotDisableOrDemoteSelf() {
        when(repository.findById("super")).thenReturn(Optional.of(actor));
        assertThrows(IllegalArgumentException.class, () -> service.setEnabled("super", false));
        assertThrows(IllegalArgumentException.class, () -> service.update("super", actor.getEmail(), "MANAGER", Set.of("one")));
    }

    @Test
    void statusCanBeToggledAndIsPreservedWhenSavingPermissions() {
        TNMUser target = user("target", "one", Role.USER);
        when(repository.findById("target")).thenReturn(Optional.of(target));

        service.setEnabled("target", false);
        assertTrue(target.isDisabled());
        service.update("target", target.getEmail(), "USER", Set.of("two"));
        assertTrue(target.isDisabled());
        service.setEnabled("target", true);
        assertTrue(target.isEnabled());
    }

    @Test
    void replacesAssignmentsAndPreservesAuthMetadata() {
        TNMUser target = user("target", "one", Role.ADMIN);
        target.getAdditionalOrganizationIds().add("two");
        target.setLastLoginMethod("PASSKEY");
        when(repository.findById("target")).thenReturn(Optional.of(target));
        service.update("target", target.getEmail(), "USER", Set.of("two"));
        assertEquals("two", target.getOrganizationId());
        assertFalse(target.canAccessOrganization("one"));
        assertEquals("PASSKEY", target.getLastLoginMethod());
        assertEquals(Role.USER, target.role());
        service.update("target", target.getEmail(), "SUPER_ADMIN", Set.of());
        assertTrue(target.isSuperAdmin());
        assertTrue(target.getAdditionalOrganizationIds().isEmpty());
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("newuser", "SUPER@EXAMPLE.COM", "USER", Set.of("one")));
        verify(repository, never()).save(any());
    }

    @Test
    void deletesDisabledUserAndAuthenticationMaterial() {
        TNMUser target = user("target", "one", Role.USER);
        target.setEnabled(false);
        when(repository.findById("target")).thenReturn(Optional.of(target));

        assertEquals("target", service.delete("target"));

        verify(passkeys).revokeAll(target);
        verify(magicLinkTokens).deleteByAccountId("target");
        verify(authAuditEvents).deleteByAccountId("target");
        verify(repository).delete(target);
    }

    @Test
    void refusesToDeleteEnabledUserOrCurrentSuperAdmin() {
        TNMUser enabled = user("target", "one", Role.USER);
        when(repository.findById("target")).thenReturn(Optional.of(enabled));
        when(repository.findById("super")).thenReturn(Optional.of(actor));

        assertThrows(IllegalArgumentException.class, () -> service.delete("target"));
        assertThrows(IllegalArgumentException.class, () -> service.delete("super"));
        verify(repository, never()).delete(any());
    }

    @Test
    void managerCreatesUserOrManagerScopedToOneManagedShul() {
        actor.setOrganizationId("one");
        actor.getAdditionalOrganizationIds().add("two");

        TNMUser user = service.createForOrganization("newuser", "New@Example.com", "USER", "two");
        TNMUser manager = service.createForOrganization("newmanager", "mgr@example.com", "MANAGER", "one");

        assertEquals(Role.USER, user.role());
        assertEquals(Set.of("two"), user.getAccessibleOrganizationIds());
        assertEquals("!PASSWORDLESS", user.getEncryptedPassword());
        assertEquals(Role.ADMIN, manager.role());
        assertFalse(manager.isSuperAdmin());
        assertEquals(Set.of("one"), manager.getAccessibleOrganizationIds());
    }

    @Test
    void organizationCreateRejectsUnrelatedShulSuperAdminAndNonManagers() {
        actor.setOrganizationId("one");
        assertThrows(AccessDeniedException.class,
                () -> service.createForOrganization("newuser", "a@example.com", "USER", "two"));
        assertThrows(AccessDeniedException.class,
                () -> service.createForOrganization("newuser", "a@example.com", "USER", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.createForOrganization("newuser", "a@example.com", "SUPER_ADMIN", "one"));
        actor.setRoleId(Role.USER.getId());
        assertThrows(AccessDeniedException.class,
                () -> service.createForOrganization("newuser", "a@example.com", "USER", "one"));
        actor.setRoleId(Role.ADMIN.getId());
        actor.setEnabled(false);
        assertThrows(AccessDeniedException.class,
                () -> service.createForOrganization("newuser", "a@example.com", "USER", "one"));
        verify(repository, never()).save(any());
    }

    @Test
    void superAdminOrganizationCreateRequiresExistingShul() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createForOrganization("newuser", "a@example.com", "USER", "missing"));
        assertEquals(Set.of("two"),
                service.createForOrganization("newuser", "a@example.com", "MANAGER", "two").getAccessibleOrganizationIds());
    }

    @Test
    void accountSettingsScopeCoversPeersButNotSuperAdminsOrBroaderScopes() {
        TNMUser manager = user("manager", "one", Role.ADMIN);
        TNMUser peer = user("peer", "one", Role.ADMIN);
        TNMUser plain = user("plain", "one", Role.USER);
        TNMUser broader = user("broader", "one", Role.ADMIN);
        broader.getAdditionalOrganizationIds().add("two");
        TNMUser otherShul = user("other", "two", Role.USER);

        assertTrue(manager.canManageAccount(manager));
        assertTrue(manager.canManageAccount(peer));
        assertTrue(manager.canManageAccount(plain));
        assertFalse(manager.canManageAccount(actor));
        assertFalse(manager.canManageAccount(broader));
        assertFalse(manager.canManageAccount(otherShul));
        assertFalse(plain.canManageAccount(peer));
        assertFalse(plain.canManageAccount(user("plain2", "one", Role.USER)));
        assertTrue(plain.canManageAccount(plain));
        assertTrue(actor.canManageAccount(broader));
        manager.setEnabled(false);
        assertFalse(manager.canManageAccount(peer));
    }

    private TNMUser user(String id, String org, Role role) {
        return TNMUser.builder().id(id).username(id).email(id + "@example.com")
                .organizationId(org).roleId(role.getId()).build();
    }
}
