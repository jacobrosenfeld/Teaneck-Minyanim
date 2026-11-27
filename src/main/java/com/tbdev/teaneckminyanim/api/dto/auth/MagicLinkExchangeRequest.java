package com.tbdev.teaneckminyanim.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body of {@code POST /api/v1/auth/magic-link/exchange}. */
@Schema(description = "Exchange the one-time token from an app sign-in link for app tokens.")
public record MagicLinkExchangeRequest(
        @Schema(description = "The token query parameter of the emailed /app/auth/magic-link link.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String token,
        @Schema(description = "Optional device label shown to admins, up to 100 characters.", example = "Moshe's iPhone")
        String deviceName) {
}
