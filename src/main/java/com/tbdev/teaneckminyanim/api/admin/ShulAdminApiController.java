package com.tbdev.teaneckminyanim.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.tbdev.teaneckminyanim.api.ApiException;
import com.tbdev.teaneckminyanim.api.OpenApiConfig;
import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.admin.ShulAdminDto;
import com.tbdev.teaneckminyanim.api.dto.admin.ShulPatchRequest;
import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.security.AdminAction;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.ShulProfileCommand;
import com.tbdev.teaneckminyanim.service.ShulProfileService;
import com.tbdev.teaneckminyanim.service.auth.ApiAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API for a shul's profile.
 *
 * <pre>PATCH /api/v1/organizations/{idOrSlug}   edit the profile (shul:write)</pre>
 *
 * The public {@code GET} on the same path lives in {@code OrganizationApiController}.
 */
@RestController
@RequestMapping("/api/v1/organizations/{idOrSlug}")
@RequiredArgsConstructor
@Tag(name = "Admin: Shul", description = "Edit a shul's profile. Requires a bearer token (API key or app token).")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ShulAdminApiController {

    private final OrganizationService organizationService;
    private final ShulProfileService shulProfileService;
    private final ApiAuthorizationService apiAuthorizationService;

    @PatchMapping
    @AdminAction("shul.update")
    @Operation(summary = "Update a shul's profile",
            description = "Partial update: send only what changes. An empty string clears address, websiteUrl, whatsapp or "
                    + "calendarUrl; an empty slug generates one from the name. slug is lowercase letters, numbers and hyphens, "
                    + "and must be unique. color is a hex color such as #275ed8. calendarUrl must be http or https. "
                    + "Requires the shul:write scope. The updated profile shows up in the public GET immediately.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "The updated profile"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: blank name, bad URL, bad or duplicate slug, bad color or nusach"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED or INVALID_TOKEN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "INSUFFICIENT_SCOPE or FORBIDDEN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND: unknown or disabled shul")
    })
    public ResponseEntity<ApiResponse<ShulAdminDto>> patch(
            @Parameter(description = "Shul ID or slug", example = "bmob") @PathVariable String idOrSlug,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = ShulPatchRequest.class)))
            @RequestBody JsonNode rawBody) {
        apiAuthorizationService.requireScope(ApiScope.SHUL_WRITE);
        Organization org = organizationService.findById(idOrSlug)
                .or(() -> organizationService.findByUrlSlug(idOrSlug))
                .filter(Organization::isEnabled)
                .orElseThrow(() -> ApiException.notFound("Organization not found: " + idOrSlug));
        apiAuthorizationService.require(ApiScope.SHUL_WRITE, org.getId());
        ShulPatchRequest body = StrictJsonBody.read(rawBody, ShulPatchRequest.class);

        try {
            ShulProfileCommand command = body.toCommand();
            Organization updated = shulProfileService.update(org.getId(), command);
            return ResponseEntity.ok(ApiResponse.ok(ShulAdminDto.from(updated)));
        } catch (ShulProfileService.ShulNotFoundException e) {
            throw ApiException.notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw ApiException.validation(e.getMessage());
        }
    }
}
