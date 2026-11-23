package com.tbdev.teaneckminyanim.api.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body of {@code POST /api/v1/notifications}. */
@Schema(description = "A new notification. `enabled` defaults to true.")
public record NotificationCreateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Yom Tov hours") String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Schedules change next week.") String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"BANNER", "POPUP"}) String type,
        Boolean enabled,
        @Schema(description = "ISO-8601 date-time, e.g. 2026-12-31T23:59:00 (site time zone unless an offset is given).")
        String expirationDate,
        @Schema(description = "At least 1 when given.") Integer maxDisplays
) {}
