package com.tbdev.teaneckminyanim.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.auth.PasskeyOptionsResponse;
import com.tbdev.teaneckminyanim.api.dto.auth.PasskeyVerifyRequest;
import com.tbdev.teaneckminyanim.api.dto.auth.TokenResponse;
import com.tbdev.teaneckminyanim.service.auth.NativePasskeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Native passkey sign-in for the mobile app: JSON WebAuthn options and verification that return app tokens.
 * Uses the same relying party as the website, so one passkey works on both.
 */
@RestController
@RequestMapping("/api/v1/auth/passkey")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class PasskeyAuthApiController {
    static final String FAILED_MESSAGE = "Passkey sign-in failed. Try again or use an email sign-in link.";

    private final NativePasskeyService passkeys;
    private final AuthResponses responses;
    private final ObjectMapper objectMapper;

    @PostMapping("/options")
    @Operation(summary = "Start a passkey sign-in",
            description = "Returns a single-use challenge (valid for 5 minutes) and PublicKeyCredentialRequestOptions "
                    + "JSON in the same shape as the website's /webauthn/authenticate/options. allowCredentials is "
                    + "empty, so the phone offers any passkey saved for this site. Pass publicKey to the native passkey "
                    + "API (iOS ASAuthorizationPlatformPublicKeyCredentialProvider, Android Credential Manager), then "
                    + "post the result to /api/v1/auth/passkey/verify. No request body.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "A new challenge."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "NOT_FOUND: passkey sign-in is turned off on this server.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<PasskeyOptionsResponse>> options() throws Exception {
        requireEnabled();
        NativePasskeyService.Challenge challenge = passkeys.createChallenge();
        JsonNode publicKey = objectMapper.readTree(challenge.optionsJson());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(new PasskeyOptionsResponse(challenge.challengeId(), publicKey)));
    }

    @PostMapping("/verify")
    @Operation(summary = "Finish a passkey sign-in",
            description = "Verifies the native passkey API's assertion against the challenge and returns an access "
                    + "token and a rotating refresh token. The challenge is used up by this call whether or not it "
                    + "succeeds.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Signed in."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR: challengeId or credential is missing or not a PublicKeyCredential.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "INVALID_TOKEN: unknown, expired or used challenge, a passkey that does not verify, "
                            + "or an account that cannot sign in.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "NOT_FOUND: passkey sign-in is turned off on this server.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<TokenResponse>> verify(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The challengeId, the native PublicKeyCredential JSON and an optional device name.")
            @RequestBody(required = false) PasskeyVerifyRequest body,
            HttpServletRequest request) {
        requireEnabled();
        if (body == null) {
            throw ApiException.validation("challengeId and credential are required.");
        }
        NativePasskeyService.VerifiedSignIn signIn;
        try {
            signIn = passkeys.verify(body.challengeId(), body.credential(), body.deviceName(), request);
        } catch (NativePasskeyService.PasskeyVerificationException e) {
            if (e.isMalformed()) {
                throw ApiException.validation("challengeId and a valid PublicKeyCredential credential are required.");
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, ApiException.INVALID_TOKEN, FAILED_MESSAGE);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(ApiResponse.ok(TokenResponse.of(signIn.tokens(), responses.forAppToken(signIn.user()))));
    }

    private void requireEnabled() {
        if (!passkeys.isEnabled()) {
            throw ApiException.notFound("Passkey sign-in is not available.");
        }
    }
}
