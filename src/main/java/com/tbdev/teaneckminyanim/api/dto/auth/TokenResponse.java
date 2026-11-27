package com.tbdev.teaneckminyanim.api.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tbdev.teaneckminyanim.service.auth.AppTokenService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * App tokens returned by a sign-in (magic link or passkey) or a refresh. This is the only place the raw tokens
 * ever appear; the server keeps only their hashes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A new access token and refresh token. Store the refresh token securely (Keychain/Keystore).")
public record TokenResponse(
        @Schema(description = "Bearer access token (tnmapp_...). Send as Authorization: Bearer <accessToken>.")
        String accessToken,
        @Schema(description = "Rotating refresh token (tnmrt_...). Each refresh returns a new one; the old one stops working.")
        String refreshToken,
        @Schema(example = "Bearer")
        String tokenType,
        @Schema(description = "When the access token expires (ISO-8601, UTC).", example = "2026-11-23T15:45:00Z")
        Instant accessExpiresAt,
        @Schema(description = "When the refresh token expires (ISO-8601, UTC).", example = "2026-12-23T15:30:00Z")
        Instant refreshExpiresAt,
        @Schema(description = "The signed-in account. Present on sign-in and refresh.")
        MeDto user) {

    public static TokenResponse of(AppTokenService.IssuedTokens tokens, MeDto user) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer",
                tokens.accessExpiresAt(), tokens.refreshExpiresAt(), user);
    }

    @Override
    public String toString() {
        return "TokenResponse[tokens=<redacted>, accessExpiresAt=" + accessExpiresAt
                + ", refreshExpiresAt=" + refreshExpiresAt + ", user=" + (user == null ? null : user.id()) + "]";
    }
}
