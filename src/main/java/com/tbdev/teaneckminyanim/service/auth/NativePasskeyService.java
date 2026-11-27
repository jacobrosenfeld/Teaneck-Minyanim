package com.tbdev.teaneckminyanim.service.auth;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbdev.teaneckminyanim.model.ApiWebAuthnChallenge;
import com.tbdev.teaneckminyanim.model.AppSession;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.ApiWebAuthnChallengeRepository;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.jackson.WebauthnJackson2Module;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Native passkey sign-in for the mobile app. It uses the same {@link WebAuthnRelyingPartyOperations} bean as the
 * website's {@code http.webAuthn} flow, so one passkey works on both. The website keeps its request options in the
 * HTTP session; the app has none, so they are stored in an {@link ApiWebAuthnChallenge} row that can be used once.
 *
 * <p>The JSON matches Spring Security's own filters: options are written, and credentials read, with an
 * ObjectMapper carrying {@link WebauthnJackson2Module}, built exactly as {@code PublicKeyCredentialRequestOptionsFilter}
 * and {@code WebAuthnAuthenticationFilter} build theirs.
 */
@Service
@Slf4j
public class NativePasskeyService {
    public static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    /** Expired challenges are kept this long, then purged. */
    static final Duration PURGE_AFTER = Duration.ofHours(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** A new challenge: the id to send back to verify, and the options JSON for the native passkey API. */
    public record Challenge(String challengeId, String optionsJson) {
    }

    /** Why a verification failed. Callers map these to API errors; the WebAuthn library's messages are never shown. */
    public static class PasskeyVerificationException extends RuntimeException {
        private final boolean malformed;

        PasskeyVerificationException(String reason, boolean malformed) {
            super(reason);
            this.malformed = malformed;
        }

        /** True when the request itself was malformed (400), false when it was a failed sign-in (401). */
        public boolean isMalformed() {
            return malformed;
        }
    }

    private final WebAuthnRelyingPartyOperations relyingParty;
    private final ApiWebAuthnChallengeRepository challenges;
    private final TNMUserService userService;
    private final AppTokenService appTokenService;
    private final AuthAuditService auditService;
    private final ObjectMapper webauthnMapper;
    private final JavaType assertionType;
    private final Clock clock;

    @Value("${auth.passkeys.enabled:true}")
    private boolean passkeysEnabled = true;

    @Autowired
    public NativePasskeyService(WebAuthnRelyingPartyOperations relyingParty,
                                ApiWebAuthnChallengeRepository challenges,
                                TNMUserService userService,
                                AppTokenService appTokenService,
                                AuthAuditService auditService) {
        this(relyingParty, challenges, userService, appTokenService, auditService, Clock.systemDefaultZone());
    }

    NativePasskeyService(WebAuthnRelyingPartyOperations relyingParty, ApiWebAuthnChallengeRepository challenges,
                         TNMUserService userService, AppTokenService appTokenService, AuthAuditService auditService,
                         Clock clock) {
        this.relyingParty = relyingParty;
        this.challenges = challenges;
        this.userService = userService;
        this.appTokenService = appTokenService;
        this.auditService = auditService;
        this.clock = clock;
        // Same construction as Spring Security's PublicKeyCredentialRequestOptionsFilter / WebAuthnAuthenticationFilter.
        this.webauthnMapper = Jackson2ObjectMapperBuilder.json().modules(new WebauthnJackson2Module()).build();
        this.assertionType = webauthnMapper.getTypeFactory()
                .constructParametricType(PublicKeyCredential.class, AuthenticatorAssertionResponse.class);
    }

    public boolean isEnabled() {
        return passkeysEnabled;
    }

    /**
     * Creates request options for an anonymous sign-in (empty allowCredentials, so the phone offers any discoverable
     * passkey for this site) and stores them in a new single-use challenge row.
     */
    public Challenge createChallenge() {
        // Always anonymous, even if the request carries a bearer token: the app is signing in, not re-authenticating.
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken("app-passkey", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        PublicKeyCredentialRequestOptions options = relyingParty.createCredentialRequestOptions(
                new ImmutablePublicKeyCredentialRequestOptionsRequest(anonymous));
        String json;
        try {
            json = webauthnMapper.writeValueAsString(options);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize passkey request options.", e);
        }
        LocalDateTime now = now();
        ApiWebAuthnChallenge challenge = ApiWebAuthnChallenge.builder()
                .id(newChallengeId())
                .optionsJson(json)
                .createdAt(now)
                .expiresAt(now.plus(CHALLENGE_TTL))
                .build();
        challenges.save(challenge);
        return new Challenge(challenge.getId(), json);
    }

    /**
     * Verifies a native passkey assertion against a stored challenge and signs the app in.
     *
     * @throws PasskeyVerificationException for a malformed request ({@link PasskeyVerificationException#isMalformed()})
     *         or any failed sign-in: unknown, expired or reused challenge, a credential that does not verify, or an
     *         account that is unknown, disabled or has no role. Every failure is audited.
     */
    public VerifiedSignIn verify(String challengeId, JsonNode credentialJson, String deviceName,
                                 HttpServletRequest request) {
        String ipHash = AuthRequests.clientIpHash(request);
        if (challengeId == null || challengeId.isBlank() || challengeId.length() > 64) {
            throw fail(ipHash, null, "missing challengeId", true);
        }
        if (credentialJson == null || !credentialJson.isObject()) {
            throw fail(ipHash, null, "missing credential", true);
        }
        PublicKeyCredential<AuthenticatorAssertionResponse> credential;
        try {
            credential = webauthnMapper.readerFor(assertionType).readValue(credentialJson);
        } catch (Exception e) {
            throw fail(ipHash, null, "malformed credential", true);
        }
        if (credential == null || credential.getResponse() == null || credential.getRawId() == null) {
            throw fail(ipHash, null, "malformed credential", true);
        }

        // Single use: claim the row first, so a failed or concurrent verify can never reuse it.
        if (challenges.consumeIfUsable(challengeId, now()) != 1) {
            throw fail(ipHash, null, "unknown, expired or used challenge", false);
        }
        Optional<ApiWebAuthnChallenge> stored = challenges.findById(challengeId);
        if (stored.isEmpty()) {
            throw fail(ipHash, null, "unknown challenge", false);
        }
        PublicKeyCredentialRequestOptions options;
        try {
            options = readOptions(stored.get().getOptionsJson());
        } catch (Exception e) {
            log.warn("Stored passkey challenge {} could not be read", challengeId);
            throw fail(ipHash, null, "unreadable challenge", false);
        }

        PublicKeyCredentialUserEntity userEntity;
        try {
            userEntity = relyingParty.authenticate(new RelyingPartyAuthenticationRequest(options, credential));
        } catch (RuntimeException e) {
            // The library's message can describe the credential; log only its type.
            log.info("Native passkey verification failed: {}", e.getClass().getSimpleName());
            throw fail(ipHash, null, "assertion rejected", false);
        }
        TNMUser user = userEntity == null || userEntity.getId() == null ? null
                : userService.findById(new String(userEntity.getId().getBytes(), StandardCharsets.UTF_8));
        if (user == null || user.isDisabled() || user.getRoleId() == null || user.role() == null) {
            throw fail(ipHash, user == null ? null : user.getId(), "account cannot sign in", false);
        }

        // Mirror the website's passkey login (AuthenticationSuccessEventListener).
        user.setLastLoginAt(LocalDateTime.now(clock));
        user.setLastLoginMethod("PASSKEY");
        userService.update(user);
        auditService.record("PASSKEY_LOGIN_SUCCEEDED", user.getId(), ipHash, "app challenge=" + challengeId);

        AppTokenService.IssuedTokens tokens;
        try {
            tokens = appTokenService.issue(user, AppSession.LoginMethod.PASSKEY, deviceName, request);
        } catch (IllegalArgumentException e) {
            throw fail(ipHash, user.getId(), "account cannot sign in", false);
        }
        return new VerifiedSignIn(user, tokens);
    }

    /** A successful native passkey sign-in. */
    public record VerifiedSignIn(TNMUser user, AppTokenService.IssuedTokens tokens) {
        @Override
        public String toString() {
            return "VerifiedSignIn[user=" + (user == null ? null : user.getId()) + ", tokens=" + tokens + "]";
        }
    }

    /** Deletes challenges that expired over an hour ago. */
    @Scheduled(fixedDelayString = "${auth.passkey-challenge.purge-interval:PT1H}",
               initialDelayString = "${auth.passkey-challenge.purge-initial-delay:PT15M}")
    public int purgeExpired() {
        try {
            int purged = challenges.deleteExpiredBefore(now().minus(PURGE_AFTER));
            if (purged > 0) {
                log.info("Purged {} expired app passkey challenges", purged);
            }
            return purged;
        } catch (RuntimeException e) {
            log.warn("Could not purge app passkey challenges: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Rebuilds the stored options. WebauthnJackson2Module only serializes request options, so the fields the relying
     * party checks (challenge, rpId, timeout, userVerification) are read back from the JSON. allowCredentials is
     * always empty for these anonymous challenges.
     */
    PublicKeyCredentialRequestOptions readOptions(String json) throws Exception {
        JsonNode node = webauthnMapper.readTree(json);
        String challenge = node.path("challenge").asText(null);
        if (challenge == null || challenge.isBlank()) {
            throw new IllegalArgumentException("missing challenge");
        }
        PublicKeyCredentialRequestOptions.PublicKeyCredentialRequestOptionsBuilder builder =
                PublicKeyCredentialRequestOptions.builder()
                        .challenge(Bytes.fromBase64(challenge))
                        .allowCredentials(List.of())
                        .userVerification(userVerification(node.path("userVerification").asText(null)));
        String rpId = node.path("rpId").asText(null);
        if (rpId != null) {
            builder.rpId(rpId);
        }
        if (node.path("timeout").isNumber()) {
            builder.timeout(Duration.ofMillis(node.path("timeout").asLong()));
        }
        return builder.build();
    }

    /** Spring Security compares these by identity ({@code == REQUIRED}), so map back to the shared constants. */
    private static UserVerificationRequirement userVerification(String value) {
        for (UserVerificationRequirement known : List.of(UserVerificationRequirement.DISCOURAGED,
                UserVerificationRequirement.PREFERRED, UserVerificationRequirement.REQUIRED)) {
            if (known.getValue().equals(value)) {
                return known;
            }
        }
        return UserVerificationRequirement.PREFERRED;
    }

    private PasskeyVerificationException fail(String ipHash, String accountId, String reason, boolean malformed) {
        auditService.record("PASSKEY_LOGIN_FAILED", accountId, ipHash, "app: " + reason);
        return new PasskeyVerificationException(reason, malformed);
    }

    private static String newChallengeId() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "W" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
