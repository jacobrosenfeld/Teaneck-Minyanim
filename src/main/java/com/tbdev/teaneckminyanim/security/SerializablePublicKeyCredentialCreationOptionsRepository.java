package com.tbdev.teaneckminyanim.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.webauthn.api.AttestationConveyancePreference;
import org.springframework.security.web.webauthn.api.AuthenticationExtensionsClientInputs;
import org.springframework.security.web.webauthn.api.AuthenticatorAttachment;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialDescriptor;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Session-backed store for passkey registration options that works with Spring Session JDBC.
 *
 * <p>Spring Security's default {@code HttpSessionPublicKeyCredentialCreationOptionsRepository}
 * puts {@link PublicKeyCredentialCreationOptions} straight into the session, but that class (and
 * several of its parts) is not {@link Serializable}. With sessions persisted in the database the
 * session commit would fail with a serialization error and passkey registration would break.
 * This repository stores a serializable snapshot instead and rebuilds the options on load.
 */
public class SerializablePublicKeyCredentialCreationOptionsRepository implements PublicKeyCredentialCreationOptionsRepository {

    static final String ATTR_NAME = SerializablePublicKeyCredentialCreationOptionsRepository.class.getName() + ".OPTIONS";

    private static final List<PublicKeyCredentialParameters> KNOWN_PARAMETERS = List.of(
            PublicKeyCredentialParameters.EdDSA,
            PublicKeyCredentialParameters.ES256,
            PublicKeyCredentialParameters.ES384,
            PublicKeyCredentialParameters.ES512,
            PublicKeyCredentialParameters.RS256,
            PublicKeyCredentialParameters.RS384,
            PublicKeyCredentialParameters.RS512,
            PublicKeyCredentialParameters.RS1);

    @Override
    public void save(HttpServletRequest request, HttpServletResponse response, PublicKeyCredentialCreationOptions options) {
        if (options == null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.removeAttribute(ATTR_NAME);
            }
            return;
        }
        request.getSession().setAttribute(ATTR_NAME, Snapshot.of(options));
    }

    @Override
    public PublicKeyCredentialCreationOptions load(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(ATTR_NAME);
        return value instanceof Snapshot snapshot ? snapshot.toOptions() : null;
    }

    /** Serializable copy of the fields of {@link PublicKeyCredentialCreationOptions}. */
    record Snapshot(String rpId,
                    String rpName,
                    PublicKeyCredentialUserEntity user,
                    Bytes challenge,
                    ArrayList<Long> pubKeyCredParamAlgorithms,
                    Duration timeout,
                    ArrayList<PublicKeyCredentialDescriptor> excludeCredentials,
                    String authenticatorAttachment,
                    String residentKey,
                    String userVerification,
                    String attestation,
                    AuthenticationExtensionsClientInputs extensions) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        static Snapshot of(PublicKeyCredentialCreationOptions options) {
            PublicKeyCredentialRpEntity rp = options.getRp();
            AuthenticatorSelectionCriteria selection = options.getAuthenticatorSelection();
            ArrayList<Long> algorithms = new ArrayList<>();
            if (options.getPubKeyCredParams() != null) {
                for (PublicKeyCredentialParameters parameters : options.getPubKeyCredParams()) {
                    algorithms.add(parameters.getAlg().getValue());
                }
            }
            return new Snapshot(
                    rp == null ? null : rp.getId(),
                    rp == null ? null : rp.getName(),
                    options.getUser(),
                    options.getChallenge(),
                    algorithms,
                    options.getTimeout(),
                    options.getExcludeCredentials() == null ? null : new ArrayList<>(options.getExcludeCredentials()),
                    selection == null || selection.getAuthenticatorAttachment() == null
                            ? null : selection.getAuthenticatorAttachment().getValue(),
                    selection == null || selection.getResidentKey() == null ? null : selection.getResidentKey().getValue(),
                    selection == null || selection.getUserVerification() == null
                            ? null : selection.getUserVerification().getValue(),
                    options.getAttestation() == null ? null : options.getAttestation().getValue(),
                    options.getExtensions());
        }

        PublicKeyCredentialCreationOptions toOptions() {
            PublicKeyCredentialCreationOptions.PublicKeyCredentialCreationOptionsBuilder builder =
                    PublicKeyCredentialCreationOptions.builder()
                            .user(user)
                            .challenge(challenge)
                            .pubKeyCredParams(parameters())
                            .timeout(timeout);
            if (excludeCredentials != null) {
                builder.excludeCredentials(List.copyOf(excludeCredentials));
            }
            if (extensions != null) {
                builder.extensions(extensions);
            }
            if (rpId != null || rpName != null) {
                builder.rp(PublicKeyCredentialRpEntity.builder().id(rpId).name(rpName).build());
            }
            if (authenticatorAttachment != null || residentKey != null || userVerification != null) {
                builder.authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .authenticatorAttachment(authenticatorAttachment == null
                                ? null : AuthenticatorAttachment.valueOf(authenticatorAttachment))
                        .residentKey(residentKey == null ? null : ResidentKeyRequirement.valueOf(residentKey))
                        .userVerification(userVerification(userVerification))
                        .build());
            }
            if (attestation != null) {
                builder.attestation(AttestationConveyancePreference.valueOf(attestation));
            }
            return builder.build();
        }

        /**
         * Map back to the shared constants: Spring Security compares these by identity
         * (e.g. {@code == UserVerificationRequirement.REQUIRED}), so a deserialized copy must not be used.
         */
        private static UserVerificationRequirement userVerification(String value) {
            if (value == null) {
                return null;
            }
            for (UserVerificationRequirement known : List.of(UserVerificationRequirement.DISCOURAGED,
                    UserVerificationRequirement.PREFERRED, UserVerificationRequirement.REQUIRED)) {
                if (known.getValue().equals(value)) {
                    return known;
                }
            }
            return UserVerificationRequirement.PREFERRED;
        }

        private List<PublicKeyCredentialParameters> parameters() {
            List<PublicKeyCredentialParameters> parameters = new ArrayList<>();
            if (pubKeyCredParamAlgorithms == null) {
                return parameters;
            }
            for (Long algorithm : pubKeyCredParamAlgorithms) {
                KNOWN_PARAMETERS.stream()
                        .filter(known -> known.getAlg().getValue() == algorithm)
                        .findFirst()
                        .ifPresent(parameters::add);
            }
            return parameters;
        }
    }
}
