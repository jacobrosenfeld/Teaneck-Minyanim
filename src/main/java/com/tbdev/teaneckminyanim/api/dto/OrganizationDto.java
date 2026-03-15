package com.tbdev.teaneckminyanim.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tbdev.teaneckminyanim.model.Organization;

/**
 * Public-facing DTO for an Organization.
 * Excludes internal fields (calendar URL, useScrapedCalendar, enabled flag).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrganizationDto(
        String id,
        String name,
        String slug,
        String color,
        String nusach,
        String nusachDisplay,
        String address,
        String websiteUrl,
        String whatsapp
) {
    public static OrganizationDto from(Organization org) {
        return new OrganizationDto(
                org.getId(),
                org.getName(),
                org.getUrlSlug(),
                org.getOrgColor() != null ? org.getOrgColor() : "#000000",
                org.getNusach() != null ? org.getNusach().name() : null,
                org.getNusach() != null ? org.getNusach().displayName() : null,
                org.getAddress(),
                org.getWebsiteURIStr(),
                org.getWhatsapp()
        );
    }
}
