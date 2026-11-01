package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
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
    private final UserManagementService service = new UserManagementService(users, repository, organizations);
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
        assertThrows(IllegalArgumentException.class, () -> service.update("super", actor.getEmail(), "SUPER_ADMIN", Set.of(), false));
        assertThrows(IllegalArgumentException.class, () -> service.update("super", actor.getEmail(), "MANAGER", Set.of("one"), true));
    }

    @Test
    void replacesAssignmentsAndPreservesAuthMetadata() {
        TNMUser target = user("target", "one", Role.ADMIN);
        target.getAdditionalOrganizationIds().add("two");
        target.setLastLoginMethod("PASSKEY");
        when(repository.findById("target")).thenReturn(Optional.of(target));
        service.update("target", target.getEmail(), "USER", Set.of("two"), true);
        assertEquals("two", target.getOrganizationId());
        assertFalse(target.canAccessOrganization("one"));
        assertEquals("PASSKEY", target.getLastLoginMethod());
        assertEquals(Role.USER, target.role());
        service.update("target", target.getEmail(), "SUPER_ADMIN", Set.of(), true);
        assertTrue(target.isSuperAdmin());
        assertTrue(target.getAdditionalOrganizationIds().isEmpty());
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("newuser", "SUPER@EXAMPLE.COM", "USER", Set.of("one")));
        verify(repository, never()).save(any());
    }

    private TNMUser user(String id, String org, Role role) {
        return TNMUser.builder().id(id).username(id).email(id + "@example.com")
                .organizationId(org).roleId(role.getId()).build();
    }
}
