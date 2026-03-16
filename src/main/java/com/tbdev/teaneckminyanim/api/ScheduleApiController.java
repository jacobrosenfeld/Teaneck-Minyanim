package com.tbdev.teaneckminyanim.api;

import com.tbdev.teaneckminyanim.api.dto.ApiResponse;
import com.tbdev.teaneckminyanim.api.dto.ScheduleEventDto;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationService.WindowBounds;
import com.tbdev.teaneckminyanim.service.EffectiveScheduleService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.ScheduleEnrichmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Public REST API for the materialized minyan schedule.
 *
 * Combined (all orgs):
 *   GET /api/v1/schedule?date=YYYY-MM-DD
 *   GET /api/v1/schedule?start=YYYY-MM-DD&end=YYYY-MM-DD   (max 14 days)
 *
 * Per-org:
 *   GET /api/v1/organizations/{id}/schedule?date=YYYY-MM-DD
 *   GET /api/v1/organizations/{id}/schedule?start=YYYY-MM-DD&end=YYYY-MM-DD  (max 30 days)
 *
 * Design notes:
 * - No /next or /last endpoints — callers supply explicit dates for predictable, cacheable responses.
 * - Flat event list, sorted by date then startTime. Client groups by date / type as needed.
 * - Org info is embedded in each event to avoid waterfall requests from the mobile app.
 * - The materialization window is surfaced in meta so the app knows query limits.
 */
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
@Tag(name = "Schedule", description = "Materialized minyan schedule — combined (all orgs) or per-org")
public class ScheduleApiController {

    /** Maximum date range for the combined (all-orgs) endpoint. */
    private static final int MAX_COMBINED_DAYS = 14;
    /** Maximum date range for the per-org endpoint. */
    private static final int MAX_ORG_DAYS = 30;

    private final EffectiveScheduleService effectiveScheduleService;
    private final OrganizationService organizationService;
    private final ScheduleEnrichmentService enrichmentService;

    // -----------------------------------------------------------------------
    // Combined schedule (all orgs) — powers the app's "Today" / "Week" views
    // -----------------------------------------------------------------------

    @GetMapping("/api/v1/schedule")
    @Operation(summary = "Combined schedule — all orgs",
               description = "Returns effective minyan events across all organizations for a date or date range " +
                             "(max 14 days). Omit all params to get today. " +
                             "Day-level IMPORTED→RULES precedence is applied server-side. " +
                             "meta includes windowStart/windowEnd so the app knows the queryable range.")
    public ResponseEntity<ApiResponse<List<ScheduleEventDto>>> getCombinedSchedule(
            @Parameter(description = "Single date (YYYY-MM-DD). Shorthand for start=date&end=date.", example = "2026-03-15")
            @RequestParam(required = false) String date,
            @Parameter(description = "Range start date (YYYY-MM-DD)", example = "2026-03-15")
            @RequestParam(required = false) String start,
            @Parameter(description = "Range end date (YYYY-MM-DD), max 14 days after start", example = "2026-03-21")
            @RequestParam(required = false) String end) {

        // Resolve date range
        LocalDate from, to;
        try {
            if (date != null) {
                from = to = LocalDate.parse(date);
            } else if (start != null && end != null) {
                from = LocalDate.parse(start);
                to = LocalDate.parse(end);
            } else {
                from = to = LocalDate.now();
            }
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("INVALID_DATE", "Use ISO-8601 format: YYYY-MM-DD"));
        }

