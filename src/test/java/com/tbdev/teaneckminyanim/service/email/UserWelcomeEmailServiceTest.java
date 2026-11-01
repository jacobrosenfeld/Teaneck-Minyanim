package com.tbdev.teaneckminyanim.service.email;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserWelcomeEmailServiceTest {
    private final EmailService emailService = mock(EmailService.class);
    private final ApplicationSettingsService settings = mock(ApplicationSettingsService.class);
    private final OrganizationService organizations = mock(OrganizationService.class);
    private final UserWelcomeEmailService service = new UserWelcomeEmailService(
            emailService, settings, organizations);

    @BeforeEach
    void setUp() {
        when(settings.getSiteName()).thenReturn("Teaneck Minyanim");
        when(emailService.send(any())).thenReturn(EmailSendResult.success(null, "sent"));
        when(organizations.findById("x")).thenReturn(Optional.of(organization("x", "Shul X")));
        when(organizations.findById("y")).thenReturn(Optional.of(organization("y", "Shul Y")));
    }

    @Test
    void userWelcomeNamesEveryAssignedShulAndUserCapabilities() {
        TNMUser user = user(Role.USER, "x", "y");

        service.sendWelcome(user, "https://example.com/activate?token=abc");

        EmailMessage message = capturedMessage();
        assertTrue(message.getTextBody().contains("User access for Shul X and Shul Y"));
        assertTrue(message.getTextBody().contains("minyan times, schedules, locations, and shul details"));
        assertTrue(message.getHtmlBody().contains("Activate access and sign in"));
        assertTrue(message.getHtmlBody().contains("add a passkey"));
    }

    @Test
    void managerAndSuperAdminWelcomeExplainTheirDifferentScope() {
        TNMUser manager = user(Role.ADMIN, "x", null);
        service.sendWelcome(manager, "https://example.com/manager");
        assertTrue(capturedMessage().getTextBody().contains("Manager access for Shul X"));

        TNMUser superAdmin = user(Role.ADMIN, null, null);
        service.sendWelcome(superAdmin, "https://example.com/super");
        EmailMessage superMessage = capturedMessage();
        assertTrue(superMessage.getTextBody().contains("Super admin access for the entire site"));
        assertTrue(superMessage.getTextBody().contains("user permissions, account recovery, and platform settings"));
    }

    private EmailMessage capturedMessage() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        return captor.getAllValues().getLast();
    }

    private TNMUser user(Role role, String primary, String additional) {
        LinkedHashSet<String> additionalIds = new LinkedHashSet<>();
        if (additional != null) additionalIds.add(additional);
        return TNMUser.builder()
                .id("A1")
                .username("newuser")
                .email("new@example.com")
                .roleId(role.getId())
                .organizationId(primary)
                .additionalOrganizationIds(additionalIds)
                .build();
    }

    private Organization organization(String id, String name) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName(name);
        return organization;
    }
}
