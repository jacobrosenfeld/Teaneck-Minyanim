package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.AppSession;
import com.tbdev.teaneckminyanim.model.Notification;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.ApiKeyRepository;
import com.tbdev.teaneckminyanim.repo.AppSessionRepository;
import com.tbdev.teaneckminyanim.repo.NotificationRepository;
import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.service.NotificationService;
import com.tbdev.teaneckminyanim.service.NotificationService.NotificationCommand;
import com.tbdev.teaneckminyanim.service.NotificationService.NotificationNotFoundException;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.ApiKeyService;
import com.tbdev.teaneckminyanim.service.auth.AppTokenService;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notifications: service gating and validation, the browser controller refusing non-super-admins, and the
 * bearer-token API (create, patch, delete, list-all), all through the real stack on H2.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-pages;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "logging.level.org.springframework.web=INFO",
        "auth.password-login.enabled=true",
        "api.ratelimit.write-requests-per-minute=1000",
        "superadmin.email="
})
@AutoConfigureMockMvc
class NotificationAdminIntegrationTest {
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TNMUserRepository userRepository;
    @Autowired private OrganizationRepository organizations;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private AppSessionRepository appSessions;
    @Autowired private NotificationRepository notifications;
    @Autowired private TNMUserService users;
    @Autowired private ApiKeyService apiKeys;
    @Autowired private AppTokenService appTokens;
    @Autowired private NotificationService service;

    @MockitoBean private RecaptchaService recaptchaService;

    private int ipCounter;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM SPRING_SESSION");
        jdbc.update("DELETE FROM AUTH_AUDIT_EVENT");
        apiKeyRepository.deleteAll();
        appSessions.deleteAll();
        notifications.deleteAll();
        userRepository.deleteAll();
        organizations.deleteAll();
        organizations.save(shul("ORGA", "Shul A", "shul-a"));
        organizations.save(shul("ORGB", "Shul B", "shul-b"));
        userRepository.save(account("SUPER1", "superadmin", null, Role.ADMIN));
        userRepository.save(account("MGR1", "manager", "ORGA", Role.ADMIN));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // --- service -------------------------------------------------------------------------------------------

    @Test
    void serviceRefusesManagersAndAnonymousCallers() {
        Notification existing = seedNotification("N1", "Hello", "BANNER", true);
        NotificationCommand cmd = NotificationCommand.full("T", "M", "BANNER", true, null, null);

        asUser("manager", () -> {
            assertThatThrownBy(() -> service.create(cmd)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.update(existing.getId(), cmd)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.setEnabled(existing.getId(), false)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.toggleEnabled(existing.getId())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.delete(existing.getId())).isInstanceOf(AccessDeniedException.class);
        });
        assertThatThrownBy(() -> service.create(cmd)).isInstanceOf(AccessDeniedException.class);
        assertThat(notifications.findAll()).hasSize(1);
        assertThat(notifications.findById("N1").orElseThrow().getEnabled()).isTrue();
    }

