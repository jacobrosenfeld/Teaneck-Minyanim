package com.tbdev.teaneckminyanim.api.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/** Result of deleting an override. */
@Schema(description = "Confirmation that an override was deleted")
public record OverrideDeletedDto(boolean deleted, String id) {
}
