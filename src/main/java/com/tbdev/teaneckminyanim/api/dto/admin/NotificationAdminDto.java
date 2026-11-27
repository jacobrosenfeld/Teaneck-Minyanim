package com.tbdev.teaneckminyanim.api.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tbdev.teaneckminyanim.model.Notification;
import io.swagger.v3.oas.annotations.media.Schema;

/** Full view of a notification for super-admin API callers, including inactive ones. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A site-wide banner or popup, including its state and bookkeeping fields.")
public record NotificationAdminDto(
        String id,
        String title,
        String message,
        @Schema(allowableValues = {"BANNER", "POPUP"}) String type,
        boolean enabled,
        @Schema(description = "ISO-8601 local date-time in the site time zone; omitted if it never expires.") String expirationDate,
        @Schema(description = "Stop showing after this many views; omitted if unlimited.") Integer maxDisplays,
        String createdAt,
        String updatedAt
) {
    public static NotificationAdminDto from(Notification n) {
        return new NotificationAdminDto(
                n.getId(), n.getTitle(), n.getMessage(), n.getType(),
                Boolean.TRUE.equals(n.getEnabled()),
                n.getExpirationDate() == null ? null : n.getExpirationDate().toString(),
                n.getMaxDisplays(),
                n.getCreatedAt() == null ? null : n.getCreatedAt().toString(),
                n.getUpdatedAt() == null ? null : n.getUpdatedAt().toString());
    }
}
