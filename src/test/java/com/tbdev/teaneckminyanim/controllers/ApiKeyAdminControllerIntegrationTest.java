package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.ApiKey;
import com.tbdev.teaneckminyanim.model.AppSession;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.ApiKeyRepository;
import com.tbdev.teaneckminyanim.repo.AppSessionRepository;
import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.ApiKeyService;
import com.tbdev.teaneckminyanim.service.auth.AppTokenService;
import com.tbdev.teaneckminyanim.service.auth.AuthAuditService;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The /admin/api-keys page through the real stack on H2. */
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
class ApiKeyAdminControllerIntegrationTest {
    private static final String PASSWORD = "correct-horse-battery";
    private static final Pattern RAW_KEY = Pattern.compile("id=\"new-key-value\"[^>]*>(tnm_[A-Za-z0-9]{40})<");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TNMUserRepository userRepository;
    @Autowired private OrganizationRepository organizations;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private AppSessionRepository appSessions;
    @Autowired private TNMUserService users;
    @Autowired private ApiKeyService apiKeys;
    @Autowired private AppTokenService appTokens;

    @MockitoBean private RecaptchaService recaptchaService;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM SPRING_SESSION");
        jdbc.update("DELETE FROM AUTH_AUDIT_EVENT");
        apiKeyRepository.deleteAll();
        appSessions.deleteAll();
        userRepository.deleteAll();
        organizations.deleteAll();
        organizations.save(shul("ORGA", "Shul A", "shul-a"));
        organizations.save(shul("ORGB", "Shul B", "shul-b"));
        userRepository.save(account("SUPER1", "superadmin", null, Role.ADMIN));
        userRepository.save(account("MGR1", "manager", "ORGA", Role.ADMIN));
        userRepository.save(account("USR1", "plainuser", "ORGA", Role.USER));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // --- access --------------------------------------------------------------------------------------------

