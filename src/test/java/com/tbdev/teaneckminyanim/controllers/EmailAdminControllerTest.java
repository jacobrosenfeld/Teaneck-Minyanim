package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.email.EmailProvider;
import com.tbdev.teaneckminyanim.service.email.EmailSendResult;
import com.tbdev.teaneckminyanim.service.email.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
        TNMUserService userService = mock(TNMUserService.class);
        EmailAdminController controller = new EmailAdminController(emailService, userService);
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
        TNMUserService userService = mock(TNMUserService.class);
        EmailAdminController controller = new EmailAdminController(emailService, userService);
        authenticate("manager");
        when(userService.findByName("manager")).thenReturn(user("manager", "org-a", Role.ADMIN));

        assertThrows(AccessDeniedException.class,
                () -> controller.sendTestEmail("admin@example.com"));
        verify(emailService, never()).sendTestEmail("admin@example.com");
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "password",
                        List.of(new SimpleGrantedAuthority(Role.ADMIN.getName()))));
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
