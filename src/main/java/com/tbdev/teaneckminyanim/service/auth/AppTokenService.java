package com.tbdev.teaneckminyanim.service.auth;

import com.tbdev.teaneckminyanim.model.AppSession;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.AppSessionRepository;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mobile app sign-in tokens: a short-lived access token ({@code tnmapp_...}) and a rotating refresh token
 * ({@code tnmrt_...}). Only SHA-256 hashes are stored. Each refresh replaces the session row with a new one
 * in the same family; presenting an already-rotated refresh token revokes the whole family.
 */
@Service
@Slf4j
public class AppTokenService {
    public static final int MAX_DEVICE_NAME_LENGTH = 100;
    public static final int MAX_USER_AGENT_LENGTH = 500;
    /** Ended sessions are kept this long (for reuse detection and the audit trail), then purged. */
    static final Duration PURGE_AFTER = Duration.ofDays(7);
    static final Duration LAST_USED_WRITE_INTERVAL = Duration.ofMinutes(1);

    /** The raw tokens of a new sign-in or refresh. Returned to the app once; never stored or logged. */
    public record IssuedTokens(String accessToken, String refreshToken, Instant accessExpiresAt,
                               Instant refreshExpiresAt, String sessionId) {
        @Override
        public String toString() {
            return "IssuedTokens[sessionId=" + sessionId + ", accessExpiresAt=" + accessExpiresAt
                    + ", refreshExpiresAt=" + refreshExpiresAt + ", tokens=<redacted>]";
        }
    }

    private final AppSessionRepository repository;
    private final TNMUserService userService;
    private final AuthAuditService auditService;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Clock clock;

    @Autowired
    public AppTokenService(AppSessionRepository repository, TNMUserService userService, AuthAuditService auditService,
                           @Value("${auth.app-token.access-ttl:15m}") Duration accessTtl,
                           @Value("${auth.app-token.refresh-ttl:30d}") Duration refreshTtl) {
        this(repository, userService, auditService, accessTtl, refreshTtl, Clock.systemDefaultZone());
    }