    @Test
    void superAdminSeesThePage() throws Exception {
        Cookie session = login("superadmin");
        String html = mvc.perform(get("/admin/api-keys").cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("API keys").contains("Create API key").contains("Active keys")
                .contains("Revoked keys").contains("Active mobile sessions").contains("scope-list");
        assertThat(html).contains("notifications:write").contains("Shul A").contains("Shul B");
    }

    @Test
    void managersAndUsersAreRefused() throws Exception {
        for (String username : List.of("manager", "plainuser")) {
            Cookie session = login(username);
            expectRefused(mvc.perform(get("/admin/api-keys").cookie(session)).andReturn());
            expectRefused(mvc.perform(post("/admin/api-keys/create").cookie(session).with(csrf())
                    .param("ownerId", "MGR1").param("name", "sneaky").param("permissions", "same")).andReturn());
            expectRefused(mvc.perform(post("/admin/api-keys/NOPE/revoke").cookie(session).with(csrf())).andReturn());
            expectRefused(mvc.perform(post("/admin/api-keys/sessions/NOPE/revoke").cookie(session).with(csrf())).andReturn());
        }
        assertThat(apiKeyRepository.count()).isZero();
    }

    private static void expectRefused(MvcResult result) {
        int status = result.getResponse().getStatus();
        assertThat(status == 403 || (status >= 300 && status < 400)).as("status %s", status).isTrue();
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        MvcResult result = mvc.perform(get("/admin/api-keys")).andReturn();
        assertThat(result.getResponse().getStatus()).isBetween(300, 403);
    }

    // --- create --------------------------------------------------------------------------------------------

    @Test
    void createShowsTheKeyExactlyOnce() throws Exception {
        Cookie session = login("superadmin");
        MvcResult created = mvc.perform(post("/admin/api-keys/create").cookie(session).with(csrf())
                        .param("ownerId", "MGR1").param("name", "Website sync").param("permissions", "same"))
                .andExpect(status().isOk())
                .andReturn();
        String html = created.getResponse().getContentAsString();
        assertThat(created.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(html).contains("This key will not be shown again.").contains("Website sync");
        Matcher matcher = RAW_KEY.matcher(html);
        assertThat(matcher.find()).as("raw key rendered once").isTrue();
        String raw = matcher.group(1);
        assertThat(matcher.find()).isFalse();
        assertThat(html.split(Pattern.quote(raw), -1)).hasSize(2);

        // Only the hash and display prefix are stored.
        ApiKey stored = apiKeyRepository.findAll().get(0);
        assertThat(stored.isSameAsOwner()).isTrue();
        assertThat(stored.getAccountId()).isEqualTo("MGR1");
        assertThat(stored.getKeyHash()).isEqualTo(AuthAuditService.hash(raw));
        assertThat(stored.getDisplayPrefix()).isEqualTo(raw.substring(0, 12));

        // A following GET shows the prefix but never the key.
        String next = mvc.perform(get("/admin/api-keys").cookie(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(next).doesNotContain(raw).doesNotContain("new-key-panel").contains(stored.getDisplayPrefix())
                .contains("Website sync").contains("Same as owner");

        // The key is not in any persisted session attribute, flash value or audit row.
        List<byte[]> attributes = jdbc.query("SELECT ATTRIBUTE_BYTES FROM SPRING_SESSION_ATTRIBUTES",
                (rs, i) -> rs.getBytes(1));
        assertThat(attributes).isNotEmpty();
        for (byte[] bytes : attributes) {
            assertThat(new String(bytes, StandardCharsets.ISO_8859_1)).doesNotContain(raw);
        }
        List<String> audit = jdbc.queryForList("SELECT DETAILS FROM AUTH_AUDIT_EVENT", String.class);
        assertThat(audit).isNotEmpty().noneMatch(detail -> detail != null && detail.contains(raw));
    }

    @Test
    void createWithScopesAndShulRestriction() throws Exception {
        Cookie session = login("superadmin");
        mvc.perform(post("/admin/api-keys/create").cookie(session).with(csrf())
                        .param("ownerId", "SUPER1").param("name", "Scoped").param("permissions", "scopes")
                        .param("scopes", "overrides:write", "schedule:read").param("shuls", "ORGB"))
                .andExpect(status().isOk());
        ApiKey stored = apiKeyRepository.findAll().get(0);
        assertThat(stored.isSameAsOwner()).isFalse();
        assertThat(stored.getScopes()).containsExactlyInAnyOrder(ApiScope.OVERRIDES_WRITE, ApiScope.SCHEDULE_READ);
        assertThat(stored.getRestrictedOrganizationIds()).containsExactly("ORGB");

        String html = mvc.perform(get("/admin/api-keys").cookie(session)).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("overrides:write").contains("schedule:read").contains("Shul B");
    }

    @Test
    void validationErrorKeepsTheFormAndShowsNoKey() throws Exception {
        Cookie session = login("superadmin");
        MvcResult result = mvc.perform(post("/admin/api-keys/create").cookie(session).with(csrf())
                        .param("ownerId", "MGR1").param("name", "Needs scopes").param("permissions", "scopes"))
                .andExpect(status().isOk())
                .andReturn();
        String html = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(html).contains("Choose at least one scope").contains("value=\"Needs scopes\"")
                .doesNotContain("new-key-value").doesNotContain("will not be shown again");
        assertThat(html).contains("data-open-create=\"true\"");
        assertThat(apiKeyRepository.count()).isZero();

        // A bogus shul or scope value is rejected the same way.
        mvc.perform(post("/admin/api-keys/create").cookie(session).with(csrf())
                        .param("ownerId", "MGR1").param("name", "Other shul").param("permissions", "same")
                        .param("shuls", "ORGB"))
                .andExpect(status().isOk());
        assertThat(apiKeyRepository.count()).isZero();
        mvc.perform(post("/admin/api-keys/create").cookie(session).with(csrf())
                        .param("ownerId", "MGR1").param("name", "Bad scope").param("permissions", "scopes")
                        .param("scopes", "everything:write"))
                .andExpect(status().isOk());
        assertThat(apiKeyRepository.count()).isZero();
    }

    @Test
    void namesAreEscaped() throws Exception {
        Cookie session = login("superadmin");
        mvc.perform(post("/admin/api-keys/create").cookie(session).with(csrf())
                        .param("ownerId", "MGR1").param("name", "<script>alert(1)</script>").param("permissions", "same"))
                .andExpect(status().isOk());
        String html = mvc.perform(get("/admin/api-keys").cookie(session)).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("<script>alert(1)</script>").contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    // --- revoke --------------------------------------------------------------------------------------------

    @Test
    void revokeKeyRedirectsAndMarksItRevoked() throws Exception {
        Cookie session = login("superadmin");
        String keyId = createKeyDirectly("MGR1", "Old key");

        MvcResult result = mvc.perform(post("/admin/api-keys/" + keyId + "/revoke").cookie(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).endsWith("/admin/api-keys");
        assertThat(apiKeyRepository.findById(keyId).orElseThrow().isRevoked()).isTrue();

        String html = mvc.perform(get("/admin/api-keys").cookie(session)).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Revoked").contains("Old key");

        // Unknown id: PRG with an error, not a 500.
        mvc.perform(post("/admin/api-keys/NOPE/revoke").cookie(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void revokeButtonsUseTheSharedConfirmDialog() throws Exception {
        Cookie session = login("superadmin");
        createKeyDirectly("MGR1", "Old key");
        String html = mvc.perform(get("/admin/api-keys").cookie(session)).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("data-confirm").contains("data-confirm-severity=\"danger\"")
                .contains("data-confirm-title=\"Revoke API key?\"").contains("Old key");
        assertThat(html).doesNotContain("onclick=\"return confirm");
    }

    @Test
    void postsWithoutCsrfAreRejected() throws Exception {
        Cookie session = login("superadmin");
        String keyId = createKeyDirectly("MGR1", "Old key");
        mvc.perform(post("/admin/api-keys/create").cookie(session)
                        .param("ownerId", "MGR1").param("name", "No csrf").param("permissions", "same"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/api-keys/" + keyId + "/revoke").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/api-keys/sessions/X/revoke").cookie(session)).andExpect(status().isForbidden());
        assertThat(apiKeyRepository.count()).isEqualTo(1);
        assertThat(apiKeyRepository.findById(keyId).orElseThrow().isRevoked()).isFalse();
    }

    // --- mobile sessions -----------------------------------------------------------------------------------

    @Test
    void sessionListRendersAndRevokeWorks() throws Exception {
        Cookie session = login("superadmin");
        AppTokenService.IssuedTokens tokens = appTokens.issue(users.findById("MGR1"), AppSession.LoginMethod.PASSKEY,
                "Jacob's iPhone", null);

        String html = mvc.perform(get("/admin/api-keys").cookie(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Jacob&#39;s iPhone").contains("manager").contains("Passkey")
                .contains("data-confirm-title=\"Revoke mobile session?\"");
        assertThat(html).doesNotContain(tokens.accessToken()).doesNotContain(tokens.refreshToken());

        mvc.perform(post("/admin/api-keys/sessions/" + tokens.sessionId() + "/revoke").cookie(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(appSessions.findById(tokens.sessionId()).orElseThrow().isRevoked()).isTrue();
        assertThat(appTokens.resolveAccess(tokens.accessToken())).isEmpty();

        String after = mvc.perform(get("/admin/api-keys").cookie(session)).andReturn().getResponse().getContentAsString();
        assertThat(after).doesNotContain("Jacob&#39;s iPhone").contains("No active mobile sessions");
    }

    // --- helpers -------------------------------------------------------------------------------------------

    private String createKeyDirectly(String owner, String name) {
        String[] id = new String[1];
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "superadmin", null, List.of(new SimpleGrantedAuthority(Role.ADMIN.getName()))));
        try {
            id[0] = apiKeys.create(owner, name, true, Set.of(), Set.of()).key().getId();
        } finally {
            SecurityContextHolder.clearContext();
        }
        return id[0];
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
