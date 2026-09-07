package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TNMUserServiceTest {

    @Test
    void saveNormalizesEmailBeforePersistence() {
        TNMUserRepository repository = mock(TNMUserRepository.class);
        TNMUserService service = new TNMUserService(repository);
        TNMUser user = user("  Manager@Example.COM  ");

        service.save(user);

        ArgumentCaptor<TNMUser> captor = ArgumentCaptor.forClass(TNMUser.class);
        verify(repository).save(captor.capture());
        assertEquals("manager@example.com", captor.getValue().getEmailNormalized());
    }

    @Test
    void findByNormalizedEmailUsesCanonicalEmailValue() {
        TNMUserRepository repository = mock(TNMUserRepository.class);
        TNMUserService service = new TNMUserService(repository);
        TNMUser expected = user("manager@example.com");
        when(repository.findByEmailNormalized("manager@example.com")).thenReturn(Optional.of(expected));

        TNMUser actual = service.findByNormalizedEmail("  Manager@Example.COM  ");

        assertEquals(expected, actual);
    }

    @Test
    void findByNormalizedEmailIgnoresBlankEmail() {
        TNMUserRepository repository = mock(TNMUserRepository.class);
        TNMUserService service = new TNMUserService(repository);

        assertNull(service.findByNormalizedEmail("   "));
    }

    @Test
    void findByIdentifierPrefersUsernameBeforeEmailLookup() {
        TNMUserRepository repository = mock(TNMUserRepository.class);
        TNMUserService service = new TNMUserService(repository);
        TNMUser expected = user("manager@example.com");
        when(repository.findByUsername("Manager")).thenReturn(Optional.of(expected));

        TNMUser actual = service.findByIdentifier("  Manager  ");

        assertEquals(expected, actual);
        verify(repository).findByUsername("Manager");
    }

    @Test
    void findByIdentifierFallsBackToCaseInsensitiveUsername() {
        TNMUserRepository repository = mock(TNMUserRepository.class);
        TNMUserService service = new TNMUserService(repository);
        TNMUser expected = user("manager@example.com");
        when(repository.findByUsernameIgnoreCase("Manager")).thenReturn(Optional.of(expected));

        TNMUser actual = service.findByIdentifier("  Manager  ");

        assertEquals(expected, actual);
        verify(repository).findByUsernameIgnoreCase("Manager");
    }

    @Test
    void findByIdentifierFallsBackToNormalizedEmail() {
        TNMUserRepository repository = mock(TNMUserRepository.class);
        TNMUserService service = new TNMUserService(repository);
        TNMUser expected = user("manager@example.com");
        when(repository.findByEmailNormalized("manager@example.com")).thenReturn(Optional.of(expected));

        TNMUser actual = service.findByIdentifier("  Manager@Example.COM  ");

        assertEquals(expected, actual);
    }

    @Test
    void findByIdentifierFallsBackToCaseInsensitiveEmailWhenNormalizedEmailMissing() {
        TNMUserRepository repository = mock(TNMUserRepository.class);
        TNMUserService service = new TNMUserService(repository);
        TNMUser expected = user("manager@example.com");
        when(repository.findByEmailIgnoreCase("manager@example.com")).thenReturn(Optional.of(expected));

        TNMUser actual = service.findByIdentifier("  Manager@Example.COM  ");

        assertEquals(expected, actual);
    }

    @Test
    void weeklyMinyanReviewRecipientsAreEnabledOptedInAdminsOnly() {
        TNMUserRepository repository = mock(TNMUserRepository.class);
        TNMUserService service = new TNMUserService(repository);
        TNMUser orgAdmin = user("A1", "manager", "org-a", Role.ADMIN, true, true);
        TNMUser superAdmin = user("A2", "super", null, Role.ADMIN, true, null);
        TNMUser disabledAdmin = user("A3", "disabled", "org-a", Role.ADMIN, false, true);
        TNMUser optedOutAdmin = user("A4", "optedout", "org-a", Role.ADMIN, true, false);
        TNMUser orgUser = user("A5", "user", "org-a", Role.USER, true, true);
        TNMUser missingRole = user("A6", "missingrole", "org-a", Role.ADMIN, true, true);
        missingRole.setRoleId(null);
        when(repository.findAll()).thenReturn(List.of(orgAdmin, superAdmin, disabledAdmin, optedOutAdmin, orgUser, missingRole));

        List<TNMUser> recipients = service.getWeeklyMinyanReviewEmailRecipients();

        assertIterableEquals(List.of(orgAdmin, superAdmin), recipients);
    }

    private TNMUser user(String email) {
        return TNMUser.builder()
                .id("A1")
                .username("manager")
                .email(email)
                .encryptedPassword("encrypted")
                .organizationId("org-a")
                .roleId(Role.ADMIN.getId())
                .enabled(true)
                .build();
    }

    private TNMUser user(
            String id,
            String username,
            String organizationId,
            Role role,
            boolean enabled,
            Boolean weeklyMinyanReviewEmailsEnabled) {
        return TNMUser.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .encryptedPassword("encrypted")
                .organizationId(organizationId)
                .roleId(role.getId())
                .enabled(enabled)
                .weeklyMinyanReviewEmailsEnabled(weeklyMinyanReviewEmailsEnabled)
                .build();
    }
}
