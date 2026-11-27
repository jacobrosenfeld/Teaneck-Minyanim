package com.tbdev.teaneckminyanim.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.ApiWebAuthnChallenge;
import com.tbdev.teaneckminyanim.model.AppSession;
import com.tbdev.teaneckminyanim.model.MagicLinkToken;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.ApiKeyRepository;
import com.tbdev.teaneckminyanim.repo.ApiWebAuthnChallengeRepository;
import com.tbdev.teaneckminyanim.repo.AppSessionRepository;
import com.tbdev.teaneckminyanim.repo.MagicLinkTokenRepository;
import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.ApiKeyService;
import com.tbdev.teaneckminyanim.service.auth.AppTokenService;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkService;
import com.tbdev.teaneckminyanim.service.email.EmailMessage;
import com.tbdev.teaneckminyanim.service.email.EmailSendResult;
import com.tbdev.teaneckminyanim.service.email.EmailService;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mobile sign-in endpoints through the real application stack on H2: app magic links, refresh, logout, /me,
 * passkey challenges (with the real relying party), the association files when unset, and the fallback page.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mobile-auth;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "logging.level.org.springframework.web=INFO",
        "superadmin.email=",
        "app.links.apple-team-id=",
        "app.links.ios-bundle-id=",
        "app.links.android-package=",
        "app.links.android-sha256-fingerprints="
})
@AutoConfigureMockMvc
class MobileAuthApiIntegrationTest {
    private static final Pattern APP_LINK = Pattern.compile("(https?://[^\\s\"<]+/app/auth/magic-link\\?token=([A-Za-z0-9_-]+))");
    private static final Pattern WEB_LINK = Pattern.compile("/admin/login/magic-link/verify\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TNMUserRepository userRepository;
    @Autowired private OrganizationRepository organizations;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private AppSessionRepository appSessions;
    @Autowired private MagicLinkTokenRepository magicLinkTokens;
    @Autowired private ApiWebAuthnChallengeRepository challenges;
    @Autowired private TNMUserService users;
    @Autowired private ApiKeyService apiKeys;
    @Autowired private AppTokenService appTokens;
    @Autowired private MagicLinkService magicLinkService;

    @MockitoBean private RecaptchaService recaptchaService;
    @MockitoBean private EmailService emailService;

    private static int ipCounter;
    private static int userCounter;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM SPRING_SESSION");
        jdbc.update("DELETE FROM AUTH_AUDIT_EVENT");
        challenges.deleteAll();
        magicLinkTokens.deleteAll();
        apiKeyRepository.deleteAll();
        appSessions.deleteAll();
        userRepository.deleteAll();
        organizations.deleteAll();
        organizations.save(shul("ORGA", "Shul A", "shul-a", true));
        organizations.save(shul("ORGB", "Shul B", "shul-b", true));
        organizations.save(shul("ORGC", "Closed Shul", "closed", false));
        userRepository.save(account("SUPER1", "superadmin", null));
        when(emailService.send(any())).thenReturn(EmailSendResult.success(null, "sent"));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // --- magic link ---------------------------------------------------------------------------------------

    @Test
    void requestSendsAnAppLinkAndAlwaysReturnsTheNeutralMessage() throws Exception {
        TNMUser manager = newManager("ORGA");

        mvc.perform(api(post("/api/v1/auth/magic-link/request"))
                        .header("Host", "teaneckminyanim.com")
                        .header("X-Forwarded-Proto", "https")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("identifier", manager.getEmail()))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.message").value("If this email is authorized, a sign-in link will be sent shortly."));

        EmailMessage email = sentEmail();
        Matcher link = APP_LINK.matcher(email.getTextBody());
        assertThat(link.find()).isTrue();
        assertThat(link.group(1)).startsWith("https://teaneckminyanim.com/app/auth/magic-link?token=");
        assertThat(email.getTextBody()).contains("on the phone where the").contains("app is installed");
        assertThat(email.getHtmlBody()).contains("/app/auth/magic-link?token=").doesNotContain("/admin/login/magic-link/verify");
        assertThat(magicLinkTokens.findAll()).singleElement().satisfies(token -> {
            assertThat(token.getPurpose()).isEqualTo(MagicLinkService.PURPOSE_APP_LOGIN);
            assertThat(token.getTokenHash()).isEqualTo(MagicLinkService.hashToken(link.group(2)));
        });

        // Unknown and empty identifiers get the same 202 and no email.
        clearInvocations(emailService);
        mvc.perform(api(post("/api/v1/auth/magic-link/request"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("identifier", "nobody@example.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.message").value("If this email is authorized, a sign-in link will be sent shortly."));
        mvc.perform(api(post("/api/v1/auth/magic-link/request")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted());
        verify(emailService, never()).send(any());
    }

    @Test
    void exchangeWorksOnceThenReplayIsRejected() throws Exception {
        TNMUser manager = newManager("ORGA");
        String token = requestAppToken(manager);

        MvcResult result = mvc.perform(api(post("/api/v1/auth/magic-link/exchange"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "deviceName", "Test iPhone"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value(org.hamcrest.Matchers.startsWith("tnmapp_")))
                .andExpect(jsonPath("$.data.refreshToken").value(org.hamcrest.Matchers.startsWith("tnmrt_")))
                .andExpect(jsonPath("$.data.accessExpiresAt").isString())
                .andExpect(jsonPath("$.data.refreshExpiresAt").isString())
                .andExpect(jsonPath("$.data.user.id").value(manager.getId()))
                .andExpect(jsonPath("$.data.user.username").value(manager.getUsername()))
                .andExpect(jsonPath("$.data.user.tokenKind").value("APP"))
                .andExpect(jsonPath("$.data.user.organizations[0].id").value("ORGA"))
                .andExpect(jsonPath("$.data.user.organizations[0].slug").value("shul-a"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("encryptedPassword").doesNotContain("password");

        JsonNode data = objectMapper.readTree(body).path("data");
        AppSession session = appSessions.findAll().get(0);
        assertThat(session.getLoginMethod()).isEqualTo(AppSession.LoginMethod.MAGIC_LINK);
        assertThat(session.getDeviceName()).isEqualTo("Test iPhone");
        TNMUser reloaded = users.findById(manager.getId());
        assertThat(reloaded.getLastLoginMethod()).isEqualTo("MAGIC_LINK");
        assertThat(reloaded.getLastLoginAt()).isNotNull();

        // The issued access token works.
        mvc.perform(api(get("/api/v1/me")).header("Authorization", "Bearer " + data.path("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(manager.getUsername()));

        // Replay: 401 with a generic message.
        mvc.perform(api(post("/api/v1/auth/magic-link/exchange"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("token", token))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.error.message").value(AuthApiController.INVALID_LINK_MESSAGE));
        assertThat(appSessions.count()).isEqualTo(1);

        List<String> audit = jdbc.queryForList("SELECT DETAILS FROM AUTH_AUDIT_EVENT", String.class);
        assertThat(audit).noneSatisfy(detail -> assertThat(String.valueOf(detail)).contains(token));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AUTH_AUDIT_EVENT WHERE EVENT_TYPE = 'MAGIC_LINK_REPLAY_REJECTED'"
                + " OR EVENT_TYPE = 'MAGIC_LINK_REJECTED'", Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AUTH_AUDIT_EVENT WHERE EVENT_TYPE = 'APP_TOKEN_ISSUED'"
                + " AND DETAILS LIKE '%method=MAGIC_LINK%'", Integer.class)).isEqualTo(1);
    }

    @Test
    void exchangeRejectsUnknownMissingAndDisabledUsers() throws Exception {
        mvc.perform(api(post("/api/v1/auth/magic-link/exchange"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("token", "not-a-real-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        mvc.perform(api(post("/api/v1/auth/magic-link/exchange")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        TNMUser manager = newManager("ORGA");
        String token = requestAppToken(manager);
        TNMUser disabled = users.findById(manager.getId());
        disabled.setEnabled(false);
        users.update(disabled);
        mvc.perform(api(post("/api/v1/auth/magic-link/exchange"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("token", token))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        assertThat(appSessions.count()).isZero();
    }

    @Test
    void appAndWebLinksCannotBeSwapped() throws Exception {
        TNMUser manager = newManager("ORGA");

        // A website link token is not accepted by the app exchange...
        magicLinkService.requestLoginLink(manager.getEmail(), null);
        Matcher web = WEB_LINK.matcher(sentEmail().getTextBody());
        assertThat(web.find()).isTrue();
        String webToken = web.group(1);
        mvc.perform(api(post("/api/v1/auth/magic-link/exchange"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("token", webToken))))
                .andExpect(status().isUnauthorized());

        // ...and still works on the website, unchanged.
        mvc.perform(get("/admin/login/magic-link/verify").param("token", webToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("error"))));

        // An app link token is not accepted by the website verify page...
        clearInvocations(emailService);
        String appToken = requestAppToken(manager);
        // (a failed verify re-renders the login page with an error instead of redirecting into /admin)
        mvc.perform(get("/admin/login/magic-link/verify").param("token", appToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isNull());
        // ...and is still unused, so the app can exchange it.
        assertThat(magicLinkTokens.findByTokenHash(MagicLinkService.hashToken(appToken)))
                .get().extracting(MagicLinkToken::getUsedAt).isNull();
        mvc.perform(api(post("/api/v1/auth/magic-link/exchange"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("token", appToken))))
                .andExpect(status().isOk());
    }

    // --- refresh and logout ----------------------------------------------------------------------------------

    @Test
    void refreshRotatesThroughTheEndpoint() throws Exception {
        TNMUser manager = newManager("ORGA");
        AppTokenService.IssuedTokens first = appTokens.issue(manager, AppSession.LoginMethod.MAGIC_LINK, "phone", null);

        JsonNode second = data(mvc.perform(api(post("/api/v1/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", first.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.username").value(manager.getUsername()))
                .andReturn());
        assertThat(second.path("refreshToken").asText()).startsWith("tnmrt_").isNotEqualTo(first.refreshToken());
        assertThat(second.path("accessToken").asText()).isNotEqualTo(first.accessToken());

        mvc.perform(api(get("/api/v1/me")).header("Authorization", "Bearer " + second.path("accessToken").asText()))
                .andExpect(status().isOk());
        mvc.perform(api(get("/api/v1/me")).header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isUnauthorized());

        // Reusing the old refresh token is rejected and revokes the family.
        mvc.perform(api(post("/api/v1/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", first.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        mvc.perform(api(post("/api/v1/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", second.path("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());

        mvc.perform(api(post("/api/v1/auth/refresh")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(api(post("/api/v1/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", "tnmrt_bogus"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void logoutThenRefreshFails() throws Exception {
        TNMUser manager = newManager("ORGA");
        AppTokenService.IssuedTokens tokens = appTokens.issue(manager, AppSession.LoginMethod.MAGIC_LINK, "phone", null);

        mvc.perform(api(post("/api/v1/auth/logout"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", tokens.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revoked").value(true));
        mvc.perform(api(post("/api/v1/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", tokens.refreshToken()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(api(get("/api/v1/me")).header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());

        // Not an oracle: unknown, malformed and missing tokens all get 200.
        mvc.perform(api(post("/api/v1/auth/logout"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", "tnmrt_" + "x".repeat(43)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revoked").value(false));
        mvc.perform(api(post("/api/v1/auth/logout")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revoked").value(false));
    }

    // --- /me ---------------------------------------------------------------------------------------------------

    @Test
    void meForAnAppToken() throws Exception {
        TNMUser manager = newManager("ORGA");
        manager.getAdditionalOrganizationIds().add("ORGB");
        manager.getAdditionalOrganizationIds().add("ORGC");
        userRepository.save(manager);
        AppTokenService.IssuedTokens tokens = appTokens.issue(users.findById(manager.getId()),
                AppSession.LoginMethod.PASSKEY, "phone", null);

        String body = mvc.perform(api(get("/api/v1/me")).header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(manager.getId()))
                .andExpect(jsonPath("$.data.email").value(manager.getEmail()))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.superAdmin").value(false))
                .andExpect(jsonPath("$.data.tokenKind").value("APP"))
                .andExpect(jsonPath("$.data.sameAsOwner").value(true))
                .andExpect(jsonPath("$.data.scopes.length()").value(ApiScope.values().length))
                .andExpect(jsonPath("$.data.scopes[0]").value("schedule:read"))
                .andExpect(jsonPath("$.data.restrictedOrganizationIds.length()").value(0))
                // The disabled shul is left out.
                .andExpect(jsonPath("$.data.organizations.length()").value(2))
                .andExpect(jsonPath("$.data.organizations[1].name").value("Shul B"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("password").doesNotContain(tokens.accessToken());

        mvc.perform(api(get("/api/v1/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void meForAScopedKeyAndForASuperAdmin() throws Exception {
        String key = createKey("SUPER1", false, Set.of(ApiScope.OVERRIDES_WRITE, ApiScope.SCHEDULE_READ), Set.of("ORGB"));
        mvc.perform(api(get("/api/v1/me")).header("Authorization", "Bearer " + key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("superadmin"))
                .andExpect(jsonPath("$.data.superAdmin").value(true))
                .andExpect(jsonPath("$.data.tokenKind").value("KEY"))
                .andExpect(jsonPath("$.data.sameAsOwner").value(false))
                .andExpect(jsonPath("$.data.scopes.length()").value(2))
                .andExpect(jsonPath("$.data.scopes[0]").value("schedule:read"))
                .andExpect(jsonPath("$.data.scopes[1]").value("overrides:write"))
                .andExpect(jsonPath("$.data.restrictedOrganizationIds[0]").value("ORGB"))
                .andExpect(jsonPath("$.data.organizations.length()").value(1))
                .andExpect(jsonPath("$.data.organizations[0].id").value("ORGB"));

        String ownerKey = createKey("SUPER1", true, Set.of(), Set.of());
        mvc.perform(api(get("/api/v1/me")).header("Authorization", "Bearer " + ownerKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenKind").value("KEY"))
                .andExpect(jsonPath("$.data.sameAsOwner").value(true))
                .andExpect(jsonPath("$.data.scopes.length()").value(ApiScope.values().length))
                // A super admin sees every enabled shul.
                .andExpect(jsonPath("$.data.organizations.length()").value(2));
    }

    // --- passkey challenges (real relying party) -------------------------------------------------------------

    @Test
    void passkeyOptionsPersistASingleUseChallenge() throws Exception {
        JsonNode data = data(mvc.perform(api(post("/api/v1/auth/passkey/options")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeId").isString())
                .andExpect(jsonPath("$.data.publicKey.challenge").isString())
                .andExpect(jsonPath("$.data.publicKey.rpId").value("localhost"))
                .andExpect(jsonPath("$.data.publicKey.timeout").value(300000))
                .andExpect(jsonPath("$.data.publicKey.userVerification").value("preferred"))
                .andExpect(jsonPath("$.data.publicKey.allowCredentials.length()").value(0))
                .andReturn());
        ApiWebAuthnChallenge stored = challenges.findById(data.path("challengeId").asText()).orElseThrow();
        assertThat(stored.getUsedAt()).isNull();
        assertThat(stored.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(4))
                .isBefore(LocalDateTime.now().plusMinutes(6));
        assertThat(objectMapper.readTree(stored.getOptionsJson())).isEqualTo(data.path("publicKey"));
        // No browser session is involved.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class)).isZero();
    }

    @Test
    void passkeyVerifyRejectsUnknownExpiredReusedAndInvalidAssertions() throws Exception {
        Map<String, Object> credential = fakeCredential();

        verifyPasskey("does-not-exist", credential).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.error.message").value(PasskeyAuthApiController.FAILED_MESSAGE));

        // Expired.
        String expiredId = newChallenge();
        ApiWebAuthnChallenge expired = challenges.findById(expiredId).orElseThrow();
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        challenges.save(expired);
        verifyPasskey(expiredId, credential).andExpect(status().isUnauthorized());

        // A real challenge with an assertion that does not verify: 401, and the challenge is used up.
        String challengeId = newChallenge();
        String body = verifyPasskey(challengeId, credential).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("authenticator").doesNotContain("Exception");
        assertThat(challenges.findById(challengeId).orElseThrow().getUsedAt()).isNotNull();
        // Reused.
        verifyPasskey(challengeId, credential).andExpect(status().isUnauthorized());

        // Malformed requests are 400.
        String fresh = newChallenge();
        verifyPasskey(fresh, Map.of("id", 42, "response", "nope")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(api(post("/api/v1/auth/passkey/verify")).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("challengeId", fresh))))
                .andExpect(status().isBadRequest());
        mvc.perform(api(post("/api/v1/auth/passkey/verify")).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());

        assertThat(appSessions.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AUTH_AUDIT_EVENT WHERE EVENT_TYPE = 'PASSKEY_LOGIN_FAILED'",
                Integer.class)).isGreaterThanOrEqualTo(5);
    }

    // --- association files and fallback page ------------------------------------------------------------------

    @Test
    void associationFilesAre404WhenUnset() throws Exception {
        mvc.perform(get("/.well-known/apple-app-site-association")).andExpect(status().isNotFound());
        mvc.perform(get("/.well-known/assetlinks.json")).andExpect(status().isNotFound());
    }

    @Test
    void fallbackPageNeverShowsTheToken() throws Exception {
        TNMUser manager = newManager("ORGA");
        String token = requestAppToken(manager);

        MvcResult result = mvc.perform(get("/app/auth/magic-link").param("token", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();
        String html = result.getResponse().getContentAsString();
        assertThat(html).doesNotContain(token)
                .contains("on the phone where the")
                .contains("href=\"/admin/login\"")
                .doesNotContain("googletagmanager")
                .doesNotContain("<script")
                .doesNotContain("confirm(")
                .doesNotContain("alert(");
        // Viewing the page does not consume the token.
        assertThat(magicLinkTokens.findByTokenHash(MagicLinkService.hashToken(token)))
                .get().extracting(MagicLinkToken::getUsedAt).isNull();
        assertThat(result.getResponse().getCookie("SESSION")).isNull();

        mvc.perform(get("/app/auth/magic-link")).andExpect(status().isOk());
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions verifyPasskey(String challengeId, Object credential)
            throws Exception {
        return mvc.perform(api(post("/api/v1/auth/passkey/verify")).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("challengeId", challengeId, "credential", credential))));
    }

    private String newChallenge() throws Exception {
        return data(mvc.perform(api(post("/api/v1/auth/passkey/options"))).andExpect(status().isOk()).andReturn())
                .path("challengeId").asText();
    }

    /** A well-formed PublicKeyCredential for a credential id the server does not know. */
    static Map<String, Object> fakeCredential() {
        return Map.of(
                "id", "AQIDBAUGBwgJCgsMDQ4PEA",
                "rawId", "AQIDBAUGBwgJCgsMDQ4PEA",
                "type", "public-key",
                "response", Map.of(
                        "authenticatorData", "SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MFAAAAAQ",
                        "clientDataJSON", "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0",
                        "signature", "MEUCIQ",
                        "userHandle", "U1VQRVIx"),
                "clientExtensionResults", Map.of(),
                "authenticatorAttachment", "platform");
    }

    private String requestAppToken(TNMUser user) throws Exception {
        clearInvocations(emailService);
        mvc.perform(api(post("/api/v1/auth/magic-link/request"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("identifier", user.getEmail()))))
                .andExpect(status().isAccepted());
        Matcher link = APP_LINK.matcher(sentEmail().getTextBody());
        assertThat(link.find()).isTrue();
        return link.group(2);
    }

    private EmailMessage sentEmail() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        return captor.getValue();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        // A fresh client IP per request keeps these tests clear of the API and magic-link rate limits.
        int n = ipCounter++;
        return builder.with(remote("10.77." + (n / 250) + "." + (n % 250 + 1)));
    }

    private static RequestPostProcessor remote(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private TNMUser newManager(String organizationId) {
        int n = ++userCounter;
        return userRepository.save(account("MGR" + n, "manager" + n, organizationId));
    }

    private String createKey(String owner, boolean sameAsOwner, Set<ApiScope> scopes, Set<String> restriction) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "superadmin", null, List.of(new SimpleGrantedAuthority(Role.ADMIN.getName()))));
        try {
            return apiKeys.create(owner, "test key", sameAsOwner, scopes, restriction).rawKey();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static Organization shul(String id, String name, String slug, boolean enabled) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName(name);
        organization.setUrlSlug(slug);
        organization.setEnabled(enabled);
        organization.setOrgColor("#275ed8");
        return organization;
    }

    static TNMUser account(String id, String username, String organizationId) {
        return TNMUser.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .encryptedPassword("{noop}unused")
                .organizationId(organizationId)
                .roleId(Role.ADMIN.getId())
                .enabled(true)
                .build();
    }
}
