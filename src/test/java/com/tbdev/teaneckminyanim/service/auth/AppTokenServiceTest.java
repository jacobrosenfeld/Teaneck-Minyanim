package com.tbdev.teaneckminyanim.service.auth;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.AppSession;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.AppSessionRepository;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Unit tests with a fixed clock; rotation against a real database is in ApiSecurityIntegrationTest. */
class AppTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    private final AppSessionRepository repository = mock(AppSessionRepository.class);
    private final TNMUserService users = mock(TNMUserService.class);
    private final AuthAuditService audit = mock(AuthAuditService.class);
    private TNMUser manager;

    @BeforeEach
    void setUp() {
        manager = ApiKeyServiceTest.user("MGR", "manager", "ORGA", Role.ADMIN);
        when(users.findById("MGR")).thenReturn(manager);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private AppTokenService serviceAt(Instant instant) {
        return new AppTokenService(repository, users, audit, Duration.ofMinutes(15), Duration.ofDays(30),
                Clock.fixed(instant, ZONE));
    }

    @Test
    void issueStoresHashesAndAudits() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "TeaneckMinyanim/1.0 iOS");
        AppTokenService.IssuedTokens tokens = serviceAt(NOW).issue(manager, AppSession.LoginMethod.PASSKEY,
                "  Jacob's iPhone  ", request);

        assertTrue(SecureTokens.looksLikeAccessToken(tokens.accessToken()));
        assertTrue(SecureTokens.looksLikeRefreshToken(tokens.refreshToken()));
        assertTrue(tokens.accessToken().matches("tnmapp_[A-Za-z0-9_-]{43}"));
        assertTrue(tokens.refreshToken().matches("tnmrt_[A-Za-z0-9_-]{43}"));
        assertEquals(NOW.plus(Duration.ofMinutes(15)), tokens.accessExpiresAt());
        assertEquals(NOW.plus(Duration.ofDays(30)), tokens.refreshExpiresAt());
        assertFalse(tokens.toString().contains(tokens.accessToken()));
        assertFalse(tokens.toString().contains(tokens.refreshToken()));

        ArgumentCaptor<AppSession> saved = ArgumentCaptor.forClass(AppSession.class);
        verify(repository).save(saved.capture());
        AppSession session = saved.getValue();
        assertEquals(AuthAuditService.hash(tokens.accessToken()), session.getAccessTokenHash());
        assertEquals(AuthAuditService.hash(tokens.refreshToken()), session.getRefreshTokenHash());
        assertEquals("Jacob's iPhone", session.getDeviceName());
        assertEquals("TeaneckMinyanim/1.0 iOS", session.getUserAgent());
        assertEquals(AppSession.LoginMethod.PASSKEY, session.getLoginMethod());
        assertNotNull(session.getFamilyId());
        assertFalse(session.toString().contains(session.getAccessTokenHash()));
        assertFalse(session.toString().contains(session.getRefreshTokenHash()));

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq("APP_TOKEN_ISSUED"), eq("MGR"), any(), details.capture());
        assertTrue(details.getValue().contains("method=PASSKEY"));
        assertFalse(details.getValue().contains(tokens.accessToken()));
        assertFalse(details.getValue().contains(tokens.refreshToken()));
    }

    @Test
    void issueRejectsUnusableAccountsAndTruncatesLongDeviceNames() {
        manager.setEnabled(false);
        assertThrows(IllegalArgumentException.class,
                () -> serviceAt(NOW).issue(manager, AppSession.LoginMethod.MAGIC_LINK, "phone", null));
        manager.setEnabled(true);
        manager.setRoleId(null);
        assertThrows(IllegalArgumentException.class,
                () -> serviceAt(NOW).issue(manager, AppSession.LoginMethod.MAGIC_LINK, "phone", null));
        assertThrows(IllegalArgumentException.class,
                () -> serviceAt(NOW).issue(null, AppSession.LoginMethod.MAGIC_LINK, "phone", null));

        manager.setRoleId(Role.ADMIN.getId());
        serviceAt(NOW).issue(manager, AppSession.LoginMethod.MAGIC_LINK, "d".repeat(300), null);
        ArgumentCaptor<AppSession> saved = ArgumentCaptor.forClass(AppSession.class);
        verify(repository).save(saved.capture());
        assertEquals(100, saved.getValue().getDeviceName().length());
    }

    @Test
    void accessTokenExpiresAfterItsTtl() {
        AppTokenService.IssuedTokens tokens = serviceAt(NOW).issue(manager, AppSession.LoginMethod.MAGIC_LINK, null, null);
        AppSession session = captureSaved();
        when(repository.findByAccessTokenHash(session.getAccessTokenHash())).thenReturn(Optional.of(session));

        assertTrue(serviceAt(NOW.plus(Duration.ofMinutes(14))).resolveAccess(tokens.accessToken()).isPresent());
        assertTrue(serviceAt(NOW.plus(Duration.ofMinutes(15))).resolveAccess(tokens.accessToken()).isEmpty());
    }

    @Test
    void refreshTokenIsNeverAnAccessToken() {
        AppTokenService.IssuedTokens tokens = serviceAt(NOW).issue(manager, AppSession.LoginMethod.MAGIC_LINK, null, null);
        assertTrue(serviceAt(NOW).resolveAccess(tokens.refreshToken()).isEmpty());
        verify(repository, never()).findByAccessTokenHash(any());
    }

    @Test
    void revokedOrRotatedAccessTokensAreRejected() {
        AppTokenService.IssuedTokens tokens = serviceAt(NOW).issue(manager, AppSession.LoginMethod.MAGIC_LINK, null, null);
        AppSession session = captureSaved();
        when(repository.findByAccessTokenHash(session.getAccessTokenHash())).thenReturn(Optional.of(session));
        session.setRotatedAt(LocalDateTime.ofInstant(NOW, ZONE));
        assertTrue(serviceAt(NOW).resolveAccess(tokens.accessToken()).isEmpty());
        session.setRotatedAt(null);
        session.setRevokedAt(LocalDateTime.ofInstant(NOW, ZONE));
        assertTrue(serviceAt(NOW).resolveAccess(tokens.accessToken()).isEmpty());
    }

    @Test
    void expiredRefreshTokenCannotRefresh() {
        AppTokenService.IssuedTokens tokens = serviceAt(NOW).issue(manager, AppSession.LoginMethod.MAGIC_LINK, null, null);
        AppSession session = captureSaved();
        when(repository.findByRefreshTokenHash(session.getRefreshTokenHash())).thenReturn(Optional.of(session));

        assertTrue(serviceAt(NOW.plus(Duration.ofDays(30))).refresh(tokens.refreshToken(), null).isEmpty());
        verify(repository, never()).claimForRotation(anyString(), any());
        verify(audit).record(eq("TOKEN_REJECTED"), eq("MGR"), any(), contains("expired"));
    }

    @Test
    void refreshRotatesWithinTheSameFamily() {
        AppTokenService.IssuedTokens tokens = serviceAt(NOW).issue(manager, AppSession.LoginMethod.PASSKEY, "phone", null);
        AppSession session = captureSaved();
        when(repository.findByRefreshTokenHash(session.getRefreshTokenHash())).thenReturn(Optional.of(session));
        when(repository.claimForRotation(eq(session.getId()), any())).thenReturn(1);
        clearInvocations(repository);

        Optional<AppTokenService.IssuedTokens> refreshed = serviceAt(NOW.plus(Duration.ofMinutes(20)))
                .refresh(tokens.refreshToken(), null);

        assertTrue(refreshed.isPresent());
        assertNotEquals(tokens.accessToken(), refreshed.get().accessToken());
        assertNotEquals(tokens.refreshToken(), refreshed.get().refreshToken());
        AppSession next = captureSaved();
        assertEquals(session.getFamilyId(), next.getFamilyId());
        assertNotEquals(session.getId(), next.getId());
        assertEquals(AppSession.LoginMethod.PASSKEY, next.getLoginMethod());
        assertEquals("phone", next.getDeviceName());
        verify(audit).record(eq("APP_TOKEN_REFRESHED"), eq("MGR"), any(), contains("previous=" + session.getId()));
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheFamily() {
        AppTokenService.IssuedTokens tokens = serviceAt(NOW).issue(manager, AppSession.LoginMethod.MAGIC_LINK, null, null);
        AppSession session = captureSaved();
        session.setRotatedAt(LocalDateTime.ofInstant(NOW, ZONE));
        session.setRevokedAt(LocalDateTime.ofInstant(NOW, ZONE));
        when(repository.findByRefreshTokenHash(session.getRefreshTokenHash())).thenReturn(Optional.of(session));

        assertTrue(serviceAt(NOW).refresh(tokens.refreshToken(), null).isEmpty());
        verify(repository).revokeFamily(eq(session.getFamilyId()), any(), eq("REFRESH_REUSE"));
        verify(audit).record(eq("APP_TOKEN_REFRESH_REUSE"), eq("MGR"), any(), any());
    }

    @Test
    void losingTheRotationRaceIssuesNothing() {
        AppTokenService.IssuedTokens tokens = serviceAt(NOW).issue(manager, AppSession.LoginMethod.MAGIC_LINK, null, null);
        AppSession session = captureSaved();
        when(repository.findByRefreshTokenHash(session.getRefreshTokenHash())).thenReturn(Optional.of(session));
        when(repository.claimForRotation(eq(session.getId()), any())).thenReturn(0);
        AppSession rotated = AppSession.builder().id(session.getId()).accountId("MGR").familyId(session.getFamilyId())
                .rotatedAt(LocalDateTime.ofInstant(NOW, ZONE)).build();
        when(repository.findById(session.getId())).thenReturn(Optional.of(rotated));
        clearInvocations(repository);

        assertTrue(serviceAt(NOW).refresh(tokens.refreshToken(), null).isEmpty());
        verify(repository, never()).save(any());
        verify(repository).revokeFamily(eq(session.getFamilyId()), any(), eq("REFRESH_REUSE"));
    }

    @Test
    void refreshForADisabledAccountRevokesTheFamily() {
        AppTokenService.IssuedTokens tokens = serviceAt(NOW).issue(manager, AppSession.LoginMethod.MAGIC_LINK, null, null);
        AppSession session = captureSaved();
        when(repository.findByRefreshTokenHash(session.getRefreshTokenHash())).thenReturn(Optional.of(session));
        manager.setEnabled(false);

        assertTrue(serviceAt(NOW).refresh(tokens.refreshToken(), null).isEmpty());
        verify(repository).revokeFamily(eq(session.getFamilyId()), any(), eq("USER_DISABLED"));
        verify(repository, never()).claimForRotation(anyString(), any());
    }

    @Test
    void logoutRevokesTheFamily() {
        AppTokenService.IssuedTokens tokens = serviceAt(NOW).issue(manager, AppSession.LoginMethod.MAGIC_LINK, null, null);
        AppSession session = captureSaved();
        when(repository.findByRefreshTokenHash(session.getRefreshTokenHash())).thenReturn(Optional.of(session));

        assertTrue(serviceAt(NOW).logout(tokens.refreshToken()));
        verify(repository).revokeFamily(eq(session.getFamilyId()), any(), eq("LOGOUT"));
        verify(audit).record(eq("APP_TOKEN_REVOKED"), eq("MGR"), any(), contains("reason=LOGOUT"));
        assertFalse(serviceAt(NOW).logout("garbage"));
        assertFalse(serviceAt(NOW).logout(tokens.accessToken()));
    }

    @Test
    void sessionAdministrationIsSuperAdminOnly() {
        when(users.isSuperAdmin()).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> serviceAt(NOW).listActive());
        assertThrows(AccessDeniedException.class, () -> serviceAt(NOW).revokeSession("S1"));
        verify(repository, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void purgeDeletesSessionsEndedMoreThanSevenDaysAgo() {
        when(repository.purgeEndedBefore(any())).thenReturn(3);
        assertEquals(3, serviceAt(NOW).purgeEnded());
        verify(repository).purgeEndedBefore(LocalDateTime.ofInstant(NOW, ZONE).minusDays(7));
    }

    private AppSession captureSaved() {
        ArgumentCaptor<AppSession> saved = ArgumentCaptor.forClass(AppSession.class);
        verify(repository, atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }
}
