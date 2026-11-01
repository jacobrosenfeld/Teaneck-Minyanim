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
        verifyNoInteractions(management);
        var tokenRequest = new org.springframework.mock.web.MockHttpServletRequest();
        var token = tokens.generateToken(tokenRequest);
        tokens.saveToken(token, tokenRequest, new org.springframework.mock.web.MockHttpServletResponse());
        mvc.perform(post("/admin/users/update").servletPath("/admin/users/update")
                .session((org.springframework.mock.web.MockHttpSession) tokenRequest.getSession())
                .param(token.getParameterName(), token.getToken())
                .param("id", "user").param("email", "user@example.com").param("access", "USER"))
                .andExpect(status().is3xxRedirection());
        verify(management).update("user", "user@example.com", "USER", null, false);
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
        assertTrue(html.contains("Shul One"));
        assertFalse(html.contains("th:each="));
        Path output = Path.of("target/user-management-preview.html");
        Files.writeString(output, html);
        context.close();
    }

    @org.springframework.stereotype.Controller
    static class FixtureController {
        @GetMapping("/preview")
        ModelAndView page() {
            TNMUser actor = TNMUser.builder().id("super").username("superadmin").email("admin@example.com").roleId(1).build();
            TNMUser manager = TNMUser.builder().id("manager").username("shulmanager").email("manager@example.com")
                    .roleId(1).organizationId("one").additionalOrganizationIds(new java.util.LinkedHashSet<>(List.of("two"))).build();
            Organization one = new Organization(); one.setId("one"); one.setName("Shul One");
            Organization two = new Organization(); two.setId("two"); two.setName("Shul Two");
            ModelAndView mv = new ModelAndView("admin/accounts");
            mv.addObject("user", actor); mv.addObject("accounts", List.of(actor, manager));
            mv.addObject("organizations", List.of(one, two)); mv.addObject("accessibleOrganizations", List.of(one, two));
            mv.addObject("organizationNames", Map.of("super", "All shuls", "manager", "Shul One, Shul Two"));
            mv.addObject("passkeyCounts", Map.of("super", 2, "manager", 1));
            mv.addObject("siteName", "Teaneck Minyanim"); mv.addObject("appVersion", "1.19.0");
            mv.addObject("supportEmail", "support@example.com"); mv.addObject("date", "September 6, 2026");
            return mv;
        }
    }
}
