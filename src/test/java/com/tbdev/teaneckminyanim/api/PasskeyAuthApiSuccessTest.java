package com.tbdev.teaneckminyanim.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbdev.teaneckminyanim.model.AppSession;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.ApiWebAuthnChallengeRepository;
import com.tbdev.teaneckminyanim.repo.AppSessionRepository;
import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.NativePasskeyService;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Native passkey sign-in with the relying party mocked, so the success path can run without a real authenticator:
 * the stored challenge is the one verified, tokens are issued, and the login is recorded like a website passkey login.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:passkey-success;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "logging.level.org.springframework.web=INFO",
        "superadmin.email="
})
@AutoConfigureMockMvc
class PasskeyAuthApiSuccessTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TNMUserRepository userRepository;
    @Autowired private OrganizationRepository organizations;
    @Autowired private AppSessionRepository appSessions;
    @Autowired private ApiWebAuthnChallengeRepository challenges;
    @Autowired private TNMUserService users;
    @Autowired private NativePasskeyService passkeys;

    @MockitoBean private WebAuthnRelyingPartyOperations relyingParty;
    @MockitoBean private RecaptchaService recaptchaService;

    private static int ipCounter;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM AUTH_AUDIT_EVENT");
        challenges.deleteAll();
        appSessions.deleteAll();
        userRepository.deleteAll();
        organizations.deleteAll();
        userRepository.save(MobileAuthApiIntegrationTest.account("MGR1", "manager", null));
        when(relyingParty.createCredentialRequestOptions(any())).thenAnswer(invocation ->
                PublicKeyCredentialRequestOptions.builder()
                        .challenge(Bytes.random())
                        .rpId("teaneckminyanim.com")
                        .timeout(Duration.ofMinutes(5))
                        .allowCredentials(List.of())
                        .userVerification(UserVerificationRequirement.PREFERRED)
                        .build());
    }

    @Test
    void successfulAssertionIssuesTokensAndRecordsAPasskeyLogin() throws Exception {
        when(relyingParty.authenticate(any())).thenReturn(userEntity("MGR1"));
        JsonNode options = data(mvc.perform(api(post("/api/v1/auth/passkey/options"))).andExpect(status().isOk()).andReturn());
        String challengeId = options.path("challengeId").asText();

        JsonNode tokens = data(verifyPasskey(challengeId, "Test Pixel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value(org.hamcrest.Matchers.startsWith("tnmapp_")))
                .andExpect(jsonPath("$.data.refreshToken").value(org.hamcrest.Matchers.startsWith("tnmrt_")))
                .andExpect(jsonPath("$.data.user.id").value("MGR1"))
                .andExpect(jsonPath("$.data.user.tokenKind").value("APP"))
                .andReturn());

        // The relying party verified the stored challenge, rebuilt with the shared constant and an anonymous request.
        ArgumentCaptor<RelyingPartyAuthenticationRequest> request = ArgumentCaptor.forClass(RelyingPartyAuthenticationRequest.class);
        verify(relyingParty).authenticate(request.capture());
        PublicKeyCredentialRequestOptions verified = request.getValue().getRequestOptions();
        assertThat(verified.getChallenge().toBase64UrlString()).isEqualTo(options.path("publicKey").path("challenge").asText());
        assertThat(verified.getRpId()).isEqualTo("teaneckminyanim.com");
        assertThat(verified.getUserVerification()).isSameAs(UserVerificationRequirement.PREFERRED);
        assertThat(verified.getAllowCredentials()).isEmpty();
        assertThat(request.getValue().getPublicKey().getRawId().toBase64UrlString()).isEqualTo("AQIDBAUGBwgJCgsMDQ4PEA");
        ArgumentCaptor<PublicKeyCredentialRequestOptionsRequest> optionsRequest =
                ArgumentCaptor.forClass(PublicKeyCredentialRequestOptionsRequest.class);
        verify(relyingParty).createCredentialRequestOptions(optionsRequest.capture());
        assertThat(optionsRequest.getValue().getAuthentication().isAuthenticated()).isTrue();
        assertThat(optionsRequest.getValue().getAuthentication())
                .isInstanceOf(org.springframework.security.authentication.AnonymousAuthenticationToken.class);

        AppSession session = appSessions.findAll().get(0);
        assertThat(session.getLoginMethod()).isEqualTo(AppSession.LoginMethod.PASSKEY);
        assertThat(session.getDeviceName()).isEqualTo("Test Pixel");
        TNMUser user = users.findById("MGR1");
        assertThat(user.getLastLoginMethod()).isEqualTo("PASSKEY");
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AUTH_AUDIT_EVENT WHERE EVENT_TYPE = 'PASSKEY_LOGIN_SUCCEEDED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AUTH_AUDIT_EVENT WHERE EVENT_TYPE = 'APP_TOKEN_ISSUED'"
                + " AND DETAILS LIKE '%method=PASSKEY%'", Integer.class)).isEqualTo(1);

        mvc.perform(api(get("/api/v1/me")).header("Authorization", "Bearer " + tokens.path("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("manager"));

        // The challenge cannot be used again, even with a valid assertion.
        verifyPasskey(challengeId, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        assertThat(appSessions.count()).isEqualTo(1);
    }

    @Test
    void disabledOrUnknownAccountsAreRejected() throws Exception {
        TNMUser manager = users.findById("MGR1");
        manager.setEnabled(false);
        users.update(manager);
        when(relyingParty.authenticate(any())).thenReturn(userEntity("MGR1"));
        verifyPasskey(newChallenge(), null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        when(relyingParty.authenticate(any())).thenReturn(userEntity("NOBODY"));
        verifyPasskey(newChallenge(), null).andExpect(status().isUnauthorized());

        when(relyingParty.authenticate(any())).thenReturn(null);
        verifyPasskey(newChallenge(), null).andExpect(status().isUnauthorized());

        // A library failure is a generic 401: its message is never shown.
        when(relyingParty.authenticate(any())).thenThrow(new IllegalStateException("No authenticator found for AQID"));
        String body = verifyPasskey(newChallenge(), null).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("authenticator").contains(PasskeyAuthApiController.FAILED_MESSAGE);

        assertThat(appSessions.count()).isZero();
        assertThat(users.findById("MGR1").getLastLoginMethod()).isNull();
    }

    @Test
    void malformedCredentialIsA400AndLeavesTheChallengeUnused() throws Exception {
        String challengeId = newChallenge();
        mvc.perform(api(post("/api/v1/auth/passkey/verify")).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("challengeId", challengeId, "credential", "x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(challenges.findById(challengeId).orElseThrow().getUsedAt()).isNull();
        verify(relyingParty, never()).authenticate(any());
    }

    @Test
    void passkeysTurnedOffAre404() throws Exception {
        ReflectionTestUtils.setField(passkeys, "passkeysEnabled", false);
        try {
            mvc.perform(api(post("/api/v1/auth/passkey/options")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
            verifyPasskey("anything", null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        } finally {
            ReflectionTestUtils.setField(passkeys, "passkeysEnabled", true);
        }
        assertThat(challenges.count()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions verifyPasskey(String challengeId, String deviceName)
            throws Exception {
        Map<String, Object> body = deviceName == null
                ? Map.of("challengeId", challengeId, "credential", MobileAuthApiIntegrationTest.fakeCredential())
                : Map.of("challengeId", challengeId, "credential", MobileAuthApiIntegrationTest.fakeCredential(),
                        "deviceName", deviceName);
        return mvc.perform(api(post("/api/v1/auth/passkey/verify")).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String newChallenge() throws Exception {
        return data(mvc.perform(api(post("/api/v1/auth/passkey/options"))).andExpect(status().isOk()).andReturn())
                .path("challengeId").asText();
    }

    private static org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity userEntity(String accountId) {
        return ImmutablePublicKeyCredentialUserEntity.builder()
                .id(new Bytes(accountId.getBytes(StandardCharsets.UTF_8)))
                .name(accountId)
                .displayName(accountId)
                .build();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        int n = ipCounter++;
        return builder.with(request -> {
            request.setRemoteAddr("10.78." + (n / 250) + "." + (n % 250 + 1));
            return request;
        });
    }
}
