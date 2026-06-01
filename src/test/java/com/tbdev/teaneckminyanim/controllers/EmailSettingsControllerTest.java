package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.enums.SettingKey;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.VersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailSettingsControllerTest {
    private ApplicationSettingsService settingsService;
    private TNMUserService userService;
    private EmailSettingsController controller;

    @BeforeEach
    void setUp() {
        settingsService = mock(ApplicationSettingsService.class);
        userService = mock(TNMUserService.class);
        OrganizationService organizationService = mock(OrganizationService.class);
        VersionService versionService = mock(VersionService.class);

        lenient().when(settingsService.getSiteName()).thenReturn("Teaneck Minyanim");
        lenient().when(settingsService.getSupportEmail()).thenReturn("support@example.com");
        lenient().when(settingsService.getEmailProvider()).thenReturn("SMTP");
        lenient().when(settingsService.getTimeZone()).thenReturn(TimeZone.getTimeZone("America/New_York"));
        lenient().when(settingsService.getString(any(SettingKey.class)))
                .thenAnswer(invocation -> ((SettingKey) invocation.getArgument(0)).getDefaultValue());
        lenient().when(organizationService.getAll()).thenReturn(List.of());
        lenient().when(versionService.getVersion()).thenReturn("test-version");

        controller = new EmailSettingsController(settingsService, userService, organizationService, versionService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdminCanViewEmailSettingsPage() {
        authenticate("super");
        when(userService.findByName("super")).thenReturn(user("super", null, Role.ADMIN));

        ModelAndView mv = controller.emailSettings();

        assertEquals("admin/email-settings", mv.getViewName());
        assertEquals("SMTP", mv.getModel().get("emailProvider"));
    }

    @Test
    void organizationAdminCannotViewEmailSettingsPage() {
        authenticate("manager");
        when(userService.findByName("manager")).thenReturn(user("manager", "org-a", Role.ADMIN));

        assertThrows(AccessDeniedException.class, () -> controller.emailSettings());
    }

    @Test
    void updateFieldRejectsNonEmailSetting() throws Exception {
        authenticate("super");
        when(userService.findByName("super")).thenReturn(user("super", null, Role.ADMIN));

        ResponseEntity<EmailSettingsController.FieldUpdateResponse> response =
                controller.updateField(SettingKey.SITE_NAME.getKey(), "New Site");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(settingsService, never()).updateSetting(any(), any());
    }

    @Test
    void updateFieldSavesEmailSetting() throws Exception {
        authenticate("super");
        when(userService.findByName("super")).thenReturn(user("super", null, Role.ADMIN));
        when(settingsService.getString(SettingKey.EMAIL_PROVIDER)).thenReturn("SMTP");

        ResponseEntity<EmailSettingsController.FieldUpdateResponse> response =
                controller.updateField(SettingKey.EMAIL_PROVIDER.getKey(), "SMTP");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(settingsService).updateSetting(SettingKey.EMAIL_PROVIDER, "SMTP");
    }

    @Test
    void blankSensitiveFieldLeavesExistingValueUnchanged() throws Exception {
        authenticate("super");
        when(userService.findByName("super")).thenReturn(user("super", null, Role.ADMIN));
        when(settingsService.getString(SettingKey.EMAIL_SMTP_PASSWORD)).thenReturn("secret");

        ResponseEntity<EmailSettingsController.FieldUpdateResponse> response =
                controller.updateField(SettingKey.EMAIL_SMTP_PASSWORD.getKey(), "");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("********", response.getBody().displayValue());
        verify(settingsService, never()).updateSetting(any(), any());
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
