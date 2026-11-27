package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.AppSession;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.ApiKeyRepository;
import com.tbdev.teaneckminyanim.repo.AppSessionRepository;
import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.ApiAuthorizationService;
import com.tbdev.teaneckminyanim.service.auth.ApiKeyService;
import com.tbdev.teaneckminyanim.service.auth.AppTokenService;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsFilter;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The bearer-token API chain through the real application stack on H2: API keys and app tokens end to end,
 * session cookies ignored on /api/v1, public endpoints still public, CORS unchanged, error envelopes,
 * refresh rotation against the real database, and the shared WebAuthn relying party.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:api-security;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "logging.level.org.springframework.web=INFO",
        "auth.password-login.enabled=true",
        "api.ratelimit.auth-requests-per-minute=3",
        "superadmin.email="
})
@AutoConfigureMockMvc
@Import(ApiSecurityIntegrationTest.ProbeController.class)
class ApiSecurityIntegrationTest {
    private static final String PASSWORD = "correct-horse-battery";

    /** Test-only endpoints that exercise ApiAuthorizationService and ApiExceptionHandler. */
    @RestController
    static class ProbeController {
        private final ApiAuthorizationService authorization;

        ProbeController(ApiAuthorizationService authorization) {
            this.authorization = authorization;
        }

        @GetMapping("/api/v1/test-probe/me")
        ApiResponse<Map<String, String>> me() {
            return ApiResponse.ok(Map.of("username", authorization.requireAuthenticated().getUsername()));
        }

        @PostMapping("/api/v1/test-probe/organizations/{orgId}/overrides")
        ApiResponse<String> write(@PathVariable String orgId) {
            return ApiResponse.ok(authorization.require(ApiScope.OVERRIDES_WRITE, orgId).getId());
        }

        @GetMapping("/api/v1/test-probe/boom")
        ApiResponse<String> boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/api/v1/test-probe/denied")
        ApiResponse<String> denied() {
            throw new AccessDeniedException("Not your shul.");
        }

