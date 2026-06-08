package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.service.TNMUserDetailsService;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.net.URISyntaxException;
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
                                           ApplicationSettingsService settingsService) throws Exception {
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
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/**").permitAll()
                .requestMatchers("/api/docs", "/api/docs.json",
                                 "/v3/api-docs", "/v3/api-docs/**").permitAll()
                .requestMatchers("/", "/zmanim/**", "/orgs/**", "/org/**", "/admin/login",
                                "/admin/login/auth-methods", "/admin/login/magic-link",
                                "/admin/login/magic-link/request", "/admin/login/magic-link/verify", "/admin/logout",
                                "/webauthn/authenticate/options", "/login/webauthn",
                                "/webjars/**", "/**/*.css", "/**/*.js", "/static/**", "/db/**",
                                "/assets/**", "/favicon.ico", "/test/errors/**", "/subscribe", "/subscription").permitAll()
                .requestMatchers("/admin", "/admin/dashboard", "/admin/organization", "/admin/account", 
                                "/admin/update-organization", "/admin/update-account", 
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
            .formLogin(form -> form
                .loginProcessingUrl("/j_spring_security_check")
                .loginPage("/admin/login")
                .defaultSuccessUrl("/admin")
                .successHandler(adminLoginSuccessHandler)
                .failureUrl("/admin/login?error=true")
                .usernameParameter("username")
                .passwordParameter("password")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/admin/login?logout=true")
                .permitAll()
            )
            .webAuthn(webAuthn -> webAuthn
                .rpId(webAuthnRpId(settingsService))
                .rpName(settingsService.getSiteName())
                .allowedOrigins(webAuthnAllowedOrigins(settingsService))
                .disableDefaultRegistrationPage(true)
            );

        return http.build();
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
