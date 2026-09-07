package com.tbdev.teaneckminyanim.service.email;

import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationService;
import com.tbdev.teaneckminyanim.service.EffectiveScheduleService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.ScheduleEnrichmentService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.calendar.CalendarImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WeeklyMinyanReviewEmailServiceTest {

    private TNMUserService userService;
    private OrganizationService organizationService;
    private EffectiveScheduleService effectiveScheduleService;
    private ScheduleEnrichmentService scheduleEnrichmentService;
    private CalendarImportService calendarImportService;
    private CalendarMaterializationService materializationService;
    private ApplicationSettingsService settingsService;
    private EmailService emailService;
    private WeeklyMinyanReviewEmailService service;

    @BeforeEach
    void setUp() {
        userService = mock(TNMUserService.class);
        organizationService = mock(OrganizationService.class);
        effectiveScheduleService = mock(EffectiveScheduleService.class);
        scheduleEnrichmentService = mock(ScheduleEnrichmentService.class);
        calendarImportService = mock(CalendarImportService.class);
        materializationService = mock(CalendarMaterializationService.class);
        settingsService = mock(ApplicationSettingsService.class);
        emailService = mock(EmailService.class);

        when(settingsService.getSiteName()).thenReturn("Teaneck Minyanim");
        when(settingsService.getSiteRootUrl()).thenReturn("https://www.teaneckminyanim.com/");
        when(settingsService.getZoneId()).thenReturn(ZoneId.of("America/New_York"));
        when(scheduleEnrichmentService.annotateZmanim(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service = new WeeklyMinyanReviewEmailService(
                userService,
                organizationService,
                effectiveScheduleService,
                scheduleEnrichmentService,
                calendarImportService,
                materializationService,
                settingsService,
                emailService);
    }

    @Test
    void saturdayNightWindowCoversUpcomingSundayThroughSaturday() {
        WeeklyMinyanReviewEmailService.DateRange range =
                service.upcomingSundayThroughSaturday(LocalDate.of(2026, 9, 5));

        assertEquals(LocalDate.of(2026, 9, 6), range.startDate());
        assertEquals(LocalDate.of(2026, 9, 12), range.endDate());
    }

    @Test
    void sendsRoleScopedDigestsWithOverrideLinks() {
        TNMUser superAdmin = user("A0", "super", null, Role.ADMIN);
        TNMUser orgAdmin = user("A1", "manager", "org-a", Role.ADMIN);
        Organization orgA = organization("org-a", "Congregation A", true);
        Organization orgB = organization("org-b", "Congregation B", true);
        LocalDate start = LocalDate.of(2026, 9, 6);
        LocalDate end = LocalDate.of(2026, 9, 12);

        when(userService.getWeeklyMinyanReviewEmailRecipients()).thenReturn(List.of(superAdmin, orgAdmin));
        when(organizationService.getAll()).thenReturn(List.of(orgA, orgB));
        when(effectiveScheduleService.getEffectiveEventsInRange("org-a", start, end))
                .thenReturn(List.of(
                        event("org-a", start, LocalTime.of(7, 0), MinyanType.SHACHARIS, EventSource.RULES),
                        event("org-a", start.plusDays(1), LocalTime.of(18, 30), MinyanType.MINCHA, EventSource.MANUAL)));
        when(effectiveScheduleService.getEffectiveEventsInRange("org-b", start, end))
                .thenReturn(List.of(
                        event("org-b", start, LocalTime.of(8, 15), MinyanType.SHACHARIS, EventSource.IMPORTED)));
        when(emailService.send(any())).thenReturn(EmailSendResult.success(EmailProvider.SMTP, "sent"));

        WeeklyMinyanReviewEmailService.WeeklyMinyanReviewSendResult result =
                service.sendWeeklyReviewEmails(false, LocalDate.of(2026, 9, 5));

        assertEquals(2, result.recipientCount());
        assertEquals(2, result.sentCount());
        assertEquals(0, result.failedCount());
        verifyNoInteractions(calendarImportService, materializationService);

        ArgumentCaptor<EmailMessage> messages = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService, org.mockito.Mockito.times(2)).send(messages.capture());

        EmailMessage superMessage = messages.getAllValues().stream()
                .filter(message -> message.getTo().contains("super@example.com"))
                .findFirst()
                .orElseThrow();
        assertTrue(superMessage.getTextBody().contains("Congregation A: 2 minyanim"));
        assertTrue(superMessage.getTextBody().contains("Congregation B: 1 minyanim"));
        assertTrue(superMessage.getTextBody().contains(
                "https://www.teaneckminyanim.com/admin/super/overrides?startDate=2026-09-06&endDate=2026-09-12"));
        assertFalse(superMessage.getTextBody().contains("8:15 AM Shacharis"));

        EmailMessage orgMessage = messages.getAllValues().stream()
                .filter(message -> message.getTo().contains("manager@example.com"))
                .findFirst()
                .orElseThrow();
        assertEquals("org-a", orgMessage.getOrganizationId());
        assertTrue(orgMessage.getTextBody().contains("7:00 AM Shacharis"));
        assertTrue(orgMessage.getTextBody().contains("[Manual]"));
        assertFalse(orgMessage.getTextBody().contains("Congregation B"));
        assertTrue(orgMessage.getTextBody().contains(
                "https://www.teaneckminyanim.com/admin/org-a/overrides?startDate=2026-09-06&endDate=2026-09-12"));
    }

    @Test
    void refreshesImportsAndMaterializedEventsBeforeSendingScheduledDigest() {
        when(userService.getWeeklyMinyanReviewEmailRecipients())
                .thenReturn(List.of(user("A0", "super", null, Role.ADMIN)));
        when(organizationService.getAll()).thenReturn(List.of());
        when(calendarImportService.importAllEnabledOrganizations()).thenReturn(Map.of());
        when(emailService.send(any())).thenReturn(EmailSendResult.success(EmailProvider.SMTP, "sent"));

        service.sendWeeklyReviewEmails(true, LocalDate.of(2026, 9, 5));

        InOrder inOrder = inOrder(calendarImportService, materializationService, emailService);
        inOrder.verify(calendarImportService).importAllEnabledOrganizations();
        inOrder.verify(materializationService).materializeAll();
        inOrder.verify(emailService).send(any());
    }

    @Test
    void refreshFailureSkipsEmailSend() {
        RuntimeException failure = new RuntimeException("import failed");
        doThrow(failure).when(calendarImportService).importAllEnabledOrganizations();

        WeeklyMinyanReviewEmailService.WeeklyMinyanReviewSendResult result =
                service.sendWeeklyReviewEmails(true, LocalDate.of(2026, 9, 5));

        assertFalse(result.refreshSucceeded());
        assertEquals("import failed", result.refreshError());
        verifyNoInteractions(emailService);
    }

    @Test
    void weeklyDigestTestSendsOnlyToRequestedAdminAccount() {
        TNMUser superAdmin = user("A0", "super", null, Role.ADMIN);
        Organization orgA = organization("org-a", "Congregation A", true);
        LocalDate start = LocalDate.of(2026, 9, 6);
        LocalDate end = LocalDate.of(2026, 9, 12);

        when(organizationService.getAll()).thenReturn(List.of(orgA));
        when(effectiveScheduleService.getEffectiveEventsInRange("org-a", start, end))
                .thenReturn(List.of(
                        event("org-a", start, LocalTime.of(7, 0), MinyanType.SHACHARIS, EventSource.RULES)));
        when(emailService.send(any())).thenReturn(EmailSendResult.success(EmailProvider.SMTP, "sent"));

        WeeklyMinyanReviewEmailService.WeeklyMinyanReviewTestSendResult result =
                service.sendWeeklyReviewTestEmail(superAdmin, false, LocalDate.of(2026, 9, 5));

        assertTrue(result.success());
        assertEquals("sent", result.message());
        verify(userService, never()).getWeeklyMinyanReviewEmailRecipients();
        verifyNoInteractions(calendarImportService, materializationService);

        ArgumentCaptor<EmailMessage> messages = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(messages.capture());

        EmailMessage message = messages.getValue();
        assertEquals(List.of("super@example.com"), message.getTo());
        assertTrue(message.getSubject().startsWith("[TEST] Teaneck Minyanim weekly minyan review"));
        assertEquals("true", message.getMetadata().get("test"));
        assertTrue(message.getTextBody().contains("Congregation A: 1 minyanim"));
    }

    private TNMUser user(String id, String username, String organizationId, Role role) {
        return TNMUser.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .encryptedPassword("encrypted")
                .organizationId(organizationId)
                .roleId(role.getId())
                .enabled(true)
                .weeklyMinyanReviewEmailsEnabled(true)
                .build();
    }

    private Organization organization(String id, String name, boolean enabled) {
        return Organization.builder()
                .id(id)
                .name(name)
                .address("1 Main St")
                .orgColor("#123456")
                .enabled(enabled)
                .build();
    }

    private CalendarEvent event(
            String organizationId,
            LocalDate date,
            LocalTime startTime,
            MinyanType minyanType,
            EventSource source) {
        return CalendarEvent.builder()
                .id(Math.abs(Objects.hash(organizationId, date, startTime, minyanType, source)) + 1L)
                .organizationId(organizationId)
                .date(date)
                .startTime(startTime)
                .minyanType(minyanType)
                .source(source)
                .enabled(true)
                .locationName("Main")
                .notes(source == EventSource.MANUAL ? "Temporary schedule" : null)
                .dynamicTimeString(source == EventSource.RULES ? "Fixed time" : null)
                .build();
    }
}
