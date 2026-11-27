package com.tbdev.teaneckminyanim.api.dto.auth;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/** Body of {@code POST /api/v1/auth/passkey/verify}. */
@Schema(description = "The native passkey API's assertion for a challenge from /api/v1/auth/passkey/options.")
public record PasskeyVerifyRequest(
        @Schema(description = "The challengeId from /api/v1/auth/passkey/options.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String challengeId,
        @Schema(description = "The PublicKeyCredential JSON from the native passkey API (the same JSON the website "
                + "posts to /login/webauthn): id, rawId, type, response {authenticatorData, clientDataJSON, "
                + "signature, userHandle}, clientExtensionResults, authenticatorAttachment. Binary fields are base64url.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "{\"id\":\"AbC...\",\"rawId\":\"AbC...\",\"type\":\"public-key\",\"response\":{"
                        + "\"authenticatorData\":\"...\",\"clientDataJSON\":\"...\",\"signature\":\"...\","
                        + "\"userHandle\":\"...\"},\"clientExtensionResults\":{},\"authenticatorAttachment\":\"platform\"}")
        JsonNode credential,
        @Schema(description = "Optional device label shown to admins, up to 100 characters.", example = "Moshe's Pixel")
        String deviceName) {
}
