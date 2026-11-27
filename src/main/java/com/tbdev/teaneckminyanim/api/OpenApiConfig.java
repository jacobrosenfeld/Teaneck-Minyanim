package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.enums.ApiScope;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenAPI metadata — spec is served at /api/docs.json and rendered by Scalar at /api/docs.
 */
@Configuration
public class OpenApiConfig {

    /** Name of the bearer security scheme; reference it with {@code @SecurityRequirement(name = "bearerAuth")}. */
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("A mobile app access token (`tnmapp_...`) or a personal API key (`tnm_...`).")))
                .info(new Info()
                        .title("Teaneck Minyanim API")
                        .version("v1")
                        .description(description())
                        .contact(new Contact()
                                .name("Teaneck Minyanim")
                                .url("https://github.com/jacobrosenfeld/Teaneck-Minyanim")))
                .servers(List.of(
                        new Server().url("https://teaneckminyanim.com").description("Production"),
                        new Server().url("https://dev.teaneckminyanim.com").description("Dev"),
                        new Server().url("http://localhost:8080").description("Local development")
                ));
    }

    static String description() {
        String scopes = Arrays.stream(ApiScope.values())
                .map(scope -> "- `" + scope.value() + "`: " + scope.description())
                .collect(Collectors.joining("\n"));
        return """
                REST API for Teaneck Minyanim.

                **Public reads** (schedules, shuls, zmanim, notifications) need no authentication.

                **Admin endpoints** need `Authorization: Bearer <token>`, where the token is either:
                - a mobile app access token (`tnmapp_...`, from the app sign-in endpoints; valid 15 minutes,
                  renewed with a rotating refresh token), which carries the signed-in user's full permissions, or
                - a personal API key (`tnm_...`, created by a super admin), which carries "same as owner" or
                  specific scopes and can be limited to some of the owner's shuls.

                A request can never do more than the token's owner can do right now. Missing credentials return
                401 `UNAUTHENTICATED`; a bad, expired or revoked token returns 401 `INVALID_TOKEN`; a missing scope
                returns 403 `INSUFFICIENT_SCOPE`; a shul outside the token's access returns 403 `FORBIDDEN`.

                API key scopes:
                %s

                Rate limits per IP: 60 reads/min, 30 writes/min, 10 sign-in requests/min, 5 feedback submissions/min.
                All dates use ISO-8601 (YYYY-MM-DD). All times use HH:mm (America/New_York).

                The schedule is pre-materialized in a rolling 11-week window (3 past, 8 future).
                Use `meta.windowStart` / `meta.windowEnd` from any schedule response to
                know the queryable date range.
                """.formatted(scopes);
    }
}
