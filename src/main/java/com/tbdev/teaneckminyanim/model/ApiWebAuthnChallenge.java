package com.tbdev.teaneckminyanim.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * A passkey sign-in challenge issued to the mobile app by {@code POST /api/v1/auth/passkey/options}. The app
 * has no browser session, so the request options live here instead of in the HTTP session. Each row can be
 * used once ({@link #usedAt}) and only until {@link #expiresAt}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "API_WEBAUTHN_CHALLENGE", indexes = {
        @Index(name = "idx_api_webauthn_challenge_expires", columnList = "EXPIRES_AT")
})
public class ApiWebAuthnChallenge {

    @Id
    @Column(name = "ID", nullable = false, length = 64)
    private String id;

    /** The PublicKeyCredentialRequestOptions JSON exactly as it was sent to the app. */
    @Column(name = "OPTIONS_JSON", nullable = false, length = 4000)
    private String optionsJson;

    @Column(name = "EXPIRES_AT", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "USED_AT")
    private LocalDateTime usedAt;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void defaults() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
