package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.enums.SettingKey;
import com.tbdev.teaneckminyanim.model.Minyan;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationService;
import com.tbdev.teaneckminyanim.service.LocationService;
import com.tbdev.teaneckminyanim.service.MinyanService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.TNMSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.VersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static com.tbdev.teaneckminyanim.enums.Role.ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerAuthorizationTest {

    private AdminController controller;
    private TNMUserService userService;
    private OrganizationService organizationService;
    private MinyanService minyanService;
    private ApplicationSettingsService settingsService;

    @BeforeEach
    void setUp() {
        controller = new AdminController();
        userService = mock(TNMUserService.class);
        organizationService = mock(OrganizationService.class);
        minyanService = mock(MinyanService.class);

        settingsService = mock(ApplicationSettingsService.class);
        when(settingsService.getTimeZone()).thenReturn(TimeZone.getTimeZone("America/New_York"));
        when(settingsService.getSiteName()).thenReturn("Teaneck Minyanim");
        when(settingsService.getSupportEmail()).thenReturn("support@example.com");

        VersionService versionService = mock(VersionService.class);
        when(versionService.getVersion()).thenReturn("test-version");

        ReflectionTestUtils.setField(controller, "TNMUserDAO", userService);
        ReflectionTestUtils.setField(controller, "organizationService", organizationService);
        ReflectionTestUtils.setField(controller, "minyanService", minyanService);
        ReflectionTestUtils.setField(controller, "locationDAO", mock(LocationService.class));
        ReflectionTestUtils.setField(controller, "tnmsettingsDAO", mock(TNMSettingsService.class));
        ReflectionTestUtils.setField(controller, "versionService", versionService);
        ReflectionTestUtils.setField(controller, "settingsService", settingsService);
        ReflectionTestUtils.setField(controller, "calendarMaterializationService", mock(CalendarMaterializationService.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void orgAdminCannotViewDifferentOrganizationByChangingQueryParam() {
        authenticate("manager", ADMIN);
        when(userService.findByName("manager")).thenReturn(user("A1", "manager", "org-a", ADMIN));
        when(organizationService.findById("org-b")).thenReturn(Optional.of(organization("org-b")));

        assertThrows(AccessDeniedException.class,
                () -> controller.organization("org-b", null, null, null, null));

        verify(organizationService, never()).getUsersForOrganization(any());
    }

    @Test
    void orgAdminCannotUpdateDifferentOrganizationByChangingFormId() {
        authenticate("manager", ADMIN);
        when(userService.findByName("manager")).thenReturn(user("A1", "manager", "org-a", ADMIN));
        when(organizationService.findById("org-b")).thenReturn(Optional.of(organization("org-b")));

        assertThrows(AccessDeniedException.class,
                () -> controller.updateOrganization(
                        "org-b",
                        "Other Shul",
                        "1 Other Place",
                        null,
                        "ASHKENAZ",
                        "#123456",
                        "other-shul",
                        null,
                        false,
                        null));

        verify(organizationService, never()).update(any());
    }

    @Test
    void orgAdminCannotOpenDifferentOrganizationScheduleByChangingPath() {
        authenticate("manager", ADMIN);
        when(userService.findByName("manager")).thenReturn(user("A1", "manager", "org-a", ADMIN));
        when(organizationService.findById("org-b")).thenReturn(Optional.of(organization("org-b")));

        assertThrows(AccessDeniedException.class,
                () -> controller.minyanim("org-b", null, null));

        verify(minyanService, never()).findMatching(any());
    }

    @Test
    void orgAdminCannotPairOwnOrganizationPathWithOtherOrganizationMinyanId() {
        authenticate("manager", ADMIN);
        when(userService.findByName("manager")).thenReturn(user("A1", "manager", "org-a", ADMIN));
        when(organizationService.findById("org-a")).thenReturn(Optional.of(organization("org-a")));

        Minyan otherOrgMinyan = new Minyan();
        otherOrgMinyan.setId("minyan-b");
        otherOrgMinyan.setOrganizationId("org-b");
        when(minyanService.findById("minyan-b")).thenReturn(otherOrgMinyan);

        assertThrows(AccessDeniedException.class,
                () -> controller.deleteMinyan("org-a", "minyan-b"));

        verify(minyanService, never()).delete(any());
    }

    @Test
    void superAdminCanViewAnyOrganization() throws Exception {
        authenticate("super", ADMIN);
        TNMUser superAdmin = user("A0", "super", null, ADMIN);
        when(userService.findByName("super")).thenReturn(superAdmin);
        Organization orgB = organization("org-b");
        when(organizationService.findById("org-b")).thenReturn(Optional.of(orgB));
        when(organizationService.getUsersForOrganization(orgB)).thenReturn(List.of());
        when(organizationService.getAll()).thenReturn(new java.util.ArrayList<>(List.of(orgB)));

        ModelAndView mv = controller.organization("org-b", null, null, null, null);

        assertEquals("admin/organization", mv.getViewName());
        assertEquals(orgB, mv.getModel().get("organization"));
    }

    @Test
    void superAdminCanUpdateApplicationSettingField() throws Exception {
        authenticate("super", ADMIN);
        when(userService.findByName("super")).thenReturn(user("A0", "super", null, ADMIN));
        when(settingsService.getString(SettingKey.SITE_NAME)).thenReturn("New Site Name");

        ResponseEntity<AdminController.SettingsFieldUpdateResponse> response =
                controller.updateSettingsField(SettingKey.SITE_NAME.getKey(), "New Site Name");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        assertEquals("New Site Name", response.getBody().displayValue());
        verify(settingsService).updateSetting(SettingKey.SITE_NAME, "New Site Name");
    }

    @Test
    void settingsFieldEndpointRejectsEmailSettings() throws Exception {
        authenticate("super", ADMIN);
        when(userService.findByName("super")).thenReturn(user("A0", "super", null, ADMIN));

        ResponseEntity<AdminController.SettingsFieldUpdateResponse> response =
                controller.updateSettingsField(SettingKey.EMAIL_PROVIDER.getKey(), "SMTP");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(settingsService, never()).updateSetting(any(), any());
    }

    @Test
    void orgAdminCannotUpdateApplicationSettingField() throws Exception {
        authenticate("manager", ADMIN);
        when(userService.findByName("manager")).thenReturn(user("A1", "manager", "org-a", ADMIN));

        assertThrows(AccessDeniedException.class,
                () -> controller.updateSettingsField(SettingKey.SITE_NAME.getKey(), "New Site Name"));

        verify(settingsService, never()).updateSetting(any(), any());
    }

    @Test
    void changeAccountPasswordPreservesAccountFieldsAndStoresNewHash() {
        authenticate("super", ADMIN);
        TNMUser superAdmin = user("A0", "super", null, ADMIN);
        TNMUser target = user("A1", "manager", "org-a", ADMIN);
        target.setEmail("manager@example.com");
        target.setEmailNormalized("manager@example.com");
        target.setEncryptedPassword(new BCryptPasswordEncoder().encode("OldPass123"));

        when(userService.findByName("super")).thenReturn(superAdmin);
        when(userService.findById("A1")).thenReturn(target);
        when(userService.update(any())).thenReturn(true);
        Organization organization = organization("org-a");
        when(organizationService.findById("org-a")).thenReturn(Optional.of(organization));
        when(organizationService.getAll()).thenReturn(new java.util.ArrayList<>(List.of(organization)));

        ModelAndView mv = controller.changeAccountPassword("A1", "NewPass123", "NewPass123");

        assertEquals("account", mv.getViewName());
        verify(userService).update(argThat(updated ->
                "A1".equals(updated.getId())
                        && "manager".equals(updated.getUsername())
                        && "manager@example.com".equals(updated.getEmail())
                        && updated.isEnabled()
                        && new BCryptPasswordEncoder().matches("NewPass123", updated.getEncryptedPassword())));
    }

    @Test
    void calendarEntriesDefaultSortMatchesDisplayedAscendingDateHeader() {
        Sort sort = ReflectionTestUtils.invokeMethod(controller, "buildSort", null, null);

        Sort.Order dateOrder = sort.getOrderFor("date");
        Sort.Order startTimeOrder = sort.getOrderFor("startTime");

        assertEquals(Sort.Direction.ASC, dateOrder.getDirection());
        assertEquals(Sort.Direction.ASC, startTimeOrder.getDirection());
    }

    @Test
    void calendarEntriesDescendingSortStillHonorsExplicitRequest() {
        Sort sort = ReflectionTestUtils.invokeMethod(controller, "buildSort", "date", "desc");

        Sort.Order dateOrder = sort.getOrderFor("date");

        assertEquals(Sort.Direction.DESC, dateOrder.getDirection());
    }

    private void authenticate(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "password",
                        List.of(new SimpleGrantedAuthority(role.getName()))));
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
                .build();
    }

    private Organization organization(String id) {
        return Organization.builder()
                .id(id)
                .name("Organization " + id)
                .address("1 Main St")
                .orgColor("#123456")
                .enabled(true)
                .build();
    }
}
