package com.tbdev.teaneckminyanim.api.dto.auth;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/** Response of {@code POST /api/v1/auth/passkey/options}. */
@Schema(description = "A passkey sign-in challenge for the native passkey API.")
public record PasskeyOptionsResponse(
        @Schema(description = "Send this back to /api/v1/auth/passkey/verify. Single use, valid for 5 minutes.")
        String challengeId,
        @Schema(description = "PublicKeyCredentialRequestOptions JSON, the same shape as the website's "
                + "/webauthn/authenticate/options. Pass it to the native passkey API unchanged.",
                example = "{\"challenge\":\"q2Vd...\",\"timeout\":300000,\"rpId\":\"teaneckminyanim.com\","
                        + "\"allowCredentials\":[],\"userVerification\":\"preferred\",\"extensions\":{}}")
        JsonNode publicKey) {
}
