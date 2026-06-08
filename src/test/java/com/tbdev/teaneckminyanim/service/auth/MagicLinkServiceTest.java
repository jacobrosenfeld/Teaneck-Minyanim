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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MagicLinkServiceTest {

    @Test
    void requestLoginLinkStoresHashedTokenAndSendsEmailForEnabledAccount() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        TNMUserService userService = mock(TNMUserService.class);
        ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
        EmailService emailService = mock(EmailService.class);
        MagicLinkService service = new MagicLinkService(tokenRepository, userService, settingsService, emailService);
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
        assertTrue(messageCaptor.getValue().getTextBody().contains("/admin/login/magic-link/verify?token="));
    }

    @Test
    void requestLoginLinkUsesCurrentDevHostWhenSiteRootIsProd() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        TNMUserService userService = mock(TNMUserService.class);
        ApplicationSettingsService settingsService = mock(ApplicationSettingsService.class);
        EmailService emailService = mock(EmailService.class);
        MagicLinkService service = new MagicLinkService(tokenRepository, userService, settingsService, emailService);
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
        MagicLinkService service = new MagicLinkService(tokenRepository, userService, settingsService, emailService);
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
        MagicLinkService service = new MagicLinkService(
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
        MagicLinkService service = new MagicLinkService(tokenRepository, userService, settingsService, emailService);
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
    void consumeLoginTokenMarksTokenUsedAndUpdatesUserLoginMetadata() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        TNMUserService userService = mock(TNMUserService.class);
        MagicLinkService service = new MagicLinkService(
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

        TNMUser authenticatedUser = service.consumeLoginToken(rawToken);

        assertEquals(user, authenticatedUser);
        assertNotNull(token.getUsedAt());
        assertEquals("MAGIC_LINK", user.getLastLoginMethod());
        verify(tokenRepository).save(token);
        verify(userService).update(user);
    }

    @Test
    void consumeLoginTokenRejectsExpiredToken() {
        MagicLinkTokenRepository tokenRepository = mock(MagicLinkTokenRepository.class);
        MagicLinkService service = new MagicLinkService(
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
