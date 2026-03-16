package com.tbdev.teaneckminyanim.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Organization;

/**
 * Public-facing DTO for a single materialized calendar event.
 * Returned by the /schedule endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduleEventDto(
        String id,
        String date,               // ISO-8601: "2026-03-15"
        String startTime,          // "HH:mm" e.g. "07:00"
        String minyanType,         // enum name: "SHACHARIS"
        String minyanTypeDisplay,  // human label: "Shacharis"
        OrgSummary organization,
        String locationName,
        String notes,
        String nusach,
        String nusachDisplay,
        String dynamicTimeString,  // "NETZ+5min" for rules-based, null for imported
        String source,             // "RULES" | "IMPORTED" | "MANUAL"
        String whatsapp
) {
    /**
     * Compact org info embedded in each event so the client needs no extra call.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrgSummary(String id, String name, String slug, String color, String whatsapp) {}

    /** Return a copy with a different notes value (used for plag annotation at API response time). */
    public ScheduleEventDto withNotes(String notes) {
        return new ScheduleEventDto(id, date, startTime, minyanType, minyanTypeDisplay,
                organization, locationName, notes, nusach, nusachDisplay,
                dynamicTimeString, source, whatsapp);
    }

    public static ScheduleEventDto from(CalendarEvent event, Organization org) {
        OrgSummary orgSummary = new OrgSummary(
                org.getId(),
                org.getName(),
                org.getUrlSlug(),
                org.getOrgColor() != null ? org.getOrgColor() : "#000000",
                org.getWhatsapp()
        );

        var nusach = event.getNusach() != null ? event.getNusach() : org.getNusach();

        return new ScheduleEventDto(
                "cal-" + event.getId(),
                event.getDate().toString(),
                event.getStartTime().toString().substring(0, 5), // "HH:mm"
                event.getMinyanType().name(),
                event.getMinyanType().displayName(),
                orgSummary,
                event.getLocationName(),
                event.getNotes(),
                nusach != null ? nusach.name() : null,
                nusach != null ? nusach.displayName() : null,
                event.getDynamicTimeString(),
                event.getSource().name(),
                event.getWhatsapp()
        );
    }
}
