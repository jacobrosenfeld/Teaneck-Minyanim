package com.tbdev.teaneckminyanim.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.tbdev.teaneckminyanim.api.ApiException;
import com.tbdev.teaneckminyanim.api.OpenApiConfig;
import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.admin.MinyanAdminDto;
import com.tbdev.teaneckminyanim.api.dto.admin.MinyanPatchRequest;
import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.model.Minyan;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.security.AdminAction;
import com.tbdev.teaneckminyanim.service.MinyanAdminService;
import com.tbdev.teaneckminyanim.service.MinyanUpdateCommand;
import com.tbdev.teaneckminyanim.service.OrganizationService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Admin API for minyan rules.
 *
 * <pre>
 * GET   /api/v1/organizations/{idOrSlug}/minyanim              list, including disabled (schedule:read)
 * GET   /api/v1/organizations/{idOrSlug}/minyanim/{minyanId}   one minyan (schedule:read)
 * PATCH /api/v1/organizations/{idOrSlug}/minyanim/{minyanId}   enable/disable and/or edit (minyanim:write)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/organizations/{idOrSlug}/minyanim")
@RequiredArgsConstructor
@Tag(name = "Admin: Minyanim", description = "Manage a shul's minyan rules. Requires a bearer token (API key or app token).")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MinyanAdminApiController {

    private final OrganizationService organizationService;
    private final MinyanAdminService minyanAdminService;
    private final ApiAuthorizationService apiAuthorizationService;

    @GetMapping
    @Operation(summary = "List a shul's minyanim (admin view)",
            description = "Every minyan rule of the shul, including disabled ones, with its full schedule. "
                    + "Requires the schedule:read scope and access to the shul.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "The minyanim"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED or INVALID_TOKEN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "INSUFFICIENT_SCOPE or FORBIDDEN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND: unknown or disabled shul")
    })
    public ResponseEntity<ApiResponse<List<MinyanAdminDto>>> list(
            @Parameter(description = "Shul ID or slug", example = "bmob") @PathVariable String idOrSlug) {
        Organization org = authorize(ApiScope.SCHEDULE_READ, idOrSlug);
        List<Minyan> minyanim = minyanAdminService.list(org.getId());
        Map<String, String> locations = minyanAdminService.locationNames(org.getId());
        List<MinyanAdminDto> data = minyanim.stream()
                .map(m -> MinyanAdminDto.from(m, locations.get(m.getLocationId())))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(data, Map.of("count", data.size())));
    }

    @GetMapping("/{minyanId}")
    @Operation(summary = "Get one minyan (admin view)",
            description = "Requires the schedule:read scope and access to the shul.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "The minyan"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED or INVALID_TOKEN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "INSUFFICIENT_SCOPE or FORBIDDEN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND: unknown shul, or no such minyan in this shul")
    })
    public ResponseEntity<ApiResponse<MinyanAdminDto>> get(
            @Parameter(description = "Shul ID or slug", example = "bmob") @PathVariable String idOrSlug,
            @Parameter(description = "Minyan ID") @PathVariable String minyanId) {
        Organization org = authorize(ApiScope.SCHEDULE_READ, idOrSlug);
        Minyan minyan = translate(() -> minyanAdminService.get(org.getId(), minyanId));
        return ResponseEntity.ok(ApiResponse.ok(toDto(org, minyan)));
    }

    @PatchMapping("/{minyanId}")
    @AdminAction({"minyan.update", "minyan.enable", "minyan.disable"})
    @Operation(summary = "Update, enable or disable a minyan",
            description = "Partial update: send only what changes. `enabled` shows or hides the minyan. `schedule` may hold any "
                    + "subset of the 11 slots (sunday, monday, tuesday, wednesday, thursday, friday, shabbos, roshChodesh, yomTov, "
                    + "chanuka, roshChodeshChanuka); slots you leave out keep their value. A slot is FIXED (`time` HH:mm), DYNAMIC or "
                    + "ROUNDED (`zman` and `offsetMinutes` from -240 to 240) or NONE. An empty `notes` or `whatsapp` clears it. "
                    + "The change is all-or-nothing, and the public schedule updates immediately. Requires the minyanim:write scope.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "The updated minyan"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: bad slot, nusach, location or text"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHENTICATED or INVALID_TOKEN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "INSUFFICIENT_SCOPE or FORBIDDEN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND: unknown shul, or no such minyan in this shul")
    })
    public ResponseEntity<ApiResponse<MinyanAdminDto>> patch(
            @Parameter(description = "Shul ID or slug", example = "bmob") @PathVariable String idOrSlug,
            @Parameter(description = "Minyan ID") @PathVariable String minyanId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = MinyanPatchRequest.class)))
            @RequestBody JsonNode rawBody) {
        Organization org = authorize(ApiScope.MINYANIM_WRITE, idOrSlug);
        MinyanPatchRequest body = StrictJsonBody.read(rawBody, MinyanPatchRequest.class);
        MinyanUpdateCommand command = parse(body);

        Minyan result;
        if (body.enabled() == null && !body.hasUpdateFields()) {
            result = translate(() -> minyanAdminService.get(org.getId(), minyanId));
        } else {
            result = translate(() -> minyanAdminService.apply(org.getId(), minyanId, body.enabled(),
                    body.hasUpdateFields() ? command : null));
        }
        return ResponseEntity.ok(ApiResponse.ok(toDto(org, result)));
    }

    private MinyanAdminDto toDto(Organization org, Minyan minyan) {
        Map<String, String> locations = minyanAdminService.locationNames(org.getId());
        return MinyanAdminDto.from(minyan, locations.get(minyan.getLocationId()));
    }

    private static MinyanUpdateCommand parse(MinyanPatchRequest body) {
        try {
            return body.toCommand();
        } catch (IllegalArgumentException e) {
            throw ApiException.validation(e.getMessage());
        }
    }

    /** 401 without a token, then 404 for an unknown or disabled shul, then scope and shul access. */
    private Organization authorize(ApiScope scope, String idOrSlug) {
        apiAuthorizationService.requireScope(scope);
        Organization org = organizationService.findById(idOrSlug)
                .or(() -> organizationService.findByUrlSlug(idOrSlug))
                .filter(Organization::isEnabled)
                .orElseThrow(() -> ApiException.notFound("Organization not found: " + idOrSlug));
        apiAuthorizationService.require(scope, org.getId());
        return org;
    }

    private static <T> T translate(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (MinyanAdminService.MinyanNotFoundException e) {
            throw ApiException.notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw ApiException.validation(e.getMessage());
        }
    }
}
