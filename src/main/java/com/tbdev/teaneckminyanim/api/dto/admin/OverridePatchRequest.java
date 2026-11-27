package com.tbdev.teaneckminyanim.api.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body of {@code PATCH /api/v1/organizations/{idOrSlug}/overrides/{eventId}}. Omitted or null fields are left
 * unchanged. An empty string clears notes, locationName, locationId (which also clears the location name) and nusach
 * (which falls back to the shul's nusach).
 */
@Schema(description = "Partial update of one override. Omitted or null fields stay as they are; an empty string "
        + "clears notes, locationId, locationName or nusach")
public record OverridePatchRequest(
        @Schema(description = "24-hour HH:mm", example = "19:00") String startTime,
        @Schema(allowableValues = {"ADDITIVE", "FULL_DAY_REPLACE"}) String overrideMode,
        String notes,
        String locationId,
        String locationName,
        String nusach,
        @Schema(description = "false hides the override without deleting it") Boolean enabled) {

    public boolean isEmpty() {
        return startTime == null && overrideMode == null && notes == null && locationId == null
                && locationName == null && nusach == null && enabled == null;
    }
}
