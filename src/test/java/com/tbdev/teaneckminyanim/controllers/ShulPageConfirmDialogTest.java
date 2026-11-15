package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers the shared confirmation dialog (#223) and the shul page's Disable account flow (#303). */
class ShulPageConfirmDialogTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shulPageDisableButtonOpensSharedDialogAndPostsToDisableEndpoint() throws Exception {
        String html = renderShulPage();

        // One shared dialog, plus its script, on the page.
        assertEquals(1, count(html, "id=\"confirm-modal\""));
        assertTrue(html.contains("src=\"/admin/confirm-modal.js\""));
        // The old per-row modal targets are gone.
        assertFalse(html.contains("disable-account-modal-"));

        String peerForm = formWithId(html, "disable-account-form-peer");
        assertTrue(peerForm.contains("action=\"/admin/account/disable\""), peerForm);
        assertTrue(peerForm.contains("method=\"post\""), peerForm);
        assertTrue(peerForm.contains("name=\"_csrf\""), peerForm);
        assertTrue(peerForm.contains("name=\"id\" value=\"peer\""), peerForm);
        assertTrue(peerForm.contains("name=\"rd\" value=\"/admin/organization?id=one\""), peerForm);
        assertTrue(peerForm.contains("data-confirm"), peerForm);
        assertTrue(peerForm.contains("data-confirm-title=\"Disable peermanager?\""), peerForm);
        // Without JavaScript the trigger cannot submit the form unconfirmed.
        assertTrue(peerForm.contains("type=\"button\""), peerForm);
        assertFalse(peerForm.contains("type=\"submit\""), peerForm);

        // Visibility rules are unchanged: never for yourself, never for accounts you cannot manage.
        assertFalse(html.contains("disable-account-form-actor"));
        assertFalse(html.contains("disable-account-form-broader"));
        assertTrue(html.contains("href=\"/admin/account?id=peer\""));

        // Every existing profile field and the form action are still there.
        assertTrue(html.contains("action=\"/admin/update-organization\""));
        for (String field : List.of("id", "name", "address", "site-url", "whatsapp", "url-slug",
                "nusach", "orgColor", "calendar", "useScrapedCalendar")) {
            assertTrue(html.contains("name=\"" + field + "\""), field);
        }
        assertTrue(html.contains("action=\"/admin/create-account?oid=one\""));
        assertFalse(html.contains("Update Organization"));
    }

    @Test
    void shulPageKeepsDisableButtonDisabledForSingleAccountShul() throws Exception {
        String html = render("/single-account-preview");
        String peerForm = formWithId(html, "disable-account-form-peer");
        assertTrue(peerForm.contains("disabled=\"disabled\""), peerForm);
    }

    @Test
    void overridePagesUseSharedDialogInsteadOfBrowserConfirm() throws Exception {
        for (String page : List.of("manual-overrides", "super-overrides")) {
            String template = Files.readString(Path.of("src/main/resources/templates/admin/" + page + ".html"));
            assertFalse(template.contains("confirm("), page);
            assertFalse(template.contains("onsubmit"), page);
            assertTrue(template.contains("~{admin/fragments/confirm-modal :: confirmModal}"), page);
            assertTrue(template.contains("data-confirm-title=\"Delete manual override?\""), page);
            Matcher deleteForm = Pattern.compile("<form[^>]*/delete[^>]*>(.*?)</form>", Pattern.DOTALL).matcher(template);
            assertTrue(deleteForm.find(), page);
            assertTrue(deleteForm.group(1).contains("type=\"button\""), page);
            assertFalse(deleteForm.group(1).contains("type=\"submit\""), page);
        }
    }

    @Test
    void disableFromShulPageRedirectsBackWithFlashMessage() throws Exception {
        TNMUserService users = mock(TNMUserService.class);
        TNMUser manager = account("actor", "shulmanager", Role.ADMIN);
        TNMUser peer = account("peer", "peermanager", Role.USER);
        when(users.findByName("shulmanager")).thenReturn(manager);
        when(users.findById("peer")).thenReturn(peer);
        when(users.update(any())).thenReturn(true);
        signIn("shulmanager");

        var result = adminMvc(users).perform(post("/admin/account/disable")
                        .param("id", "peer")
                        .param("rd", "/admin/organization?id=one"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/organization?id=one"))
                .andReturn();

        assertEquals("Disabled peermanager.", result.getFlashMap().get("successMessage"));

        assertFalse(peer.isEnabled());
    }

    @Test
    void disableIgnoresOffSiteRedirectTargets() throws Exception {
        TNMUserService users = mock(TNMUserService.class);
        TNMUser manager = account("actor", "shulmanager", Role.ADMIN);
        TNMUser peer = account("peer", "peermanager", Role.USER);
        when(users.findByName("shulmanager")).thenReturn(manager);
        when(users.findById("peer")).thenReturn(peer);
        when(users.update(any())).thenReturn(true);
        signIn("shulmanager");
        MockMvc mvc = adminMvc(users);

        for (String rd : List.of("https://evil.example/admin/", "//evil.example/admin", "/\\evil.example",
                "/admin/../login", "javascript:alert(1)", "/org/keter-torah")) {
            var result = mvc.perform(post("/admin/account/disable").param("id", "peer").param("rd", rd))
                    .andExpect(status().isOk())
                    .andReturn();
            assertNull(result.getResponse().getRedirectedUrl(), rd);
            // Falls back to the account page, as before rd was given.
            assertEquals("account", result.getModelAndView().getViewName(), rd);
        }
    }

    @Test
    void disableStillBlocksYourOwnAccountFromShulPage() throws Exception {
        TNMUserService users = mock(TNMUserService.class);
        TNMUser manager = account("actor", "shulmanager", Role.ADMIN);
        when(users.findByName("shulmanager")).thenReturn(manager);
        when(users.findById("actor")).thenReturn(manager);
        signIn("shulmanager");

        AdminController controller = adminController(users);
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> controller.disableAccount("actor", "/admin/organization?id=one"));
        verify(users, never()).update(any());
    }

    @Test
    void otherRdHandlersIgnoreOffSiteTargetsAndKeepTheirDefaults() throws Exception {
        TNMUserService users = mock(TNMUserService.class);
        TNMUser manager = account("actor", "shulmanager", Role.ADMIN);
        TNMUser peer = account("peer", "peermanager", Role.USER);
        when(users.findByName("shulmanager")).thenReturn(manager);
        when(users.findById("peer")).thenReturn(peer);
        when(users.update(any())).thenReturn(true);
        signIn("shulmanager");

        AdminController controller = adminController(users);
        com.tbdev.teaneckminyanim.service.OrganizationService organizations =
                mock(com.tbdev.teaneckminyanim.service.OrganizationService.class);
        Organization one = new Organization();
        one.setId("one");
        one.setName("Shul One");
        when(organizations.findById("one")).thenReturn(java.util.Optional.of(one));
        ReflectionTestUtils.setField(controller, "organizationService", organizations);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setSingleView(new org.springframework.web.servlet.view.AbstractView() {
                    @Override
                    protected void renderMergedOutputModel(java.util.Map<String, Object> model,
                                                           jakarta.servlet.http.HttpServletRequest request,
                                                           jakarta.servlet.http.HttpServletResponse response) {
                    }
                })
                .build();

        for (String path : List.of("/admin/enable-organization", "/admin/disable-organization")) {
            mvc.perform(post(path).param("id", "one").param("rd", "https://evil.example/admin"))
                    .andExpect(redirectedUrl("/admin/organization?id=one"));
            mvc.perform(post(path).param("id", "one").param("rd", "//evil.example"))
                    .andExpect(redirectedUrl("/admin/organization?id=one"));
            mvc.perform(post(path).param("id", "one").param("rd", "/admin/organizations"))
                    .andExpect(redirectedUrl("/admin/organizations"));
        }

        var enabled = mvc.perform(post("/admin/account/enable").param("id", "peer").param("rd", "//evil.example"))
                .andExpect(status().isOk()).andReturn();
        assertEquals("account", enabled.getModelAndView().getViewName());
        mvc.perform(post("/admin/account/enable").param("id", "peer").param("rd", "/admin/organization?id=one"))
                .andExpect(redirectedUrl("/admin/organization?id=one"));
    }

    @Test
    void safeAdminRedirectAcceptsOnlyAdminPaths() {
        assertEquals("/admin/organization?id=one", AdminController.safeAdminRedirect("/admin/organization?id=one"));
        assertEquals("/admin/accounts", AdminController.safeAdminRedirect(" /admin/accounts "));
        assertNull(AdminController.safeAdminRedirect(null));
        assertNull(AdminController.safeAdminRedirect(""));
        assertNull(AdminController.safeAdminRedirect("//evil.example/admin"));
        assertNull(AdminController.safeAdminRedirect("https://evil.example/admin/x"));
        assertNull(AdminController.safeAdminRedirect("/administrator"));
        assertNull(AdminController.safeAdminRedirect("/admin/../login"));
        assertNull(AdminController.safeAdminRedirect("/admin/x\r\nSet-Cookie: a=b"));
        assertNull(AdminController.safeAdminRedirect("/admin\\@evil.example"));
    }

    // ---- helpers ----

    private static AdminController adminController(TNMUserService users) {
        AdminController controller = new AdminController();
        ApplicationSettingsService settings = mock(ApplicationSettingsService.class);
        when(settings.getSiteName()).thenReturn("Teaneck Minyanim");
        ReflectionTestUtils.setField(controller, "TNMUserDAO", users);
        ReflectionTestUtils.setField(controller, "settingsService", settings);
        when(settings.getTimeZone()).thenReturn(java.util.TimeZone.getTimeZone("America/New_York"));
        com.tbdev.teaneckminyanim.service.VersionService version = mock(com.tbdev.teaneckminyanim.service.VersionService.class);
        when(version.getVersion()).thenReturn("test-version");
        ReflectionTestUtils.setField(controller, "tnmsettingsDAO", mock(TNMSettingsService.class));
        ReflectionTestUtils.setField(controller, "organizationService", mock(com.tbdev.teaneckminyanim.service.OrganizationService.class));
        ReflectionTestUtils.setField(controller, "versionService", version);
        ReflectionTestUtils.setField(controller, "passkeyCredentialService",
                mock(com.tbdev.teaneckminyanim.service.auth.PasskeyCredentialService.class));
        return controller;
    }

    private static MockMvc adminMvc(TNMUserService users) {
        return MockMvcBuilders.standaloneSetup(adminController(users))
                .setSingleView(new org.springframework.web.servlet.view.AbstractView() {
                    @Override
                    protected void renderMergedOutputModel(java.util.Map<String, Object> model,
                                                           jakarta.servlet.http.HttpServletRequest request,
                                                           jakarta.servlet.http.HttpServletResponse response) {
                        // Only redirects matter here.
                    }
                })
                .build();
    }

    private static void signIn(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "password", List.of(new SimpleGrantedAuthority(Role.ADMIN.getName()))));
    }

    private static TNMUser account(String id, String username, Role role) {
        return TNMUser.builder().id(id).username(username).email(username + "@example.com")
                .encryptedPassword("encrypted").organizationId("one").roleId(role.getId()).enabled(true).build();
    }

    private String renderShulPage() throws Exception {
        return render("/shul-preview");
    }

    private String render(String path) throws Exception {
        var context = new StaticWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.refresh();
        var resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(context);
        resolver.setPrefix("classpath:/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.addDialect(new LayoutDialect());
        var views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FixtureController()).setViewResolvers(views)
                .addFilters(new CsrfFilter(new HttpSessionCsrfTokenRepository())).build();
        ReflectionTestUtils.invokeMethod(mvc.getDispatcherServlet().getWebApplicationContext(),
                "addBean", "requestDataValueProcessor",
                new org.springframework.security.web.servlet.support.csrf.CsrfRequestDataValueProcessor());
        String html = mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        context.close();
        assertFalse(Pattern.compile("\\sth:[a-z-]+=").matcher(html).find(), "unprocessed Thymeleaf attributes");
        Files.writeString(Path.of("target" + path + ".html"), html);
        return html;
    }

    private static String formWithId(String html, String id) {
        Matcher m = Pattern.compile("<form[^>]*id=\"" + Pattern.quote(id) + "\"[^>]*>.*?</form>", Pattern.DOTALL)
                .matcher(html);
        assertTrue(m.find(), "form " + id + " not rendered");
        String form = m.group();
        assertNotNull(form);
        return form;
    }

    private static int count(String haystack, String needle) {
        return haystack.split(Pattern.quote(needle), -1).length - 1;
    }

    @org.springframework.stereotype.Controller
    static class FixtureController {
        @GetMapping("/shul-preview")
        ModelAndView shulPage() {
            TNMUser actor = TNMUser.builder().id("actor").username("shulmanager").email("actor@example.com")
                    .roleId(1).organizationId("one").build();
            TNMUser peer = TNMUser.builder().id("peer").username("peermanager").email("peer@example.com")
                    .roleId(1).organizationId("one").build();
            TNMUser broader = TNMUser.builder().id("broader").username("broaderuser").email("broader@example.com")
                    .roleId(2).organizationId("one")
                    .additionalOrganizationIds(new java.util.LinkedHashSet<>(List.of("two"))).build();
            return page(actor, List.of(actor, peer, broader), java.util.Set.of("actor", "peer"));
        }

        @GetMapping("/single-account-preview")
        ModelAndView singleAccountPage() {
            TNMUser superAdmin = TNMUser.builder().id("super").username("superadmin").email("super@example.com")
                    .roleId(1).build();
            TNMUser peer = TNMUser.builder().id("peer").username("peermanager").email("peer@example.com")
                    .roleId(1).organizationId("one").build();
            return page(superAdmin, List.of(peer), java.util.Set.of("peer"));
        }

        private ModelAndView page(TNMUser viewer, List<TNMUser> accounts, java.util.Set<String> manageable) {
            Organization one = new Organization();
            one.setId("one");
            one.setName("Shul One");
            one.setOrgColor("#123456");
            ModelAndView mv = new ModelAndView("admin/organization");
            mv.addObject("user", viewer);
            mv.addObject("organization", one);
            mv.addObject("associatedusers", accounts);
            mv.addObject("manageableAccountIds", manageable);
            mv.addObject("accessibleOrganizations", List.of(one));
            mv.addObject("siteName", "Teaneck Minyanim");
            mv.addObject("appVersion", "1.20.0");
            mv.addObject("supportEmail", "support@example.com");
            mv.addObject("date", "November 15, 2026");
            return mv;
        }
    }
}
