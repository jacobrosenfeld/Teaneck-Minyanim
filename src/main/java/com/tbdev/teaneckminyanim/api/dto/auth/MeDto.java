package com.tbdev.teaneckminyanim.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** The signed-in account and what the current credential may do. Never carries a password, hash or token. */
@Schema(description = "The account behind the bearer token and the credential's effective permissions.")
public record MeDto(
        @Schema(example = "A1b2C3d4") String id,
        @Schema(example = "gabbai") String username,
        @Schema(example = "gabbai@example.com") String email,
        @Schema(description = "ADMIN or USER. A super admin is an ADMIN with no home shul.", example = "ADMIN")
        String role,
        boolean superAdmin,
        @Schema(description = "Shuls this credential can manage: every shul for a super admin, otherwise the account's "
                + "shuls, narrowed to the key's shul restriction when it has one.")
        List<OrganizationRef> organizations,
        @Schema(description = "APP for a mobile app access token, KEY for a personal API key.", example = "APP")
        String tokenKind,
        @Schema(description = "Effective scopes: every scope for an app token or a same-as-owner key, otherwise the "
                + "key's scopes. The owner's role and shul access still apply on every call.",
                example = "[\"schedule:read\",\"overrides:write\"]")
        List<String> scopes,
        @Schema(description = "True for app tokens and same-as-owner keys.")
        boolean sameAsOwner,
        @Schema(description = "Shul ids an API key is limited to. Empty means no restriction.")
        List<String> restrictedOrganizationIds) {

    @Schema(description = "A shul the credential can manage.")
    public record OrganizationRef(
            @Schema(example = "ORG123") String id,
            @Schema(example = "Congregation Beth Aaron") String name,
            @Schema(example = "beth-aaron") String slug) {
    }
}
