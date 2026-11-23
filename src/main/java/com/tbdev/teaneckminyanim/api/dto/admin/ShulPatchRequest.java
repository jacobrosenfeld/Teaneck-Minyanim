package com.tbdev.teaneckminyanim.api.dto.admin;

import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.service.ShulProfileCommand;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Partial update of a shul profile. Fields that are left out (or null) keep their current value. For the
 * optional text fields, an empty string clears the value.
 */
@Schema(description = "Partial update. Omitted fields are unchanged; an empty string clears an optional text field.")
public record ShulPatchRequest(
        String name,
        String address,
        String websiteUrl,
        @Schema(example = "ASHKENAZ") String nusach,
        @Schema(description = "Hex color such as #275ed8", example = "#275ed8") String color,
        @Schema(description = "Lowercase letters, numbers and hyphens. Empty generates one from the name.", example = "keter-torah") String slug,
        String whatsapp,
        @Schema(description = "http(s) base URL of the shul's calendar system") String calendarUrl,
        Boolean useScrapedCalendar
) {
    /**
     * @throws IllegalArgumentException with a message that is safe to show to API clients
     */
    public ShulProfileCommand toCommand() {
        Nusach parsedNusach = null;
        if (nusach != null) {
            parsedNusach = Nusach.fromString(nusach.trim());
            if (parsedNusach == null) {
                throw new IllegalArgumentException("Invalid nusach type.");
            }
        }
        return new ShulProfileCommand(name, address, websiteUrl, parsedNusach, color, slug, whatsapp, calendarUrl,
                useScrapedCalendar);
    }
}
