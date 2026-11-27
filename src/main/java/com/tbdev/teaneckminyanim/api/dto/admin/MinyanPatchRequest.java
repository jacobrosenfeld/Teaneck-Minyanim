package com.tbdev.teaneckminyanim.api.dto.admin;

import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.minyan.MinyanDay;
import com.tbdev.teaneckminyanim.minyan.MinyanTime;
import com.tbdev.teaneckminyanim.service.MinyanUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Partial update of a minyan. Fields that are left out (or null) keep their current value. For
 * {@code notes} and {@code whatsapp}, an empty string clears the value.
 */
@Schema(description = "Partial update. Omitted fields are unchanged; an empty string clears notes or whatsapp.")
public record MinyanPatchRequest(
        @Schema(description = "Show or hide the minyan") Boolean enabled,
        MinyanScheduleDto schedule,
        @Schema(description = "Public notes, up to 255 characters") String notes,
        @Schema(description = "A location that belongs to this shul") String locationId,
        @Schema(description = "ASHKENAZ, SEFARD, EDOT_HAMIZRACH, ARIZAL or UNSPECIFIED", example = "ASHKENAZ") String nusach,
        @Schema(description = "WhatsApp contact or group link") String whatsapp
) {
    /**
     * Builds the service command for everything except {@code enabled}.
     *
     * @throws IllegalArgumentException with a message that is safe to show to API clients
     */
    public MinyanUpdateCommand toCommand() {
        Nusach parsedNusach = null;
        if (nusach != null) {
            parsedNusach = Nusach.fromString(nusach.trim());
            if (parsedNusach == null) {
                throw new IllegalArgumentException("Invalid nusach. Use ASHKENAZ, SEFARD, EDOT_HAMIZRACH, ARIZAL or UNSPECIFIED.");
            }
        }
        Map<MinyanDay, MinyanTime> slots = schedule == null ? Map.of() : schedule.toSlots();
        return new MinyanUpdateCommand(null, locationId, parsedNusach, notes, whatsapp, slots);
    }

    /** True when the body changes anything besides {@code enabled}. */
    public boolean hasUpdateFields() {
        return schedule != null || notes != null || locationId != null || nusach != null || whatsapp != null;
    }
}
