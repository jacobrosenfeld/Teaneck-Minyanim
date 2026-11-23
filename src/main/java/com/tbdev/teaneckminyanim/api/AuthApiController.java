package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.auth.LogoutResponse;
import com.tbdev.teaneckminyanim.api.dto.auth.MagicLinkExchangeRequest;
import com.tbdev.teaneckminyanim.api.dto.auth.MagicLinkRequest;
import com.tbdev.teaneckminyanim.api.dto.auth.MagicLinkRequestResponse;
import com.tbdev.teaneckminyanim.api.dto.auth.MeDto;
import com.tbdev.teaneckminyanim.api.dto.auth.RefreshTokenRequest;
import com.tbdev.teaneckminyanim.api.dto.auth.TokenResponse;
import com.tbdev.teaneckminyanim.model.AppSession;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.AppSessionRepository;
import com.tbdev.teaneckminyanim.security.ApiAuthentication;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.ApiAuthorizationService;
import com.tbdev.teaneckminyanim.service.auth.AppTokenService;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkAuthenticationException;
import com.tbdev.teaneckminyanim.service.auth.MagicLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mobile app sign-in by magic link, token refresh and logout, and {@code /me}. Tokens are issued by
 * {@link AppTokenService}; this controller only parses input and maps results and errors.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Mobile app sign-in (magic link, passkey), token refresh, logout and the current account")
public class AuthApiController {
    static final String INVALID_LINK_MESSAGE = "The sign-in link is invalid or has expired. Request a new one.";
    static final String INVALID_REFRESH_MESSAGE = "The refresh token is invalid, expired or revoked. Sign in again.";

    private final MagicLinkService magicLinkService;
    private final AppTokenService appTokenService;
    private final AppSessionRepository appSessions;
    private final TNMUserService userService;
    private final ApiAuthorizationService authorization;
    private final AuthResponses responses;

    @PostMapping("/auth/magic-link/request")
    @Operation(summary = "Email an app sign-in link",
            description = "Emails a one-time sign-in link for the mobile app: <siteRoot>/app/auth/magic-link?token=... "
                    + "The link opens the app as a universal link (iOS) or app link (Android); the app then calls "
                    + "/api/v1/auth/magic-link/exchange. Links expire after 15 minutes and work once. Always returns 202 "
                    + "with the same message, whether or not the account exists. Rate limited (10/min/IP plus the "
                    + "website's per-address limits).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202",
                    description = "Accepted. A link is sent if the account exists and is enabled."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limited.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<MagicLinkRequestResponse>> requestMagicLink(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The account's email or username.")
            @RequestBody(required = false) MagicLinkRequest body,
            HttpServletRequest request) {
        MagicLinkService.MagicLinkRequestResult result =
                magicLinkService.requestAppLoginLink(body == null ? null : body.identifier(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(new MagicLinkRequestResponse(result.message())));
    }

    @PostMapping("/auth/magic-link/exchange")
    @Operation(summary = "Exchange an app sign-in link for tokens",
            description = "Consumes the one-time token from an app sign-in link and returns an access token "
                    + "(15 minutes) and a rotating refresh token (30 days). A token works once; website sign-in links "
                    + "are not accepted here.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Signed in."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR: the token is missing.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "INVALID_TOKEN: unknown, expired or already used link, or the account cannot sign in.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<TokenResponse>> exchangeMagicLink(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The link's token and an optional device name.")
            @RequestBody(required = false) MagicLinkExchangeRequest body,
            HttpServletRequest request) {
        if (body == null || body.token() == null || body.token().isBlank()) {
            throw ApiException.validation("token is required.");
        }
        TNMUser user;
        try {
            user = magicLinkService.consumeAppLoginToken(body.token().trim());
        } catch (MagicLinkAuthenticationException e) {
            throw invalidLink();
        }
        AppTokenService.IssuedTokens tokens;
        try {
            tokens = appTokenService.issue(user, AppSession.LoginMethod.MAGIC_LINK, body.deviceName(), request);
        } catch (IllegalArgumentException e) {
            throw invalidLink();
        }
        return tokenResponse(tokens, user);
    }

    @PostMapping("/auth/refresh")
    @Operation(summary = "Refresh the app tokens",
            description = "Exchanges a refresh token for a new access token and refresh token. The old refresh token "
                    + "stops working. Presenting an already-used refresh token signs that device out everywhere "
                    + "(the whole sign-in is revoked). Do not send an Authorization header.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "New tokens."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_ERROR: refreshToken is missing.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "INVALID_TOKEN: unknown, expired, revoked or reused refresh token. Sign in again.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The current refresh token.")
            @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletRequest request) {
        if (body == null || body.refreshToken() == null || body.refreshToken().isBlank()) {
            throw ApiException.validation("refreshToken is required.");
        }
        AppTokenService.IssuedTokens tokens = appTokenService.refresh(body.refreshToken().trim(), request)
                .orElseThrow(AuthApiController::invalidRefresh);
        TNMUser user = appSessions.findById(tokens.sessionId())
                .map(AppSession::getAccountId)
                .map(userService::findById)
                .orElseThrow(AuthApiController::invalidRefresh);
        return tokenResponse(tokens, user);
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "Sign the app out",
            description = "Revokes the refresh token's whole sign-in (every token issued from it). Always returns 200; "
                    + "revoked is false when the token is unknown or malformed.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Done.")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The refresh token to revoke.")
            @RequestBody(required = false) RefreshTokenRequest body) {
        boolean revoked = body != null && body.refreshToken() != null && !body.refreshToken().isBlank()
                && appTokenService.logout(body.refreshToken().trim());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(new LogoutResponse(revoked)));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "The current account",
            description = "Returns the account behind the bearer token (app token or API key), the shuls it can manage "
                    + "and the credential's effective scopes.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "The account."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHENTICATED (no bearer token) or INVALID_TOKEN.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<MeDto>> me() {
        TNMUser user = authorization.requireAuthenticated();
        ApiAuthentication auth = authorization.currentAuthentication().orElseThrow(ApiException::unauthenticated);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(responses.forAuthentication(user, auth)));
    }

    private ResponseEntity<ApiResponse<TokenResponse>> tokenResponse(AppTokenService.IssuedTokens tokens, TNMUser user) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(ApiResponse.ok(TokenResponse.of(tokens, responses.forAppToken(user))));
    }

    private static ApiException invalidLink() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ApiException.INVALID_TOKEN, INVALID_LINK_MESSAGE);
    }

    private static ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ApiException.INVALID_TOKEN, INVALID_REFRESH_MESSAGE);
    }
}
