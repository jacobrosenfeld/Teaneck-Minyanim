package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.UserManagementService;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkService;
import com.tbdev.teaneckminyanim.service.auth.PasskeyCredentialService;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.mock.web.MockHttpServletRequest;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserManagementWebTest {
    @Test
    void accountDialogsUseAdminDesignSystemStyling() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/account.html"));

        assertTrue(template.contains("modal fade account-modal"));
        assertTrue(template.contains("account-modal-icon danger"));
        assertTrue(template.contains("account-modal-icon warning"));
        assertTrue(template.contains("modal-dialog modal-dialog-centered"));
        assertTrue(template.contains("btn-modern btn-outline"));
        assertTrue(template.contains("btn-modern btn-danger"));
        assertFalse(template.contains("class=\"btn btn-outline-secondary\""));
    }

    @Test
    void creatingUserAutomaticallySendsWelcomeEmail() {
        UserManagementService management = mock(UserManagementService.class);
        MagicLinkService magicLinks = mock(MagicLinkService.class);
        UserManagementController controller = new UserManagementController(
                management, magicLinks, mock(PasskeyCredentialService.class));
        TNMUser created = TNMUser.builder().id("A1").username("newuser").email("new@example.com").build();
        when(management.create("newuser", "new@example.com", "USER", java.util.Set.of("one")))
                .thenReturn(created);
        when(magicLinks.sendManagedWelcomeLink(any(), any())).thenReturn(true);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.create("newuser", "new@example.com", "USER", java.util.Set.of("one"),
                new MockHttpServletRequest(), redirect);

        assertEquals("redirect:/admin/accounts", view);
        verify(magicLinks).sendManagedWelcomeLink(eq(created), any());
        assertTrue(redirect.getFlashAttributes().get("successmessage").toString().contains("welcome email"));
    }

    @Test
    void accountMutationsRequireCsrf() throws Exception {
        UserManagementService management = mock(UserManagementService.class);
        UserManagementController controller = new UserManagementController(management,
                mock(MagicLinkService.class), mock(PasskeyCredentialService.class));
        var tokens = new HttpSessionCsrfTokenRepository();
        CsrfFilter filter = new CsrfFilter(tokens);
        filter.setRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(filter).build();
        mvc.perform(post("/admin/users/update").servletPath("/admin/users/update")
                .param("id", "user").param("email", "user@example.com").param("access", "USER"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/status").servletPath("/admin/users/status")
                .param("id", "user").param("enabled", "false"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(management);
        var tokenRequest = new org.springframework.mock.web.MockHttpServletRequest();
        var token = tokens.generateToken(tokenRequest);
        tokens.saveToken(token, tokenRequest, new org.springframework.mock.web.MockHttpServletResponse());
        when(management.update("user", "user@example.com", "USER", null))
                .thenReturn(new UserManagementService.UserUpdateResult(
                        TNMUser.builder().id("user").email("user@example.com").build(), false));
        mvc.perform(post("/admin/users/update").servletPath("/admin/users/update")
                .session((org.springframework.mock.web.MockHttpSession) tokenRequest.getSession())
                .param(token.getParameterName(), token.getToken())
                .param("id", "user").param("email", "user@example.com").param("access", "USER"))
                .andExpect(status().is3xxRedirection());
        verify(management).update("user", "user@example.com", "USER", null);
        var statusResult = mvc.perform(post("/admin/users/status").servletPath("/admin/users/status")
                .session((org.springframework.mock.web.MockHttpSession) tokenRequest.getSession())
                .param(token.getParameterName(), token.getToken())
                .param("id", "user").param("enabled", "false"))
                .andExpect(redirectedUrl("/admin/accounts"))
                .andReturn();
        assertEquals("user", statusResult.getFlashMap().get("editUserId"));
        verify(management).setEnabled("user", false);
    }

    @Test
    void rendersUserManagementWithLayoutAndCsrfFields() throws Exception {
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
        CsrfFilter csrfFilter = new CsrfFilter(new HttpSessionCsrfTokenRepository());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FixtureController()).setViewResolvers(views)
                .addFilters(csrfFilter).build();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(mvc.getDispatcherServlet().getWebApplicationContext(),
                "addBean", "requestDataValueProcessor",
                        new org.springframework.security.web.servlet.support.csrf.CsrfRequestDataValueProcessor());
        String html = mvc.perform(get("/preview"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("User Management"));
        assertTrue(html.contains("name=\"_csrf\""));
        assertTrue(html.contains("/admin/users/update"));
        assertTrue(html.contains("/admin/users/send-welcome"));
        assertTrue(html.contains("/admin/users/delete"));
        assertTrue(html.contains("Shul One"));
        assertFalse(html.contains("th:each="));
        String enabledCard = html.substring(html.indexOf("data-search=\"shulmanager"), html.indexOf("data-search=\"disableduser"));
        assertTrue(enabledCard.contains("Disable account</button>"));
        assertTrue(enabledCard.contains("An account must be disabled before it can be deleted."));
        assertFalse(enabledCard.contains("Delete user</button>"));
        assertTrue(enabledCard.contains("disabled=\"disabled\">Revoke passkeys</button>"));
        String disabledCard = html.substring(html.indexOf("data-search=\"disableduser"));
        assertTrue(disabledCard.contains("Enable account</button>"));
        assertTrue(disabledCard.contains("Delete user</button>"));
        assertFalse(disabledCard.contains("disabled=\"disabled\">Revoke passkeys</button>"));
        Path output = Path.of("target/user-management-preview.html");
        Files.writeString(output, html);
        context.close();
    }

    @Test
    void organizationPageOffersManagerRoleChoicesAndScopedActions() throws Exception {
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
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(mvc.getDispatcherServlet().getWebApplicationContext(),
                "addBean", "requestDataValueProcessor",
                new org.springframework.security.web.servlet.support.csrf.CsrfRequestDataValueProcessor());

        String html = mvc.perform(get("/org-preview"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("action=\"/admin/create-account?oid=one\""));
        assertFalse(html.contains("r=u"));
        assertTrue(html.contains("<option value=\"u\" selected>User</option>"));
        assertTrue(html.contains("<option value=\"m\">Manager</option>"));
        assertFalse(html.toLowerCase().contains("super admin</option>"));
        assertFalse(html.contains("/admin/accounts\">User Management</a> to grant"));
        assertTrue(html.contains("href=\"/admin/account?id=peer\""));
        assertTrue(html.contains("href=\"/admin/account?id=actor\""));
        assertFalse(html.contains("href=\"/admin/account?id=broader\""));
        assertFalse(html.contains("#disable-account-modal-actor"));
        assertTrue(html.contains("#disable-account-modal-peer"));
        assertFalse(html.contains("#disable-account-modal-broader"));
        context.close();
    }

    @org.springframework.stereotype.Controller
    static class FixtureController {
        @GetMapping("/org-preview")
        ModelAndView organizationPage() {
            TNMUser actor = TNMUser.builder().id("actor").username("shulmanager").email("actor@example.com")
                    .roleId(1).organizationId("one").build();
            TNMUser peer = TNMUser.builder().id("peer").username("peermanager").email("peer@example.com")
                    .roleId(1).organizationId("one").build();
            TNMUser broader = TNMUser.builder().id("broader").username("broaderuser").email("broader@example.com")
                    .roleId(2).organizationId("one").additionalOrganizationIds(new java.util.LinkedHashSet<>(List.of("two"))).build();
            Organization one = new Organization(); one.setId("one"); one.setName("Shul One"); one.setOrgColor("#123456");
            ModelAndView mv = new ModelAndView("admin/organization");
            mv.addObject("user", actor);
            mv.addObject("organization", one);
            mv.addObject("associatedusers", List.of(actor, peer, broader));
            mv.addObject("manageableAccountIds", java.util.Set.of("actor", "peer"));
            mv.addObject("accessibleOrganizations", List.of(one));
            mv.addObject("siteName", "Teaneck Minyanim"); mv.addObject("appVersion", "1.19.1");
            mv.addObject("supportEmail", "support@example.com"); mv.addObject("date", "November 11, 2026");
            return mv;
        }

        @GetMapping("/preview")
        ModelAndView page() {
            TNMUser actor = TNMUser.builder().id("super").username("superadmin").email("admin@example.com").roleId(1).build();
            TNMUser manager = TNMUser.builder().id("manager").username("shulmanager").email("manager@example.com")
                    .roleId(1).organizationId("one").additionalOrganizationIds(new java.util.LinkedHashSet<>(List.of("two"))).build();
            TNMUser disabled = TNMUser.builder().id("disabled").username("disableduser").email("disabled@example.com")
                    .roleId(1).organizationId("one").enabled(false).build();
            Organization one = new Organization(); one.setId("one"); one.setName("Shul One");
            Organization two = new Organization(); two.setId("two"); two.setName("Shul Two");
            ModelAndView mv = new ModelAndView("admin/accounts");
            mv.addObject("user", actor); mv.addObject("accounts", List.of(actor, manager, disabled));
            mv.addObject("organizations", List.of(one, two)); mv.addObject("accessibleOrganizations", List.of(one, two));
            mv.addObject("organizationNames", Map.of("super", "All shuls", "manager", "Shul One, Shul Two", "disabled", "Shul One"));
            mv.addObject("passkeyCounts", Map.of("super", 2, "manager", 0, "disabled", 1));
            mv.addObject("siteName", "Teaneck Minyanim"); mv.addObject("appVersion", "1.19.0");
            mv.addObject("supportEmail", "support@example.com"); mv.addObject("date", "September 6, 2026");
            return mv;
        }
    }
}
