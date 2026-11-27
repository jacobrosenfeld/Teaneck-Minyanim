package com.tbdev.teaneckminyanim.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbdev.teaneckminyanim.model.ApiWebAuthnChallenge;
import com.tbdev.teaneckminyanim.repo.ApiWebAuthnChallengeRepository;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NativePasskeyServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneId.of("America/New_York"));

    private final ApiWebAuthnChallengeRepository challenges = mock(ApiWebAuthnChallengeRepository.class);

    @Test
    void optionsFromTheRealRelyingPartyRoundTripThroughTheStoredJson() throws Exception {
        WebAuthnRelyingPartyOperations relyingParty = new Webauthn4JRelyingPartyOperations(
                mock(PublicKeyCredentialUserEntityRepository.class), mock(UserCredentialRepository.class),
                PublicKeyCredentialRpEntity.builder().id("teaneckminyanim.com").name("Teaneck Minyanim").build(),
                Set.of("https://teaneckminyanim.com"));
        NativePasskeyService service = service(relyingParty);

        NativePasskeyService.Challenge challenge = service.createChallenge();

        ArgumentCaptor<ApiWebAuthnChallenge> saved = ArgumentCaptor.forClass(ApiWebAuthnChallenge.class);
        verify(challenges).save(saved.capture());
        ApiWebAuthnChallenge row = saved.getValue();
        assertThat(row.getId()).isEqualTo(challenge.challengeId()).startsWith("W").hasSize(33);
        assertThat(row.getOptionsJson()).isEqualTo(challenge.optionsJson());
        assertThat(row.getCreatedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(row.getExpiresAt()).isEqualTo(LocalDateTime.now(CLOCK).plusMinutes(5));
        assertThat(row.getUsedAt()).isNull();

        // Same shape as Spring Security's /webauthn/authenticate/options.
        JsonNode json = new ObjectMapper().readTree(challenge.optionsJson());
        assertThat(json.path("rpId").asText()).isEqualTo("teaneckminyanim.com");
        assertThat(json.path("timeout").asLong()).isEqualTo(300000);
        assertThat(json.path("userVerification").asText()).isEqualTo("preferred");
        assertThat(json.path("allowCredentials").isArray()).isTrue();
        assertThat(json.path("allowCredentials")).isEmpty();
        assertThat(json.path("challenge").asText()).matches("[A-Za-z0-9_-]{43}");

        PublicKeyCredentialRequestOptions rebuilt = service.readOptions(challenge.optionsJson());
        assertThat(rebuilt.getChallenge().toBase64UrlString()).isEqualTo(json.path("challenge").asText());
        assertThat(rebuilt.getRpId()).isEqualTo("teaneckminyanim.com");
        assertThat(rebuilt.getTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(rebuilt.getAllowCredentials()).isEmpty();
        // Spring Security compares this by identity.
        assertThat(rebuilt.getUserVerification()).isSameAs(UserVerificationRequirement.PREFERRED);
    }

    @Test
    void readOptionsMapsEveryUserVerificationToTheSharedConstant() throws Exception {
        NativePasskeyService service = service(mock(WebAuthnRelyingPartyOperations.class));
        String challenge = Bytes.random().toBase64UrlString();
        for (UserVerificationRequirement known : List.of(UserVerificationRequirement.REQUIRED,
                UserVerificationRequirement.PREFERRED, UserVerificationRequirement.DISCOURAGED)) {
            PublicKeyCredentialRequestOptions rebuilt = service.readOptions(
                    "{\"rpId\":\"localhost\",\"challenge\":\"" + challenge + "\",\"userVerification\":\"" + known.getValue() + "\"}");
            assertThat(rebuilt.getUserVerification()).isSameAs(known);
        }
        assertThat(service.readOptions("{\"rpId\":\"localhost\",\"challenge\":\"" + challenge + "\"}").getUserVerification())
                .isSameAs(UserVerificationRequirement.PREFERRED);
    }

    @Test
    void optionsAreAlwaysRequestedAnonymously() {
        WebAuthnRelyingPartyOperations relyingParty = mock(WebAuthnRelyingPartyOperations.class);
        when(relyingParty.createCredentialRequestOptions(any())).thenReturn(PublicKeyCredentialRequestOptions.builder()
                .challenge(Bytes.random()).rpId("localhost").allowCredentials(List.of()).build());
        service(relyingParty).createChallenge();
        verify(relyingParty).createCredentialRequestOptions(org.mockito.ArgumentMatchers.argThat(request ->
                request.getAuthentication() instanceof org.springframework.security.authentication.AnonymousAuthenticationToken));
    }

    @Test
    void purgeDeletesChallengesExpiredOverAnHourAgo() {
        when(challenges.deleteExpiredBefore(any())).thenReturn(3);
        assertThat(service(mock(WebAuthnRelyingPartyOperations.class)).purgeExpired()).isEqualTo(3);
        verify(challenges).deleteExpiredBefore(LocalDateTime.now(CLOCK).minusHours(1));
    }

    private NativePasskeyService service(WebAuthnRelyingPartyOperations relyingParty) {
        return new NativePasskeyService(relyingParty, challenges, mock(TNMUserService.class),
                mock(AppTokenService.class), mock(AuthAuditService.class), CLOCK);
    }
}
