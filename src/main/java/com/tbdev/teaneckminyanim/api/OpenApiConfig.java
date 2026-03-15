package com.tbdev.teaneckminyanim.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI metadata shown in the Swagger UI at /api/docs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Teaneck Minyanim API")
                        .version("v1")
                        .description("""
                                Public REST API for Teaneck Minyanim.

                                All endpoints are read-only and require no authentication.
                                Rate limit: 60 requests per minute per IP.
                                All dates use ISO-8601 (YYYY-MM-DD). All times use HH:mm (America/New_York).

                                The schedule is pre-materialized in a rolling 11-week window (3 past, 8 future).
                                Use `meta.windowStart` / `meta.windowEnd` from any schedule response to
                                know the queryable date range.
                                """)
                        .contact(new Contact()
                                .name("Teaneck Minyanim")
                                .url("https://github.com/jacobrosenfeld/Teaneck-Minyanim")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development"),
                        new Server().url("https://minyanim.teaneck.org").description("Production")
                ));
    }
}
