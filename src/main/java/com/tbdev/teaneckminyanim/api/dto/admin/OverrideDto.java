package com.tbdev.teaneckminyanim.api.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.service.ManualOverrideService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.format.DateTimeFormatter;

/** A manual override row as exposed by the admin API. Never carries the entity or its internal source reference. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A manual schedule override for one shul on one date")
public record OverrideDto(
        @Schema(example = "4821") String id,
        String organizationId,
        @Schema(description = "ISO date", example = "2026-12-14") String date,
        @Schema(description = "24-hour HH:mm", example = "18:45") String startTime,
        @Schema(example = "MINCHA_MAARIV") String minyanType,
        @Schema(description = "ADDITIVE adds to the day's schedule; FULL_DAY_REPLACE shows only manual rows that day",
                allowableValues = {"ADDITIVE", "FULL_DAY_REPLACE"}) String overrideMode,
        boolean enabled,
        String notes,
        String locationId,
        String locationName,
        @Schema(example = "ASHKENAZ") String nusach,
        @Schema(description = "Username that last edited the override") String editedBy,
        @Schema(description = "ISO local date-time of the last edit (site time zone)", example = "2026-11-23T09:15:30")
        String editedAt) {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public static OverrideDto from(CalendarEvent event) {
        return new OverrideDto(
                event.getId() == null ? null : String.valueOf(event.getId()),
                event.getOrganizationId(),
                event.getDate().toString(),
                event.getStartTime().format(HH_MM),
                event.getMinyanType().name(),
                ManualOverrideService.modeOf(event),
                event.isEnabled(),
                event.getNotes(),
                event.getLocationId(),
                event.getLocationName(),
                event.getNusach() == null ? null : event.getNusach().name(),
                event.getEditedBy(),
                event.getEditedAt() == null ? null : event.getEditedAt().toString());
    }
}
