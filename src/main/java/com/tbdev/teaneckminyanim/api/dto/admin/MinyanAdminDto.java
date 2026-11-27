package com.tbdev.teaneckminyanim.api.dto.admin;

import com.tbdev.teaneckminyanim.model.Minyan;
import io.swagger.v3.oas.annotations.media.Schema;

/** Admin view of a minyan rule, including disabled ones. */
@Schema(description = "A minyan rule as seen by shul admins, including its full schedule.")
public record MinyanAdminDto(
        String id,
        @Schema(example = "SHACHARIS") String type,
        boolean enabled,
        String locationId,
        String locationName,
        @Schema(example = "ASHKENAZ") String nusach,
        String notes,
        String whatsapp,
        MinyanScheduleDto schedule
) {
    public static MinyanAdminDto from(Minyan minyan, String locationName) {
        return new MinyanAdminDto(
                minyan.getId(),
                minyan.getType() != null ? minyan.getType().name() : null,
                minyan.isEnabled(),
                minyan.getLocationId(),
                locationName,
                minyan.getNusach() != null ? minyan.getNusach().name() : null,
                minyan.getNotes(),
                minyan.getWhatsapp(),
                MinyanScheduleDto.from(minyan));
    }
}
