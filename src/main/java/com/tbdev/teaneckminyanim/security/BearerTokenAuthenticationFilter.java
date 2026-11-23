package com.tbdev.teaneckminyanim.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbdev.teaneckminyanim.api.ApiException;
import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.model.ApiKey;
import com.tbdev.teaneckminyanim.model.AppSession;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.ApiKeyService;
import com.tbdev.teaneckminyanim.service.auth.AppTokenService;
import com.tbdev.teaneckminyanim.service.auth.AuthAuditService;
import com.tbdev.teaneckminyanim.service.auth.AuthRequests;
import com.tbdev.teaneckminyanim.service.auth.SecureTokens;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Authenticates {@code /api/v1} requests from {@code Authorization: Bearer <token>}.
 * <ul>
 *   <li>No Authorization header (or a non-Bearer scheme): passes through anonymously, so public reads work.</li>
 *   <li>{@code tnm_...}: an API key. {@code tnmapp_...}: a mobile access token. Anything else, or an unknown,
 *       expired or revoked token, or a token whose account is missing, disabled or role-less: 401
 *       {@code INVALID_TOKEN} with {@code WWW-Authenticate: Bearer}, audited as TOKEN_REJECTED.</li>
 *   <li>Success: sets an {@link ApiAuthentication} and audits API_KEY_USED / APP_TOKEN_USED.</li>
 * </ul>
 * Not a Spring bean on purpose, so it is never registered as a servlet filter outside the API chain.
 */
@Slf4j
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER = "bearer ";

    private final ApiKeyService apiKeyService;
    private final AppTokenService appTokenService;
    private final TNMUserService userService;
    private final AuthAuditService auditService;
    private final ObjectMapper objectMapper;
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public BearerTokenAuthenticationFilter(ApiKeyService apiKeyService, AppTokenService appTokenService,
                                           TNMUserService userService, AuthAuditService auditService,
                                           ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.appTokenService = appTokenService;
        this.userService = userService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank() || !header.toLowerCase(Locale.ROOT).startsWith(BEARER.trim())) {
            chain.doFilter(request, response);
            return;
        }
        String ipHash = AuthRequests.clientIpHash(request);
        if (!header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            reject(request, response, null, ipHash, "malformed header");
            return;
        }
        String token = header.substring(BEARER.length()).trim();

        ApiAuthentication authentication;
        if (token.startsWith(SecureTokens.API_KEY_PREFIX)) {
            Optional<ApiKey> key = apiKeyService.resolve(token, request);
            if (key.isEmpty()) {
                reject(request, response, null, ipHash, "api key: unknown or revoked");
                return;
            }
            TNMUser owner = usableUser(key.get().getAccountId());
            if (owner == null) {
                reject(request, response, key.get().getAccountId(), ipHash, "api key: owner unusable key=" + key.get().getId());
                return;
            }
            authentication = new ApiAuthentication(ApiAuthentication.Kind.KEY, owner.getId(), owner.getUsername(),
                    owner.role().getName(), key.get().getScopes(), key.get().isSameAsOwner(),
                    key.get().getRestrictedOrganizationIds(), key.get().getId());
            auditService.record("API_KEY_USED", owner.getId(), ipHash,
                    "key=" + key.get().getId() + " " + request.getMethod() + " " + request.getRequestURI());
        } else if (token.startsWith(SecureTokens.ACCESS_TOKEN_PREFIX)) {
            Optional<AppSession> session = appTokenService.resolveAccess(token);
            if (session.isEmpty()) {
                reject(request, response, null, ipHash, "app token: unknown, expired or revoked");
                return;
            }
            TNMUser user = usableUser(session.get().getAccountId());
            if (user == null) {
                reject(request, response, session.get().getAccountId(), ipHash,
                        "app token: account unusable session=" + session.get().getId());
                return;
            }
            authentication = ApiAuthentication.forAppSession(user.getId(), user.getUsername(), user.role().getName(),
                    session.get().getId());
            auditService.record("APP_TOKEN_USED", user.getId(), ipHash,
                    "session=" + session.get().getId() + " " + request.getMethod() + " " + request.getRequestURI());
        } else {
            // Includes refresh tokens (tnmrt_), which are never accepted as bearer tokens.
            reject(request, response, null, ipHash, "unrecognised token type");
            return;
        }

        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        chain.doFilter(request, response);
    }

    private TNMUser usableUser(String accountId) {
        TNMUser user = accountId == null ? null : userService.findById(accountId);
        if (user == null || user.isDisabled() || user.getRoleId() == null || user.role() == null
                || user.role().getName() == null) {
            return null;
        }
        return user;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String accountId, String ipHash,
                        String reason) throws IOException {
        contextHolder.clearContext();
        auditService.record("TOKEN_REJECTED", accountId, ipHash,
                reason + " " + request.getMethod() + " " + request.getRequestURI());
        ApiException error = ApiException.invalidToken();
        response.setStatus(error.getStatus().value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.err(error.getCode(), error.getMessage()));
    }
}
