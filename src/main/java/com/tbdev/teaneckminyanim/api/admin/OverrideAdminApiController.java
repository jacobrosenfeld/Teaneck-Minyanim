package com.tbdev.teaneckminyanim.api.admin;

import com.tbdev.teaneckminyanim.api.ApiException;
import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.admin.OverrideCreateRequest;
import com.tbdev.teaneckminyanim.api.dto.admin.OverrideDeletedDto;
import com.tbdev.teaneckminyanim.api.dto.admin.OverrideDto;
import com.tbdev.teaneckminyanim.api.dto.admin.OverridePatchRequest;
import com.tbdev.teaneckminyanim.api.OpenApiConfig;
import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.security.AdminAction;
import com.tbdev.teaneckminyanim.service.ManualOverrideService;
import com.tbdev.teaneckminyanim.service.NotManualOverrideException;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.OverrideNotFoundException;
import com.tbdev.teaneckminyanim.service.auth.ApiAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Admin API for manual schedule overrides. A thin wrapper around {@link ManualOverrideService}, which does the
 * validation, the per-shul authorization and the writes (the admin panel uses the same service).
 *
 * <p>Manual rows are read live by the public schedule, so every change here shows up in
 * {@code /api/v1/organizations/{id}/schedule} immediately.
 */
@RestController
@RequestMapping("/api/v1/organizations/{idOrSlug}/overrides")
@RequiredArgsConstructor
@Tag(name = "Admin: Overrides",
        description = "Manual schedule overrides for a shul. Requires a bearer token; writes need the overrides:write scope.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class OverrideAdminApiController {

    private final OrganizationService organizationService;
    private final ApiAuthorizationService apiAuthorizationService;
    private final ManualOverrideService manualOverrideService;

    @GetMapping
    @Operation(summary = "List manual overrides",
            description = "Manual overrides of this shul with a date in start..end (inclusive, at most 62 days), "
                    + "ordered by date then time. Includes disabled rows. Requires the schedule:read scope.")
    public ResponseEntity<ApiResponse<List<OverrideDto>>> list(
            @Parameter(description = "Shul ID or slug", example = "bmob") @PathVariable String idOrSlug,
            @Parameter(description = "First date, ISO YYYY-MM-DD", example = "2026-12-01", required = true)
            @RequestParam(required = false) String start,
            @Parameter(description = "Last date, ISO YYYY-MM-DD", example = "2026-12-31", required = true)
            @RequestParam(required = false) String end) {
        Organization org = resolve(idOrSlug);
        apiAuthorizationService.require(ApiScope.SCHEDULE_READ, org.getId());

        LocalDate from = parseDate(start, "start");
        LocalDate to = parseDate(end, "end");
        List<OverrideDto> rows = guard(() -> manualOverrideService.list(org.getId(), from, to))
                .stream().map(OverrideDto::from).toList();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("start", from.toString());
        meta.put("end", to.toString());
        meta.put("count", rows.size());
        return ResponseEntity.ok(ApiResponse.ok(rows, meta));
    }

    @PostMapping
    @AdminAction("override.create")
    @Operation(summary = "Create manual overrides",
            description = "Creates one override per day from date to endDate (inclusive, at most 62 days). "
                    + "overrideMode ADDITIVE (default) adds to the day's schedule; FULL_DAY_REPLACE makes the day show "
                    + "only manual overrides. Creating the same date/type/time again updates that row in place. "
                    + "Requires the overrides:write scope.")
    public ResponseEntity<ApiResponse<List<OverrideDto>>> create(
            @Parameter(description = "Shul ID or slug", example = "bmob") @PathVariable String idOrSlug,
            @RequestBody OverrideCreateRequest body) {
        Organization org = resolve(idOrSlug);
        TNMUser user = apiAuthorizationService.require(ApiScope.OVERRIDES_WRITE, org.getId());
        if (body == null) {
            throw ApiException.validation("A JSON request body is required");
        }

        ManualOverrideService.CreateOverrideCommand command = new ManualOverrideService.CreateOverrideCommand(
                parseRequiredDate(body.date(), "date"),
                body.endDate() == null || body.endDate().isBlank() ? null : parseDate(body.endDate(), "endDate"),
                parseRequiredTime(body.startTime()),
                body.minyanType(), body.overrideMode(), body.locationId(), body.locationName(),
                body.notes(), body.nusach(), body.enabled());

        ManualOverrideService.CreateResult result =
                guard(() -> manualOverrideService.create(org.getId(), command, user.getUsername()));
        List<OverrideDto> rows = result.events().stream().map(OverrideDto::from).toList();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("count", rows.size());
        meta.put("created", result.created());
        meta.put("updated", result.updated());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(rows, meta));
    }

    @PatchMapping("/{eventId}")
    @AdminAction({"override.update", "override.toggle"})
    @Operation(summary = "Update a manual override",
            description = "Partial update of startTime, overrideMode, notes, locationId, locationName, nusach and/or "
                    + "enabled. Omitted fields are unchanged; an empty string clears notes, locationId, locationName "
                    + "or nusach. Setting enabled is the toggle. Only MANUAL rows can be changed: a rules-generated or "
                    + "imported row returns 409 NOT_MANUAL_OVERRIDE. Requires the overrides:write scope.")
    public ResponseEntity<ApiResponse<OverrideDto>> update(
            @Parameter(description = "Shul ID or slug", example = "bmob") @PathVariable String idOrSlug,
            @Parameter(description = "Override ID (the id of an override from the list/create responses)",
                    example = "4821") @PathVariable String eventId,
            @RequestBody OverridePatchRequest body) {
        Organization org = resolve(idOrSlug);
        TNMUser user = apiAuthorizationService.require(ApiScope.OVERRIDES_WRITE, org.getId());
        if (body == null || body.isEmpty()) {
            throw ApiException.validation("Provide at least one field to update");
        }

        ManualOverrideService.UpdateOverrideCommand command = new ManualOverrideService.UpdateOverrideCommand(
                body.startTime() == null ? null : parseRequiredTime(body.startTime()),
                body.overrideMode(), body.notes(), body.locationId(), body.locationName(),
                body.nusach(), body.enabled());

        CalendarEvent updated =
                guard(() -> manualOverrideService.update(org.getId(), eventId, command, user.getUsername()));
        return ResponseEntity.ok(ApiResponse.ok(OverrideDto.from(updated)));
    }

    @DeleteMapping("/{eventId}")
    @AdminAction("override.delete")
    @Operation(summary = "Delete a manual override",
            description = "Permanently deletes one MANUAL override. Rules-generated and imported rows cannot be "
                    + "deleted (409 NOT_MANUAL_OVERRIDE); disable them from the admin panel instead. "
                    + "Requires the overrides:write scope.")
    public ResponseEntity<ApiResponse<OverrideDeletedDto>> delete(
            @Parameter(description = "Shul ID or slug", example = "bmob") @PathVariable String idOrSlug,
            @Parameter(description = "Override ID", example = "4821") @PathVariable String eventId) {
        Organization org = resolve(idOrSlug);
        apiAuthorizationService.require(ApiScope.OVERRIDES_WRITE, org.getId());

        guard(() -> {
            manualOverrideService.delete(org.getId(), eventId);
            return null;
        });
        return ResponseEntity.ok(ApiResponse.ok(new OverrideDeletedDto(true, eventId.trim())));
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Same lookup as OrganizationApiController: ID first, then slug; unknown or disabled is a 404. */
    private Organization resolve(String idOrSlug) {
        Optional<Organization> orgOpt = organizationService.findById(idOrSlug);
        if (orgOpt.isEmpty()) {
            orgOpt = organizationService.findByUrlSlug(idOrSlug);
        }
        return orgOpt.filter(Organization::isEnabled)
                .orElseThrow(() -> ApiException.notFound("Organization not found: " + idOrSlug));
    }

    /** Maps the service's domain exceptions onto API error codes. */
    private static <T> T guard(Supplier<T> call) {
        try {
            return call.get();
        } catch (IllegalArgumentException e) {
            throw ApiException.validation(e.getMessage());
        } catch (OverrideNotFoundException e) {
            throw ApiException.notFound(e.getMessage());
        } catch (NotManualOverrideException e) {
            throw ApiException.conflict(ApiException.NOT_MANUAL_OVERRIDE, e.getMessage());
        } catch (AccessDeniedException e) {
            throw ApiException.forbidden("You do not have access to this shul.");
        }
    }

    private static LocalDate parseRequiredDate(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ApiException.validation(field + " is required (YYYY-MM-DD)");
        }
        return parseDate(value, field);
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ApiException.validation(field + " is required (YYYY-MM-DD)");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.validation(field + " must be an ISO date (YYYY-MM-DD)");
        }
    }

    private static LocalTime parseRequiredTime(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.validation("startTime is required (HH:mm)");
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.validation("startTime must be 24-hour HH:mm");
        }
    }
}
