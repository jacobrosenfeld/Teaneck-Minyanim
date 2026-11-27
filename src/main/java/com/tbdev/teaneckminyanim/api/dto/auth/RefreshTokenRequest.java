package com.tbdev.teaneckminyanim.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body of {@code POST /api/v1/auth/refresh} and {@code POST /api/v1/auth/logout}. */
@Schema(description = "A refresh token (tnmrt_...).")
public record RefreshTokenRequest(
        @Schema(description = "The refresh token from the latest sign-in or refresh.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken) {
}
