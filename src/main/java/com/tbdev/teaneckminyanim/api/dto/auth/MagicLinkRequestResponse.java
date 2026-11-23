package com.tbdev.teaneckminyanim.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** Neutral response of {@code POST /api/v1/auth/magic-link/request}. It never says whether the account exists. */
@Schema(description = "Always the same message, whether or not the account exists.")
public record MagicLinkRequestResponse(
        @Schema(example = "If this email is authorized, a sign-in link will be sent shortly.")
        String message) {
}
