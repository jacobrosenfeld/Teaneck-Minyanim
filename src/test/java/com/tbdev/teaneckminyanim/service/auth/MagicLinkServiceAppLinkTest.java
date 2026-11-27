package com.tbdev.teaneckminyanim.service.auth;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.MagicLinkToken;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.MagicLinkTokenRepository;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.email.EmailMessage;
import com.tbdev.teaneckminyanim.service.email.EmailSendResult;
import com.tbdev.teaneckminyanim.service.email.EmailService;
import com.tbdev.teaneckminyanim.service.email.UserWelcomeEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The mobile-app variant of magic links: its own purpose, URL and wording, and no crossover with website links. */
class MagicLinkServiceAppLinkTest {
    private final MagicLinkTokenRepository tokens = mock(MagicLinkTokenRepository.class);
    private final TNMUserService users = mock(TNMUserService.class);
    private final ApplicationSettingsService settings = mock(ApplicationSettingsService.class);
    private final EmailService email = mock(EmailService.class);
    private final MagicLinkRateLimitService rateLimits = mock(MagicLinkRateLimitService.class);
    private final AuthAuditService audit = mock(AuthAuditService.class);
    private MagicLinkService service;

    @BeforeEach
    void setUp() {
        when(rateLimits.allow(any(), any())).thenReturn(true);
        when(settings.getSiteName()).thenReturn("Teaneck Minyanim");
        when(settings.getSiteRootUrl()).thenReturn("https://teaneckminyanim.com");
        when(email.send(any())).thenReturn(EmailSendResult.success(null, "sent"));
        service = new MagicLinkService(tokens, users, settings, email, rateLimits, audit,
                mock(UserWelcomeEmailService.class));
    }

    @Test
    void appLinkUsesTheAppPurposePathAndWording() {
        when(users.findByIdentifier("manager@example.com")).thenReturn(user(true));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/magic-link/request");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "dev.teaneckminyanim.com");

        MagicLinkService.MagicLinkRequestResult result = service.requestAppLoginLink("manager@example.com", request);

        assertThat(result.message()).isEqualTo(MagicLinkService.MagicLinkRequestResult.neutral().message());
        ArgumentCaptor<MagicLinkToken> token = ArgumentCaptor.forClass(MagicLinkToken.class);
        verify(tokens).save(token.capture());
        assertThat(token.getValue().getPurpose()).isEqualTo(MagicLinkService.PURPOSE_APP_LOGIN);
        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(email).send(message.capture());
        EmailMessage sent = message.getValue();
        // Same safe root as website links: the dev host is trusted.
        assertThat(sent.getTextBody()).contains("https://dev.teaneckminyanim.com/app/auth/magic-link?token=")
                .contains("Open this link on the phone where the Teaneck Minyanim app is installed")
                .doesNotContain("/admin/login");
        assertThat(sent.getSubject()).isEqualTo("Sign in to the Teaneck Minyanim app");
        assertThat(sent.getHtmlBody()).contains("on the phone where the Teaneck Minyanim app is installed")
                .contains(">Sign in to the Teaneck Minyanim app</a>")
                .contains("/app/auth/magic-link?token=");
        assertThat(sent.getMetadata()).containsEntry("source", "app-magic-link");
        String raw = sent.getTextBody().replaceAll("(?s).*token=([A-Za-z0-9_-]+).*", "$1");
        assertThat(token.getValue().getTokenHash()).isEqualTo(MagicLinkService.hashToken(raw));
        verify(audit).record(eq("MAGIC_LINK_REQUESTED"), eq("A1"), any(), eq("app"));
    }

    @Test
    void appLinkRejectsUntrustedHostsLikeTheWebsiteLink() {
        when(users.findByIdentifier("manager@example.com")).thenReturn(user(true));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/magic-link/request");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "evil.example.net");

        service.requestAppLoginLink("manager@example.com", request);

        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(email).send(message.capture());
        assertThat(message.getValue().getTextBody()).contains("https://teaneckminyanim.com/app/auth/magic-link?token=")
                .doesNotContain("evil");
    }

    @Test
    void appRequestsShareTheNeutralUnknownAndRateLimitedHandling() {
        when(users.findByIdentifier(any())).thenReturn(null);
        service.requestAppLoginLink("nobody@example.com", new MockHttpServletRequest());
        when(users.findByIdentifier(any())).thenReturn(user(false));
        service.requestAppLoginLink("manager@example.com", new MockHttpServletRequest());
        when(rateLimits.allow(any(), any())).thenReturn(false);
        when(users.findByIdentifier(any())).thenReturn(user(true));
        assertThat(service.requestAppLoginLink("manager@example.com", new MockHttpServletRequest()).message())
                .isEqualTo(MagicLinkService.MagicLinkRequestResult.neutral().message());
        verify(email, never()).send(any());
        verify(tokens, never()).save(any());
    }

    @Test
    void appAndWebsiteTokensAreNotInterchangeable() {
        TNMUser user = user(true);
        when(users.findById("A1")).thenReturn(user);
        MagicLinkToken appToken = token("T-APP", MagicLinkService.PURPOSE_APP_LOGIN);
        MagicLinkToken webToken = token("T-WEB", MagicLinkService.PURPOSE_LOGIN);
        when(tokens.findByTokenHash(MagicLinkService.hashToken("app-raw"))).thenReturn(Optional.of(appToken));
        when(tokens.findByTokenHash(MagicLinkService.hashToken("web-raw"))).thenReturn(Optional.of(webToken));
        when(tokens.consumeIfUsable(any(), any())).thenReturn(1);

        assertThatThrownBy(() -> service.consumeLoginToken("app-raw")).isInstanceOf(MagicLinkAuthenticationException.class);
        assertThatThrownBy(() -> service.consumeAppLoginToken("web-raw")).isInstanceOf(MagicLinkAuthenticationException.class);
        verify(tokens, never()).consumeIfUsable(any(), any());

        assertThat(service.consumeAppLoginToken("app-raw")).isSameAs(user);
        assertThat(user.getLastLoginMethod()).isEqualTo("MAGIC_LINK");
        verify(tokens).consumeIfUsable(eq("T-APP"), any());
        verify(audit).record(eq("MAGIC_LINK_USED"), eq("A1"), any(), eq("app T-APP"));
    }

    @Test
    void appTokenReplayIsRejected() {
        when(users.findById("A1")).thenReturn(user(true));
        when(tokens.findByTokenHash(MagicLinkService.hashToken("app-raw")))
                .thenReturn(Optional.of(token("T-APP", MagicLinkService.PURPOSE_APP_LOGIN)));
        when(tokens.consumeIfUsable(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.consumeAppLoginToken("app-raw")).isInstanceOf(MagicLinkAuthenticationException.class);
        verify(audit).record(eq("MAGIC_LINK_REPLAY_REJECTED"), eq("A1"), any(), eq("T-APP"));
    }

    private static MagicLinkToken token(String id, String purpose) {
        return MagicLinkToken.builder().id(id).accountId("A1").tokenHash("h-" + id).purpose(purpose)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build();
    }

    private static TNMUser user(boolean enabled) {
        return TNMUser.builder()
                .id("A1")
                .username("manager")
                .email("manager@example.com")
                .encryptedPassword("unused")
                .organizationId("ORG1")
                .roleId(Role.ADMIN.getId())
                .enabled(enabled)
                .build();
    }
}
