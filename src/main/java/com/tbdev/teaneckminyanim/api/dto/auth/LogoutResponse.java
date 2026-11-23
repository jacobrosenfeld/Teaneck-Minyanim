package com.tbdev.teaneckminyanim.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response of {@code POST /api/v1/auth/logout}. */
@Schema(description = "Whether a sign-in was found and revoked. False for an unknown or malformed token.")
public record LogoutResponse(boolean revoked) {
}
