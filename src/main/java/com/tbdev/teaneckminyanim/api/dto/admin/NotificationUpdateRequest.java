package com.tbdev.teaneckminyanim.api.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Documentation shape of the {@code PATCH /api/v1/notifications/{id}} body. The controller reads the raw
 * JSON so it can tell an omitted field (unchanged) from an explicit {@code null} (clears expirationDate or
 * maxDisplays).
 */
@Schema(description = "Partial update. Omitted fields are unchanged; null clears expirationDate or maxDisplays.")
public record NotificationUpdateRequest(
        String title,
        String message,
        @Schema(allowableValues = {"BANNER", "POPUP"}) String type,
        Boolean enabled,
        String expirationDate,
        Integer maxDisplays
) {}
