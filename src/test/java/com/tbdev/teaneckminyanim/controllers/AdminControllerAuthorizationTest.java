package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static com.tbdev.teaneckminyanim.enums.Role.ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerAuthorizationTest {

    private AdminController controller;
    private TNMUserService userService;
    private OrganizationService organizationService;
    private MinyanService minyanService;

    @BeforeEach
    void setUp() {
        controller = new AdminController();
        userService = mock(TNMUserService.class);
        organizationService = mock(OrganizationService.class);
        minyanService = mock(MinyanService.class);

        ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
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