    AppTokenService(AppSessionRepository repository, TNMUserService userService, AuthAuditService auditService,
                    Duration accessTtl, Duration refreshTtl, Clock clock) {
        this.repository = repository;
        this.userService = userService;
        this.auditService = auditService;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.clock = clock;
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    /**
     * Starts a new app session for a user who has just proved who they are (magic link or passkey).
     *
     * @throws IllegalArgumentException when the user is missing, disabled or has no role
     */
    @Transactional
    public IssuedTokens issue(TNMUser user, AppSession.LoginMethod loginMethod, String deviceName,
                              HttpServletRequest request) {
        if (!isUsable(user)) {
            throw new IllegalArgumentException("This account cannot sign in.");
        }
        if (loginMethod == null) {
            throw new IllegalArgumentException("A login method is required.");
        }
        String familyId = "F" + UUID.randomUUID().toString().replace("-", "");
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        IssuedTokens tokens = newSession(user.getId(), familyId, loginMethod, deviceName, userAgent);
        auditService.record("APP_TOKEN_ISSUED", user.getId(), AuthRequests.clientIpHash(request),
                "session=" + tokens.sessionId() + " method=" + loginMethod.name());
        return tokens;
    }

    /**
     * Looks up a usable access token (well-formed, known, not expired, not revoked or rotated) and records
     * its use. The account is NOT checked here; the bearer filter checks the live account.
     */
    public Optional<AppSession> resolveAccess(String rawAccessToken) {
        if (!SecureTokens.looksLikeAccessToken(rawAccessToken)) {
            return Optional.empty();
        }
        LocalDateTime now = now();
        Optional<AppSession> found = repository.findByAccessTokenHash(AuthAuditService.hash(rawAccessToken))
                .filter(s -> !s.isRevoked() && !s.isRotated())
                .filter(s -> s.getAccessExpiresAt() != null && s.getAccessExpiresAt().isAfter(now));
        found.ifPresent(session -> markUsed(session, now));
        return found;
    }

    /**
     * Exchanges a refresh token for a new access + refresh pair in the same family. Race-safe: the old row is
     * claimed with a conditional update, and only the caller that changed exactly one row issues new tokens.
     * Reusing an already-rotated refresh token revokes the whole family.
     *
     * @return empty when the refresh token is malformed, unknown, expired, revoked, reused, or its account
     *         can no longer sign in
     */
    @Transactional
    public Optional<IssuedTokens> refresh(String rawRefreshToken, HttpServletRequest request) {
        String ipHash = AuthRequests.clientIpHash(request);
        if (!SecureTokens.looksLikeRefreshToken(rawRefreshToken)) {
            auditService.record("TOKEN_REJECTED", null, ipHash, "refresh: malformed");
            return Optional.empty();
        }
        Optional<AppSession> found = repository.findByRefreshTokenHash(AuthAuditService.hash(rawRefreshToken));
        if (found.isEmpty()) {
            auditService.record("TOKEN_REJECTED", null, ipHash, "refresh: unknown");
            return Optional.empty();
        }
        AppSession session = found.get();
        if (session.isRotated()) {
            handleReuse(session, ipHash);
            return Optional.empty();
        }
        LocalDateTime now = now();
        if (session.isRevoked() || !session.getRefreshExpiresAt().isAfter(now)) {
            auditService.record("TOKEN_REJECTED", session.getAccountId(), ipHash,
                    "refresh: " + (session.isRevoked() ? "revoked" : "expired") + " session=" + session.getId());
            return Optional.empty();
        }
        TNMUser user = userService.findById(session.getAccountId());
        if (!isUsable(user)) {
            repository.revokeFamily(session.getFamilyId(), now, "USER_DISABLED");
            auditService.record("TOKEN_REJECTED", session.getAccountId(), ipHash,
                    "refresh: account unusable session=" + session.getId());
            return Optional.empty();
        }
        if (repository.claimForRotation(session.getId(), now) != 1) {
            // Another request rotated or revoked it first. A rotation means this token is being replayed.
            Optional<AppSession> current = repository.findById(session.getId());
            if (current.isPresent() && current.get().isRotated()) {
                handleReuse(current.get(), ipHash);
            }
            return Optional.empty();
        }
        String userAgent = request == null ? null : request.getHeader("User-Agent");
        IssuedTokens tokens = newSession(session.getAccountId(), session.getFamilyId(), session.getLoginMethod(),
                session.getDeviceName(), userAgent != null ? userAgent : session.getUserAgent());
        auditService.record("APP_TOKEN_REFRESHED", session.getAccountId(), ipHash,
                "session=" + tokens.sessionId() + " previous=" + session.getId() + " family=" + session.getFamilyId());
        return Optional.of(tokens);
    }

    /**
     * Signs the app out: revokes every session in the refresh token's family.
     *
     * @return false when the refresh token is malformed or unknown
     */
    @Transactional
    public boolean logout(String rawRefreshToken) {
        if (!SecureTokens.looksLikeRefreshToken(rawRefreshToken)) {
            return false;
        }
        Optional<AppSession> found = repository.findByRefreshTokenHash(AuthAuditService.hash(rawRefreshToken));
        if (found.isEmpty()) {
            return false;
        }
        AppSession session = found.get();
        int revoked = repository.revokeFamily(session.getFamilyId(), now(), "LOGOUT");
        auditService.record("APP_TOKEN_REVOKED", session.getAccountId(), AuthRequests.currentClientIpHash(),
                "session=" + session.getId() + " family=" + session.getFamilyId() + " reason=LOGOUT count=" + revoked);
        return true;
    }

    /**
     * Revokes the sign-in a session row belongs to (its whole family). Super admins only.
     *
     * @throws IllegalArgumentException when no session has this id
     */
    @Transactional
    public void revokeSession(String sessionId) {
        requireSuperAdmin();
        AppSession session = (sessionId == null ? Optional.<AppSession>empty() : repository.findById(sessionId))
                .orElseThrow(() -> new IllegalArgumentException("Session not found."));
        TNMUser actor = userService.getCurrentUser();
        int revoked = repository.revokeFamily(session.getFamilyId(), now(), "ADMIN_REVOKED");
        auditService.record("APP_TOKEN_REVOKED", session.getAccountId(), AuthRequests.currentClientIpHash(),
                "session=" + session.getId() + " family=" + session.getFamilyId() + " reason=ADMIN_REVOKED by="
                        + (actor == null ? null : actor.getId()) + " count=" + revoked);
    }

    /**
     * Revokes every active app session for an account. Not gated: called by account lifecycle code
     * (disabling a user). Returns how many session rows were revoked.
     */
    @Transactional
    public int revokeAllForAccount(String accountId, String reason) {
        if (accountId == null) return 0;
        int revoked = repository.revokeAllActiveForAccount(accountId, now(), reason == null ? "REVOKED" : reason);
        if (revoked > 0) {
            auditService.record("APP_TOKEN_REVOKED", accountId, AuthRequests.currentClientIpHash(),
                    "all sessions count=" + revoked + " reason=" + reason);
        }
        return revoked;
    }

    /** Deletes every app session row for an account (account deletion). */
    @Transactional
    public int deleteAllForAccount(String accountId) {
        if (accountId == null) return 0;
        return repository.deleteAllForAccount(accountId);
    }

    /** Sessions that can still be refreshed, most recently used first. Super admins only. */
    public List<AppSession> listActive() {
        requireSuperAdmin();
        return repository.findActive(now());
    }

    /** Deletes sessions revoked or expired more than 7 days ago. */
    @Scheduled(fixedDelayString = "${auth.app-token.purge-interval:PT6H}",
               initialDelayString = "${auth.app-token.purge-initial-delay:PT10M}")
    public int purgeEnded() {
        try {
            int purged = repository.purgeEndedBefore(now().minus(PURGE_AFTER));
            if (purged > 0) {
                log.info("Purged {} ended app sessions", purged);
            }
            return purged;
        } catch (RuntimeException e) {
            log.warn("Could not purge ended app sessions: {}", e.getMessage());
            return 0;
        }
    }

    private void handleReuse(AppSession session, String ipHash) {
        int revoked = repository.revokeFamily(session.getFamilyId(), now(), "REFRESH_REUSE");
        auditService.record("APP_TOKEN_REFRESH_REUSE", session.getAccountId(), ipHash,
                "session=" + session.getId() + " family=" + session.getFamilyId() + " revoked=" + revoked);
        log.warn("Refresh token reuse detected for app session family {}; family revoked", session.getFamilyId());
    }

    private IssuedTokens newSession(String accountId, String familyId, AppSession.LoginMethod loginMethod,
                                    String deviceName, String userAgent) {
        LocalDateTime now = now();
        String accessToken = SecureTokens.newAccessToken();
        String refreshToken = SecureTokens.newRefreshToken();
        AppSession session = AppSession.builder()
                .id("S" + UUID.randomUUID().toString().replace("-", ""))
                .accountId(accountId)
                .familyId(familyId)
                .accessTokenHash(AuthAuditService.hash(accessToken))
                .accessExpiresAt(now.plus(accessTtl))
                .refreshTokenHash(AuthAuditService.hash(refreshToken))
                .refreshExpiresAt(now.plus(refreshTtl))
                .deviceName(AuthRequests.truncate(deviceName, MAX_DEVICE_NAME_LENGTH))
                .userAgent(AuthRequests.truncate(userAgent, MAX_USER_AGENT_LENGTH))
                .loginMethod(loginMethod)
                .createdAt(now)
                .lastUsedAt(now)
                .build();
        repository.save(session);
        return new IssuedTokens(accessToken, refreshToken, toInstant(session.getAccessExpiresAt()),
                toInstant(session.getRefreshExpiresAt()), session.getId());
    }

    private void markUsed(AppSession session, LocalDateTime now) {
        if (session.getLastUsedAt() != null && session.getLastUsedAt().isAfter(now.minus(LAST_USED_WRITE_INTERVAL))) {
            return;
        }
        try {
            repository.markUsed(session.getId(), now);
            session.setLastUsedAt(now);
        } catch (RuntimeException e) {
            log.warn("Could not record use of app session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void requireSuperAdmin() {
        if (!userService.isSuperAdmin()) {
            throw new AccessDeniedException("Only super admins can manage app sessions.");
        }
    }

    private static boolean isUsable(TNMUser user) {
        return user != null && user.isEnabled() && user.getRoleId() != null && user.role() != null;
    }

    private Instant toInstant(LocalDateTime value) {
        return value.atZone(clock.getZone() == null ? ZoneId.systemDefault() : clock.getZone()).toInstant();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
