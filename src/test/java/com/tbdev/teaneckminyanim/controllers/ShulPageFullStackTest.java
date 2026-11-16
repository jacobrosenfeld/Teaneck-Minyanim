package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders the shul page and the pages it links to through the real application stack (security,
 * Thymeleaf with the layout and security dialects, JPA on H2), with legacy data like the dev shul
 * from PR #315 comment 4: calendar import flag NULL, no calendar URL, mixed accounts.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shul-page-full-stack;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "logging.level.org.springframework.web=INFO",
        "superadmin.email="
})
@AutoConfigureMockMvc
class ShulPageFullStackTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TNMUserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private com.tbdev.teaneckminyanim.repo.LocationRepository locations;

    @Autowired
    private com.tbdev.teaneckminyanim.repo.NotificationRepository notifications;

    @MockitoBean
    private RecaptchaService recaptchaService;

    @BeforeEach
    void seed() {
        users.deleteAll();
        locations.deleteAll();
        notifications.deleteAll();
        organizations.deleteAll();

        organizations.save(shul("FSORGA", "Keter Torah Test", "keter-torah-test"));
        organizations.save(shul("FSORGB", "Other Shul", "other-shul"));
        organizations.save(shul("FSORGC", "Empty Shul", "empty-shul"));
        // Legacy rows predate the calendar import columns.
        jdbc.update("UPDATE organization SET USE_SCRAPED_CALENDAR = NULL, CALENDAR = NULL WHERE ID = 'FSORGA'");
        jdbc.update("UPDATE organization SET USE_SCRAPED_CALENDAR = NULL WHERE ID = 'FSORGC'");

        locations.save(new com.tbdev.teaneckminyanim.model.Location("FSLOC", "Main Sanctuary", "FSORGA"));
        notifications.save(new com.tbdev.teaneckminyanim.model.Notification("FSNOTE", "Shul picnic",
                "Sunday at noon", "BANNER", true, null, null,
                java.time.LocalDateTime.of(2026, 11, 1, 9, 0), java.time.LocalDateTime.of(2026, 11, 1, 9, 0)));

        users.save(account("FSSUPER", "fs-super", null, Role.ADMIN, true));
        users.save(account("FSMGR", "fs-manager", "FSORGA", Role.ADMIN, true));
        users.save(account("FSUSER", "fs-user", "FSORGA", Role.USER, true));
        users.save(account("FSOFF", "fs-disabled", "FSORGA", Role.USER, false));
        TNMUser broader = account("FSBROAD", "fs-broader", "FSORGA", Role.USER, true);
        broader.setAdditionalOrganizationIds(new LinkedHashSet<>(Set.of("FSORGB")));
        users.save(broader);
    }

    @Test
    void superAdminSeesShulPageAndEveryAccountLinkRenders() throws Exception {
        String html = page("/admin/organization?id=FSORGA", as("fs-super"));
        assertTrue(html.contains("id=\"useScrapedCalendar-state\">Off</span>"));
        for (String id : List.of("FSMGR", "FSUSER", "FSOFF", "FSBROAD")) {
            assertTrue(html.contains("href=\"/admin/account?id=" + id + "\""), id);
            assertTrue(html.contains("id=\"disable-account-form-" + id + "\""), id);
            page("/admin/account?id=" + id, as("fs-super"));
        }
        assertTrue(html.contains("id=\"add-account-modal\""));
        assertTrue(html.contains("id=\"confirm-modal\""));
        page("/admin/accounts", as("fs-super"));
        page("/orgs/FSORGA", null);
    }

    @Test
    void managerSeesShulPageAndCanOpenTheAccountsTheyManage() throws Exception {
        String html = page("/admin/organization?id=FSORGA", as("fs-manager"));
        assertFalse(html.contains("id=\"disable-account-form-FSMGR\""), "never offered for yourself");
        assertFalse(html.contains("id=\"disable-account-form-FSBROAD\""), "broader-scoped account is not manageable");
        assertTrue(html.contains("id=\"disable-account-form-FSUSER\""));
        for (String id : List.of("FSMGR", "FSUSER", "FSOFF")) {
            page("/admin/account?id=" + id, as("fs-manager"));
        }
        mvc.perform(get("/admin/account").param("id", "FSBROAD").with(as("fs-manager")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shulWithNoAccountsShowsEmptyState() throws Exception {
        String html = page("/admin/organization?id=FSORGC", as("fs-super"));
        assertTrue(html.contains("No accounts are assigned to this shul yet."));
    }

    @Test
    void disablingAShulsOnlyRemainingAccountIsAllowed() throws Exception {
        users.deleteAll();
        users.save(account("FSSUPER", "fs-super", null, Role.ADMIN, true));
        users.save(account("FSONLY", "fs-only", "FSORGC", Role.ADMIN, true));

        String html = page("/admin/organization?id=FSORGC", as("fs-super"));
        String form = html.substring(html.indexOf("id=\"disable-account-form-FSONLY\""));
        form = form.substring(0, form.indexOf("</form>"));
        assertFalse(form.contains("disabled"), form);

        mvc.perform(post("/admin/account/disable").with(as("fs-super")).with(csrf())
                        .param("id", "FSONLY").param("rd", "/admin/organization?id=FSORGC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/organization?id=FSORGC"));
        assertFalse(users.findById("FSONLY").orElseThrow().isEnabled());
    }

    @Test
    void convertedAdminPagesRenderWithTheSharedDialog() throws Exception {
        for (String path : List.of("/admin/FSORGA/locations", "/admin/notifications", "/admin/super/maintenance",
                "/admin/FSORGA/calendar-events", "/admin/calendar-events/all", "/admin/FSORGA/overrides",
                "/admin/super/overrides", "/admin/accounts", "/admin/account?id=FSOFF")) {
            String html = page(path, as("fs-super"));
            assertEquals(1, html.split("id=\"confirm-modal\"", -1).length - 1, path);
            assertTrue(html.contains("/admin/admin-modal.css"), path);
        }
    }

    private String page(String path, RequestPostProcessor auth) throws Exception {
        var request = get(path);
        if (auth != null) {
            request = request.with(auth);
        }
        String html = mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("Something Went Wrong"), path);
        assertTrue(html.trim().endsWith("</html>"), path);
        // Kept for manual review in a browser.
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/full-stack-pages"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/full-stack-pages",
                path.replaceAll("[^A-Za-z0-9]+", "_") + ".html"), html);
        return html;
    }

    private static RequestPostProcessor as(String username) {
        return user(username).authorities(new SimpleGrantedAuthority(Role.ADMIN.getName()));
    }

    private static Organization shul(String id, String name, String slug) {
        Organization org = new Organization();
        org.setId(id);
        org.setName(name);
        org.setUrlSlug(slug);
        org.setEnabled(true);
        org.setOrgColor("#275ed8");
        return org;
    }

    private static TNMUser account(String id, String username, String orgId, Role role, boolean enabled) {
        return TNMUser.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .encryptedPassword("x")
                .roleId(role.getId())
                .organizationId(orgId)
                .enabled(enabled)
                .build();
    }
}
