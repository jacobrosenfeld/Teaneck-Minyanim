package com.tbdev.teaneckminyanim.api.dto.admin;

import com.tbdev.teaneckminyanim.model.Organization;
import io.swagger.v3.oas.annotations.media.Schema;

/** Admin view of a shul profile, including the fields the public organization DTO leaves out. */
@Schema(description = "A shul profile as seen by its admins.")
public record ShulAdminDto(
        String id,
        String name,
        String slug,
        String color,
        @Schema(example = "ASHKENAZ") String nusach,
        String nusachDisplay,
        String address,
        String websiteUrl,
        String whatsapp,
        String calendarUrl,
        boolean useScrapedCalendar,
        boolean enabled,
        Double latitude,
        Double longitude
) {
    public static ShulAdminDto from(Organization org) {
        return new ShulAdminDto(
                org.getId(),
                org.getName(),
                org.getUrlSlug(),
                org.getOrgColor(),
                org.getNusach().name(),
                org.getNusach().displayName(),
                org.getAddress(),
                org.getWebsiteURIStr(),
                org.getWhatsapp(),
                org.getCalendar(),
                Boolean.TRUE.equals(org.getUseScrapedCalendar()),
                org.isEnabled(),
                org.getLatitude(),
                org.getLongitude());
    }
}
