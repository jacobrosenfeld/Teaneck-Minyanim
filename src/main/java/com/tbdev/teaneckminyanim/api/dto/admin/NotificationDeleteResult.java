package com.tbdev.teaneckminyanim.api.dto.admin;

/** Result of {@code DELETE /api/v1/notifications/{id}}. */
public record NotificationDeleteResult(boolean deleted, String id) {}
