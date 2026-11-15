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
import com.tbdev.teaneckminyanim.service.UserManagementService;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkService;
import com.tbdev.teaneckminyanim.service.auth.PasskeyCredentialService;
import com.tbdev.teaneckminyanim.repo.AuthAuditEventRepository;
import com.tbdev.teaneckminyanim.repo.MagicLinkTokenRepository;
import com.tbdev.teaneckminyanim.repo.OrganizationCalendarEntryRepository;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
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
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static com.tbdev.teaneckminyanim.enums.Role.ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerAuthorizationTest {

    private AdminController controller;
    private TNMUserService userService;
    private OrganizationService organizationService;
    private MinyanService minyanService;
    private ApplicationSettingsService settingsService;
    private OrganizationCalendarEntryRepository calendarEntryRepository;
    private TNMUserRepository userRepository;
    private MagicLinkService magicLinkService;
    private MockMvc mvc;

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
        ReflectionTestUtils.setField(controller, "passkeyCredentialService", mock(PasskeyCredentialService.class));
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        calendarEntryRepository = mock(OrganizationCalendarEntryRepository.class);
        when(applicationContext.getBean(OrganizationCalendarEntryRepository.class)).thenReturn(calendarEntryRepository);
        ReflectionTestUtils.setField(controller, "applicationContext", applicationContext);

        userRepository = mock(TNMUserRepository.class);
        when(userRepository.findAll()).thenReturn(List.of());
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UserManagementService userManagementService = new UserManagementService(
                userService, userRepository, organizationService, mock(PasskeyCredentialService.class),
                mock(MagicLinkTokenRepository.class), mock(AuthAuditEventRepository.class));
        magicLinkService = mock(MagicLinkService.class);
        when(magicLinkService.sendProfileWelcomeLink(any(), any())).thenReturn(true);
        when(magicLinkService.sendManagedWelcomeLink(any(), any())).thenReturn(true);
        ReflectionTestUtils.setField(controller, "userManagementService", userManagementService);
        ReflectionTestUtils.setField(controller, "magicLinkService", magicLinkService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AccessDeniedAdvice())
                .setSingleView(new org.springframework.web.servlet.view.AbstractView() {
                    @Override
                    protected void renderMergedOutputModel(java.util.Map<String, Object> model,
                                                           jakarta.servlet.http.HttpServletRequest request,
                                                           jakarta.servlet.http.HttpServletResponse response) {
                        // Authorization outcome is asserted via status; template rendering is covered elsewhere.
                    }
                })
                .build();
    }

    /** Mirrors Spring Security's ExceptionTranslationFilter: AccessDeniedException becomes HTTP 403. */
    @RestControllerAdvice
    static class AccessDeniedAdvice {
        @ExceptionHandler(AccessDeniedException.class)
        ResponseEntity<String> denied(AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    // --- #289: account creation and Account Settings scope, exercised with direct requests ---

    @Test
    void managerCreatesUserInOwnShulAndWelcomeEmailIsSent() throws Exception {
        signIn(user("A1", "manager", "org-a", ADMIN));
        stubOrganizations("org-a");

        mvc.perform(post("/admin/create-account").param("oid", "org-a").param("r", "u")
                        .param("username", "newuser1").param("email", "New.User@Example.com"))
                .andExpect(status().isOk());

        verify(userRepository).save(argThat(saved -> saved.role() == Role.USER
                && "org-a".equals(saved.getOrganizationId())
                && saved.getAdditionalOrganizationIds().isEmpty()
                && "newuser1".equals(saved.getUsername())
                && "new.user@example.com".equals(saved.getEmail())
                && "!PASSWORDLESS".equals(saved.getEncryptedPassword())));
        verify(magicLinkService).sendProfileWelcomeLink(
                argThat(created -> "newuser1".equals(created.getUsername())), any());
        verify(magicLinkService, never()).sendManagedWelcomeLink(any(), any());
    }

    @Test
    void managerCreatesManagerInOwnShul() throws Exception {
        signIn(user("A1", "manager", "org-a", ADMIN));
        stubOrganizations("org-a");

        mvc.perform(post("/admin/create-account").param("oid", "org-a").param("r", "m")
                        .param("username", "newmanager").param("email", "newmanager@example.com"))
                .andExpect(status().isOk());

        verify(userRepository).save(argThat(saved -> saved.role() == ADMIN
                && !saved.isSuperAdmin()
                && Set.of("org-a").equals(saved.getAccessibleOrganizationIds())));
        verify(magicLinkService).sendProfileWelcomeLink(any(), any());
    }

    @Test
    void managerCanCreateInSecondAssignedShul() throws Exception {
        TNMUser manager = user("A1", "manager", "org-a", ADMIN);
        manager.getAdditionalOrganizationIds().add("org-b");
        signIn(manager);
        stubOrganizations("org-a", "org-b");

        mvc.perform(post("/admin/create-account").param("oid", "org-b").param("r", "u")
                        .param("username", "seconduser").param("email", "second@example.com"))
                .andExpect(status().isOk());

        verify(userRepository).save(argThat(saved -> Set.of("org-b").equals(saved.getAccessibleOrganizationIds())));
    }

    @Test
    void managerCannotCreateAccountForUnrelatedShul() throws Exception {
        signIn(user("A1", "manager", "org-a", ADMIN));
        stubOrganizations("org-a", "org-c");

        mvc.perform(post("/admin/create-account").param("oid", "org-c").param("r", "u")
                        .param("username", "outsider1").param("email", "outsider@example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/create-account").param("oid", "").param("r", "m")
                        .param("username", "outsider1").param("email", "outsider@example.com"))
                .andExpect(status().isForbidden());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(magicLinkService);
    }

    @Test
    void managerCannotGrantSuperAdminWhenCreatingAccount() throws Exception {
        signIn(user("A1", "manager", "org-a", ADMIN));
        stubOrganizations("org-a");

        for (String role : List.of("s", "SUPER_ADMIN", "super", "")) {
            mvc.perform(post("/admin/create-account").param("oid", "org-a").param("r", role)
                            .param("username", "wouldbesuper").param("email", "super2@example.com"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/admin/create-account").param("oid", "org-a")
                        .param("username", "wouldbesuper").param("email", "super2@example.com"))
                .andExpect(status().isOk());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(magicLinkService);
    }

    @Test
    void userCannotCreateAccounts() throws Exception {
        signIn(user("A2", "plainuser", "org-a", Role.USER));
        stubOrganizations("org-a");

        mvc.perform(post("/admin/create-account").param("oid", "org-a").param("r", "u")
                        .param("username", "newuser1").param("email", "new@example.com"))
                .andExpect(status().isForbidden());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(magicLinkService);
    }

    @Test
    void superAdminCreatesFromOrganizationPageWithManagedWelcome() throws Exception {
        signIn(user("A0", "super", null, ADMIN));
        stubOrganizations("org-c");

        mvc.perform(post("/admin/create-account").param("oid", "org-c").param("r", "m")
                        .param("username", "cmanager").param("email", "cmanager@example.com"))
                .andExpect(status().isOk());

        verify(userRepository).save(argThat(saved -> saved.role() == ADMIN
                && "org-c".equals(saved.getOrganizationId())));
        verify(magicLinkService).sendManagedWelcomeLink(any(), any());
    }

    @Test
    void managerCanOpenAndEditPeerManagerInOwnShul() throws Exception {
        signIn(user("A1", "manager", "org-a", ADMIN));
        stubOrganizations("org-a");
        TNMUser peer = user("A2", "peermanager", "org-a", ADMIN);
        when(userService.findById("A2")).thenReturn(peer);
        when(userService.update(any())).thenReturn(true);

        mvc.perform(get("/admin/account").param("id", "A2")).andExpect(status().isOk());
        mvc.perform(post("/admin/update-account").param("id", "A2")
                        .param("username", "peermanager2").param("email", "peermanager@example.com"))
                .andExpect(status().isOk());
        mvc.perform(post("/admin/account/notifications").param("id", "A2"))
                .andExpect(status().isOk());
        mvc.perform(post("/admin/account/disable").param("id", "A2"))
                .andExpect(status().isOk());

        verify(userService, atLeastOnce()).update(argThat(updated ->
                "A2".equals(updated.getId()) && updated.role() == ADMIN && "org-a".equals(updated.getOrganizationId())));
        assertEquals("peermanager2", peer.getUsername());
        assertFalse(peer.isEnabled());
    }

    @Test
    void managerCannotDeleteOrResetPeerManagerCredentials() throws Exception {
        signIn(user("A1", "manager", "org-a", ADMIN));
        TNMUser peer = user("A2", "peermanager", "org-a", ADMIN);
        peer.setEnabled(false);
        when(userService.findById("A2")).thenReturn(peer);

        mvc.perform(post("/admin/delete-account").param("id", "A2")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/accounts/A2/changepassword")
                        .param("password", "NewPass123").param("cpassword", "NewPass123"))
                .andExpect(status().isForbidden());

        verify(userService, never()).delete(any());
        verify(userService, never()).update(any());
    }

    @Test
    void managerCannotManageSuperAdmin() throws Exception {
        signIn(user("A1", "manager", "org-a", ADMIN));
        TNMUser superAdmin = user("A0", "super", null, ADMIN);
        when(userService.findById("A0")).thenReturn(superAdmin);

        mvc.perform(get("/admin/account").param("id", "A0")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/update-account").param("id", "A0")
                        .param("username", "superadmin").param("email", "super@example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/account/notifications").param("id", "A0")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/account/disable").param("id", "A0")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/account/enable").param("id", "A0")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/delete-account").param("id", "A0")).andExpect(status().isForbidden());

        verify(userService, never()).update(any());
        verify(userService, never()).delete(any());
    }

    @Test
    void managerCannotEditMultiShulAccountThatIncludesUnmanagedShul() throws Exception {
        signIn(user("A1", "manager", "org-a", ADMIN));
        TNMUser multiShulManager = user("A2", "multimanager", "org-a", ADMIN);
        multiShulManager.getAdditionalOrganizationIds().add("org-b");
        TNMUser multiShulUser = user("A3", "multiuser", "org-a", Role.USER);
        multiShulUser.getAdditionalOrganizationIds().add("org-b");
        when(userService.findById("A2")).thenReturn(multiShulManager);
        when(userService.findById("A3")).thenReturn(multiShulUser);

        for (String id : List.of("A2", "A3")) {
            mvc.perform(get("/admin/account").param("id", id)).andExpect(status().isForbidden());
            mvc.perform(post("/admin/update-account").param("id", id)
                            .param("username", "renamed1").param("email", "renamed@example.com"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/admin/account/disable").param("id", id)).andExpect(status().isForbidden());
        }

        verify(userService, never()).update(any());
    }

    @Test
    void managerWhoManagesEveryShulCanOpenMultiShulManager() throws Exception {
        TNMUser manager = user("A1", "manager", "org-a", ADMIN);
        manager.getAdditionalOrganizationIds().add("org-b");
        signIn(manager);
        stubOrganizations("org-a", "org-b");
        TNMUser multiShulManager = user("A2", "multimanager", "org-b", ADMIN);
        multiShulManager.getAdditionalOrganizationIds().add("org-a");
        when(userService.findById("A2")).thenReturn(multiShulManager);

        mvc.perform(get("/admin/account").param("id", "A2")).andExpect(status().isOk());
    }

    @Test
    void userCannotManageAnotherUserButCanManageSelf() throws Exception {
        TNMUser actor = signIn(user("A2", "plainuser", "org-a", Role.USER));
        stubOrganizations("org-a");
        TNMUser other = user("A3", "otheruser", "org-a", Role.USER);
        when(userService.findById("A2")).thenReturn(actor);
        when(userService.findById("A3")).thenReturn(other);

        mvc.perform(get("/admin/account").param("id", "A3")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/update-account").param("id", "A3")
                        .param("username", "otheruser").param("email", "other@example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/account/notifications").param("id", "A3")).andExpect(status().isForbidden());
        verify(userService, never()).update(any());

        mvc.perform(get("/admin/account").param("id", "A2")).andExpect(status().isOk());
    }

    @Test
    void organizationPageOffersManageOnlyForAccountsInActorScope() throws Exception {
        TNMUser manager = signIn(user("A1", "manager", "org-a", ADMIN));
        Organization orgA = organization("org-a");
        when(organizationService.findById("org-a")).thenReturn(Optional.of(orgA));
        TNMUser peer = user("A2", "peermanager", "org-a", ADMIN);
        TNMUser plain = user("A3", "plainuser", "org-a", Role.USER);
        TNMUser multi = user("A4", "multiuser", "org-a", Role.USER);
        multi.getAdditionalOrganizationIds().add("org-b");
        when(organizationService.getUsersForOrganization(orgA)).thenReturn(List.of(manager, peer, plain, multi));

        ModelAndView view = controller.organization("org-a", null, null, null, null);

        assertEquals(Set.of("A1", "A2", "A3"), view.getModel().get("manageableAccountIds"));
    }

    private TNMUser signIn(TNMUser actor) {
        authenticate(actor.getUsername(), actor.role());
        when(userService.findByName(actor.getUsername())).thenReturn(actor);
        when(userService.getCurrentUser()).thenReturn(actor);
        return actor;
    }

    private void stubOrganizations(String... ids) {
        for (String id : ids) {
            when(organizationService.findById(id)).thenReturn(Optional.of(organization(id)));
        }
        when(organizationService.getAll()).thenReturn(new ArrayList<>(
                Arrays.stream(ids).map(this::organization).toList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerCanOpenSecondAssignedShulButNotUnassignedShul() {
        authenticate("manager", ADMIN);
        TNMUser manager = user("A1", "manager", "org-a", ADMIN);
        manager.getAdditionalOrganizationIds().add("org-b");
        when(userService.findByName("manager")).thenReturn(manager);
        assertEquals("/admin/org-b/minyanim", controller.orgDashboard("org-b").getUrl());
        assertThrows(AccessDeniedException.class, () -> controller.orgDashboard("org-c"));
        manager.getAdditionalOrganizationIds().clear();
        assertThrows(AccessDeniedException.class, () -> controller.orgDashboard("org-b"));
    }

    @Test
    void accountPageListsEveryAssignedOrganizationAlphabetically() {
        authenticate("user", Role.USER);
        TNMUser user = user("A1", "user", "org-b", Role.USER);
        user.getAdditionalOrganizationIds().add("org-c");
        user.getAdditionalOrganizationIds().add("org-a");
        when(userService.findByName("user")).thenReturn(user);
        when(userService.findById("A1")).thenReturn(user);
        when(organizationService.findById("org-a")).thenReturn(Optional.of(organization("org-a")));
        when(organizationService.findById("org-b")).thenReturn(Optional.of(organization("org-b")));
        when(organizationService.findById("org-c")).thenReturn(Optional.of(organization("org-c")));

        ModelAndView view = controller.accountRoute("A1", null, null, null, false);

        @SuppressWarnings("unchecked")
        List<Organization> organizations = (List<Organization>) view.getModel().get("associatedOrganizations");
        assertEquals(List.of("org-a", "org-b", "org-c"),
                organizations.stream().map(Organization::getId).toList());
    }

    @Test
    void legacyProfileCannotPromoteUser() {
        authenticate("manager", ADMIN);
        TNMUser manager = user("A1", "manager", "org-a", ADMIN);
        TNMUser target = user("A2", "target", "org-a", Role.USER);
        when(userService.findByName("manager")).thenReturn(manager);
        when(userService.findById("A2")).thenReturn(target);
        assertThrows(AccessDeniedException.class, () -> controller.updateAccount(
                "A2", "target", "target@example.com", 1, null));
        verify(userService, never()).update(any());
    }

    @Test
    void managerCannotEditUserWithBroaderShulScope() {
        authenticate("manager", ADMIN);
        TNMUser manager = user("A1", "manager", "org-a", ADMIN);
        TNMUser target = user("A2", "target", "org-a", Role.USER);
        target.getAdditionalOrganizationIds().add("org-b");
        when(userService.findByName("manager")).thenReturn(manager);
        when(userService.findById("A2")).thenReturn(target);
        assertThrows(AccessDeniedException.class, () -> controller.disableAccount("A2", null));
        verify(userService, never()).update(any());
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
    void userCanOpenCalendarEntriesForAssignedShul() {
        authenticate("user", Role.USER);
        TNMUser user = user("A2", "user", "org-a", Role.USER);
        Organization organization = organization("org-a");
        when(userService.findByName("user")).thenReturn(user);
        when(organizationService.findById("org-a")).thenReturn(Optional.of(organization));
        when(calendarEntryRepository.findFromDateWithClassification(any(), any(), any(), any()))
                .thenReturn(List.of());

        ModelAndView mv = controller.viewCalendarEntries(
                "org-a", null, null, null, null, null, null, null, null, null, false);

        assertEquals("admin/calendar-entries", mv.getViewName());
        assertEquals(organization, mv.getModel().get("organization"));
        assertEquals(List.of(), mv.getModel().get("entries"));
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

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.changeAccountPassword("A1", "NewPass123", "NewPass123", redirect);

        assertEquals("redirect:/admin/account?id=A1", view);
        assertEquals("Successfully updated the account password.",
                redirect.getFlashAttributes().get("successMessage"));
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
