package com.tbdev.teaneckminyanim.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbdev.teaneckminyanim.api.ApiException;
import com.tbdev.teaneckminyanim.api.ApiRateLimitFilter;
import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.service.TNMUserDetailsService;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.ApiKeyService;
import com.tbdev.teaneckminyanim.service.auth.AppTokenService;
import com.tbdev.teaneckminyanim.service.auth.AuthAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.web.filter.CorsFilter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.Filter;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration {
    static final String PASSKEY_RP_ID = "teaneckminyanim.com";
    static final String PASSKEY_PROD_ORIGIN = "https://teaneckminyanim.com";
    static final String PASSKEY_DEV_ORIGIN = "https://dev.teaneckminyanim.com";

    @Autowired
    TNMUserDetailsService userDetailsService;

    @Value("${auth.password-login.enabled:false}")
    private boolean passwordLoginEnabled;

    @Value("${auth.passkeys.enabled:true}")
    private boolean passkeysEnabled;

    /** Comma-separated SHA-256 signing-certificate fingerprints of the Android app, colon-separated hex. */
    @Value("${app.links.android-sha256-fingerprints:}")
    private String androidSha256Fingerprints = "";

    /**
     * Stateless chain for the REST API. It never reads or creates the HTTP session, so a browser admin
     * cookie cannot authorize API calls (which is why CSRF is off here). Requests authenticate with
     * {@code Authorization: Bearer}; URL rules are permitAll and authorization happens in the controllers
     * through {@code ApiAuthorizationService}.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http,
                                              ApiRateLimitFilter rateLimitFilter,
                                              ApiKeyService apiKeyService,
                                              AppTokenService appTokenService,
                                              TNMUserService users,
                                              AuthAuditService auditService,
                                              ObjectMapper objectMapper) throws Exception {
        BearerTokenAuthenticationFilter bearerFilter = new BearerTokenAuthenticationFilter(
                apiKeyService, appTokenService, users, auditService, objectMapper);
        http
            .securityMatcher("/api/v1/**")
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, e) ->
                        writeApiError(response, objectMapper, ApiException.unauthenticated()))
                .accessDeniedHandler((request, response, e) ->
                        writeApiError(response, objectMapper, ApiException.forbidden("Access denied."))))
            .addFilterAfter(rateLimitFilter, CorsFilter.class)
            .addFilterBefore(bearerFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /** The rate limiter runs inside {@link #apiFilterChain}; stop Boot also registering it as a servlet filter. */
    @Bean
    public FilterRegistrationBean<ApiRateLimitFilter> apiRateLimitFilterRegistration(ApiRateLimitFilter filter) {
        FilterRegistrationBean<ApiRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private static void writeApiError(jakarta.servlet.http.HttpServletResponse response, ObjectMapper objectMapper,
                                      ApiException error) throws java.io.IOException {
        response.setStatus(error.getStatus().value());
        if (error.getStatus().value() == 401) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.err(error.getCode(), error.getMessage()));
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           LoginRecaptchaFilter loginRecaptchaFilter,
                                           AdminLoginSuccessHandler adminLoginSuccessHandler,
                                           ApplicationSettingsService settingsService,
                                           com.tbdev.teaneckminyanim.service.TNMUserService users) throws Exception {
        // Never save static asset requests as the post-login redirect target
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(new NegatedRequestMatcher(new OrRequestMatcher(
            new AntPathRequestMatcher("/**/*.js"),
            new AntPathRequestMatcher("/**/*.css"),
            new AntPathRequestMatcher("/**/*.ico"),
            new AntPathRequestMatcher("/**/*.png"),
            new AntPathRequestMatcher("/**/*.svg"),
            new AntPathRequestMatcher("/**/*.woff"),
            new AntPathRequestMatcher("/**/*.woff2")
        )));

        http
            .requestCache(cache -> cache.requestCache(requestCache))
            .sessionManagement(session -> session.sessionFixation(fixation -> fixation.migrateSession()))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/**", "/subscribe"))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/**").permitAll()
                .requestMatchers("/.well-known/**", "/app/auth/**").permitAll()
                .requestMatchers("/api/docs", "/api/docs.json",
                                 "/v3/api-docs", "/v3/api-docs/**").permitAll()
                .requestMatchers("/", "/zmanim/**", "/orgs/**", "/org/**", "/admin/login",
                                "/admin/login/auth-methods", "/admin/login/magic-link",
                                "/admin/login/magic-link/request", "/admin/login/magic-link/verify", "/admin/logout",
                                "/webauthn/authenticate/options", "/login/webauthn",
                                "/webjars/**", "/**/*.css", "/**/*.js", "/static/**", "/db/**",
                                "/assets/**", "/favicon.ico", "/test/errors/**", "/subscribe", "/subscription").permitAll()
                .requestMatchers("/admin", "/admin/dashboard", "/admin/org/*/dashboard", "/admin/organization", "/admin/account",
                                "/admin/update-organization", "/admin/update-account", "/admin/account/notifications",
                                "/admin/*/locations", "/admin/*/locations/**",
                                "/admin/create-location", "/admin/update-location", "/admin/delete-location",
                                "/admin/*/minyanim", "/admin/*/minyanim/**",
                                "/admin/*/calendar-entries", "/admin/*/calendar-entries/**",
                                "/admin/*/calendar-events", "/admin/*/calendar-events/**",
                                "/admin/*/overrides", "/admin/*/overrides/**",
                                "/admin/settings", "/admin/update-settings").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/admin/**").hasAnyRole("ADMIN")
                .requestMatchers("/{slug:[a-z0-9-]+}", "/{slug:[a-z0-9-]+}/next", "/{slug:[a-z0-9-]+}/last").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(loginRecaptchaFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new AccountAccessFilter(users), UsernamePasswordAuthenticationFilter.class)
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/admin/login?logout=true")
                .permitAll()
            );

        if (passkeysEnabled) {
            http.webAuthn(webAuthn -> webAuthn
                .rpId(webAuthnRpId(settingsService))
                .rpName(settingsService.getSiteName())
                .allowedOrigins(webAuthnAllowedOrigins(settingsService))
                .disableDefaultRegistrationPage(true)
                // Sessions are stored in the database, so session attributes must be Serializable.
                .creationOptionsRepository(new SerializablePublicKeyCredentialCreationOptionsRepository())
            );
        }

        if (passwordLoginEnabled) {
            http.formLogin(form -> form
                    .loginProcessingUrl("/j_spring_security_check")
                    .loginPage("/admin/login")
                    .defaultSuccessUrl("/admin")
                    .successHandler(adminLoginSuccessHandler)
                    .failureUrl("/admin/login?error=true")
                    .usernameParameter("username")
                    .passwordParameter("password")
                    .permitAll());
        } else {
            http.exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/admin/login")));
        }

        DefaultSecurityFilterChain chain = http.build();
        applySessionFixationProtectionToPasskeyLogin(chain, http.getSharedObject(SessionAuthenticationStrategy.class));
        return chain;
    }

    /**
     * Spring Security's WebAuthn configurer builds its authentication filter without the session
     * authentication strategy that form login gets, so a passkey sign-in would keep the pre-login
     * session ID. Apply the shared strategy (session ID rotation + CSRF token rotation) so passkey
     * logins get the same session-fixation protection as every other login path.
     */
    static void applySessionFixationProtectionToPasskeyLogin(DefaultSecurityFilterChain chain,
                                                             SessionAuthenticationStrategy sharedStrategy) {
        SessionAuthenticationStrategy strategy = sharedStrategy != null
                ? sharedStrategy
                : new ChangeSessionIdAuthenticationStrategy();
        for (Filter filter : chain.getFilters()) {
            if (filter instanceof WebAuthnAuthenticationFilter webAuthnFilter) {
                webAuthnFilter.setSessionAuthenticationStrategy(strategy);
            }
        }
    }

    /**
     * The WebAuthn relying party shared by browser passkey sign-in ({@code http.webAuthn}, which looks this bean
     * up by type and uses it instead of building its own) and native app passkey sign-in, so one passkey works
     * on the website and in the app.
     */
    @Bean
    public WebAuthnRelyingPartyOperations webAuthnRelyingPartyOperations(
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository userCredentials,
            ApplicationSettingsService settingsService) {
        PublicKeyCredentialRpEntity rp = PublicKeyCredentialRpEntity.builder()
                .id(webAuthnRpId(settingsService))
                .name(settingsService.getSiteName())
                .build();
        return new Webauthn4JRelyingPartyOperations(userEntities, userCredentials, rp,
                webAuthnAllowedOrigins(settingsService));
    }

    @Bean
    public UserCredentialRepository userCredentialRepository(JdbcOperations jdbcOperations,
                                                             WebAuthnCredentialSchemaInitializer schemaInitializer) {
        schemaInitializer.ensureSchema();
        return new JdbcUserCredentialRepository(jdbcOperations);
    }

    /**
     * CORS config for the public REST API (/api/v1/**).
     * Allows public API clients to query data and submit feedback without a proxy.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }

    String webAuthnRpId(ApplicationSettingsService settingsService) {
        String host = normalizedHost(siteRootUri(settingsService));
        if (isTeaneckMinyanimHost(host)) {
            return PASSKEY_RP_ID;
        }
        return host;
    }

    Set<String> webAuthnAllowedOrigins(ApplicationSettingsService settingsService) {
        URI uri = siteRootUri(settingsService);
        Set<String> origins = new LinkedHashSet<>();
        if (isTeaneckMinyanimHost(normalizedHost(uri))) {
            origins.add(PASSKEY_PROD_ORIGIN);
            origins.add(PASSKEY_DEV_ORIGIN);
        }
        origins.add(origin(uri));
        origins.addAll(androidApkKeyHashOrigins(androidSha256Fingerprints));
        return origins;
    }

    /**
     * Android passkey origins: {@code android:apk-key-hash:<base64url, no padding, of the SHA-256 signing
     * certificate fingerprint>} for each comma-separated colon-hex fingerprint. Invalid entries are skipped.
     */
    static Set<String> androidApkKeyHashOrigins(String fingerprints) {
        Set<String> origins = new LinkedHashSet<>();
        if (fingerprints == null || fingerprints.isBlank()) {
            return origins;
        }
        for (String fingerprint : fingerprints.split(",")) {
            String hex = fingerprint.trim().replace(":", "");
            if (hex.isEmpty()) {
                continue;
            }
            try {
                byte[] bytes = HexFormat.of().parseHex(hex);
                if (bytes.length != 32) {
                    throw new IllegalArgumentException("expected 32 bytes");
                }
                origins.add("android:apk-key-hash:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            } catch (IllegalArgumentException e) {
                org.slf4j.LoggerFactory.getLogger(WebSecurityConfiguration.class)
                        .warn("Ignoring invalid Android SHA-256 fingerprint in app.links.android-sha256-fingerprints");
            }
        }
        return origins;
    }

    private String origin(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = normalizedHost(uri);
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return scheme + "://" + host + port;
    }

    private boolean isTeaneckMinyanimHost(String host) {
        return PASSKEY_RP_ID.equals(host) || host.endsWith("." + PASSKEY_RP_ID);
    }

    private String normalizedHost(URI uri) {
        return uri.getHost().toLowerCase(Locale.ROOT);
    }

    private URI siteRootUri(ApplicationSettingsService settingsService) {
        try {
            URI uri = new URI(settingsService.getSiteRootUrl());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return URI.create("http://localhost:8080");
            }
            return uri;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return URI.create("http://localhost:8080");
        }
    }
}
