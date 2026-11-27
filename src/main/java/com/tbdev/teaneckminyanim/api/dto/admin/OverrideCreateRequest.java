package com.tbdev.teaneckminyanim.api.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body of {@code POST /api/v1/organizations/{idOrSlug}/overrides}. */
@Schema(description = "Creates one override per day from date to endDate (inclusive, at most 62 days)")
public record OverrideCreateRequest(
        @Schema(description = "First date, ISO", example = "2026-12-14", requiredMode = Schema.RequiredMode.REQUIRED)
        String date,
        @Schema(description = "Last date, ISO. Defaults to date", example = "2026-12-16") String endDate,
        @Schema(description = "24-hour HH:mm", example = "18:45", requiredMode = Schema.RequiredMode.REQUIRED)
        String startTime,
        @Schema(description = "A MinyanType name", example = "MINCHA_MAARIV",
                requiredMode = Schema.RequiredMode.REQUIRED) String minyanType,
        @Schema(description = "Defaults to ADDITIVE", allowableValues = {"ADDITIVE", "FULL_DAY_REPLACE"})
        String overrideMode,
        @Schema(description = "A location of this shul") String locationId,
        @Schema(description = "Free-text location; defaults to the chosen location's name") String locationName,
        @Schema(description = "Shown with the minyan (max 1000 characters)") String notes,
        @Schema(description = "A Nusach name. Defaults to the shul's nusach", example = "ASHKENAZ") String nusach,
        @Schema(description = "Defaults to true") Boolean enabled) {
}
