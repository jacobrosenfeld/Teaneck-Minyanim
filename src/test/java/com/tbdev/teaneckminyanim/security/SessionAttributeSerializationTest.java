package com.tbdev.teaneckminyanim.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.webauthn.api.AttestationConveyancePreference;
import org.springframework.security.web.webauthn.api.AuthenticatorAttachment;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutableAuthenticationExtensionsClientInput;
import org.springframework.security.web.webauthn.api.ImmutableAuthenticationExtensionsClientInputs;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialDescriptor;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Session JDBC Java-serializes every session attribute. These tests cover the attributes
 * the passkey flows put into the session.
 */
class SessionAttributeSerializationTest {

    private static final PublicKeyCredentialUserEntity USER = ImmutablePublicKeyCredentialUserEntity.builder()
            .id(Bytes.random())
            .name("manager")
            .displayName("manager")
            .build();

    @Test
    void creationOptionsSurviveSerializationRoundTripThroughSession() throws Exception {
        PublicKeyCredentialCreationOptions original = PublicKeyCredentialCreationOptions.builder()
                .rp(PublicKeyCredentialRpEntity.builder().id("teaneckminyanim.com").name("Teaneck Minyanim").build())
                .user(USER)
                .challenge(Bytes.random())
                .pubKeyCredParams(PublicKeyCredentialParameters.EdDSA, PublicKeyCredentialParameters.ES256,
                        PublicKeyCredentialParameters.RS256)
                .timeout(Duration.ofMinutes(5))
                .excludeCredentials(List.of(PublicKeyCredentialDescriptor.builder()
                        .id(Bytes.random())
                        .transports(Set.of(AuthenticatorTransport.INTERNAL, AuthenticatorTransport.HYBRID))
                        .build()))
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .authenticatorAttachment(AuthenticatorAttachment.PLATFORM)
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.PREFERRED)
                        .build())
                .attestation(AttestationConveyancePreference.DIRECT)
                .extensions(new ImmutableAuthenticationExtensionsClientInputs(
                        ImmutableAuthenticationExtensionsClientInput.credProps))
                .build();

        SerializablePublicKeyCredentialCreationOptionsRepository repository =
                new SerializablePublicKeyCredentialCreationOptionsRepository();
        MockHttpServletRequest request = new MockHttpServletRequest();
        repository.save(request, new MockHttpServletResponse(), original);

        Object stored = request.getSession().getAttribute(SerializablePublicKeyCredentialCreationOptionsRepository.ATTR_NAME);
        MockHttpServletRequest afterRestart = new MockHttpServletRequest();
        afterRestart.getSession().setAttribute(SerializablePublicKeyCredentialCreationOptionsRepository.ATTR_NAME,
                roundTrip(stored));

        PublicKeyCredentialCreationOptions loaded = repository.load(afterRestart);

        assertThat(loaded.getRp().getId()).isEqualTo("teaneckminyanim.com");
        assertThat(loaded.getRp().getName()).isEqualTo("Teaneck Minyanim");
        assertThat(loaded.getUser().getName()).isEqualTo("manager");
        assertThat(loaded.getUser().getId()).isEqualTo(USER.getId());
        assertThat(loaded.getChallenge()).isEqualTo(original.getChallenge());
        assertThat(loaded.getPubKeyCredParams()).containsExactly(PublicKeyCredentialParameters.EdDSA,
                PublicKeyCredentialParameters.ES256, PublicKeyCredentialParameters.RS256);
        assertThat(loaded.getTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(loaded.getExcludeCredentials()).hasSize(1);
        assertThat(loaded.getExcludeCredentials().get(0).getId())
                .isEqualTo(original.getExcludeCredentials().get(0).getId());
        assertThat(loaded.getAuthenticatorSelection().getAuthenticatorAttachment()).isEqualTo(AuthenticatorAttachment.PLATFORM);
        assertThat(loaded.getAuthenticatorSelection().getResidentKey()).isEqualTo(ResidentKeyRequirement.REQUIRED);
        assertThat(loaded.getAuthenticatorSelection().getUserVerification()).isEqualTo(UserVerificationRequirement.PREFERRED);
        assertThat(loaded.getAttestation()).isEqualTo(AttestationConveyancePreference.DIRECT);
        assertThat(loaded.getExtensions().getInputs()).hasSize(1);
    }

    @Test
    void savingNullRemovesStoredCreationOptions() {
        SerializablePublicKeyCredentialCreationOptionsRepository repository =
                new SerializablePublicKeyCredentialCreationOptionsRepository();
        MockHttpServletRequest request = new MockHttpServletRequest();
        repository.save(request, new MockHttpServletResponse(), PublicKeyCredentialCreationOptions.builder()
                .rp(PublicKeyCredentialRpEntity.builder().id("localhost").name("Local").build())
                .user(USER)
                .challenge(Bytes.random())
                .pubKeyCredParams(PublicKeyCredentialParameters.ES256)
                .build());

        repository.save(request, new MockHttpServletResponse(), null);

        assertThat(repository.load(request)).isNull();
    }

    @Test
    void loadWithoutSessionReturnsNull() {
        assertThat(new SerializablePublicKeyCredentialCreationOptionsRepository()
                .load(new MockHttpServletRequest())).isNull();
    }

    @Test
    void passkeyRequestOptionsAndAuthenticationAreSerializable() throws Exception {
        PublicKeyCredentialRequestOptions requestOptions = PublicKeyCredentialRequestOptions.builder()
                .challenge(Bytes.random())
                .rpId("teaneckminyanim.com")
                .timeout(Duration.ofMinutes(5))
                .userVerification(UserVerificationRequirement.PREFERRED)
                .build();
        PublicKeyCredentialRequestOptions copy = roundTrip(requestOptions);
        assertThat(copy.getChallenge()).isEqualTo(requestOptions.getChallenge());

        WebAuthnAuthentication authentication = new WebAuthnAuthentication(USER,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextImpl context = roundTrip(new SecurityContextImpl(authentication));
        assertThat(context.getAuthentication().getName()).isEqualTo("manager");
        assertThat(context.getAuthentication().isAuthenticated()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }
}