        if (from.isAfter(to)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("INVALID_RANGE", "start must be before or equal to end"));
        }
        if (to.toEpochDay() - from.toEpochDay() > MAX_COMBINED_DAYS) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("RANGE_TOO_LARGE",
                            "Combined schedule range cannot exceed " + MAX_COMBINED_DAYS + " days"));
        }

        WindowBounds window = effectiveScheduleService.getWindowBounds();
        if (from.isBefore(window.getStartDate()) || to.isAfter(window.getEndDate())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("OUT_OF_WINDOW",
                            "Requested range is outside the materialized window ("
                                    + window.getStartDate() + " – " + window.getEndDate() + ")"));
        }

        List<CalendarEvent> events = effectiveScheduleService.getAllOrgsEffectiveEventsInRange(from, to);
        Map<String, Organization> orgCache = buildOrgCache(events);
        List<ScheduleEventDto> dtos = enrichmentService.annotatePlag(toSortedDtos(events, orgCache));

        return ResponseEntity.ok(ApiResponse.ok(dtos, buildMeta(from, to, dtos.size(), window)));
    }

    // -----------------------------------------------------------------------
    // Per-org schedule — powers the app's individual shul / org detail views
    // -----------------------------------------------------------------------

    @GetMapping("/api/v1/organizations/{idOrSlug}/schedule")
    @Operation(summary = "Per-org schedule",
               description = "Returns this organization's effective events for a date or range (max 30 days). " +
                             "Accepts org ID or slug.")
    public ResponseEntity<ApiResponse<List<ScheduleEventDto>>> getOrgSchedule(
            @Parameter(description = "Organization ID or slug", example = "bmob")
            @PathVariable String idOrSlug,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        // Resolve org
        Optional<Organization> orgOpt = organizationService.findById(idOrSlug);
        if (orgOpt.isEmpty()) {
            orgOpt = organizationService.findByUrlSlug(idOrSlug);
        }
        if (orgOpt.isEmpty() || !orgOpt.get().isEnabled()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.err("NOT_FOUND", "Organization not found: " + idOrSlug));
        }
        Organization org = orgOpt.get();

        // Resolve date range
        LocalDate from, to;
        try {
            if (date != null) {
                from = to = LocalDate.parse(date);
            } else if (start != null && end != null) {
                from = LocalDate.parse(start);
                to = LocalDate.parse(end);
            } else {
                from = to = LocalDate.now();
            }
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("INVALID_DATE", "Use ISO-8601 format: YYYY-MM-DD"));
        }

        if (from.isAfter(to)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("INVALID_RANGE", "start must be before or equal to end"));
        }
        if (to.toEpochDay() - from.toEpochDay() > MAX_ORG_DAYS) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("RANGE_TOO_LARGE",
                            "Per-org range cannot exceed " + MAX_ORG_DAYS + " days"));
        }

        WindowBounds window = effectiveScheduleService.getWindowBounds();
        if (from.isBefore(window.getStartDate()) || to.isAfter(window.getEndDate())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("OUT_OF_WINDOW",
                            "Requested range is outside the materialized window ("
                                    + window.getStartDate() + " – " + window.getEndDate() + ")"));
        }

        List<CalendarEvent> events = effectiveScheduleService.getEffectiveEventsInRange(org.getId(), from, to);
        List<ScheduleEventDto> dtos = enrichmentService.annotatePlag(events.stream()
                .sorted(Comparator.comparing(CalendarEvent::getDate).thenComparing(CalendarEvent::getStartTime))
                .map(e -> ScheduleEventDto.from(e, org))
                .toList());

        return ResponseEntity.ok(ApiResponse.ok(dtos, buildMeta(from, to, dtos.size(), window)));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Build an org lookup map to avoid N+1 org fetches when converting events. */
    private Map<String, Organization> buildOrgCache(List<CalendarEvent> events) {
        Set<String> orgIds = events.stream()
                .map(CalendarEvent::getOrganizationId)
                .collect(Collectors.toSet());
        Map<String, Organization> cache = new HashMap<>();
        for (String orgId : orgIds) {
            organizationService.findById(orgId).ifPresent(o -> cache.put(orgId, o));
        }
        return cache;
    }

    private List<ScheduleEventDto> toSortedDtos(List<CalendarEvent> events, Map<String, Organization> orgCache) {
        return events.stream()
                .sorted(Comparator.comparing(CalendarEvent::getDate).thenComparing(CalendarEvent::getStartTime))
                .filter(e -> orgCache.containsKey(e.getOrganizationId()))
                .map(e -> ScheduleEventDto.from(e, orgCache.get(e.getOrganizationId())))
                .toList();
    }

    private Map<String, Object> buildMeta(LocalDate from, LocalDate to, int count, WindowBounds window) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (from.equals(to)) {
            meta.put("date", from.toString());
        } else {
            meta.put("start", from.toString());
            meta.put("end", to.toString());
        }
        meta.put("count", count);
        meta.put("windowStart", window.getStartDate().toString());
        meta.put("windowEnd", window.getEndDate().toString());
        return meta;
    }
}
