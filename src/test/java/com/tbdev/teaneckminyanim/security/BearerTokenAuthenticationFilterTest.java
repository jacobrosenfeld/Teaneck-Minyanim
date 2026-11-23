package com.tbdev.teaneckminyanim.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.ApiKey;
import com.tbdev.teaneckminyanim.model.AppSession;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.ApiKeyService;
import com.tbdev.teaneckminyanim.service.auth.AppTokenService;
import com.tbdev.teaneckminyanim.service.auth.AuthAuditService;
import com.tbdev.teaneckminyanim.service.auth.SecureTokens;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BearerTokenAuthenticationFilterTest {
    private final ApiKeyService apiKeys = mock(ApiKeyService.class);
    private final AppTokenService appTokens = mock(AppTokenService.class);
    private final TNMUserService users = mock(TNMUserService.class);
    private final AuthAuditService audit = mock(AuthAuditService.class);
    private final BearerTokenAuthenticationFilter filter =
            new BearerTokenAuthenticationFilter(apiKeys, appTokens, users, audit, new ObjectMapper());

    private TNMUser owner;
    private final AtomicReference<Authentication> seen = new AtomicReference<>();
    private final FilterChain chain = (request, response) ->
            seen.set(SecurityContextHolder.getContext().getAuthentication());
    private boolean chainCalled;

    @BeforeEach
    void setUp() {
        owner = TNMUser.builder().id("MGR").username("manager").email("m@example.com")
                .encryptedPassword("!PASSWORDLESS").organizationId("ORGA").roleId(Role.ADMIN.getId()).enabled(true).build();
        when(users.findById("MGR")).thenReturn(owner);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthorizationHeaderPassesThroughAnonymously() throws Exception {
        MockHttpServletResponse response = run(request(null));
        assertTrue(chainCalled);
        assertNull(seen.get());
        assertEquals(200, response.getStatus());
        verifyNoInteractions(apiKeys, appTokens, audit);
    }

    @Test
    void nonBearerSchemeIsIgnored() throws Exception {
        run(request("Basic dXNlcjpwYXNz"));
        assertTrue(chainCalled);
        assertNull(seen.get());
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer", "Bearer ", "Bearer    ", "Bearertnm_abc", "Bearer not-a-token",
            "Bearer tnm_tooshort", "Bearer tnmapp_tooshort", "Bearer tnmrt_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void malformedOrUnrecognisedTokensAreRejected(String header) throws Exception {
        assertRejected(run(request(header)), header);
    }

    @Test
    void unknownApiKeyIsRejected() throws Exception {
        String raw = SecureTokens.newApiKey();
        when(apiKeys.resolve(eq(raw), any())).thenReturn(Optional.empty());
        assertRejected(run(request("Bearer " + raw)), raw);
    }

    @Test
    void unknownOrExpiredAccessTokenIsRejected() throws Exception {
        String raw = SecureTokens.newAccessToken();
        when(appTokens.resolveAccess(raw)).thenReturn(Optional.empty());
        assertRejected(run(request("Bearer " + raw)), raw);
    }

    @Test
    void refreshTokenIsNotABearerToken() throws Exception {
        String raw = SecureTokens.newRefreshToken();
        assertRejected(run(request("Bearer " + raw)), raw);
        verifyNoInteractions(appTokens, apiKeys);
    }

    @Test
    void keyWithDisabledMissingOrRolelessOwnerIsRejected() throws Exception {
        String raw = SecureTokens.newApiKey();
        when(apiKeys.resolve(eq(raw), any())).thenReturn(Optional.of(ApiKey.builder().id("K1").accountId("MGR").build()));

        owner.setEnabled(false);
        assertRejected(run(request("Bearer " + raw)), raw);

        owner.setEnabled(true);
        owner.setRoleId(null);
        assertRejected(run(request("Bearer " + raw)), raw);

        when(users.findById("MGR")).thenReturn(null);
        assertRejected(run(request("Bearer " + raw)), raw);
    }

    @Test
    void appTokenWithDisabledAccountIsRejected() throws Exception {
        String raw = SecureTokens.newAccessToken();
        when(appTokens.resolveAccess(raw)).thenReturn(Optional.of(AppSession.builder().id("S1").accountId("MGR").build()));
        owner.setEnabled(false);
        assertRejected(run(request("Bearer " + raw)), raw);
    }

    @Test
    void validApiKeySetsAuthenticationAndAuditsUse() throws Exception {
        String raw = SecureTokens.newApiKey();
        ApiKey key = ApiKey.builder().id("K1").accountId("MGR").scopes(Set.of(ApiScope.OVERRIDES_WRITE))
                .restrictedOrganizationIds(Set.of("ORGA")).build();
        when(apiKeys.resolve(eq(raw), any())).thenReturn(Optional.of(key));

        MockHttpServletRequest request = request("Bearer " + raw);
        request.setMethod("POST");
        run(request);

        assertTrue(chainCalled);
        ApiAuthentication auth = assertInstanceOf(ApiAuthentication.class, seen.get());
        assertTrue(auth.isAuthenticated());
        assertEquals("manager", auth.getName());
        assertEquals(ApiAuthentication.Kind.KEY, auth.getKind());
        assertEquals("K1", auth.getCredentialId());
        assertEquals(Set.of(ApiScope.OVERRIDES_WRITE), auth.getScopes());
        assertEquals(Set.of("ORGA"), auth.getRestrictedOrganizationIds());
        assertEquals(Role.ADMIN.getName(), auth.getAuthorities().iterator().next().getAuthority());
        assertNull(auth.getCredentials());
        assertFalse(auth.toString().contains(raw));
        verify(audit).record(eq("API_KEY_USED"), eq("MGR"), any(), eq("key=K1 POST /api/v1/organizations/x/overrides"));
    }

    @Test
    void validAppTokenSetsAuthenticationWithEveryScope() throws Exception {
        String raw = SecureTokens.newAccessToken();
        when(appTokens.resolveAccess(raw)).thenReturn(Optional.of(AppSession.builder().id("S1").accountId("MGR").build()));

        run(request("bearer " + raw));

        ApiAuthentication auth = assertInstanceOf(ApiAuthentication.class, seen.get());
        assertEquals(ApiAuthentication.Kind.APP, auth.getKind());
        assertEquals("S1", auth.getCredentialId());
        for (ApiScope scope : ApiScope.values()) assertTrue(auth.hasScope(scope));
        assertFalse(auth.isOrganizationRestricted());
        verify(audit).record(eq("APP_TOKEN_USED"), eq("MGR"), any(), eq("session=S1 GET /api/v1/organizations/x/overrides"));
    }

    private void assertRejected(MockHttpServletResponse response, String rawToken) throws Exception {
        assertFalse(chainCalled, "chain must stop");
        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        assertTrue(response.getContentType().startsWith("application/json"));
        String body = response.getContentAsString();
        assertTrue(body.contains("\"code\":\"INVALID_TOKEN\""), body);
        assertFalse(body.contains("\"data\""), body);
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(audit, atLeastOnce()).record(eq("TOKEN_REJECTED"), any(), any(), details.capture());
        String token = rawToken.startsWith("Bearer") ? rawToken.substring(6).trim() : rawToken;
        if (token.length() > 6) {
            for (String detail : details.getAllValues()) {
                assertFalse(detail.contains(token), "audit details must not contain the token");
            }
        }
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/organizations/x/overrides");
        if (authorization != null) request.addHeader("Authorization", authorization);
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        chainCalled = false;
        seen.set(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (FilterChain) (req, res) -> {
            chainCalled = true;
            chain.doFilter(req, res);
        });
        return response;
    }
}
