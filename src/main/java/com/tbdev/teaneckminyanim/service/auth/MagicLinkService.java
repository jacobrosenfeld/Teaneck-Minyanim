package com.tbdev.teaneckminyanim.service.auth;

import com.tbdev.teaneckminyanim.model.MagicLinkToken;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.MagicLinkTokenRepository;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.email.EmailMessage;
import com.tbdev.teaneckminyanim.service.email.EmailSendResult;
import com.tbdev.teaneckminyanim.service.email.EmailService;
import com.tbdev.teaneckminyanim.tools.IDGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

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

    @Transactional
    public MagicLinkRequestResult requestLoginLink(String email, HttpServletRequest request) {
        String normalizedEmail = TNMUser.normalizeEmail(email);
        if (normalizedEmail == null) {
            return MagicLinkRequestResult.neutral();
        }

        TNMUser user = userService.findByNormalizedEmail(normalizedEmail);
        if (user == null || user.isDisabled()) {
            log.info("Magic-link login requested for unknown or disabled account email hash {}",
                    sha256(normalizedEmail));
            return MagicLinkRequestResult.neutral();
        }

        String rawToken = generateToken();
        MagicLinkToken token = MagicLinkToken.builder()
                .id(IDGenerator.generateID('M'))
                .accountId(user.getId())
                .tokenHash(hashToken(rawToken))
                .purpose(PURPOSE_LOGIN)
                .expiresAt(LocalDateTime.now().plus(TOKEN_TTL))
                .requestedEmailHash(sha256(normalizedEmail))
                .requestIpHash(sha256(clientIp(request)))
                .userAgent(truncate(request == null ? null : request.getHeader("User-Agent"), 500))
                .build();
        tokenRepository.save(token);

        EmailSendResult result = sendLoginLink(user, rawToken);
        if (!result.isSuccess()) {
            log.warn("Magic-link email for account {} was not sent: {}", user.getId(), result.getMessage());
        }
        return MagicLinkRequestResult.neutral();
    }

    @Transactional
    public TNMUser consumeLoginToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new MagicLinkAuthenticationException("Sign-in link is missing.");
        }

        MagicLinkToken token = tokenRepository.findByTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new MagicLinkAuthenticationException("Sign-in link is invalid or expired."));

        LocalDateTime now = LocalDateTime.now();
        if (!PURPOSE_LOGIN.equals(token.getPurpose()) || !token.isUsable(now)) {
            throw new MagicLinkAuthenticationException("Sign-in link is invalid or expired.");
        }

        TNMUser user = userService.findById(token.getAccountId());
        if (user == null || user.isDisabled()) {
            throw new MagicLinkAuthenticationException("Sign-in link is invalid or expired.");
        }

        token.setUsedAt(now);
        tokenRepository.save(token);

        user.setLastLoginAt(now);
        user.setLastLoginMethod("MAGIC_LINK");
        if (user.getInviteAcceptedAt() == null) {
            user.setInviteAcceptedAt(now);
        }
        userService.update(user);

        return user;
    }

    public static String hashToken(String token) {
        return sha256(token);
    }

    private EmailSendResult sendLoginLink(TNMUser user, String rawToken) {
        String siteName = settingsService.getSiteName();
        String escapedSiteName = HtmlUtils.htmlEscape(siteName);
        String loginUrl = UriComponentsBuilder
                .fromHttpUrl(settingsService.getSiteRootUrl())
                .path("/admin/login/magic-link/verify")
                .queryParam("token", rawToken)
                .toUriString();

        EmailMessage message = EmailMessage.builder()
                .to(user.getEmail())
                .subject("Sign in to " + siteName)
                .textBody("Use this link to sign in to " + siteName + ":\n\n"
                        + loginUrl + "\n\n"
                        + "This link expires in 15 minutes and can only be used once.")
                .htmlBody("<p>Use this link to sign in to <strong>" + escapedSiteName + "</strong>:</p>"
                        + "<p><a href=\"" + HtmlUtils.htmlEscape(loginUrl) + "\">Sign in to "
                        + escapedSiteName + "</a></p>"
                        + "<p>This link expires in 15 minutes and can only be used once.</p>")
                .metadata("source", "admin-magic-link")
                .build();
        return emailService.send(message);
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
        static MagicLinkRequestResult neutral() {
            return new MagicLinkRequestResult(
                    "If this email is authorized, a sign-in link will be sent shortly.");
        }
    }
}
