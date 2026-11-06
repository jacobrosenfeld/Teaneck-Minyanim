package com.tbdev.teaneckminyanim.service.auth;

import com.tbdev.teaneckminyanim.model.MagicLinkToken;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.MagicLinkTokenRepository;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.email.EmailMessage;
import com.tbdev.teaneckminyanim.service.email.EmailSendResult;
import com.tbdev.teaneckminyanim.service.email.EmailService;
import com.tbdev.teaneckminyanim.service.email.UserWelcomeEmailService;
import com.tbdev.teaneckminyanim.tools.IDGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class MagicLinkService {
    public static final String PURPOSE_LOGIN = "LOGIN";
    public static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MagicLinkTokenRepository tokenRepository;
    private final TNMUserService userService;
    private final ApplicationSettingsService settingsService;
    private final EmailService emailService;
    private final MagicLinkRateLimitService rateLimitService;
    private final AuthAuditService auditService;
    private final UserWelcomeEmailService welcomeEmailService;

    @Value("${auth.magic-link.enabled:true}")
    private boolean magicLinkEnabled = true;

    @Transactional
    public MagicLinkRequestResult requestLoginLink(String identifier, HttpServletRequest request) {
        if (!magicLinkEnabled) return MagicLinkRequestResult.neutral();
        String normalizedIdentifier = TNMUser.normalizeEmail(identifier);
        if (normalizedIdentifier == null) {
            return MagicLinkRequestResult.neutral();
        }

        String ipHash = sha256(clientIp(request));
        String identifierHash = sha256(normalizedIdentifier);
        if (!rateLimitService.allow(ipHash, identifierHash)) {
            auditService.record("MAGIC_LINK_RATE_LIMITED", null, ipHash, identifierHash);
            return MagicLinkRequestResult.neutral();
        }

        TNMUser user = userService.findByIdentifier(normalizedIdentifier);
        if (user == null || user.isDisabled()) {
            log.info("Magic-link login requested for unknown or disabled account identifier hash {}",
                    sha256(normalizedIdentifier));
            auditService.record("MAGIC_LINK_REQUESTED_UNKNOWN", null, ipHash, identifierHash);
            return MagicLinkRequestResult.neutral();
        }
        String normalizedEmail = TNMUser.normalizeEmail(user.getEmail());
        if (normalizedEmail == null) {
            log.warn("Magic-link login requested for account {} but no email is configured", user.getId());
            return MagicLinkRequestResult.neutral();
        }

        auditService.record("MAGIC_LINK_REQUESTED", user.getId(), ipHash, null);
        sendManagedLinkInternal(user, request, false);
        return MagicLinkRequestResult.neutral();
    }

    /** Called only after super-admin authorization. */
    @Transactional
    public boolean sendManagedLoginLink(TNMUser user, HttpServletRequest request) {
        return magicLinkEnabled && sendManagedLinkInternal(user, request, false);
    }

    /** Called only after super-admin authorization. */
    @Transactional
    public boolean sendManagedWelcomeLink(TNMUser user, HttpServletRequest request) {
        return magicLinkEnabled && sendManagedLinkInternal(user, request, true);
    }

    @Transactional
    public boolean sendProfileWelcomeLink(TNMUser user, HttpServletRequest request) {
        if (!magicLinkEnabled || user == null) return false;
        String ipHash = sha256(clientIp(request));
        if (!rateLimitService.allow(ipHash, sha256("welcome:" + user.getId()))) {
            auditService.record("WELCOME_EMAIL_RATE_LIMITED", user.getId(), ipHash, "profile");
            return false;
        }
        return sendManagedLinkInternal(user, request, true);
    }

    private boolean sendManagedLinkInternal(TNMUser user, HttpServletRequest request, boolean welcome) {
        if (user == null || user.isDisabled()) return false;
        String normalizedEmail = TNMUser.normalizeEmail(user.getEmail());
        if (normalizedEmail == null) return false;
        String rawToken = generateToken();
        MagicLinkToken token = MagicLinkToken.builder()
                .id(IDGenerator.generateID('M')).accountId(user.getId())
                .tokenHash(hashToken(rawToken)).purpose(PURPOSE_LOGIN)
                .expiresAt(LocalDateTime.now().plus(TOKEN_TTL))
                .requestedEmailHash(sha256(normalizedEmail))
                .requestIpHash(sha256(clientIp(request)))
                .userAgent(truncate(request == null ? null : request.getHeader("User-Agent"), 500)).build();
        tokenRepository.save(token);
        String loginUrl = buildLoginUrl(rawToken, request);
        EmailSendResult result = welcome
                ? welcomeEmailService.sendWelcome(user, loginUrl)
                : sendLoginLink(user, loginUrl);
        if (result.isSuccess()) {
            user.setInvitedAt(LocalDateTime.now());
            userService.update(user);
            auditService.record(welcome ? "WELCOME_EMAIL_SENT" : "MAGIC_LINK_SENT",
                    user.getId(), sha256(clientIp(request)), null);
        } else {
            tokenRepository.delete(token);
            log.warn("Magic-link delivery failed for account {}", user.getId());
            auditService.record(welcome ? "WELCOME_EMAIL_SEND_FAILED" : "MAGIC_LINK_SEND_FAILED",
                    user.getId(), sha256(clientIp(request)),
                    result.getMessage());
        }
        return result.isSuccess();
    }

    @Transactional(noRollbackFor = MagicLinkAuthenticationException.class)
    public TNMUser consumeLoginToken(String rawToken) {
        if (!magicLinkEnabled) {
            throw new MagicLinkAuthenticationException("Sign-in link is invalid or expired.");
        }
        if (rawToken == null || rawToken.isBlank()) {
            auditService.record("MAGIC_LINK_REJECTED", null, null, "missing");
            throw new MagicLinkAuthenticationException("Sign-in link is missing.");
        }

        String tokenHash = hashToken(rawToken);
        MagicLinkToken token = tokenRepository.findByTokenHash(tokenHash).orElse(null);
        if (token == null) {
            auditService.record("MAGIC_LINK_REJECTED", null, null, tokenHash);
            throw new MagicLinkAuthenticationException("Sign-in link is invalid or expired.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!PURPOSE_LOGIN.equals(token.getPurpose()) || !token.isUsable(now)) {
            auditService.record("MAGIC_LINK_REJECTED", token.getAccountId(), null, token.getId());
            throw new MagicLinkAuthenticationException("Sign-in link is invalid or expired.");
        }

        TNMUser user = userService.findById(token.getAccountId());
        if (user == null || user.isDisabled()) {
            auditService.record("MAGIC_LINK_REJECTED", token.getAccountId(), null, "disabled-or-missing");
            throw new MagicLinkAuthenticationException("Sign-in link is invalid or expired.");
        }

        if (tokenRepository.consumeIfUsable(token.getId(), now) != 1) {
            auditService.record("MAGIC_LINK_REPLAY_REJECTED", token.getAccountId(), null, token.getId());
            throw new MagicLinkAuthenticationException("Sign-in link is invalid or expired.");
        }

        user.setLastLoginAt(now);
        user.setLastLoginMethod("MAGIC_LINK");
        if (user.getInviteAcceptedAt() == null) {
            user.setInviteAcceptedAt(now);
        }
        userService.update(user);
        auditService.record("MAGIC_LINK_USED", user.getId(), null, token.getId());

        return user;
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeExpiredAndUsedTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        long deleted = tokenRepository.deleteByExpiresAtBeforeOrUsedAtBefore(cutoff, cutoff);
        if (deleted > 0) log.info("Purged {} expired or used magic-link tokens", deleted);
    }

    public static String hashToken(String token) {
        return sha256(token);
    }

    private EmailSendResult sendLoginLink(TNMUser user, String loginUrl) {
        String siteName = settingsService.getSiteName();
        String escapedSiteName = HtmlUtils.htmlEscape(siteName);
        String escapedLoginUrl = HtmlUtils.htmlEscape(loginUrl);
        String escapedUsername = HtmlUtils.htmlEscape(
                user.getUsername() == null || user.getUsername().isBlank() ? "there" : user.getUsername());

        EmailMessage message = EmailMessage.builder()
                .to(user.getEmail())
                .subject("Sign in to " + siteName)
                .textBody("Use this link to sign in to " + siteName + ":\n\n"
                        + loginUrl + "\n\n"
                        + "This link expires in 15 minutes and can only be used once.")
                .htmlBody(brandedLoginEmail(escapedSiteName, escapedUsername, escapedLoginUrl))
                .metadata("source", "admin-magic-link")
                .build();
        return emailService.send(message);
    }

    private String brandedLoginEmail(String siteName, String username, String loginUrl) {
        return """
                <!doctype html>
                <html lang="en">
                <body style="margin:0;padding:0;background:#f3f6fb;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#f3f6fb;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border:1px solid #dbe3ef;border-radius:14px;overflow:hidden;box-shadow:0 8px 24px rgba(31,41,55,0.08);">
                        <tr><td style="background:#275ed8;padding:28px 32px;color:#ffffff;">
                          <div style="font-size:13px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;opacity:.88;">Secure admin sign-in</div>
                          <div style="font-size:25px;font-weight:700;margin-top:6px;">{{SITE_NAME}}</div>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <p style="font-size:17px;line-height:1.55;margin:0 0 14px;">Hello {{USERNAME}},</p>
                          <p style="font-size:16px;line-height:1.6;margin:0 0 24px;">Use the secure button below to sign in. No password is required.</p>
                          <table role="presentation" cellspacing="0" cellpadding="0" style="margin:0 0 24px;"><tr><td style="border-radius:8px;background:#275ed8;">
                            <a href="{{LOGIN_URL}}" style="display:inline-block;padding:14px 24px;color:#ffffff;text-decoration:none;font-size:16px;font-weight:700;">Sign in to {{SITE_NAME}}</a>
                          </td></tr></table>
                          <div style="background:#f8fafc;border-left:4px solid #275ed8;border-radius:6px;padding:14px 16px;margin-bottom:24px;">
                            <p style="font-size:14px;line-height:1.55;margin:0;"><strong>This link expires in 15 minutes</strong> and can only be used once.</p>
                          </div>
                          <p style="font-size:13px;line-height:1.55;color:#64748b;margin:0 0 8px;">If the button does not work, copy and paste this link into your browser:</p>
                          <p style="font-size:12px;line-height:1.5;word-break:break-all;margin:0 0 24px;"><a href="{{LOGIN_URL}}" style="color:#275ed8;">{{LOGIN_URL}}</a></p>
                          <p style="font-size:13px;line-height:1.55;color:#64748b;margin:0;">If you did not request this email, you can safely ignore it.</p>
                        </td></tr>
                        <tr><td style="border-top:1px solid #e5e7eb;padding:18px 32px;color:#64748b;font-size:12px;line-height:1.5;">Sent by {{SITE_NAME}} account security.</td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """
                .replace("{{SITE_NAME}}", siteName)
                .replace("{{USERNAME}}", username)
                .replace("{{LOGIN_URL}}", loginUrl);
    }

    String buildLoginUrl(String rawToken, HttpServletRequest request) {
        return UriComponentsBuilder
                .fromHttpUrl(resolveMagicLinkRootUrl(request))
                .path("/admin/login/magic-link/verify")
                .queryParam("token", rawToken)
                .toUriString();
    }

    private String resolveMagicLinkRootUrl(HttpServletRequest request) {
        String configuredRootUrl = normalizeRootUrl(settingsService.getSiteRootUrl(), "http://localhost:8080");
        String requestRootUrl = requestRootUrl(request);
        if (requestRootUrl != null && isAllowedRequestRoot(requestRootUrl, configuredRootUrl)) {
            return requestRootUrl;
        }
        return configuredRootUrl;
    }

    private String requestRootUrl(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String forwardedHost = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
        String host = forwardedHost != null ? forwardedHost : firstHeaderValue(request.getHeader("Host"));
        boolean explicitHostHeader = host != null;
        if (!explicitHostHeader) {
            host = request.getServerName();
        }
        if (host == null || host.isBlank() || !isSafeAuthority(host)) {
            return null;
        }
        if (!explicitHostHeader && isLocalhost(host)) {
            return null;
        }

        String scheme = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
        if (scheme == null || !isSafeScheme(scheme)) {
            scheme = request.getScheme() == null ? "http" : request.getScheme();
        }

        String authority = host;
        String forwardedPort = firstHeaderValue(request.getHeader("X-Forwarded-Port"));
        if (!authorityHasPort(authority) && forwardedPort != null && isSafePort(forwardedPort)
                && !isDefaultPort(scheme, forwardedPort)) {
            authority += ":" + forwardedPort;
        } else if (!explicitHostHeader && !authorityHasPort(authority) && request.getServerPort() > 0
                && !isDefaultPort(scheme, String.valueOf(request.getServerPort()))) {
            authority += ":" + request.getServerPort();
        }

        return normalizeRootUrl(scheme + "://" + authority, null);
    }

    private boolean isAllowedRequestRoot(String requestRootUrl, String configuredRootUrl) {
        URI requestUri = URI.create(requestRootUrl);
        URI configuredUri = URI.create(configuredRootUrl);
        String requestHost = requestUri.getHost();
        String configuredHost = configuredUri.getHost();
        if (requestHost == null) {
            return false;
        }
        if (configuredHost != null && requestHost.equalsIgnoreCase(configuredHost)) {
            return true;
        }
        String normalizedHost = requestHost.toLowerCase();
        return isLocalhost(normalizedHost)
                || normalizedHost.equals("teaneckminyanim.com")
                || normalizedHost.endsWith(".teaneckminyanim.com");
    }

    private static String normalizeRootUrl(String rootUrl, String fallback) {
        if (rootUrl == null || rootUrl.isBlank()) {
            return fallback;
        }
        try {
            URI uri = new URI(rootUrl.trim());
            if (uri.getScheme() == null || uri.getHost() == null || !isSafeScheme(uri.getScheme())) {
                return fallback;
            }
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase() + port;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String firstHeaderValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String firstValue = value.split(",", 2)[0].trim();
        return firstValue.isBlank() ? null : firstValue;
    }

    private static boolean isSafeScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static boolean isSafeAuthority(String authority) {
        return authority.matches("[A-Za-z0-9.\\-:\\[\\]]+");
    }

    private static boolean authorityHasPort(String authority) {
        int lastColon = authority.lastIndexOf(':');
        if (lastColon < 0) {
            return false;
        }
        return authority.indexOf(']') < lastColon;
    }

    private static boolean isSafePort(String port) {
        return port.matches("\\d{1,5}");
    }

    private static boolean isDefaultPort(String scheme, String port) {
        return ("http".equalsIgnoreCase(scheme) && "80".equals(port))
                || ("https".equalsIgnoreCase(scheme) && "443".equals(port));
    }

    private static boolean isLocalhost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record MagicLinkRequestResult(String message) {
        public static MagicLinkRequestResult neutral() {
            return new MagicLinkRequestResult(
                    "If this email is authorized, a sign-in link will be sent shortly.");
        }
    }
}