        @PostMapping("/api/v1/test-probe/body")
        ApiResponse<Map<String, Object>> body(@RequestBody Map<String, Object> body) {
            return ApiResponse.ok(body);
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TNMUserRepository userRepository;
    @Autowired private OrganizationRepository organizations;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private AppSessionRepository appSessions;
    @Autowired private TNMUserService users;
    @Autowired private ApiKeyService apiKeys;
    @Autowired private AppTokenService appTokens;
    @Autowired private FilterChainProxy filterChainProxy;
    @Autowired private WebAuthnRelyingPartyOperations relyingParty;

    @MockitoBean private RecaptchaService recaptchaService;

    private int ipCounter;

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
        userRepository.save(account("SUPER1", "superadmin", null));
        userRepository.save(account("MGR1", "manager", "ORGA"));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // --- public API and anonymous access ---------------------------------------------------------------

    @Test
    void publicReadsStayPublicAndCreateNoSession() throws Exception {
        MvcResult result = mvc.perform(api(get("/api/v1/organizations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();
        assertThat(result.getResponse().getCookie("SESSION")).isNull();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class)).isZero();
    }

    @Test
    void anonymousCallToAdminEndpointIsUnauthenticated() throws Exception {
        mvc.perform(api(get("/api/v1/test-probe/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void invalidBearerTokenIsRejectedEvenOnPublicEndpoints() throws Exception {
        String bogus = "tnm_" + "A".repeat(40);
        mvc.perform(api(get("/api/v1/organizations")).header("Authorization", "Bearer " + bogus))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        List<String> details = jdbc.queryForList(
                "SELECT DETAILS FROM AUTH_AUDIT_EVENT WHERE EVENT_TYPE = 'TOKEN_REJECTED'", String.class);
        assertThat(details).hasSize(1);
        assertThat(details.get(0)).doesNotContain(bogus).contains("GET /api/v1/organizations");
    }

    // --- session cookies are ignored on /api/v1 ---------------------------------------------------------

    @Test
    void adminSessionCookieDoesNotAuthenticateApiRequests() throws Exception {
        Cookie session = login("superadmin");
        // The cookie is a working admin session...
        mvc.perform(get("/admin/settings").cookie(session)).andExpect(status().isOk());

        // ...but the API chain never reads it.
        MvcResult me = mvc.perform(api(get("/api/v1/test-probe/me")).cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                .andReturn();
        assertThat(me.getResponse().getCookie("SESSION")).isNull();
        mvc.perform(api(post("/api/v1/test-probe/organizations/ORGA/overrides")).cookie(session))
                .andExpect(status().isUnauthorized());
    }

    // --- API keys -----------------------------------------------------------------------------------------

    @Test
    void apiKeyAuthenticatesWithinItsScopeAndOwnersShuls() throws Exception {
        String key = createKey("MGR1", false, Set.of(ApiScope.OVERRIDES_WRITE), Set.of());

        mvc.perform(api(post("/api/v1/test-probe/organizations/ORGA/overrides")).header("Authorization", "Bearer " + key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("MGR1"));
        mvc.perform(api(post("/api/v1/test-probe/organizations/ORGB/overrides")).header("Authorization", "Bearer " + key))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(api(get("/api/v1/test-probe/me")).header("Authorization", "Bearer " + key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("manager"));

        List<String> used = jdbc.queryForList(
                "SELECT DETAILS FROM AUTH_AUDIT_EVENT WHERE EVENT_TYPE = 'API_KEY_USED'", String.class);
        assertThat(used).anySatisfy(detail ->
                assertThat(detail).contains("POST /api/v1/test-probe/organizations/ORGA/overrides"));
        assertThat(used).allSatisfy(detail -> assertThat(detail).doesNotContain(key));
        assertThat(apiKeyRepository.findAll()).singleElement()
                .satisfies(stored -> assertThat(stored.getLastUsedAt()).isNotNull());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM API_KEY WHERE KEY_HASH = ?", Integer.class,
                com.tbdev.teaneckminyanim.service.auth.AuthAuditService.hash(key))).isEqualTo(1);
    }

    @Test
    void keyWithoutTheScopeIsInsufficient() throws Exception {
        String key = createKey("MGR1", false, Set.of(ApiScope.SCHEDULE_READ), Set.of());
        mvc.perform(api(post("/api/v1/test-probe/organizations/ORGA/overrides")).header("Authorization", "Bearer " + key))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_SCOPE"));
    }

    @Test
    void shulRestrictedSuperAdminKeyCannotLeaveItsShuls() throws Exception {
        String key = createKey("SUPER1", true, Set.of(), Set.of("ORGB"));
        mvc.perform(api(post("/api/v1/test-probe/organizations/ORGB/overrides")).header("Authorization", "Bearer " + key))
                .andExpect(status().isOk());
        mvc.perform(api(post("/api/v1/test-probe/organizations/ORGA/overrides")).header("Authorization", "Bearer " + key))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokedKeyIsInvalid() throws Exception {
        String key = createKey("MGR1", true, Set.of(), Set.of());
        asSuperAdmin(() -> apiKeys.revoke(apiKeyRepository.findAll().get(0).getId()));
        mvc.perform(api(get("/api/v1/test-probe/me")).header("Authorization", "Bearer " + key))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void disablingTheOwnerRevokesKeysAndSessions() throws Exception {
        String key = createKey("MGR1", true, Set.of(), Set.of());
        AppTokenService.IssuedTokens tokens = appTokens.issue(users.findById("MGR1"), AppSession.LoginMethod.MAGIC_LINK,
                "phone", null);

        TNMUser manager = users.findById("MGR1");
        manager.setEnabled(false);
        assertThat(users.update(manager)).isTrue();

        assertThat(apiKeyRepository.findAll()).allSatisfy(stored -> {
            assertThat(stored.isRevoked()).isTrue();
            assertThat(stored.getRevokedReason()).isEqualTo("USER_DISABLED");
        });
        assertThat(appSessions.findAll()).allSatisfy(stored -> assertThat(stored.isRevoked()).isTrue());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM AUTH_AUDIT_EVENT WHERE EVENT_TYPE = 'TOKENS_REVOKED_USER_DISABLED'",
                Integer.class)).isEqualTo(1);

        // Re-enabling the account does not bring the credentials back.
        manager.setEnabled(true);
        users.update(manager);
        mvc.perform(api(get("/api/v1/test-probe/me")).header("Authorization", "Bearer " + key))
                .andExpect(status().isUnauthorized());
        mvc.perform(api(get("/api/v1/test-probe/me")).header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
        assertThat(appTokens.refresh(tokens.refreshToken(), null)).isEmpty();
    }

    @Test
    void deletingTheOwnerRemovesKeysAndSessions() {
        createKey("MGR1", true, Set.of(), Set.of());
        appTokens.issue(users.findById("MGR1"), AppSession.LoginMethod.MAGIC_LINK, "phone", null);

        assertThat(users.delete(users.findById("MGR1"))).isTrue();

        assertThat(apiKeyRepository.findAll()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM API_KEY_SCOPE", Integer.class)).isZero();
        assertThat(appSessions.findAll()).isEmpty();
    }

    // --- app tokens ----------------------------------------------------------------------------------------

    @Test
    void appTokenRotationAndReuseDetection() throws Exception {
        AppTokenService.IssuedTokens first = appTokens.issue(users.findById("MGR1"), AppSession.LoginMethod.PASSKEY,
                "phone", null);
        mvc.perform(api(get("/api/v1/test-probe/me")).header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("manager"));
        // A refresh token is never a bearer token.
        mvc.perform(api(get("/api/v1/test-probe/me")).header("Authorization", "Bearer " + first.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        AppTokenService.IssuedTokens second = appTokens.refresh(first.refreshToken(), null).orElseThrow();
        assertThat(appSessions.findById(second.sessionId()).orElseThrow().getFamilyId())
                .isEqualTo(appSessions.findById(first.sessionId()).orElseThrow().getFamilyId());
        // The replaced access token stops working; the new one works.
        mvc.perform(api(get("/api/v1/test-probe/me")).header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isUnauthorized());
        mvc.perform(api(get("/api/v1/test-probe/me")).header("Authorization", "Bearer " + second.accessToken()))
                .andExpect(status().isOk());

        // Replaying the first refresh token revokes the whole family, including the new pair.
        assertThat(appTokens.refresh(first.refreshToken(), null)).isEmpty();
        assertThat(appSessions.findAll()).allSatisfy(stored -> assertThat(stored.isRevoked()).isTrue());
        mvc.perform(api(get("/api/v1/test-probe/me")).header("Authorization", "Bearer " + second.accessToken()))
                .andExpect(status().isUnauthorized());
        assertThat(appTokens.refresh(second.refreshToken(), null)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM AUTH_AUDIT_EVENT WHERE EVENT_TYPE = 'APP_TOKEN_REFRESH_REUSE'", Integer.class))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void concurrentRefreshesIssueExactlyOnePair() throws Exception {
        AppTokenService.IssuedTokens tokens = appTokens.issue(users.findById("MGR1"), AppSession.LoginMethod.MAGIC_LINK,
                "phone", null);
        int callers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<AppTokenService.IssuedTokens>>> results = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                Callable<Optional<AppTokenService.IssuedTokens>> call = () -> {
                    start.await();
                    return appTokens.refresh(tokens.refreshToken(), null);
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            long issued = 0;
            for (Future<Optional<AppTokenService.IssuedTokens>> result : results) {
                if (result.get().isPresent()) issued++;
            }
            assertThat(issued).isLessThanOrEqualTo(1);
            assertThat(appSessions.findAll().size()).isLessThanOrEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void logoutRevokesTheFamily() throws Exception {
        AppTokenService.IssuedTokens tokens = appTokens.issue(users.findById("MGR1"), AppSession.LoginMethod.MAGIC_LINK,
                "phone", null);
        assertThat(appTokens.logout(tokens.refreshToken())).isTrue();
        mvc.perform(api(get("/api/v1/test-probe/me")).header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
        assertThat(appTokens.refresh(tokens.refreshToken(), null)).isEmpty();
    }

    // --- errors, CORS, rate limits, docs, WebAuthn -----------------------------------------------------

    @Test
    void unhandledErrorsUseTheEnvelopeWithoutDetails() throws Exception {
        MvcResult result = mvc.perform(api(get("/api/v1/test-probe/boom")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret").doesNotContain("Exception");

        mvc.perform(api(get("/api/v1/test-probe/denied")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(api(post("/api/v1/test-probe/body")).contentType("application/json").content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void existingControllerErrorsAreUnchanged() throws Exception {
        mvc.perform(api(get("/api/v1/organizations/nope")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(api(get("/api/v1/schedule").param("date", "not-a-date")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE"));
    }

    @Test
    void corsIsUnchanged() throws Exception {
        mvc.perform(api(options("/api/v1/organizations"))
                        .header("Origin", "https://example.org")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://example.org"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,OPTIONS"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(api(options("/api/v1/organizations/ORGA/minyanim/x"))
                        .header("Origin", "https://example.org")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isForbidden());
        mvc.perform(api(get("/api/v1/organizations")).header("Origin", "https://example.org"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://example.org"));
    }

    @Test
    void rateLimiterRunsOnceInsideTheApiChainAndKeepsCorsHeaders() throws Exception {
        // auth bucket is 3/min here; if the limiter ran twice per request the second call would be limited.
        String ip = "198.51.100.77";
        for (int i = 0; i < 3; i++) {
            int status = mvc.perform(post("/api/v1/auth/refresh").with(remote(ip)).header("Origin", "https://example.org"))
                    .andReturn().getResponse().getStatus();
            assertThat(status).isNotEqualTo(429);
        }
        mvc.perform(post("/api/v1/auth/refresh").with(remote(ip)).header("Origin", "https://example.org"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://example.org"));
        // Limiting happens before bearer authentication, so bad tokens are throttled too.
        mvc.perform(post("/api/v1/auth/refresh").with(remote(ip)).header("Authorization", "Bearer junk"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void openApiDeclaresTheBearerScheme() throws Exception {
        mvc.perform(api(get("/api/docs.json")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.info.description").value(org.hamcrest.Matchers.containsString("overrides:write")));
    }

    @Test
    void browserPasskeyFlowUsesTheSharedRelyingPartyBean() {
        List<Object> rpOperations = new ArrayList<>();
        for (SecurityFilterChain chain : filterChainProxy.getFilterChains()) {
            for (Filter filter : chain.getFilters()) {
                if (filter instanceof PublicKeyCredentialRequestOptionsFilter options) {
                    rpOperations.add(ReflectionTestUtils.getField(options, "rpOptions"));
                }
            }
        }
        assertThat(rpOperations).isNotEmpty().allSatisfy(operations -> assertThat(operations).isSameAs(relyingParty));
    }

    @Test
    void apiChainIsFirstAndStateless() {
        List<SecurityFilterChain> chains = filterChainProxy.getFilterChains();
        assertThat(chains).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chains.get(0).getFilters()).anySatisfy(filter ->
                assertThat(filter).isInstanceOf(com.tbdev.teaneckminyanim.security.BearerTokenAuthenticationFilter.class));
        assertThat(chains.get(0).getFilters()).anySatisfy(filter -> assertThat(filter).isInstanceOf(ApiRateLimitFilter.class));
        assertThat(chains.get(1).getFilters()).noneSatisfy(filter ->
                assertThat(filter).isInstanceOf(com.tbdev.teaneckminyanim.security.BearerTokenAuthenticationFilter.class));
    }

    // --- helpers -------------------------------------------------------------------------------------------

    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        // A fresh client IP per request keeps these tests clear of the read/write rate limits.
        return builder.with(remote("10.9." + (ipCounter / 250) + "." + (ipCounter++ % 250 + 1)));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor remote(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private String createKey(String owner, boolean sameAsOwner, Set<ApiScope> scopes, Set<String> restriction) {
        String[] raw = new String[1];
        asSuperAdmin(() -> raw[0] = apiKeys.create(owner, "test key", sameAsOwner, scopes, restriction).rawKey());
        return raw[0];
    }

    private void asSuperAdmin(Runnable action) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "superadmin", null, List.of(new SimpleGrantedAuthority(Role.ADMIN.getName()))));
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

    private static TNMUser account(String id, String username, String organizationId) {
        return TNMUser.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .encryptedPassword(new BCryptPasswordEncoder().encode(PASSWORD))
                .organizationId(organizationId)
                .roleId(Role.ADMIN.getId())
                .enabled(true)
                .build();
    }
}
