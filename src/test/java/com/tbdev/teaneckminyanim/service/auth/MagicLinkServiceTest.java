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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MagicLinkServiceTest {

    @Test
    void requestLoginLinkStoresHashedTokenAndSendsEmailForEnabledAccount() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        TNMUserService userService = mock(TNMUserService.class);
        ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
        EmailService emailService = mock(EmailService.class);
        MagicLinkService service = service(tokenRepository, userService, settingsService, emailService);
        TNMUser user = user(true);
        when(userService.findByIdentifier("manager@example.com")).thenReturn(user);
        when(settingsService.getSiteName()).thenReturn("Teaneck Minyanim");
        when(settingsService.getSiteRootUrl()).thenReturn("https://example.com");
        when(emailService.send(any())).thenReturn(EmailSendResult.success(null, "sent"));

        MagicLinkService.MagicLinkRequestResult result = service.requestLoginLink(
                " Manager@Example.COM ",
                new MockHttpServletRequest());

        assertEquals("If this email is authorized, a sign-in link will be sent shortly.", result.message());
        ArgumentCaptor<MagicLinkToken> tokenCaptor = ArgumentCaptor.forClass(MagicLinkToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        MagicLinkToken token = tokenCaptor.getValue();
        assertEquals(user.getId(), token.getAccountId());
        assertEquals(MagicLinkService.PURPOSE_LOGIN, token.getPurpose());
        assertNotNull(token.getTokenHash());
        assertEquals(64, token.getTokenHash().length());

        ArgumentCaptor<EmailMessage> messageCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(messageCaptor.capture());
        EmailMessage message = messageCaptor.getValue();
        assertTrue(message.getTextBody().contains("/admin/login/magic-link/verify?token="));
        assertTrue(message.getHtmlBody().contains("Secure admin sign-in"));
        assertTrue(message.getHtmlBody().contains("background:#275ed8"));
        assertTrue(message.getHtmlBody().contains("This link expires in 15 minutes"));
    }

    @Test
    void requestLoginLinkUsesCurrentDevHostWhenSiteRootIsProd() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        TNMUserService userService = mock(TNMUserService.class);
        ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
        EmailService emailService = mock(EmailService.class);
        MagicLinkService service = service(tokenRepository, userService, settingsService, emailService);
        TNMUser user = user(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/login/magic-link/request");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "dev.teaneckminyanim.com");
        when(userService.findByIdentifier("manager@example.com")).thenReturn(user);
        when(settingsService.getSiteName()).thenReturn("Teaneck Minyanim");
        when(settingsService.getSiteRootUrl()).thenReturn("https://teaneckminyanim.com");
        when(emailService.send(any())).thenReturn(EmailSendResult.success(null, "sent"));

        service.requestLoginLink("manager@example.com", request);

        ArgumentCaptor<EmailMessage> messageCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(messageCaptor.capture());
        assertTrue(messageCaptor.getValue().getTextBody()
                .contains("https://dev.teaneckminyanim.com/admin/login/magic-link/verify?token="));
    }

    @Test
    void requestLoginLinkRejectsUntrustedHostHeaderForEmailUrl() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        TNMUserService userService = mock(TNMUserService.class);
        ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
        EmailService emailService = mock(EmailService.class);
        MagicLinkService service = service(tokenRepository, userService, settingsService, emailService);
        TNMUser user = user(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/login/magic-link/request");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "example.net");
        when(userService.findByIdentifier("manager@example.com")).thenReturn(user);
        when(settingsService.getSiteName()).thenReturn("Teaneck Minyanim");
        when(settingsService.getSiteRootUrl()).thenReturn("https://teaneckminyanim.com");
        when(emailService.send(any())).thenReturn(EmailSendResult.success(null, "sent"));

        service.requestLoginLink("manager@example.com", request);

        ArgumentCaptor<EmailMessage> messageCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(messageCaptor.capture());
        assertTrue(messageCaptor.getValue().getTextBody()
                .contains("https://teaneckminyanim.com/admin/login/magic-link/verify?token="));
    }

    @Test
    void requestLoginLinkDoesNotSendEmailForUnknownAccount() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        TNMUserService userService = mock(TNMUserService.class);
        MagicLinkService service = service(
                tokenRepository,
                userService,
                mock(ApplicationSettingsService.class),
                mock(EmailService.class));

        MagicLinkService.MagicLinkRequestResult result = service.requestLoginLink(
                "unknown@example.com",
                new MockHttpServletRequest());

        assertEquals("If this email is authorized, a sign-in link will be sent shortly.", result.message());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void requestLoginLinkAcceptsUsernameIdentifier() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        TNMUserService userService = mock(TNMUserService.class);
        ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
        EmailService emailService = mock(EmailService.class);
        MagicLinkService service = service(tokenRepository, userService, settingsService, emailService);
        TNMUser user = user(true);
        when(userService.findByIdentifier("manager")).thenReturn(user);
        when(settingsService.getSiteName()).thenReturn("Teaneck Minyanim");
        when(settingsService.getSiteRootUrl()).thenReturn("https://example.com");
        when(emailService.send(any())).thenReturn(EmailSendResult.success(null, "sent"));

        service.requestLoginLink("manager", new MockHttpServletRequest());

        verify(tokenRepository).save(any());
        verify(emailService).send(any());
    }

    @Test
    void managedWelcomeAndSignInSendWithoutConsumingPublicQuota() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        TNMUserService userService = mock(TNMUserService.class);
        ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
        EmailService emailService = mock(EmailService.class);
        UserWelcomeEmailService welcomeEmails = mock(UserWelcomeEmailService.class);
        MagicLinkRateLimitService rateLimits = mock(MagicLinkRateLimitService.class);
        when(settingsService.getSiteRootUrl()).thenReturn("https://example.com");
        when(settingsService.getSiteName()).thenReturn("Teaneck Minyanim");
        when(emailService.send(any())).thenReturn(EmailSendResult.success(null, "sent"));
        when(welcomeEmails.sendWelcome(any(), any()))
                .thenReturn(EmailSendResult.success(null, "sent"));
        MagicLinkService service = new MagicLinkService(tokenRepository, userService, settingsService,
                emailService, rateLimits, mock(AuthAuditService.class), welcomeEmails);
        TNMUser user = user(true);

        assertTrue(service.sendManagedWelcomeLink(user, new MockHttpServletRequest()));
        assertTrue(service.sendManagedLoginLink(user, new MockHttpServletRequest()));

        verify(tokenRepository, times(2)).save(any());
        verify(welcomeEmails).sendWelcome(org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.contains("/admin/login/magic-link/verify?token="));
        verify(emailService).send(any());
        verify(userService, times(2)).update(user);
        verifyNoInteractions(rateLimits);
    }

    @Test
    void publicAndProfileRequestsStillStopWhenRateLimited() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        TNMUserService userService = mock(TNMUserService.class);
        EmailService emailService = mock(EmailService.class);
        UserWelcomeEmailService welcomeEmails = mock(UserWelcomeEmailService.class);
        MagicLinkRateLimitService rateLimits = mock(MagicLinkRateLimitService.class);
        MagicLinkService service = new MagicLinkService(tokenRepository, userService,
                mock(ApplicationSettingsService.class), emailService, rateLimits, mock(AuthAuditService.class), welcomeEmails);
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals(MagicLinkService.MagicLinkRequestResult.neutral(),
                service.requestLoginLink("manager@example.com", request));
        assertFalse(service.sendProfileWelcomeLink(user(true), request));

        verify(rateLimits, times(2)).allow(any(), any());
        verifyNoInteractions(tokenRepository, userService, emailService, welcomeEmails);
    }

    @Test
    void consumeLoginTokenMarksTokenUsedAndUpdatesUserLoginMetadata() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        TNMUserService userService = mock(TNMUserService.class);
        MagicLinkService service = service(
                tokenRepository,
                userService,
                mock(ApplicationSettingsService.class),
                mock(EmailService.class));
        String rawToken = "token";
        MagicLinkToken token = MagicLinkToken.builder()
                .id("M1")
                .accountId("A1")
                .tokenHash(MagicLinkService.hashToken(rawToken))
                .purpose(MagicLinkService.PURPOSE_LOGIN)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        TNMUser user = user(true);
        when(tokenRepository.findByTokenHash(MagicLinkService.hashToken(rawToken))).thenReturn(Optional.of(token));
        when(userService.findById("A1")).thenReturn(user);
        when(tokenRepository.consumeIfUsable(any(), any())).thenReturn(1);

        TNMUser authenticatedUser = service.consumeLoginToken(rawToken);

        assertEquals(user, authenticatedUser);
        assertEquals("MAGIC_LINK", user.getLastLoginMethod());
        verify(tokenRepository).consumeIfUsable(any(), any());
        verify(userService).update(user);
    }

    @Test
    void consumeLoginTokenRejectsExpiredToken() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        MagicLinkService service = service(
                tokenRepository,
                mock(TNMUserService.class),
                mock(ApplicationSettingsService.class),
                mock(EmailService.class));
        String rawToken = "token";
        MagicLinkToken token = MagicLinkToken.builder()
                .id("M1")
                .accountId("A1")
                .tokenHash(MagicLinkService.hashToken(rawToken))
                .purpose(MagicLinkService.PURPOSE_LOGIN)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(tokenRepository.findByTokenHash(MagicLinkService.hashToken(rawToken))).thenReturn(Optional.of(token));

        assertThrows(MagicLinkAuthenticationException.class, () -> service.consumeLoginToken(rawToken));
    }

    @Test
    void consumeLoginTokenRejectsReplayWhenAtomicUpdateLosesRace() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        TNMUserService userService = mock(TNMUserService.class);
        MagicLinkService service = service(tokenRepository, userService,
                mock(ApplicationSettingsService.class), mock(EmailService.class));
        String rawToken = "token";
        MagicLinkToken token = MagicLinkToken.builder()
                .id("M1")
                .accountId("A1")
                .tokenHash(MagicLinkService.hashToken(rawToken))
                .purpose(MagicLinkService.PURPOSE_LOGIN)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        when(tokenRepository.findByTokenHash(MagicLinkService.hashToken(rawToken))).thenReturn(Optional.of(token));
        when(userService.findById("A1")).thenReturn(user(true));
        when(tokenRepository.consumeIfUsable(any(), any())).thenReturn(0);

        assertThrows(MagicLinkAuthenticationException.class, () -> service.consumeLoginToken(rawToken));
        verify(userService, never()).update(any());
    }

    private MagicLinkService service(MagicLinkTokenRepository tokenRepository,
                                     TNMUserService userService,
                                     ApplicationSettingsService settingsService,
                                     EmailService emailService) {
        MagicLinkRateLimitService rateLimitService = mock(MagicLinkRateLimitService.class);
        when(rateLimitService.allow(any(), any())).thenReturn(true);
        return new MagicLinkService(tokenRepository, userService, settingsService, emailService,
                rateLimitService, mock(AuthAuditService.class), mock(UserWelcomeEmailService.class));
    }

    private TNMUser user(boolean enabled) {
        return TNMUser.builder()
                .id("A1")
                .username("manager")
                .email("manager@example.com")
                .emailNormalized("manager@example.com")
                .encryptedPassword("encrypted")
                .organizationId("org-a")
                .roleId(Role.ADMIN.getId())
                .enabled(enabled)
                .build();
    }
}
