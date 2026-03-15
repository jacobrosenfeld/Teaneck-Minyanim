package com.tbdev.teaneckminyanim.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tbdev.teaneckminyanim.model.Notification;

/**
 * Public-facing DTO for an active notification/announcement.
 * maxDisplays is exposed so the mobile app can replicate the same
 * "stop showing after N views" logic used on the website.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationDto(
        String id,
        String title,
        String message,
        String type,        // "BANNER" | "POPUP"
        String expiresAt,   // ISO-8601 datetime, null if no expiry
        Integer maxDisplays // null if unlimited
) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getTitle(),
                n.getMessage(),
                n.getType(),
                n.getExpirationDate() != null ? n.getExpirationDate().toString() : null,
                n.getMaxDisplays()
        );
    }
}
