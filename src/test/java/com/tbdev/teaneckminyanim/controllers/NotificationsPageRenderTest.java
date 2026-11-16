package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.model.Notification;
import com.tbdev.teaneckminyanim.model.TNMUser;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders admin/notifications.html with the real layout to check the status pill precedence
 * (Expired &gt; Enabled &gt; Disabled) and the restyled page (#304, #208).
 */
class NotificationsPageRenderTest {

    private static final LocalDateTime EXPIRED_ON = LocalDateTime.of(2026, 9, 21, 23, 59);

    private StaticWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new StaticWebApplicationContext();
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
        mvc = MockMvcBuilders.standaloneSetup(new FixtureController()).setViewResolvers(views)
                .addFilters(new CsrfFilter(new HttpSessionCsrfTokenRepository())).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    private static Notification notification(String id, String title, boolean enabled, LocalDateTime expiration) {
        Notification n = new Notification(id, title, "Message for " + title, "BANNER", enabled,
                expiration, null, LocalDateTime.of(2026, 1, 1, 9, 0), null);
        return n;
    }

    /** Returns the table row for the notification with the given id. */
    private static String row(String html, String id) {
        int idx = html.indexOf("ID: " + id);
        assertTrue(idx >= 0, "row for " + id + " not rendered");
        int start = html.lastIndexOf("<tr", idx);
        int end = html.indexOf("</tr>", idx);
        return html.substring(start, end);
    }

    @Test
    void statusPillShowsExpiredBeforeEnabledOrDisabled() throws Exception {
        String html = mvc.perform(get("/notifications-preview"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String expiredEnabled = row(html, "expired-enabled");
        assertTrue(expiredEnabled.contains("badge-modern badge-danger nt-status"));
        assertTrue(expiredEnabled.contains(">Expired</span>"));
        assertTrue(expiredEnabled.contains("Expired on Sep 21, 2026. Edit the expiration date to show it again."));
        assertFalse(expiredEnabled.contains(">Enabled</span>"));
        assertTrue(expiredEnabled.contains("nt-date is-expired"));
        // Toggle stays available; this one is enabled, so it offers Disable.
        assertTrue(expiredEnabled.contains("toggle-btn"));
        assertTrue(expiredEnabled.contains("data-expired=\"true\""));

        String expiredDisabled = row(html, "expired-disabled");
        assertTrue(expiredDisabled.contains(">Expired</span>"));
        assertFalse(expiredDisabled.contains(">Disabled</span>"));
        assertTrue(expiredDisabled.contains("Re-enabling won't show it until you change the expiration date."));
        assertTrue(expiredDisabled.contains("Enable (stays hidden until the expiration date is changed)"));

        String live = row(html, "live");
        assertTrue(live.contains("badge-modern badge-success nt-status\">Enabled</span>"));
        assertFalse(live.contains(">Expired</span>"));
        assertFalse(live.contains("is-expired"));
        assertTrue(live.contains("data-expired=\"false\""));

        String disabled = row(html, "paused");
        assertTrue(disabled.contains("badge-modern badge-secondary nt-status\">Disabled</span>"));
        assertTrue(disabled.contains(">Never</span>"));
        assertFalse(disabled.contains("Re-enabling won't show it"));
    }

    @Test
    void pageUsesCurrentAdminStyleAndKeepsFormContract() throws Exception {
        String html = mvc.perform(get("/notifications-preview"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertFalse(html.matches("(?s).*\\sth:[a-z-]+=.*"), "unprocessed Thymeleaf attribute");
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/notifications-preview.html"), html);
        // Stat cards: total 4, live 1, disabled (not expired) 1, expired 2
        assertTrue(html.matches("(?s).*nt-stat-num\">4</div>\\s*<div class=\"nt-stat-label\">Total.*"));
        assertTrue(html.matches("(?s).*nt-stat-num\">1</div>\\s*<div class=\"nt-stat-label\">Live now.*"));
        assertTrue(html.matches("(?s).*nt-stat-num\">1</div>\\s*<div class=\"nt-stat-label\">Disabled.*"));
        assertTrue(html.matches("(?s).*nt-stat-num\">2</div>\\s*<div class=\"nt-stat-label\">Expired.*"));

        // Modern modals and inputs, no old inline-styled Bootstrap controls
        assertTrue(html.contains("modal fade nt-modal\" id=\"createNotificationModal\""));
        assertTrue(html.contains("modal fade nt-modal\" id=\"editNotificationModal\""));
        assertFalse(html.contains("class=\"form-control\""));
        assertFalse(html.contains("var(--color-bg-secondary)"));

        // Field names and endpoints are unchanged
        for (String field : List.of("name=\"type\"", "name=\"title\"", "name=\"message\"", "name=\"maxDisplays\"",
                "name=\"expirationDate\"", "name=\"enabled\"", "name=\"id\"")) {
            assertTrue(html.contains(field), field);
        }
        for (String endpoint : List.of("/admin/notifications/create", "/admin/notifications/update/",
                "/admin/notifications/toggle/", "/admin/notifications/delete/")) {
            assertTrue(html.contains(endpoint), endpoint);
        }
        // Edit button carries the local datetime-local value, not a UTC-converted one
        assertTrue(row(html, "expired-enabled").contains("data-expiration=\"2026-09-21T23:59\""));
        assertFalse(html.contains("toISOString"));
        assertTrue(html.contains("id=\"nt-server-now\""));
    }

    @Test
    void emptyStateRendersWithoutTable() throws Exception {
        String html = mvc.perform(get("/notifications-empty"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("No notifications yet"));
        assertFalse(html.contains("<table"));
        assertTrue(html.matches("(?s).*nt-stat-num\">0</div>\\s*<div class=\"nt-stat-label\">Expired.*"));
    }

    @org.springframework.stereotype.Controller
    static class FixtureController {
        private ModelAndView page(List<Notification> notifications) {
            TNMUser actor = TNMUser.builder().id("super").username("superadmin").email("admin@example.com").roleId(1).build();
            ModelAndView mv = new ModelAndView("admin/notifications");
            mv.addObject("user", actor);
            mv.addObject("notifications", notifications);
            mv.addObject("allOrganizations", new ArrayList<>());
            mv.addObject("siteName", "Teaneck Minyanim");
            mv.addObject("appVersion", "1.21.0");
            mv.addObject("supportEmail", "support@example.com");
            return mv;
        }

        @GetMapping("/notifications-preview")
        ModelAndView preview() {
            return page(List.of(
                    notification("expired-enabled", "Selichos Times", true, EXPIRED_ON),
                    notification("expired-disabled", "New Feature", false, LocalDateTime.of(2026, 3, 30, 0, 0)),
                    notification("live", "Live Banner", true, LocalDateTime.now().plusYears(1)),
                    notification("paused", "Paused Banner", false, null)));
        }

        @GetMapping("/notifications-empty")
        ModelAndView empty() {
            return page(new ArrayList<>());
        }
    }
}
