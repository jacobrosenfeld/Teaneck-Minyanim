package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.email.EmailProvider;
import com.tbdev.teaneckminyanim.service.email.EmailSendResult;
import com.tbdev.teaneckminyanim.service.email.EmailService;
import com.tbdev.teaneckminyanim.service.email.WeeklyMinyanReviewEmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailAdminControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdminCanSendTestEmail() {
        EmailService emailService = mock(EmailService.class);
        WeeklyMinyanReviewEmailService weeklyMinyanReviewEmailService = mock(WeeklyMinyanReviewEmailService.class);
        TNMUserService userService = mock(TNMUserService.class);
        EmailAdminController controller = new EmailAdminController(
                emailService,
                weeklyMinyanReviewEmailService,
                userService);
        authenticate("super");
        when(userService.findByName("super")).thenReturn(user("super", null, Role.ADMIN));
        when(emailService.sendTestEmail("admin@example.com"))
                .thenReturn(EmailSendResult.success(EmailProvider.SMTP, "Email sent successfully."));

        ResponseEntity<EmailAdminController.TestEmailResponse> response =
                controller.sendTestEmail("admin@example.com");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().success());
        assertEquals("SMTP", response.getBody().provider());
    }

    @Test
    void organizationAdminCannotSendTestEmail() {
        EmailService emailService = mock(EmailService.class);
        WeeklyMinyanReviewEmailService weeklyMinyanReviewEmailService = mock(WeeklyMinyanReviewEmailService.class);
        TNMUserService userService = mock(TNMUserService.class);
        EmailAdminController controller = new EmailAdminController(
                emailService,
                weeklyMinyanReviewEmailService,
                userService);
        authenticate("manager");
        when(userService.findByName("manager")).thenReturn(user("manager", "org-a", Role.ADMIN));

        assertThrows(AccessDeniedException.class,
                () -> controller.sendTestEmail("admin@example.com"));
        verify(emailService, never()).sendTestEmail("admin@example.com");
    }

    @Test
    void adminCanSendWeeklyDigestTestEmailToSelf() {
        EmailService emailService = mock(EmailService.class);
        WeeklyMinyanReviewEmailService weeklyMinyanReviewEmailService = mock(WeeklyMinyanReviewEmailService.class);
        TNMUserService userService = mock(TNMUserService.class);
        EmailAdminController controller = new EmailAdminController(
                emailService,
                weeklyMinyanReviewEmailService,
                userService);
        TNMUser manager = user("manager", "org-a", Role.ADMIN);
        WeeklyMinyanReviewEmailService.DateRange range =
                new WeeklyMinyanReviewEmailService.DateRange(
                        LocalDate.of(2026, 9, 6),
                        LocalDate.of(2026, 9, 12));

        authenticate("manager");
        when(userService.findByName("manager")).thenReturn(manager);
        when(weeklyMinyanReviewEmailService.sendWeeklyReviewTestEmail(manager, true))
                .thenReturn(new WeeklyMinyanReviewEmailService.WeeklyMinyanReviewTestSendResult(
                        range,
                        "Congregation A",
                        true,
                        true,
                        null,
                        "sent"));

        ResponseEntity<EmailAdminController.WeeklyDigestTestEmailResponse> response =
                controller.sendWeeklyDigestTestEmail(true, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().success());
        assertEquals("Sep 6, 2026 - Sep 12, 2026", response.getBody().range());
        assertEquals("Weekly digest test sent for Congregation A, Sep 6, 2026 - Sep 12, 2026.",
                response.getBody().message());
    }

    @Test
    void superAdminCanSendSelectedShulDigestTestEmailToSelf() {
        EmailService emailService = mock(EmailService.class);
        WeeklyMinyanReviewEmailService weeklyMinyanReviewEmailService = mock(WeeklyMinyanReviewEmailService.class);
        TNMUserService userService = mock(TNMUserService.class);
        EmailAdminController controller = new EmailAdminController(
                emailService,
                weeklyMinyanReviewEmailService,
                userService);
        TNMUser superAdmin = user("super", null, Role.ADMIN);
        WeeklyMinyanReviewEmailService.DateRange range =
                new WeeklyMinyanReviewEmailService.DateRange(
                        LocalDate.of(2026, 9, 6),
                        LocalDate.of(2026, 9, 12));

        authenticate("super");
        when(userService.findByName("super")).thenReturn(superAdmin);
        when(weeklyMinyanReviewEmailService.sendWeeklyReviewTestEmailForOrganization(superAdmin, "org-a", false))
                .thenReturn(new WeeklyMinyanReviewEmailService.WeeklyMinyanReviewTestSendResult(
                        range,
                        "Congregation A",
                        true,
                        true,
                        null,
                        "sent"));

        ResponseEntity<EmailAdminController.WeeklyDigestTestEmailResponse> response =
                controller.sendWeeklyDigestTestEmail(false, "org-a");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().success());
        assertEquals("Weekly digest test sent for Congregation A, Sep 6, 2026 - Sep 12, 2026.",
                response.getBody().message());
    }

    @Test
    void nonAdminCannotSendWeeklyDigestTestEmail() {
        EmailService emailService = mock(EmailService.class);
        WeeklyMinyanReviewEmailService weeklyMinyanReviewEmailService = mock(WeeklyMinyanReviewEmailService.class);
        TNMUserService userService = mock(TNMUserService.class);
        EmailAdminController controller = new EmailAdminController(
                emailService,
                weeklyMinyanReviewEmailService,
                userService);
        authenticate("viewer", Role.USER);
        when(userService.findByName("viewer")).thenReturn(user("viewer", "org-a", Role.USER));

        assertThrows(AccessDeniedException.class,
                () -> controller.sendWeeklyDigestTestEmail(false, null));
        verify(weeklyMinyanReviewEmailService, never()).sendWeeklyReviewTestEmail(
                org.mockito.Mockito.any(),
                org.mockito.Mockito.anyBoolean());
    }

    @Test
    void organizationAdminCannotSelectAnotherShulForWeeklyDigestTestEmail() {
        EmailService emailService = mock(EmailService.class);
        WeeklyMinyanReviewEmailService weeklyMinyanReviewEmailService = mock(WeeklyMinyanReviewEmailService.class);
        TNMUserService userService = mock(TNMUserService.class);
        EmailAdminController controller = new EmailAdminController(
                emailService,
                weeklyMinyanReviewEmailService,
                userService);
        authenticate("manager");
        when(userService.findByName("manager")).thenReturn(user("manager", "org-a", Role.ADMIN));

        assertThrows(AccessDeniedException.class,
                () -> controller.sendWeeklyDigestTestEmail(false, "org-b"));
        verify(weeklyMinyanReviewEmailService, never()).sendWeeklyReviewTestEmailForOrganization(
                org.mockito.Mockito.any(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyBoolean());
    }

    private void authenticate(String username) {
        authenticate(username, Role.ADMIN);
    }

    private void authenticate(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "password",
                        List.of(new SimpleGrantedAuthority(role.getName()))));
    }

    private TNMUser user(String username, String organizationId, Role role) {
        return TNMUser.builder()
                .id(username)
                .username(username)
                .email(username + "@example.com")
                .encryptedPassword("encrypted")
                .organizationId(organizationId)
                .roleId(role.getId())
                .enabled(true)
                .build();
    }
}