    @Test
    void serviceValidatesInput() {
        asUser("superadmin", () -> {
            assertThatThrownBy(() -> service.create(NotificationCommand.full(" ", "M", "BANNER", true, null, null)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Title");
            assertThatThrownBy(() -> service.create(NotificationCommand.full("T", "", "BANNER", true, null, null)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Message");
            assertThatThrownBy(() -> service.create(NotificationCommand.full("T", "M", "TOAST", true, null, null)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BANNER or POPUP");
            assertThatThrownBy(() -> service.create(NotificationCommand.full("T", "M", null, true, null, null)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.create(NotificationCommand.full("T", "M", "POPUP", true, null, 0)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least 1");
            assertThatThrownBy(() -> service.update("missing", NotificationCommand.full("T", "M", "POPUP", true, null, 1)))
                    .isInstanceOf(NotificationNotFoundException.class);
            assertThatThrownBy(() -> service.delete("missing")).isInstanceOf(NotificationNotFoundException.class);
            assertThatThrownBy(() -> service.setEnabled("missing", true)).isInstanceOf(NotificationNotFoundException.class);

            Notification created = service.create(NotificationCommand.full("  Title ", " Msg ", "popup", null, null, 3));
            assertThat(created.getTitle()).isEqualTo("Title");
            assertThat(created.getMessage()).isEqualTo("Msg");
            assertThat(created.getType()).isEqualTo("POPUP");
            assertThat(created.getEnabled()).isTrue();
            assertThat(created.getMaxDisplays()).isEqualTo(3);
        });
        assertThatThrownBy(() -> NotificationService.parseExpiration("next tuesday"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(NotificationService.parseExpiration("2030-01-02T03:04:00")).isEqualTo(LocalDateTime.of(2030, 1, 2, 3, 4));
        assertThat(NotificationService.parseExpiration(" ")).isNull();
    }

    @Test
    void publicReadMethodsStayUngated() {
        seedNotification("N1", "Banner", "BANNER", true);
        seedNotification("N2", "Off", "BANNER", false);
        assertThat(service.getActiveBanners()).extracting(Notification::getId).containsExactly("N1");
        assertThat(service.getAll()).hasSize(2);
    }

    // --- MVC controller ------------------------------------------------------------------------------------

    @Test
    void browserControllerRefusesManagers() throws Exception {
        seedNotification("N1", "Hello", "BANNER", true);
        Cookie manager = login("manager");

        mvc.perform(get("/admin/notifications").cookie(manager)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/notifications/create").cookie(manager).with(csrf())
                        .param("title", "Hacked").param("message", "m").param("type", "BANNER"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/notifications/update/N1").cookie(manager).with(csrf())
                        .param("title", "Hacked").param("message", "m").param("type", "BANNER").param("enabled", "true"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/notifications/toggle/N1").cookie(manager).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/notifications/delete/N1").cookie(manager).with(csrf())).andExpect(status().isForbidden());

        Notification untouched = notifications.findById("N1").orElseThrow();
        assertThat(untouched.getTitle()).isEqualTo("Hello");
        assertThat(untouched.getEnabled()).isTrue();
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void browserControllerWorksForSuperAdmins() throws Exception {
        Cookie superAdmin = login("superadmin");
        mvc.perform(get("/admin/notifications").cookie(superAdmin)).andExpect(status().isOk());

        mvc.perform(post("/admin/notifications/create").cookie(superAdmin).with(csrf())
                        .param("title", "Hi").param("message", "There").param("type", "POPUP").param("maxDisplays", "2"))
                .andExpect(status().isOk());
        Notification created = notifications.findAll().get(0);
        assertThat(created.getTitle()).isEqualTo("Hi");

        mvc.perform(post("/admin/notifications/create").cookie(superAdmin).with(csrf())
                        .param("title", "").param("message", "There").param("type", "POPUP"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/admin/notifications/toggle/" + created.getId()).cookie(superAdmin).with(csrf()))
                .andExpect(status().isOk());
        assertThat(notifications.findById(created.getId()).orElseThrow().getEnabled()).isFalse();
        mvc.perform(post("/admin/notifications/update/" + created.getId()).cookie(superAdmin).with(csrf())
                        .param("title", "Renamed").param("message", "There").param("type", "BANNER").param("enabled", "true"))
                .andExpect(status().isOk());
        assertThat(notifications.findById(created.getId()).orElseThrow().getTitle()).isEqualTo("Renamed");
        mvc.perform(post("/admin/notifications/delete/" + created.getId()).cookie(superAdmin).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/admin/notifications/delete/" + created.getId()).cookie(superAdmin).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(notifications.count()).isZero();
    }

    // --- API -----------------------------------------------------------------------------------------------

    @Test
    void publicGetIsUnchangedAndAnonymousIncludeInactiveIsRejected() throws Exception {
        seedNotification("N1", "Banner", "BANNER", true);
        seedNotification("N2", "Disabled", "BANNER", false);
        seedNotification("N3", "Popup", "POPUP", true);

        mvc.perform(api(get("/api/v1/notifications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.count").value(2))
                .andExpect(jsonPath("$.data[0].id").value("N1"))
                .andExpect(jsonPath("$.data[0].enabled").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist());
        mvc.perform(api(get("/api/v1/notifications?type=POPUP")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("N3"));
        mvc.perform(api(get("/api/v1/notifications?type=NOPE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TYPE"));
        mvc.perform(api(get("/api/v1/notifications?includeInactive=true")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void superAdminCanListEveryNotification() throws Exception {
        seedNotification("N1", "Banner", "BANNER", true);
        seedNotification("N2", "Disabled", "BANNER", false);
        String token = appToken("SUPER1");

        mvc.perform(api(get("/api/v1/notifications?includeInactive=true")).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.count").value(2))
                .andExpect(jsonPath("$.data[?(@.id=='N2')].enabled").value(false))
                .andExpect(jsonPath("$.data[?(@.id=='N1')].createdAt").isNotEmpty());
    }

    @Test
    void superAdminCreatesPatchesAndDeletes() throws Exception {
        String token = appToken("SUPER1");

        MvcResult created = mvc.perform(api(post("/api/v1/notifications")).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Yom Tov hours\",\"message\":\"Schedules change.\",\"type\":\"banner\","
                                + "\"expirationDate\":\"2099-12-31T23:59:00\",\"maxDisplays\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Yom Tov hours"))
                .andExpect(jsonPath("$.data.type").value("BANNER"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.expirationDate").value("2099-12-31T23:59"))
                .andExpect(jsonPath("$.data.maxDisplays").value(3))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        // Toggle only.
        mvc.perform(api(patch("/api/v1/notifications/" + id)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.title").value("Yom Tov hours"))
                .andExpect(jsonPath("$.data.maxDisplays").value(3));
        // Edit one field, clear the expiration and the display limit.
        mvc.perform(api(patch("/api/v1/notifications/" + id)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"New text\",\"expirationDate\":null,\"maxDisplays\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("New text"))
                .andExpect(jsonPath("$.data.expirationDate").doesNotExist())
                .andExpect(jsonPath("$.data.maxDisplays").doesNotExist())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mvc.perform(api(delete("/api/v1/notifications/" + id)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(api(delete("/api/v1/notifications/" + id)).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        assertThat(notifications.count()).isZero();
    }

    @Test
    void apiValidationErrorsAre400() throws Exception {
        String token = appToken("SUPER1");
        seedNotification("N1", "Banner", "BANNER", true);
        String[] bad = {
                "{\"message\":\"m\",\"type\":\"BANNER\"}",
                "{\"title\":\"t\",\"type\":\"BANNER\"}",
                "{\"title\":\"t\",\"message\":\"m\",\"type\":\"TOAST\"}",
                "{\"title\":\"t\",\"message\":\"m\",\"type\":\"BANNER\",\"maxDisplays\":0}",
                "{\"title\":\"t\",\"message\":\"m\",\"type\":\"BANNER\",\"expirationDate\":\"soon\"}"
        };
        for (String body : bad) {
            mvc.perform(api(post("/api/v1/notifications")).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        for (String body : new String[]{"{\"title\":\"\"}", "{\"type\":\"X\"}", "{\"maxDisplays\":-1}",
                "{\"enabled\":\"yes\"}", "{\"expirationDate\":\"soon\"}", "[]"}) {
            mvc.perform(api(patch("/api/v1/notifications/N1")).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(api(patch("/api/v1/notifications/missing")).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void sameAsOwnerSuperAdminKeyCanWrite() throws Exception {
        String key = createKey("SUPER1", true, Set.of(), Set.of());
        mvc.perform(api(post("/api/v1/notifications")).header("Authorization", "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"message\":\"m\",\"type\":\"POPUP\",\"enabled\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void scopedSuperAdminKeyWithNotificationsWriteCanWrite() throws Exception {
        String key = createKey("SUPER1", false, Set.of(ApiScope.NOTIFICATIONS_WRITE), Set.of());
        mvc.perform(api(post("/api/v1/notifications")).header("Authorization", "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"message\":\"m\",\"type\":\"POPUP\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void managerCredentialsAreForbidden() throws Exception {
        Notification existing = seedNotification("N1", "Banner", "BANNER", true);
        String appToken = appToken("MGR1");
        String sameAsOwnerKey = createKey("MGR1", true, Set.of(), Set.of());
        for (String token : List.of(appToken, sameAsOwnerKey)) {
            assertForbidden(token, existing.getId(), "FORBIDDEN");
        }
        assertThat(notifications.findById("N1").orElseThrow().getTitle()).isEqualTo("Banner");
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void keyWithoutNotificationsWriteIsInsufficientScope() throws Exception {
        Notification existing = seedNotification("N1", "Banner", "BANNER", true);
        String key = createKey("SUPER1", false, Set.of(ApiScope.OVERRIDES_WRITE, ApiScope.SCHEDULE_READ), Set.of());
        assertForbidden(key, existing.getId(), "INSUFFICIENT_SCOPE");
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void shulRestrictedKeyIsForbidden() throws Exception {
        Notification existing = seedNotification("N1", "Banner", "BANNER", true);
        String key = createKey("SUPER1", true, Set.of(), Set.of("ORGB"));
        assertForbidden(key, existing.getId(), "FORBIDDEN");
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void adminSessionCookieDoesNotAuthorizeApiWrites() throws Exception {
        Cookie superAdmin = login("superadmin");
        mvc.perform(api(post("/api/v1/notifications")).cookie(superAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"message\":\"m\",\"type\":\"POPUP\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(notifications.count()).isZero();
    }

    private void assertForbidden(String token, String id, String code) throws Exception {
        String body = "{\"title\":\"x\",\"message\":\"m\",\"type\":\"BANNER\"}";
        mvc.perform(api(get("/api/v1/notifications?includeInactive=true")).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(code));
        mvc.perform(api(post("/api/v1/notifications")).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(code));
        mvc.perform(api(patch("/api/v1/notifications/" + id)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(code));
        mvc.perform(api(delete("/api/v1/notifications/" + id)).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value(code));
    }

    // --- helpers -------------------------------------------------------------------------------------------

    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr("10.8." + (ipCounter / 250) + "." + (ipCounter++ % 250 + 1));
            return request;
        });
    }

    private Notification seedNotification(String id, String title, String type, boolean enabled) {
        Notification n = new Notification();
        n.setId(id);
        n.setTitle(title);
        n.setMessage("Message for " + title);
        n.setType(type);
        n.setEnabled(enabled);
        return notifications.save(n);
    }

    private String appToken(String accountId) {
        return appTokens.issue(users.findById(accountId), AppSession.LoginMethod.PASSKEY, "test phone", null).accessToken();
    }

    private String createKey(String owner, boolean sameAsOwner, Set<ApiScope> scopes, Set<String> restriction) {
        String[] raw = new String[1];
        asUser("superadmin", () -> raw[0] = apiKeys.create(owner, "test key", sameAsOwner, scopes, restriction).rawKey());
        return raw[0];
    }

    private void asUser(String username, Runnable action) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority(Role.ADMIN.getName()))));
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Cookie login(String username) throws Exception {
        MvcResult result = mvc.perform(post("/j_spring_security_check")
                        .param("username", username)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static Organization shul(String id, String name, String slug) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName(name);
        organization.setUrlSlug(slug);
        organization.setEnabled(true);
        organization.setOrgColor("#275ed8");
        return organization;
    }

    private static TNMUser account(String id, String username, String organizationId, Role role) {
        return TNMUser.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .encryptedPassword(new BCryptPasswordEncoder().encode(PASSWORD))
                .organizationId(organizationId)
                .roleId(role.getId())
                .enabled(true)
                .build();
    }
}
