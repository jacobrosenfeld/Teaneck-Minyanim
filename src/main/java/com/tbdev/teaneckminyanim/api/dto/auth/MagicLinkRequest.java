package com.tbdev.teaneckminyanim.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body of {@code POST /api/v1/auth/magic-link/request}. */
@Schema(description = "Ask for a sign-in link for the mobile app.")
public record MagicLinkRequest(
        @Schema(description = "The account's email address or username.", example = "gabbai@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String identifier) {
}
